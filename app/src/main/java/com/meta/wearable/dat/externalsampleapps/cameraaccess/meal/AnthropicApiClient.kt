/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AnthropicApiClient {

  private const val TAG = "MealMode:AnthropicApiClient"
  private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
  private const val ANTHROPIC_VERSION = "2023-06-01"
  private const val DEFAULT_MODEL = "claude-sonnet-4-6"
  private const val JPEG_QUALITY = 85
  private const val MAX_TOKENS = 1024

  suspend fun recommendMeal(
      apiKey: String,
      preferences: String,
      menuPhotos: List<Bitmap>,
  ): Result<String> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
          return@withContext Result.failure(
              IllegalStateException(
                  "Anthropic API key is missing. Add 'anthropic_api_key=...' to local.properties."))
        }
        if (menuPhotos.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("No menu photos captured."))
        }

        try {
          val body = buildRequestBody(preferences, menuPhotos)
          val response = postJson(apiKey, body)
          val text = extractText(response)
          Result.success(text)
        } catch (e: Exception) {
          Log.e(TAG, "Anthropic API call failed", e)
          Result.failure(e)
        }
      }

  private fun buildRequestBody(preferences: String, menuPhotos: List<Bitmap>): String {
    val systemPrompt =
        """
        You are a meal-recommendation assistant. The user is at a restaurant and has
        photographed the menu. Look carefully at the menu image(s) and recommend the
        single best menu item for the user based on their stated preferences.

        Format your response as:
        - **Recommendation:** <name of the dish exactly as it appears on the menu>
        - **Why:** 2-3 short sentences explaining why this item fits the user's preferences.
        - **Heads up:** Any allergens, large portions, or caveats they should know.

        If the menu photo is unreadable, say so plainly.
        """
            .trimIndent()

    val userInstructions =
        if (preferences.isBlank()) {
          "Recommend the best item from the menu shown in the photos."
        } else {
          "My preferences: $preferences\n\nRecommend the best item from the menu shown in the photos."
        }

    val content = JSONArray()
    menuPhotos.forEach { bitmap ->
      val imageObj =
          JSONObject().apply {
            put("type", "image")
            put(
                "source",
                JSONObject().apply {
                  put("type", "base64")
                  put("media_type", "image/jpeg")
                  put("data", bitmapToBase64Jpeg(bitmap))
                })
          }
      content.put(imageObj)
    }
    content.put(JSONObject().apply { put("type", "text").put("text", userInstructions) })

    val message =
        JSONObject().apply {
          put("role", "user")
          put("content", content)
        }

    val root =
        JSONObject().apply {
          put("model", DEFAULT_MODEL)
          put("max_tokens", MAX_TOKENS)
          put("system", systemPrompt)
          put("messages", JSONArray().put(message))
        }
    return root.toString()
  }

  private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
  }

  private fun postJson(apiKey: String, body: String): String {
    val url = URL(ENDPOINT)
    val conn = url.openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"
      conn.connectTimeout = 30_000
      conn.readTimeout = 60_000
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setRequestProperty("x-api-key", apiKey)
      conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)

      conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
      if (code !in 200..299) {
        throw RuntimeException("HTTP $code: $response")
      }
      return response
    } finally {
      conn.disconnect()
    }
  }

  private fun extractText(response: String): String {
    val root = JSONObject(response)
    val content = root.optJSONArray("content") ?: return response
    val sb = StringBuilder()
    for (i in 0 until content.length()) {
      val block = content.getJSONObject(i)
      if (block.optString("type") == "text") {
        if (sb.isNotEmpty()) sb.append("\n\n")
        sb.append(block.optString("text"))
      }
    }
    return sb.toString().ifBlank { response }
  }
}
