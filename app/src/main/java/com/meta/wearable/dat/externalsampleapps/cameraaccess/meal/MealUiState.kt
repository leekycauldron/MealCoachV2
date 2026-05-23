/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

import android.graphics.Bitmap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

data class MealUiState(
    val step: MealFlowStep = MealFlowStep.PREFERENCES,
    val preferences: String = "",
    val capturedPhotos: ImmutableList<Bitmap> = persistentListOf(),
    val menuDetected: Boolean = false,
    val recommendations: ImmutableList<MenuRecommendation> = persistentListOf(),
    val selectedRecommendation: MenuRecommendation? = null,
    // Dish name -> fetched image URL (null value = looked up, none found). Absent = not fetched yet.
    val dishImageUrls: ImmutableMap<String, String?> = persistentMapOf(),
    val errorMessage: String? = null,
)
