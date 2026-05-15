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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

@Composable
fun ComingSoonScreen(
    onBack: () -> Unit,
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
      Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.mode_back),
      )
    }
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.coming_soon_title),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.coming_soon_description),
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    SwitchButton(
        label = stringResource(R.string.mode_back),
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(16.dp))
  }
}
