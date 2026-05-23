/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device OCR (ML Kit) + fuzzy matching that turns Claude's recommended dish names into pixel
 * locations on the menu photo. Detection and recognition are independent, so callers run
 * [recognize] concurrently with the Claude request and then [match].
 */
object MenuTextLocator {

  private const val TAG = "MealMode:TextLocator"
  // Require this fraction of a dish name's words to appear in an OCR line before we trust the match.
  private const val MATCH_THRESHOLD = 0.6f

  data class OcrLine(val text: String, val box: Rect)

  suspend fun recognize(bitmap: Bitmap): List<OcrLine> =
      suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer
            .process(image)
            .addOnSuccessListener { text ->
              val lines = mutableListOf<OcrLine>()
              for (block in text.textBlocks) {
                for (line in block.lines) {
                  line.boundingBox?.let { lines.add(OcrLine(line.text, it)) }
                }
              }
              recognizer.close()
              if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { e ->
              Log.e(TAG, "OCR failed", e)
              recognizer.close()
              if (cont.isActive) cont.resume(emptyList())
            }
      }

  /** Returns the recommendations with a [NormalizedBox] attached wherever a confident match exists. */
  fun match(
      recommendations: List<MenuRecommendation>,
      lines: List<OcrLine>,
      imageWidth: Int,
      imageHeight: Int,
  ): List<MenuRecommendation> {
    if (lines.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return recommendations
    return recommendations.map { rec ->
      val line = bestLine(rec.name, lines) ?: return@map rec
      rec.copy(
          box =
              NormalizedBox(
                  left = line.box.left.toFloat() / imageWidth,
                  top = line.box.top.toFloat() / imageHeight,
                  right = line.box.right.toFloat() / imageWidth,
                  bottom = line.box.bottom.toFloat() / imageHeight,
              ))
    }
  }

  private fun bestLine(name: String, lines: List<OcrLine>): OcrLine? {
    val nameTokens = tokenize(name)
    if (nameTokens.isEmpty()) return null
    var best: OcrLine? = null
    var bestScore = 0f
    for (line in lines) {
      val lineTokens = tokenize(line.text)
      if (lineTokens.isEmpty()) continue
      val overlap = nameTokens.count { it in lineTokens }.toFloat() / nameTokens.size
      if (overlap > bestScore) {
        bestScore = overlap
        best = line
      }
    }
    return if (bestScore >= MATCH_THRESHOLD) best else null
  }

  private fun tokenize(s: String): Set<String> =
      s.lowercase()
          .replace(Regex("[^a-z0-9 ]"), " ")
          .split(Regex("\\s+"))
          .filter { it.length > 1 }
          .toSet()
}
