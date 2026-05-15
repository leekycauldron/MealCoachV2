/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.AppMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

@Composable
fun ModeSelectionScreen(
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .systemBarsPadding()
              .padding(horizontal = 24.dp)
              .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Spacer(Modifier.size(32.dp))
    Text(
        text = stringResource(R.string.mode_selection_title),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.mode_selection_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.weight(1f))

    ModeCard(
        iconRes = R.drawable.camera_icon,
        title = stringResource(R.string.mode_meal_title),
        description = stringResource(R.string.mode_meal_description),
        onClick = { onModeSelected(AppMode.MEAL) },
    )
    ModeCard(
        iconRes = R.drawable.walking_icon,
        title = stringResource(R.string.mode_coach_title),
        description = stringResource(R.string.mode_coach_description),
        onClick = { onModeSelected(AppMode.COACH) },
    )

    Spacer(Modifier.weight(1f))
  }
}

@Composable
private fun ModeCard(
    iconRes: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      contentPadding = PaddingValues(20.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = AppColor.DeepBlue,
              contentColor = Color.White,
          ),
  ) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(40.dp),
    )
    Spacer(Modifier.size(16.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
      Text(
          text = description,
          fontSize = 13.sp,
          color = Color.White.copy(alpha = 0.85f),
      )
    }
  }
}
