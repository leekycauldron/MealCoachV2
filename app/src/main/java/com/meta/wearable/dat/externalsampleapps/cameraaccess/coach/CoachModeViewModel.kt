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
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import java.util.Locale
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
  }

  private val _uiState = MutableStateFlow(CoachUiState())
  val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

  private var watchJob: Job? = null
  private var frameProvider: (() -> Bitmap?)? = null

  private val tts: TextToSpeech =
      TextToSpeech(application.applicationContext) { status ->
        val ready = status == TextToSpeech.SUCCESS
        if (ready) {
          tts.language = Locale.getDefault()
        } else {
          Log.w(TAG, "TextToSpeech init failed (status=$status)")
        }
        _uiState.update { it.copy(ttsReady = ready) }
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
    speak(roast)
  }

  private fun speak(text: String) {
    if (!_uiState.value.ttsReady) {
      Log.w(TAG, "TTS not ready; skipping speak")
      return
    }
    val utteranceId = "coach-roast-${System.currentTimeMillis()}"
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
  }

  fun resumeWatching() {
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

  fun stopMonitoring() {
    watchJob?.cancel()
    watchJob = null
    frameProvider = null
    if (tts.isSpeaking) tts.stop()
    _uiState.update { it.copy(isWatching = false) }
  }

  fun resetFlow() {
    stopMonitoring()
    _uiState.value = _uiState.value.copy(
        step = CoachFlowStep.PREFERENCES,
        roastMessage = null,
        lastError = null,
    )
  }

  fun goBackToPreferences() {
    stopMonitoring()
    _uiState.update {
      it.copy(step = CoachFlowStep.PREFERENCES, roastMessage = null, lastError = null)
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopMonitoring()
    tts.shutdown()
  }
}
