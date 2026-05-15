/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MealModeViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(MealUiState())
  val uiState: StateFlow<MealUiState> = _uiState.asStateFlow()

  fun updatePreferences(text: String) {
    _uiState.update { it.copy(preferences = text) }
  }

  fun startScanning() {
    _uiState.update { it.copy(step = MealFlowStep.SCANNING, errorMessage = null) }
  }

  fun addCapturedPhoto(bitmap: Bitmap) {
    _uiState.update { state ->
      state.copy(capturedPhotos = (state.capturedPhotos + bitmap).toImmutableList())
    }
  }

  fun removePhoto(index: Int) {
    _uiState.update { state ->
      val updated = state.capturedPhotos.toMutableList()
      if (index in updated.indices) {
        updated.removeAt(index)
      }
      state.copy(capturedPhotos = updated.toImmutableList())
    }
  }

  fun backToPreferences() {
    _uiState.update { it.copy(step = MealFlowStep.PREFERENCES) }
  }

  fun finishScanningAndAnalyze() {
    val current = _uiState.value
    if (current.capturedPhotos.isEmpty()) {
      _uiState.update { it.copy(errorMessage = "Capture at least one menu photo before analyzing.") }
      return
    }
    _uiState.update {
      it.copy(step = MealFlowStep.ANALYZING, errorMessage = null, recommendation = null)
    }
    viewModelScope.launch {
      val result =
          AnthropicApiClient.recommendMeal(
              apiKey = BuildConfig.ANTHROPIC_API_KEY,
              preferences = current.preferences,
              menuPhotos = current.capturedPhotos,
          )
      result
          .onSuccess { text ->
            _uiState.update {
              it.copy(step = MealFlowStep.RESULT, recommendation = text, errorMessage = null)
            }
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  step = MealFlowStep.RESULT,
                  recommendation = null,
                  errorMessage = error.message ?: "Failed to get recommendation.",
              )
            }
          }
    }
  }

  fun retryAnalyze() {
    finishScanningAndAnalyze()
  }

  fun startOver() {
    _uiState.value = MealUiState()
  }

  fun backToScanning() {
    _uiState.update { it.copy(step = MealFlowStep.SCANNING, errorMessage = null) }
  }
}
