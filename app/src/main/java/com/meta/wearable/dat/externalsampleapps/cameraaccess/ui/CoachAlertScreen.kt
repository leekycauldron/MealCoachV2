/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

@Composable
fun CoachAlertScreen(
    roastMessage: String,
    onKeepWatching: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onStop)
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(Color(0xFF1A0000))
              .systemBarsPadding()
              .padding(horizontal = 24.dp)
              .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.weight(0.4f))
    Text(
        text = stringResource(R.string.coach_alert_title),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Black,
        color = AppColor.Red,
        fontSize = 44.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
    ) {
      Text(
          text = roastMessage,
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.fillMaxWidth(),
      )
    }
    Spacer(Modifier.size(16.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SwitchButton(
          label = stringResource(R.string.coach_alert_keep_watching),
          onClick = onKeepWatching,
      )
      SwitchButton(
          label = stringResource(R.string.coach_alert_stop),
          onClick = onStop,
          isDestructive = true,
      )
    }
    Spacer(Modifier.size(16.dp))
  }
}
