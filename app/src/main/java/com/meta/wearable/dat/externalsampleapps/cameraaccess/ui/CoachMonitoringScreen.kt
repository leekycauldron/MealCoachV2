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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.coach.CoachModeViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun CoachMonitoringScreen(
    wearablesViewModel: WearablesViewModel,
    coachViewModel: CoachModeViewModel,
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
  val coachUiState by coachViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    streamViewModel.setSuppressShareDialog(true)
    streamViewModel.startStream()
    coachViewModel.startMonitoring { streamViewModel.uiState.value.videoFrame }
  }
  DisposableEffect(Unit) {
    onDispose {
      coachViewModel.stopMonitoring()
      streamViewModel.stopStream()
      streamViewModel.setSuppressShareDialog(false)
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

      WatchingChip(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
      )

      Column(
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth(),
      ) {
        coachUiState.lastError?.let { err ->
          Text(
              text = err,
              color = Color.White,
              modifier =
                  Modifier.fillMaxWidth()
                      .background(Color(0xCC8B0000), RoundedCornerShape(8.dp))
                      .padding(12.dp),
          )
          Spacer(Modifier.size(12.dp))
        }
        SwitchButton(
            label = stringResource(R.string.coach_stop),
            onClick = onBack,
            isDestructive = true,
        )
      }
    }
  }
}

@Composable
private fun WatchingChip(modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
              .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.size(8.dp)
                .background(Color(0xFF4CD964), shape = RoundedCornerShape(50)),
    )
    Spacer(Modifier.width(8.dp))
    Text(
        text = stringResource(R.string.coach_status_watching),
        color = Color.White,
        fontWeight = FontWeight.Medium,
    )
  }
}

