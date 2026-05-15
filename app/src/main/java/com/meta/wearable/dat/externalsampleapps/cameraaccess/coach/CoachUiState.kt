/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

data class CoachUiState(
    val step: CoachFlowStep = CoachFlowStep.PREFERENCES,
    val avoidances: String = "",
    val isWatching: Boolean = false,
    val roastMessage: String? = null,
    val lastError: String? = null,
    val ttsReady: Boolean = false,
)
