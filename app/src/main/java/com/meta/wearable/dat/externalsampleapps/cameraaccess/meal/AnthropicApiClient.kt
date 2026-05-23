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
  private const val DETECT_MODEL = "claude-haiku-4-5-20251001"
  private const val JPEG_QUALITY = 85
  private const val MAX_TOKENS = 1024
  private const val DETECT_MAX_TOKENS = 8
  private const val TOOL_NAME = "report_recommendations"

  /** Asks Claude for the user's best menu items as structured data (via tool use). */
  suspend fun recommendMeal(
      apiKey: String,
      preferences: String,
      menuPhotos: List<Bitmap>,
  ): Result<List<MenuRecommendation>> =
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
          val body = buildRecommendBody(preferences, menuPhotos)
          val response = postJson(apiKey, body)
          Result.success(parseRecommendations(response))
        } catch (e: Exception) {
          Log.e(TAG, "recommendMeal failed", e)
          Result.failure(e)
        }
      }

  /** Fast YES/NO classifier: does [frame] show a readable restaurant menu? */
  suspend fun detectMenu(apiKey: String, frame: Bitmap): Result<Boolean> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
          return@withContext Result.failure(
              IllegalStateException("Anthropic API key is missing."))
        }
        try {
          val system =
              """
              You are a fast visual classifier. Decide whether the image clearly shows a
              restaurant menu: a list of food or drink items, typically with names and prices,
              readable enough to make a recommendation from. A plain table, food on a plate, or
              an unreadable blur is NOT a menu.
              Respond with exactly one word: YES or NO. No punctuation, no explanation.
              """
                  .trimIndent()

          val content =
              JSONArray()
                  .put(imageBlock(frame))
                  .put(textBlock("Is this a readable menu? YES or NO."))

          val root =
              JSONObject().apply {
                put("model", DETECT_MODEL)
                put("max_tokens", DETECT_MAX_TOKENS)
                put("system", system)
                put("messages", JSONArray().put(userMessage(content)))
              }

          val response = postJson(apiKey, root.toString())
          val text = extractText(response).trim().uppercase()
          Result.success(text.startsWith("YES"))
        } catch (e: Exception) {
          Log.e(TAG, "detectMenu failed", e)
          Result.failure(e)
        }
      }

  private fun buildRecommendBody(preferences: String, menuPhotos: List<Bitmap>): String {
    val systemPrompt =
        """
        You are a meal-recommendation assistant. The user photographed a restaurant menu.
        Choose the user's best options based on their preferences and report them by calling the
        $TOOL_NAME tool. Return up to 5 items, strongest first.

        For each item:
          - name: copy the dish name EXACTLY as printed on the menu (verbatim), so it can be
            located on the image. Do not paraphrase or translate.
          - score: 0.0 to 1.0, how strongly you recommend it for THIS user (1.0 = top pick).
          - reason: 1-2 short sentences on why it fits their preferences.
          - caveats: allergens, large portions, or things to watch for (omit if none).

        Only include items that actually appear on the menu. If the menu is unreadable, return an
        empty list.
        """
            .trimIndent()

    val userInstructions =
        if (preferences.isBlank()) {
          "Recommend the best items from the menu shown in the photos."
        } else {
          "My preferences: $preferences\n\nRecommend the best items from the menu shown in the photos."
        }

    val content = JSONArray()
    menuPhotos.forEach { content.put(imageBlock(it)) }
    content.put(textBlock(userInstructions))

    val root =
        JSONObject().apply {
          put("model", DEFAULT_MODEL)
          put("max_tokens", MAX_TOKENS)
          put("system", systemPrompt)
          put("tools", JSONArray().put(recommendationTool()))
          put(
              "tool_choice",
              JSONObject().apply {
                put("type", "tool")
                put("name", TOOL_NAME)
              })
          put("messages", JSONArray().put(userMessage(content)))
        }
    return root.toString()
  }

  private fun recommendationTool(): JSONObject {
    val item =
        JSONObject().apply {
          put("type", "object")
          put(
              "properties",
              JSONObject().apply {
                put("name", schema("string", "Dish name exactly as printed on the menu"))
                put("score", schema("number", "0..1 recommendation strength (1 = top pick)"))
                put("reason", schema("string", "Why it fits the user's preferences"))
                put("caveats", schema("string", "Allergens or things to watch for; optional"))
              })
          put("required", JSONArray().put("name").put("score").put("reason"))
        }

    val inputSchema =
        JSONObject().apply {
          put("type", "object")
          put(
              "properties",
              JSONObject().apply {
                put(
                    "recommendations",
                    JSONObject().apply {
                      put("type", "array")
                      put("items", item)
                    })
              })
          put("required", JSONArray().put("recommendations"))
        }

    return JSONObject().apply {
      put("name", TOOL_NAME)
      put("description", "Report the recommended menu items for the user.")
      put("input_schema", inputSchema)
    }
  }

  private fun schema(type: String, description: String): JSONObject =
      JSONObject().apply {
        put("type", type)
        put("description", description)
      }

  private fun parseRecommendations(response: String): List<MenuRecommendation> {
    val content = JSONObject(response).optJSONArray("content") ?: return emptyList()
    for (i in 0 until content.length()) {
      val block = content.getJSONObject(i)
      if (block.optString("type") != "tool_use") continue
      val items = block.optJSONObject("input")?.optJSONArray("recommendations") ?: continue
      val result = mutableListOf<MenuRecommendation>()
      for (j in 0 until items.length()) {
        val o = items.getJSONObject(j)
        val name = o.optString("name").trim()
        if (name.isEmpty()) continue
        result.add(
            MenuRecommendation(
                name = name,
                score = o.optDouble("score", 0.0).toFloat().coerceIn(0f, 1f),
                reason = o.optString("reason").trim(),
                caveats = o.optString("caveats").trim().ifEmpty { null },
            ))
      }
      return result
    }
    return emptyList()
  }

  private fun imageBlock(bitmap: Bitmap): JSONObject =
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

  private fun textBlock(text: String): JSONObject =
      JSONObject().apply {
        put("type", "text")
        put("text", text)
      }

  private fun userMessage(content: JSONArray): JSONObject =
      JSONObject().apply {
        put("role", "user")
        put("content", content)
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
