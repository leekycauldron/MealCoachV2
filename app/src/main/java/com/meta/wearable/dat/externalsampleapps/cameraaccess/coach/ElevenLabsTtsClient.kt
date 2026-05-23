/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.coach

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ElevenLabsTtsClient {

  private const val TAG = "CoachMode:ElevenLabs"
  private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech/"
  private const val MODEL_ID = "eleven_multilingual_v2"

  /** Synthesizes [text] with the given voice and returns the spoken audio as MP3 bytes. */
  suspend fun synthesize(apiKey: String, voiceId: String, text: String): Result<ByteArray> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || voiceId.isBlank()) {
          return@withContext Result.failure(
              IllegalStateException("ElevenLabs API key or voice ID is missing."))
        }
        val conn = (URL(BASE_URL + voiceId).openConnection() as HttpURLConnection)
        try {
          conn.requestMethod = "POST"
          conn.connectTimeout = 15_000
          conn.readTimeout = 30_000
          conn.doOutput = true
          conn.setRequestProperty("Content-Type", "application/json")
          conn.setRequestProperty("Accept", "audio/mpeg")
          conn.setRequestProperty("xi-api-key", apiKey)

          val body = JSONObject().apply {
            put("text", text)
            put("model_id", MODEL_ID)
          }
          conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

          val code = conn.responseCode
          if (code !in 200..299) {
            val error =
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            throw RuntimeException("HTTP $code: ${error ?: "ElevenLabs request failed"}")
          }
          val audio = conn.inputStream.use { it.readBytes() }
          Result.success(audio)
        } catch (e: Exception) {
          Log.e(TAG, "synthesize failed", e)
          Result.failure(e)
        } finally {
          conn.disconnect()
        }
      }
}
