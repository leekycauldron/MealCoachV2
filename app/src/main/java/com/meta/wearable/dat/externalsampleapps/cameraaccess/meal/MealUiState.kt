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
import kotlinx.collections.immutable.persistentListOf

data class MealUiState(
    val step: MealFlowStep = MealFlowStep.PREFERENCES,
    val preferences: String = "",
    val capturedPhotos: ImmutableList<Bitmap> = persistentListOf(),
    val recommendation: String? = null,
    val errorMessage: String? = null,
)
