/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.meal

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Fetches a representative dish photo URL from the Pexels stock-photo API (free key, no project). */
object PexelsImageClient {

  private const val TAG = "MealMode:PexelsImage"
  private const val ENDPOINT = "https://api.pexels.com/v1/search"

  suspend fun firstImageUrl(apiKey: String, query: String): Result<String?> =
      withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
          return@withContext Result.failure(IllegalStateException("Pexels API key is missing."))
        }
        val url = "$ENDPOINT?query=${enc(query)}&per_page=1&orientation=landscape"
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
          conn.requestMethod = "GET"
          conn.connectTimeout = 15_000
          conn.readTimeout = 20_000
          // Pexels authenticates with the raw key in the Authorization header (no "Bearer ").
          conn.setRequestProperty("Authorization", apiKey)

          val code = conn.responseCode
          if (code !in 200..299) {
            val error = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            throw RuntimeException("HTTP $code: ${error ?: "Pexels image search failed"}")
          }
          val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
          val photos = JSONObject(response).optJSONArray("photos")
          val link =
              if (photos != null && photos.length() > 0) {
                photos.getJSONObject(0).optJSONObject("src")?.optString("large")?.ifBlank { null }
              } else {
                null
              }
          Result.success(link)
        } catch (e: Exception) {
          Log.e(TAG, "firstImageUrl failed", e)
          Result.failure(e)
        } finally {
          conn.disconnect()
        }
      }

  private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
