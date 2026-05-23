/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

/** A single menu item Claude recommends, plus where it sits on the menu photo (once located). */
data class MenuRecommendation(
    val name: String,
    val score: Float, // 0..1, how strongly recommended (1 = top pick)
    val reason: String,
    val caveats: String?,
    val box: NormalizedBox? = null, // null until OCR matches the name to a text box
)

/** A rectangle expressed as fractions (0..1) of the image's width/height, so it is resolution-independent. */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
  val centerX: Float
    get() = (left + right) / 2f

  val centerY: Float
    get() = (top + bottom) / 2f
}
