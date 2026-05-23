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
    private const val POLL_INTERVAL_MS = 10_000L
    private const val COOLDOWN_MS = 30_000L
    private const val MAX_CONSECUTIVE_ERRORS = 3
    // Delay before speaking so the camera stream's stop/disconnect sound finishes first.
    private const val TTS_START_DELAY_MS = 1_500L
  }

  private val _uiState = MutableStateFlow(CoachUiState())
  val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

  private var watchJob: Job? = null
  private var speakJob: Job? = null
  private var frameProvider: (() -> Bitmap?)? = null
  private var mediaPlayer: MediaPlayer? = null

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
    _uiState.update {
      it.copy(step = CoachFlowStep.MONITORING, roastMessage = null, lastError = null)
    }
  }

  fun startMonitoring(getFrame: () -> Bitmap?) {
    frameProvider = getFrame
    if (watchJob?.isActive == true) return
    _uiState.update { it.copy(isWatching = true, lastError = null) }
    watchJob = viewModelScope.launch { watchLoop(initialDelay = POLL_INTERVAL_MS) }
  }

  private suspend fun watchLoop(initialDelay: Long) {
    var consecutiveErrors = 0
    delay(initialDelay)
    while (currentCoroutineContext().isActive) {
      val frame = frameProvider?.invoke()
      if (frame == null) {
        delay(POLL_INTERVAL_MS)
        continue
      }
      val avoidances = _uiState.value.avoidances
      val apiKey = BuildConfig.ANTHROPIC_API_KEY

      val detectResult = CoachApiClient.detectJunkFood(apiKey, avoidances, frame)
      detectResult.fold(
          onSuccess = { isJunk ->
            consecutiveErrors = 0
            if (isJunk) {
              val roastResult = CoachApiClient.generateRoast(apiKey, avoidances, frame)
              roastResult.fold(
                  onSuccess = { text -> triggerAlert(text) },
                  onFailure = { error ->
                    Log.e(TAG, "Roast generation failed", error)
                    triggerAlert(
                        "Hey — that doesn't look like part of the plan. (Couldn't reach my snark generator, but you know what you're doing.)")
                  },
              )
              return
            }
          },
          onFailure = { error ->
            Log.e(TAG, "Detection failed", error)
            consecutiveErrors += 1
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
              _uiState.update {
                it.copy(
                    isWatching = false,
                    lastError = error.message ?: "Coach watcher gave up after repeated errors.",
                )
              }
              return
            }
          },
      )
      delay(POLL_INTERVAL_MS)
    }
  }

  private fun triggerAlert(roast: String) {
    _uiState.update {
      it.copy(step = CoachFlowStep.ALERT, roastMessage = roast, isWatching = false)
    }
    speakJob?.cancel()
    speakJob =
        viewModelScope.launch {
          // Wait for the stream-end sound to finish so it doesn't talk over the roast.
          delay(TTS_START_DELAY_MS)
          if (_uiState.value.step == CoachFlowStep.ALERT) speak(roast)
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
          // Route as media so it plays through the connected glasses (A2DP) or the phone.
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

  fun resumeWatching() {
    stopSpeaking()
    if (frameProvider == null) {
      _uiState.update { it.copy(step = CoachFlowStep.MONITORING) }
      return
    }
    _uiState.update {
      it.copy(
          step = CoachFlowStep.MONITORING,
          roastMessage = null,
          isWatching = true,
          lastError = null,
      )
    }
    watchJob?.cancel()
    watchJob = viewModelScope.launch { watchLoop(initialDelay = COOLDOWN_MS) }
  }

  // Stops the watch loop only. Deliberately does NOT stop TTS: this runs when the monitoring
  // screen is disposed on the MONITORING -> ALERT transition, which is exactly when the roast
  // is starting to speak. Speech is cancelled separately via stopSpeaking() when the user
  // actually leaves the alert/coach flow.
  fun stopMonitoring() {
    watchJob?.cancel()
    watchJob = null
    frameProvider = null
    _uiState.update { it.copy(isWatching = false) }
  }

  private fun stopSpeaking() {
    speakJob?.cancel()
    speakJob = null
    releasePlayer()
  }

  fun resetFlow() {
    stopMonitoring()
    stopSpeaking()
    _uiState.value = _uiState.value.copy(
        step = CoachFlowStep.PREFERENCES,
        roastMessage = null,
        lastError = null,
    )
  }

  fun goBackToPreferences() {
    stopMonitoring()
    stopSpeaking()
    _uiState.update {
      it.copy(step = CoachFlowStep.PREFERENCES, roastMessage = null, lastError = null)
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
