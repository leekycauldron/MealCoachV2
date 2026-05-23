/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class CoachUiState(
    val step: CoachFlowStep = CoachFlowStep.PREFERENCES,
    val avoidances: String = "",
    val isWatching: Boolean = false,
    val log: ImmutableList<FoodLogEntry> = persistentListOf(),
    val lastRoast: String? = null, // most recent roast text, for a brief on-screen banner
    val lastError: String? = null,
    val ttsReady: Boolean = false,
)
