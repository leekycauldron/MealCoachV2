/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.ShutterMediaSession
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun MealScanScreen(
    wearablesViewModel: WearablesViewModel,
    mealViewModel: MealModeViewModel,
    onBack: () -> Unit,
    onFinish: () -> Unit,
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
  val context = LocalContext.current
  val shutterSession =
      remember(context) {
        ShutterMediaSession(
            context = context.applicationContext,
            onShutter = { streamViewModel.capturePhoto() },
        )
      }

  LaunchedEffect(Unit) {
    streamViewModel.setSuppressShareDialog(true)
    streamViewModel.startStream()
    shutterSession.start()
  }
  DisposableEffect(Unit) {
    onDispose {
      shutterSession.release()
      streamViewModel.stopStream()
      streamViewModel.setSuppressShareDialog(false)
    }
  }

  LaunchedEffect(streamUiState.capturedPhoto) {
    streamUiState.capturedPhoto?.let { photo ->
      mealViewModel.addCapturedPhoto(photo)
      streamViewModel.clearCapturedPhoto()
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
              Modifier.align(Alignment.TopCenter)
                  .padding(top = 8.dp, start = 56.dp, end = 56.dp),
      )

      Column(
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (mealUiState.capturedPhotos.isNotEmpty()) {
          LazyRow(
              modifier = Modifier.fillMaxWidth().height(96.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
          ) {
            itemsIndexed(
                items = mealUiState.capturedPhotos,
                key = { _, bitmap -> System.identityHashCode(bitmap) },
            ) { index, bitmap ->
              PhotoThumbnail(
                  bitmap = bitmap,
                  onRemove = { mealViewModel.removePhoto(index) },
              )
            }
          }
        }

        Text(
            text =
                stringResource(R.string.meal_scan_count, mealUiState.capturedPhotos.size),
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          SwitchButton(
              label = stringResource(R.string.meal_scan_done),
              onClick = onFinish,
              enabled = mealUiState.capturedPhotos.isNotEmpty(),
              modifier = Modifier.weight(1f),
          )
          CaptureButton(onClick = { streamViewModel.capturePhoto() })
        }
      }
    }
  }
}

@Composable
private fun PhotoThumbnail(bitmap: Bitmap, onRemove: () -> Unit) {
  Box(modifier = Modifier.size(96.dp)) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
    )
    IconButton(
        onClick = onRemove,
        modifier =
            Modifier.align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50)),
    ) {
      Icon(
          Icons.Default.Close,
          contentDescription = stringResource(R.string.meal_scan_remove),
          tint = Color.White,
          modifier = Modifier.size(16.dp),
      )
    }
  }
}
