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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealFlowStep
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealUiState

@Composable
fun MealResultScreen(
    state: MealUiState,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onStartOver: () -> Unit,
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
  ) {
    Spacer(Modifier.size(24.dp))

    when {
      state.step == MealFlowStep.ANALYZING -> {
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator()
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.meal_analyzing),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
      }
      state.errorMessage != null -> {
        Text(
            text = stringResource(R.string.meal_result_error_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = state.errorMessage,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        ButtonStack(
            buttons =
                listOf(
                    stringResource(R.string.meal_result_retry) to onRetry,
                    stringResource(R.string.meal_result_rescan) to onRescan,
                    stringResource(R.string.meal_result_start_over) to onStartOver,
                ),
        )
      }
      else -> {
        Text(
            text = stringResource(R.string.meal_result_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        ) {
          Text(
              text = state.recommendation.orEmpty(),
              style = MaterialTheme.typography.bodyLarge,
          )
        }
        Spacer(Modifier.size(16.dp))
        ButtonStack(
            buttons =
                listOf(
                    stringResource(R.string.meal_result_rescan) to onRescan,
                    stringResource(R.string.meal_result_start_over) to onStartOver,
                ),
        )
      }
    }
    Spacer(Modifier.size(16.dp))
  }
}

@Composable
private fun ButtonStack(buttons: List<Pair<String, () -> Unit>>) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    buttons.forEachIndexed { index, (label, action) ->
      SwitchButton(
          label = label,
          onClick = action,
          isDestructive = index == buttons.lastIndex && buttons.size > 1,
      )
    }
  }
}
