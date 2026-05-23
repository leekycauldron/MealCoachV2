/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

import android.app.Application
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CoachModeViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "CoachMode:ViewModel"
    // How often we send a frame to the model for food/junk analysis (core loop cadence).
    private const val POLL_INTERVAL_MS = 4_000L
    // Skip the first frame(s) right after the stream starts (often black while the camera settles).
    private const val INITIAL_DELAY_MS = 2_000L
    // Per-food de-dup: the same item won't be logged again within this window (demo double-count guard).
    private const val LOG_DEDUP_MS = 5_000L
    // Minimum time between spoken roasts so junk detection doesn't constantly go off.
    private const val ROAST_COOLDOWN_MS = 30_000L
    // After an analysis error, wait this long before trying again (instead of killing the session).
    private const val ERROR_BACKOFF_MS = 8_000L
    private const val MAX_CONSECUTIVE_ERRORS = 3
  }

  private val _uiState = MutableStateFlow(CoachUiState())
  val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

  private val dataset = FoodDataset.load(application)
  private val datasetLabels = dataset.values.map { it.label }

  private var watchJob: Job? = null
  private var roastJob: Job? = null
  private var frameProvider: (() -> Bitmap?)? = null
  private var mediaPlayer: MediaPlayer? = null

  private val lastLoggedAtMs = HashMap<String, Long>() // per-food de-dup timestamps
  private var lastRoastAtMs = 0L
  private var logIdSeq = 0L

  init {
    val configured =
        BuildConfig.ELEVENLABS_API_KEY.isNotBlank() && BuildConfig.ELEVENLABS_VOICE_ID.isNotBlank()
    if (!configured) {
      Log.w(TAG, "ElevenLabs API key/voice ID not set in local.properties; roasts will be silent")
    }
    _uiState.update { it.copy(ttsReady = configured) }
  }

  fun updateAvoidances(text: String) {
    _uiState.update { it.copy(avoidances = text) }
  }

  fun goToMonitoring() {
    lastLoggedAtMs.clear()
    lastRoastAtMs = 0L
    logIdSeq = 0L
    _uiState.update {
      it.copy(
          step = CoachFlowStep.MONITORING,
          log = persistentListOf(),
          lastRoast = null,
          lastError = null,
      )
    }
  }

  fun startMonitoring(getFrame: () -> Bitmap?) {
    frameProvider = getFrame
    if (watchJob?.isActive == true) return
    _uiState.update { it.copy(isWatching = true) }
    watchJob = viewModelScope.launch { watchLoop() }
  }

  private suspend fun watchLoop() {
    var consecutiveErrors = 0
    delay(INITIAL_DELAY_MS)
    while (currentCoroutineContext().isActive) {
      val frame = frameProvider?.invoke()
      if (frame == null) {
        delay(POLL_INTERVAL_MS)
        continue
      }
      val apiKey = BuildConfig.ANTHROPIC_API_KEY
      CoachApiClient.analyzeFrame(apiKey, _uiState.value.avoidances, datasetLabels, frame)
          .fold(
              onSuccess = { foods ->
                consecutiveErrors = 0
                if (_uiState.value.lastError != null) _uiState.update { it.copy(lastError = null) }
                foods.forEach(::logFood)
                if (foods.any { it.isJunk }) maybeRoast(frame)
              },
              onFailure = { error ->
                Log.e(TAG, "analyzeFrame failed", error)
                consecutiveErrors += 1
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                  _uiState.update { it.copy(lastError = "Coach is having trouble connecting…") }
                }
                delay(ERROR_BACKOFF_MS) // back off, but keep the session alive
              },
          )
      delay(POLL_INTERVAL_MS)
    }
  }

  private fun logFood(food: DetectedFood) {
    val key = FoodDataset.normalize(food.label ?: food.name)
    if (key.isBlank()) return
    val now = System.currentTimeMillis()
    if (now - (lastLoggedAtMs[key] ?: 0L) < LOG_DEDUP_MS) return // de-dup
    lastLoggedAtMs[key] = now

    val info = food.label?.let { dataset[FoodDataset.normalize(it)] }
    val entry =
        if (info != null) {
          FoodLogEntry(
              id = logIdSeq++,
              timestampMs = now,
              displayName = info.displayName,
              calories = info.calories,
              proteinG = info.proteinG,
              carbsG = info.carbsG,
              fatG = info.fatG,
              fromDataset = true,
              isJunk = food.isJunk,
          )
        } else {
          FoodLogEntry(
              id = logIdSeq++,
              timestampMs = now,
              displayName = food.name,
              calories = food.calories,
              proteinG = food.proteinG,
              carbsG = food.carbsG,
              fatG = food.fatG,
              fromDataset = false,
              isJunk = food.isJunk,
          )
        }
    _uiState.update { it.copy(log = (it.log + entry).toImmutableList()) }
  }

  private fun maybeRoast(frame: Bitmap) {
    val now = System.currentTimeMillis()
    if (now - lastRoastAtMs < ROAST_COOLDOWN_MS) return // global cooldown
    if (mediaPlayer != null) return // a roast is still playing
    lastRoastAtMs = now
    // Copy so the stream's buffer can't recycle the frame while the roast call runs.
    val snapshot = frame.copy(Bitmap.Config.ARGB_8888, false)
    roastJob =
        viewModelScope.launch {
          val roast =
              CoachApiClient.generateRoast(
                      BuildConfig.ANTHROPIC_API_KEY, _uiState.value.avoidances, snapshot)
                  .getOrElse { "Hey — that doesn't look like part of the plan." }
          _uiState.update { it.copy(lastRoast = roast) }
          speak(roast)
        }
  }

  private suspend fun speak(text: String) {
    if (!_uiState.value.ttsReady) {
      Log.w(TAG, "ElevenLabs not configured; skipping speak")
      return
    }
    ElevenLabsTtsClient.synthesize(
            apiKey = BuildConfig.ELEVENLABS_API_KEY,
            voiceId = BuildConfig.ELEVENLABS_VOICE_ID,
            text = text,
        )
        .onSuccess { audio -> playAudio(audio) }
        .onFailure { error -> Log.e(TAG, "ElevenLabs synthesis failed", error) }
  }

  private fun playAudio(data: ByteArray) {
    releasePlayer()
    mediaPlayer =
        MediaPlayer().apply {
          setAudioAttributes(
              AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                  .build())
          setDataSource(ByteArrayMediaDataSource(data))
          setOnPreparedListener { it.start() }
          setOnCompletionListener { releasePlayer() }
          setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
            releasePlayer()
            true
          }
          prepareAsync()
        }
  }

  private fun releasePlayer() {
    mediaPlayer?.release()
    mediaPlayer = null
  }

  /** Ends the session: stops the watch loop but keeps the log, and moves to the debrief. */
  fun endStream() {
    stopMonitoring()
    stopSpeaking()
    _uiState.update { it.copy(step = CoachFlowStep.DEBRIEF) }
  }

  fun stopMonitoring() {
    watchJob?.cancel()
    watchJob = null
    frameProvider = null
    _uiState.update { it.copy(isWatching = false) }
  }

  private fun stopSpeaking() {
    roastJob?.cancel()
    roastJob = null
    releasePlayer()
  }

  fun resetFlow() {
    stopMonitoring()
    stopSpeaking()
    lastLoggedAtMs.clear()
    lastRoastAtMs = 0L
    _uiState.update {
      it.copy(
          step = CoachFlowStep.PREFERENCES,
          log = persistentListOf(),
          lastRoast = null,
          lastError = null,
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopMonitoring()
    stopSpeaking()
  }
}

/** Lets [MediaPlayer] stream the in-memory MP3 returned by ElevenLabs without a temp file. */
private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
  override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
    if (position >= data.size) return -1
    val count = minOf(size, data.size - position.toInt())
    System.arraycopy(data, position.toInt(), buffer, offset, count)
    return count
  }

  override fun getSize(): Long = data.size.toLong()

  override fun close() {}
}
