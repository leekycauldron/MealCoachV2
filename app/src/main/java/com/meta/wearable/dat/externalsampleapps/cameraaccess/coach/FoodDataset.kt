/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

import android.content.Context
import android.util.Log

/** One row of the nutrition dataset (app/src/main/assets/foods.csv). */
data class FoodInfo(
    val label: String,
    val displayName: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
)

/** Loads the bundled food dataset. Edit app/src/main/assets/foods.csv to change it (needs rebuild). */
object FoodDataset {

  private const val TAG = "CoachMode:FoodDataset"
  private const val ASSET = "foods.csv"

  /** Returns the dataset keyed by [normalize]d food_label. Tolerates tab- or comma-separated rows. */
  fun load(context: Context): Map<String, FoodInfo> =
      try {
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
          lines
              .drop(1) // header row
              .mapNotNull(::parseRow)
              .associateBy { normalize(it.label) }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load $ASSET", e)
        emptyMap()
      }

  /** Normalizes a label/name so dataset keys, model labels, and log keys all line up. */
  fun normalize(s: String): String = s.lowercase().trim().replace(Regex("[\\s_]+"), " ")

  private fun parseRow(line: String): FoodInfo? {
    if (line.isBlank()) return null
    val cols = if (line.contains('\t')) line.split('\t') else line.split(',')
    if (cols.size < 6) return null
    val label = cols[0].trim()
    if (label.isEmpty()) return null
    // Schema is fixed: label first, the four macros last. Anything between is the display name, so
    // joining the middle back tolerates a comma inside the name in a comma-separated file.
    val n = cols.size
    val displayName = cols.subList(1, n - 4).joinToString(",").trim()
    return FoodInfo(
        label = label,
        displayName = displayName.ifEmpty { label },
        calories = cols[n - 4].trim().toFloatOrNull()?.toInt() ?: 0,
        proteinG = cols[n - 3].trim().toFloatOrNull() ?: 0f,
        carbsG = cols[n - 2].trim().toFloatOrNull() ?: 0f,
        fatG = cols[n - 1].trim().toFloatOrNull() ?: 0f,
    )
  }
}
