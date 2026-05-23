/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.coach.FoodLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal data class LogTotals(
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val count: Int,
)

internal fun totalsOf(log: List<FoodLogEntry>): LogTotals {
  var cal = 0
  var p = 0f
  var c = 0f
  var f = 0f
  for (e in log) {
    cal += e.calories
    p += e.proteinG
    c += e.carbsG
    f += e.fatG
  }
  return LogTotals(cal, p, c, f, log.size)
}

@Composable
internal fun TotalsBar(totals: LogTotals, textColor: Color, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text("${totals.calories} cal", color = textColor, fontWeight = FontWeight.Bold)
    Text("P ${totals.protein.roundToInt()}g", color = textColor)
    Text("C ${totals.carbs.roundToInt()}g", color = textColor)
    Text("F ${totals.fat.roundToInt()}g", color = textColor)
  }
}

@Composable
internal fun FoodLogRow(
    entry: FoodLogEntry,
    textColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        Modifier.size(8.dp)
            .background(if (entry.isJunk) Color(0xFFE53935) else Color(0xFF43A047), CircleShape))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(
          text = entry.displayName + if (!entry.fromDataset) "  ~est" else "",
          color = textColor,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text =
              "${entry.calories} cal · P${entry.proteinG.roundToInt()} " +
                  "C${entry.carbsG.roundToInt()} F${entry.fatG.roundToInt()}",
          color = mutedColor,
          fontSize = 12.sp,
      )
    }
    Text(formatTime(entry.timestampMs), color = mutedColor, fontSize = 12.sp)
  }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(ms: Long): String = timeFormat.format(Date(ms))
