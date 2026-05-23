/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

/** A single logged sighting of a food during a coaching session. */
data class FoodLogEntry(
    val id: Long,
    val timestampMs: Long,
    val displayName: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fromDataset: Boolean, // true = macros came from foods.csv; false = model estimate
    val isJunk: Boolean,
)
