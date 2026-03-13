package com.snaky.poker.cev.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.kotlin.logger
import java.awt.Desktop
import java.net.HttpURLConnection
import java.net.URI


object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/m-anthony/cEV/releases/latest"
    val CURRENT_VERSION = AppConfig.version

    fun checkForUpdates(): String? {
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


@Composable
fun UpdateBanner() {
    // State to hold the new version tag (null if no update or dev mode)
    var latestVersion by remember { mutableStateOf<String?>(null) }

    // Launch the check once when the component enters the Composition
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            latestVersion = UpdateChecker.checkForUpdates()
        }
    }

    // AnimatedVisibility makes the banner slide in smoothly
    AnimatedVisibility(
        visible = latestVersion != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3CD)) // Light yellow/amber background
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🚀 a new version ($latestVersion) is available ! ",
                fontSize = 13.sp,
                color = Color(0xFF856404)
            )
            Text(
                text = "Download update",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0056b3),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    try {
                        Desktop.getDesktop().browse(URI("https://github.com/m-anthony/cEV/releases/latest"))
                    } catch (e: Exception) {
                        logger.warn { "Could not open browser: ${e.message}" }
                    }
                }
            )
        }
    }
}