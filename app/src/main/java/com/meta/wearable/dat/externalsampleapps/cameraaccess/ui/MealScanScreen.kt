/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealModeViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun MealScanScreen(
    wearablesViewModel: WearablesViewModel,
    mealViewModel: MealModeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        ),
) {
  val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()
  val mealUiState by mealViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    streamViewModel.setSuppressShareDialog(true)
    streamViewModel.startStream()
    mealViewModel.startAutoScan(
        getFrame = { streamViewModel.uiState.value.videoFrame },
        triggerCapture = { streamViewModel.capturePhoto() },
    )
  }
  DisposableEffect(Unit) {
    onDispose {
      mealViewModel.stopAutoScan()
      streamViewModel.stopStream()
      streamViewModel.setSuppressShareDialog(false)
    }
  }

  // When the auto-triggered high-res capture lands, hand it to the view model for analysis.
  LaunchedEffect(streamUiState.capturedPhoto) {
    streamUiState.capturedPhoto?.let { photo ->
      streamViewModel.clearCapturedPhoto()
      mealViewModel.onMenuPhotoCaptured(photo)
      wearablesViewModel.navigateToDeviceSelection()
    }
  }

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    streamUiState.videoFrame?.let { videoFrame ->
      key(streamUiState.videoFrameCount) {
        Image(
            bitmap = videoFrame.asImageBitmap(),
            contentDescription = stringResource(R.string.live_stream),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
      }
    }
    if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
      IconButton(
          onClick = onBack,
          modifier = Modifier.align(Alignment.TopStart),
      ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.mode_back),
            tint = Color.White,
        )
      }

      Text(
          text = stringResource(R.string.meal_scan_instruction),
          color = Color.White,
          fontWeight = FontWeight.Medium,
          modifier =
              Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 56.dp, end = 56.dp),
      )

      ScanStatusPill(
          text =
              if (mealUiState.menuDetected) stringResource(R.string.meal_scan_capturing)
              else stringResource(R.string.meal_scan_searching),
          modifier =
              Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
      )
    }
  }
}

@Composable
private fun ScanStatusPill(text: String, modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
              .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        color = Color.White,
        strokeWidth = 2.dp,
    )
    Spacer(Modifier.width(10.dp))
    Text(text = text, color = Color.White, fontWeight = FontWeight.Medium)
  }
}
