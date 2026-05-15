/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

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

object CoachApiClient {

  private const val TAG = "CoachMode:ApiClient"
  private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
  private const val ANTHROPIC_VERSION = "2023-06-01"
  private const val HAIKU_MODEL = "claude-haiku-4-5-20251001"
  private const val SONNET_MODEL = "claude-sonnet-4-6"
  private const val JPEG_QUALITY = 75
  private const val DETECT_MAX_TOKENS = 8
  private const val ROAST_MAX_TOKENS = 200

  suspend fun detectJunkFood(
      apiKey: String,
      avoidances: String,
      frame: Bitmap,
  ): Result<Boolean> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
          return@withContext Result.failure(
              IllegalStateException("Anthropic API key is missing."))
        }
        try {
          val avoidanceLine =
              if (avoidances.isBlank()) {
                "The user is generally trying to avoid junk food."
              } else {
                "The user is trying to avoid: $avoidances."
              }
          val system =
              """
              You are a fast visual classifier. Decide whether the image shows the user
              about to consume junk food they are trying to avoid. $avoidanceLine
              Junk food examples: fast food, fried food, chips, candy, sugary drinks,
              donuts, pastries, ice cream, processed snacks.
              Respond with exactly one word: YES or NO. No punctuation, no explanation.
              """
                  .trimIndent()

          val body =
              buildBody(
                  model = HAIKU_MODEL,
                  maxTokens = DETECT_MAX_TOKENS,
                  system = system,
                  imagePart = bitmapPart(frame),
                  textPart = "Is this junk food the user should avoid? YES or NO.",
              )
          val response = postJson(apiKey, body)
          val text = extractText(response).trim().uppercase()
          val isJunk = text.startsWith("YES")
          Result.success(isJunk)
        } catch (e: Exception) {
          Log.e(TAG, "detectJunkFood failed", e)
          Result.failure(e)
        }
      }

  suspend fun generateRoast(
      apiKey: String,
      avoidances: String,
      frame: Bitmap,
  ): Result<String> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
          return@withContext Result.failure(
              IllegalStateException("Anthropic API key is missing."))
        }
        try {
          val avoidanceLine =
              if (avoidances.isBlank()) {
                "They are trying to eat better."
              } else {
                "They told you they are trying to avoid: $avoidances."
              }
          val system =
              """
              You are the user's witty, sarcastic friend who keeps them honest about
              their eating goals. $avoidanceLine

              You just spotted them about to eat something off-limits. Write ONE short
              roast (1-2 sentences, max ~30 words) that will make them put it down.
              Be playful, specific to what is in the image, and a little dramatic.
              Do NOT be cruel, preachy, or use slurs. No emojis. No quotation marks.
              Output only the roast text, nothing else.
              """
                  .trimIndent()

          val body =
              buildBody(
                  model = SONNET_MODEL,
                  maxTokens = ROAST_MAX_TOKENS,
                  system = system,
                  imagePart = bitmapPart(frame),
                  textPart = "Roast me about what I'm looking at right now.",
              )
          val response = postJson(apiKey, body)
          val text = extractText(response).trim().trim('"', '“', '”')
          Result.success(text)
        } catch (e: Exception) {
          Log.e(TAG, "generateRoast failed", e)
          Result.failure(e)
        }
      }

  private fun bitmapPart(bitmap: Bitmap): JSONObject {
    return JSONObject().apply {
      put("type", "image")
      put(
          "source",
          JSONObject().apply {
            put("type", "base64")
            put("media_type", "image/jpeg")
            put("data", bitmapToBase64Jpeg(bitmap))
          })
    }
  }

  private fun buildBody(
      model: String,
      maxTokens: Int,
      system: String,
      imagePart: JSONObject,
      textPart: String,
  ): String {
    val content =
        JSONArray()
            .put(imagePart)
            .put(JSONObject().apply { put("type", "text").put("text", textPart) })

    val message =
        JSONObject().apply {
          put("role", "user")
          put("content", content)
        }

    val root =
        JSONObject().apply {
          put("model", model)
          put("max_tokens", maxTokens)
          put("system", system)
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
      conn.connectTimeout = 15_000
      conn.readTimeout = 30_000
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
        if (sb.isNotEmpty()) sb.append("\n")
        sb.append(block.optString("text"))
      }
    }
    return sb.toString().ifBlank { response }
  }
}
