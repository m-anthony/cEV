package com.snaky.poker.cev.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaky.poker.cev.core.discovery.RoomDetector
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.ui.config.ConfigurationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger

// --- LOGIQUE MÉTIER ---

sealed class MigrationState {
    object Idle : MigrationState()
    object Warning : MigrationState()
    data class Processing(val currentSourceName: String = "") : MigrationState()
    data class Summary(val results: List<MigrationResult>) : MigrationState()
}

data class MigrationResult(val name: String, val room: Room?)

class LiveMigrationViewModel() {
    var state by mutableStateOf<MigrationState>(MigrationState.Idle)
        private set

    val shouldShowBanner: Boolean
        get() = UpdateChecker.isReady &&
                UpdateChecker.latestVersion == null &&
                !ConfigurationManager.configuration.isLiveMigrationDismissed &&
                ConfigurationManager.configuration.sources.isNotEmpty() &&
                ConfigurationManager.configuration.sources.none { it.liveRoom != null }

    fun startWorkflow() { state = MigrationState.Warning }
    fun cancel() { state = MigrationState.Idle }

    fun dismiss() {
        ConfigurationManager.dismissLiveMigration()
    }

    fun runAutoMigration() {
        logger.info { "Starting source migration" }
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {

            val currentSources = ConfigurationManager.configuration.sources
            val results = mutableListOf<MigrationResult>()

            val updatedSources = currentSources.map { source ->
                state = MigrationState.Processing(source.name)
                val detectedRoom = withContext(Dispatchers.IO) {
                    RoomDetector.detect(source.path)
                }
                results.add(MigrationResult(source.name, detectedRoom))
                if (detectedRoom != null) source.copy(liveRoom = detectedRoom) else source
            }

            ConfigurationManager.update { it.copy(sources = updatedSources) }
            dismiss() // Cache la bannière car le travail est fait
            state = MigrationState.Summary(results)
        }
    }
}

// --- COMPOSANTS GRAPHIQUES ---

@Composable
fun LiveMigrationBanner(viewModel: LiveMigrationViewModel) {
    AnimatedVisibility(
        visible = viewModel.shouldShowBanner,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3CD))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🚀 Live tracking is now available! We can auto-configure your Winamax and Unibet folders. ",
                fontSize = 13.sp, color = Color(0xFF856404)
            )
            Text(
                text = "Activate now",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0056b3),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { viewModel.startWorkflow() }
            )
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = { viewModel.dismiss() }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun MigrationDialogs(viewModel: LiveMigrationViewModel) {
    when (val s = viewModel.state) {
        MigrationState.Warning -> AlertDialog(
            onDismissRequest = { viewModel.cancel() },
            title = { Text("Live Activation") },
            text = { Text("We will scan your folders to enable Live tracking. Please close any poker software to allow file access.") },
            confirmButton = { Button(onClick = { viewModel.runAutoMigration() }) { Text("START SCAN") } },
            dismissButton = { TextButton(onClick = { viewModel.cancel() }) { Text("CANCEL") } }
        )
        is MigrationState.Processing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Analyzing folders...") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))

                    // Affichage dynamique du nom
                    Text(
                        text = "Scanning: ${s.currentSourceName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "This may take a moment on slower drives...",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {}
        )
        is MigrationState.Summary -> AlertDialog(
            onDismissRequest = { viewModel.cancel() },
            title = { Text("Analysis Complete") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val activated = s.results.filter { it.room != null }
                if (activated.isEmpty()) {
                    Text("No compatible software detected. You can configure your sources manually.")
                } else {
                    activated.forEach { res ->
                        Text(if (res.room == Room.IPOKER) "⚙️ ${res.name}: iPoker detected (Soon)" else "✅ ${res.name}: Live tracking enabled")
                    }
                }
            }},
            confirmButton = { Button(onClick = { viewModel.cancel() }) { Text("DONE") } }
        )
        else -> {}
    }
}