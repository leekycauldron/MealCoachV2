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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

@Composable
fun MealPreferencesScreen(
    preferences: String,
    onPreferencesChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .systemBarsPadding()
              .padding(horizontal = 24.dp)
              .navigationBarsPadding()
              .imePadding()
              .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.Start,
  ) {
    Spacer(Modifier.size(8.dp))
    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
      Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.mode_back),
      )
    }
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.meal_prefs_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.meal_prefs_description),
        color = Color.Gray,
    )
    Spacer(Modifier.size(24.dp))
    OutlinedTextField(
        value = preferences,
        onValueChange = onPreferencesChange,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        placeholder = { Text(stringResource(R.string.meal_prefs_hint)) },
    )
    Spacer(Modifier.size(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SwitchButton(
          label = stringResource(R.string.meal_prefs_continue),
          onClick = onContinue,
      )
    }
    Spacer(Modifier.size(24.dp))
  }
}
