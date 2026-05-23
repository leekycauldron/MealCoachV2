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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
    onEnd: () -> Unit,
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

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
    ) {
      coachUiState.lastRoast?.let { Banner(text = it, container = Color(0xCC222222)) }
      coachUiState.lastError?.let {
        Spacer(Modifier.size(8.dp))
        Banner(text = it, container = Color(0xCC8B0000))
      }

      Spacer(Modifier.weight(1f))

      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                  .padding(12.dp),
      ) {
        TotalsBar(totals = totalsOf(coachUiState.log), textColor = Color.White)
        Spacer(Modifier.size(8.dp))
        if (coachUiState.log.isEmpty()) {
          Text(stringResource(R.string.coach_log_empty), color = Color.White.copy(alpha = 0.7f))
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            items(items = coachUiState.log.asReversed(), key = { it.id }) { entry ->
              FoodLogRow(
                  entry = entry,
                  textColor = Color.White,
                  mutedColor = Color.White.copy(alpha = 0.7f),
              )
            }
          }
        }
      }

      Spacer(Modifier.size(12.dp))
      Column(modifier = Modifier.navigationBarsPadding()) {
        SwitchButton(
            label = stringResource(R.string.coach_end_session),
            onClick = onEnd,
            isDestructive = true,
        )
      }
    }
  }
}

@Composable
private fun Banner(text: String, container: Color) {
  Text(
      text = text,
      color = Color.White,
      fontWeight = FontWeight.Medium,
      modifier =
          Modifier.fillMaxWidth().background(container, RoundedCornerShape(8.dp)).padding(12.dp),
  )
}
