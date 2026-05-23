/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.coach.CoachUiState

@Composable
fun CoachDebriefScreen(
    state: CoachUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .systemBarsPadding()
              .padding(horizontal = 24.dp)
              .navigationBarsPadding(),
  ) {
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.coach_debrief_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.size(12.dp))

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(16.dp)) {
        TotalsBar(
            totals = totalsOf(state.log),
            textColor = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.coach_items_logged, state.log.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.size(16.dp))

    if (state.log.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.coach_log_empty), color = Color.Gray)
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        items(items = state.log, key = { it.id }) { entry ->
          FoodLogRow(
              entry = entry,
              textColor = MaterialTheme.colorScheme.onSurface,
              mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Spacer(Modifier.size(12.dp))
    SwitchButton(label = stringResource(R.string.coach_debrief_done), onClick = onDone)
    Spacer(Modifier.size(12.dp))
  }
}
