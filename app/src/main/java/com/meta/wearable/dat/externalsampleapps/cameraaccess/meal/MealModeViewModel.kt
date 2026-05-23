/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MealModeViewModel : ViewModel() {

  companion object {
    private const val TAG = "MealMode:ViewModel"
    private const val POLL_INTERVAL_MS = 2_000L
    private const val MAX_CONSECUTIVE_ERRORS = 3
    // If the high-res capture never arrives, fall back to the detection frame after this long.
    private const val CAPTURE_TIMEOUT_MS = 5_000L
  }

  private val _uiState = MutableStateFlow(MealUiState())
  val uiState: StateFlow<MealUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null
  private var captureTimeoutJob: Job? = null
  private var frameProvider: (() -> Bitmap?)? = null
  private var captureTrigger: (() -> Unit)? = null

  fun updatePreferences(text: String) {
    _uiState.update { it.copy(preferences = text) }
  }

  fun startScanning() {
    _uiState.update {
      it.copy(
          step = MealFlowStep.SCANNING,
          menuDetected = false,
          errorMessage = null,
          recommendations = persistentListOf(),
          selectedRecommendation = null,
          dishImageUrls = persistentMapOf(),
      )
    }
  }

  /**
   * Begins automatic scanning: polls camera frames, asks Claude whether each shows a menu, and on
   * the first hit triggers a high-res capture via [triggerCapture]. The captured photo is delivered
   * back through [onMenuPhotoCaptured].
   */
  fun startAutoScan(getFrame: () -> Bitmap?, triggerCapture: () -> Unit) {
    frameProvider = getFrame
    captureTrigger = triggerCapture
    if (scanJob?.isActive == true) return
    _uiState.update { it.copy(menuDetected = false, errorMessage = null) }
    scanJob = viewModelScope.launch { scanLoop() }
  }

  private suspend fun scanLoop() {
    var consecutiveErrors = 0
    while (currentCoroutineContext().isActive) {
      val frame = frameProvider?.invoke()
      if (frame == null) {
        delay(POLL_INTERVAL_MS)
        continue
      }
      val apiKey = BuildConfig.ANTHROPIC_API_KEY
      AnthropicApiClient.detectMenu(apiKey, frame).fold(
          onSuccess = { isMenu ->
            consecutiveErrors = 0
            if (isMenu) {
              onMenuDetected(frame)
              return
            }
          },
          onFailure = { error ->
            Log.e(TAG, "Menu detection failed", error)
            consecutiveErrors += 1
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
              _uiState.update {
                it.copy(
                    step = MealFlowStep.RESULT,
                    errorMessage = error.message ?: "Couldn't scan the menu. Try again.",
                )
              }
              return
            }
          },
      )
      delay(POLL_INTERVAL_MS)
    }
  }

  private fun onMenuDetected(detectionFrame: Bitmap) {
    scanJob?.cancel()
    _uiState.update { it.copy(menuDetected = true) }
    captureTrigger?.invoke()

    // Copy the frame so the stream's buffer pool can't recycle it before the fallback runs.
    val fallbackFrame = detectionFrame.copy(Bitmap.Config.ARGB_8888, false)
    captureTimeoutJob?.cancel()
    captureTimeoutJob =
        viewModelScope.launch {
          delay(CAPTURE_TIMEOUT_MS)
          if (_uiState.value.step == MealFlowStep.SCANNING) {
            Log.w(TAG, "High-res capture timed out; analyzing the detection frame instead")
            analyze(listOf(fallbackFrame))
          }
        }
  }

  /** Called by the scan screen when the SDK delivers the high-res photo. */
  fun onMenuPhotoCaptured(photo: Bitmap) {
    if (_uiState.value.step != MealFlowStep.SCANNING) return
    analyze(listOf(photo))
  }

  private fun analyze(photos: List<Bitmap>) {
    scanJob?.cancel()
    scanJob = null
    captureTimeoutJob?.cancel()
    captureTimeoutJob = null

    if (photos.isEmpty()) {
      _uiState.update {
        it.copy(step = MealFlowStep.RESULT, errorMessage = "No menu was captured.")
      }
      return
    }

    _uiState.update {
      it.copy(
          step = MealFlowStep.ANALYZING,
          capturedPhotos = photos.toImmutableList(),
          recommendations = persistentListOf(),
          selectedRecommendation = null,
          dishImageUrls = persistentMapOf(),
          errorMessage = null,
      )
    }

    viewModelScope.launch {
      val bitmap = photos.first()
      // Claude (recommendations) and ML Kit (OCR boxes) are independent — run them together.
      val recsDeferred =
          async {
            AnthropicApiClient.recommendMeal(
                apiKey = BuildConfig.ANTHROPIC_API_KEY,
                preferences = _uiState.value.preferences,
                menuPhotos = photos,
            )
          }
      val ocrDeferred = async { MenuTextLocator.recognize(bitmap) }

      recsDeferred
          .await()
          .onSuccess { recs ->
            val lines = ocrDeferred.await()
            val located = MenuTextLocator.match(recs, lines, bitmap.width, bitmap.height)
            _uiState.update {
              it.copy(
                  step = MealFlowStep.RESULT,
                  recommendations = located.toImmutableList(),
                  errorMessage =
                      if (located.isEmpty()) "No menu items found. Try rescanning." else null,
              )
            }
          }
          .onFailure { error ->
            ocrDeferred.cancel()
            _uiState.update {
              it.copy(
                  step = MealFlowStep.RESULT,
                  recommendations = persistentListOf(),
                  errorMessage = error.message ?: "Failed to get recommendation.",
              )
            }
          }
    }
  }

  fun selectRecommendation(item: MenuRecommendation) {
    _uiState.update { it.copy(selectedRecommendation = item) }
    fetchDishImage(item.name)
  }

  fun dismissDetail() {
    _uiState.update { it.copy(selectedRecommendation = null) }
  }

  private fun fetchDishImage(name: String) {
    if (_uiState.value.dishImageUrls.containsKey(name)) return
    viewModelScope.launch {
      val url =
          PexelsImageClient.firstImageUrl(
                  apiKey = BuildConfig.PEXELS_API_KEY,
                  query = "$name food",
              )
              .getOrNull()
      _uiState.update { it.copy(dishImageUrls = (it.dishImageUrls + (name to url)).toImmutableMap()) }
    }
  }

  fun retryAnalyze() {
    analyze(_uiState.value.capturedPhotos)
  }

  fun stopAutoScan() {
    scanJob?.cancel()
    scanJob = null
    captureTimeoutJob?.cancel()
    captureTimeoutJob = null
    frameProvider = null
    captureTrigger = null
  }

  fun startOver() {
    stopAutoScan()
    _uiState.value = MealUiState()
  }

  fun backToPreferences() {
    stopAutoScan()
    _uiState.update { it.copy(step = MealFlowStep.PREFERENCES, menuDetected = false) }
  }

  fun backToScanning() {
    _uiState.update {
      it.copy(
          step = MealFlowStep.SCANNING,
          menuDetected = false,
          errorMessage = null,
          recommendations = persistentListOf(),
          selectedRecommendation = null,
          dishImageUrls = persistentMapOf(),
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopAutoScan()
  }
}
