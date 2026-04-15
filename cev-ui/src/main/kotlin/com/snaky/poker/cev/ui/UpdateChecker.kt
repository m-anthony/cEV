package com.snaky.poker.cev.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import kotlin.time.Duration.Companion.seconds


object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/m-anthony/cEV/releases/latest"
    val CURRENT_VERSION = AppConfig.version

    var latestVersion by mutableStateOf<String?>(null)
        private set

    var isReady by mutableStateOf(false)
        private set

    suspend fun initialize() {
        if (isReady) return

        try {
            val result = withTimeoutOrNull(5.seconds) {
                withContext(Dispatchers.IO) {
                    checkForUpdates()
                }
            }
            latestVersion = result
        } catch (_: Exception) {
        } finally {
            isReady = true
        }
    }

    private fun checkForUpdates(): String? {
        // Short-circuit if we are in a dev environment
        if (CURRENT_VERSION.contains("SNAPSHOT")) return null

        return try {
            val url = URI.create(GITHUB_API_URL).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Spin-cEV-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = Json.parseToJsonElement(response).jsonObject
                val latestTag = json["tag_name"]?.jsonPrimitive?.content

                if (latestTag != null && isNewer(latestTag, CURRENT_VERSION)) {
                    latestTag
                } else null
            } else null
        } catch (_: Exception) {
            null // Silent fail to not annoy the user
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        // Add your logic to compare semantic versions properly
        val l = latest.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val c = current.replace("v", "").split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

        // Simple list comparison (1.0.2 > 1.0.1)
        for (i in 0 until minOf(l.size, c.size)) {
            if (l[i] > c[i]) return true
            if (l[i] < c[i]) return false
        }
        return l.size > c.size
    }
}


