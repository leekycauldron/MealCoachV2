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

/** A food/drink the model spotted in a frame. [label] is set when it matched a known dataset label. */
data class DetectedFood(
    val label: String?,
    val name: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val isJunk: Boolean,
)

object CoachApiClient {

  private const val TAG = "CoachMode:ApiClient"
  private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
  private const val ANTHROPIC_VERSION = "2023-06-01"
  private const val HAIKU_MODEL = "claude-haiku-4-5-20251001"
  private const val SONNET_MODEL = "claude-sonnet-4-6"
  private const val JPEG_QUALITY = 75
  private const val ANALYZE_MAX_TOKENS = 1024
  private const val ROAST_MAX_TOKENS = 200
  private const val TOOL_NAME = "report_foods"

  /** Identifies foods in [frame] with nutrition + a junk flag. Matched items echo a known [knownLabels] label. */
  suspend fun analyzeFrame(
      apiKey: String,
      avoidances: String,
      knownLabels: List<String>,
      frame: Bitmap,
  ): Result<List<DetectedFood>> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
          return@withContext Result.failure(IllegalStateException("Anthropic API key is missing."))
        }
        try {
          val avoidanceLine =
              if (avoidances.isBlank()) "generally trying to eat healthier"
              else "trying to avoid: $avoidances"
          val system =
              """
              You are a nutrition vision assistant analyzing a live first-person camera frame.
              Identify each distinct food or drink item the person could eat or drink in the frame.
              Report every item by calling $TOOL_NAME. For each item:
                - label: if it clearly matches one of the KNOWN LABELS, copy that label EXACTLY;
                  otherwise null.
                - name: a short human-readable name.
                - calories, protein_g, carbs_g, fat_g: best estimate for ONE typical serving.
                - is_junk: true if it is junk / off-plan for someone $avoidanceLine.
              If there is no food or drink in the frame, return an empty foods array.
              """
                  .trimIndent()

          val labelsText = if (knownLabels.isEmpty()) "(none)" else knownLabels.joinToString(", ")
          val content =
              JSONArray()
                  .put(bitmapPart(frame))
                  .put(
                      textBlock(
                          "KNOWN LABELS: $labelsText\n\nIdentify the food or drink in this frame."))

          val root =
              JSONObject().apply {
                put("model", HAIKU_MODEL)
                put("max_tokens", ANALYZE_MAX_TOKENS)
                put("system", system)
                put("tools", JSONArray().put(foodsTool()))
                put(
                    "tool_choice",
                    JSONObject().apply {
                      put("type", "tool")
                      put("name", TOOL_NAME)
                    })
                put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject().apply {
                              put("role", "user")
                              put("content", content)
                            }))
              }

          Result.success(parseFoods(postJson(apiKey, root.toString())))
        } catch (e: Exception) {
          Log.e(TAG, "analyzeFrame failed", e)
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
          return@withContext Result.failure(IllegalStateException("Anthropic API key is missing."))
        }
        try {
          val avoidanceLine =
              if (avoidances.isBlank()) "They are trying to eat better."
              else "They told you they are trying to avoid: $avoidances."
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

  private fun foodsTool(): JSONObject {
    val item =
        JSONObject().apply {
          put("type", "object")
          put(
              "properties",
              JSONObject().apply {
                put("label", schema("string", "Exact known label if matched, else null"))
                put("name", schema("string", "Short human-readable food/drink name"))
                put("calories", schema("number", "Calories for one typical serving"))
                put("protein_g", schema("number", "Protein grams"))
                put("carbs_g", schema("number", "Carb grams"))
                put("fat_g", schema("number", "Fat grams"))
                put("is_junk", schema("boolean", "True if junk / off-plan for this user"))
              })
          put(
              "required",
              JSONArray()
                  .put("name")
                  .put("calories")
                  .put("protein_g")
                  .put("carbs_g")
                  .put("fat_g")
                  .put("is_junk"))
        }
    val inputSchema =
        JSONObject().apply {
          put("type", "object")
          put(
              "properties",
              JSONObject().apply {
                put(
                    "foods",
                    JSONObject().apply {
                      put("type", "array")
                      put("items", item)
                    })
              })
          put("required", JSONArray().put("foods"))
        }
    return JSONObject().apply {
      put("name", TOOL_NAME)
      put("description", "Report every food/drink item visible in the frame.")
      put("input_schema", inputSchema)
    }
  }

  private fun parseFoods(response: String): List<DetectedFood> {
    val content = JSONObject(response).optJSONArray("content") ?: return emptyList()
    for (i in 0 until content.length()) {
      val block = content.getJSONObject(i)
      if (block.optString("type") != "tool_use") continue
      val foods = block.optJSONObject("input")?.optJSONArray("foods") ?: continue
      val result = mutableListOf<DetectedFood>()
      for (j in 0 until foods.length()) {
        val o = foods.getJSONObject(j)
        val name = o.optString("name").trim()
        if (name.isEmpty()) continue
        val label = o.optString("label").trim().ifEmpty { null }
        result.add(
            DetectedFood(
                label = if (label.equals("null", ignoreCase = true)) null else label,
                name = name,
                calories = o.optDouble("calories", 0.0).toInt(),
                proteinG = o.optDouble("protein_g", 0.0).toFloat(),
                carbsG = o.optDouble("carbs_g", 0.0).toFloat(),
                fatG = o.optDouble("fat_g", 0.0).toFloat(),
                isJunk = o.optBoolean("is_junk", false),
            ))
      }
      return result
    }
    return emptyList()
  }

  private fun schema(type: String, description: String): JSONObject =
      JSONObject().apply {
        put("type", type)
        put("description", description)
      }

  private fun textBlock(text: String): JSONObject =
      JSONObject().apply {
        put("type", "text")
        put("text", text)
      }

  private fun bitmapPart(bitmap: Bitmap): JSONObject =
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

  private fun buildBody(
      model: String,
      maxTokens: Int,
      system: String,
      imagePart: JSONObject,
      textPart: String,
  ): String {
    val content = JSONArray().put(imagePart).put(textBlock(textPart))
    val message =
        JSONObject().apply {
          put("role", "user")
          put("content", content)
        }
    return JSONObject()
        .apply {
          put("model", model)
          put("max_tokens", maxTokens)
          put("system", system)
          put("messages", JSONArray().put(message))
        }
        .toString()
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
