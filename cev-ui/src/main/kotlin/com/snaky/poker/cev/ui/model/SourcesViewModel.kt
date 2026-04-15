package com.snaky.poker.cev.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.core.discovery.RoomDetector
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.HistorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SourcesViewModel() {
    val isDirty: Boolean get() = draftConfig != ConfigurationManager.configuration
    var draftConfig by mutableStateOf(ConfigurationManager.configuration)
        private set
    var isDetecting by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun updateSources(newSources: List<HistorySource>) {
        draftConfig = draftConfig.copy(sources = newSources)
    }

    fun detectRoomForPath(path: String, onDetected: (Room) -> Unit) {
        scope.launch {
            isDetecting = true
            val detected = withContext(Dispatchers.IO) {
                RoomDetector.detect(path)
            }
            isDetecting = false
            detected?.let { onDetected(it) }
        }
    }

    fun clear() {
        scope.cancel()
    }

    fun save() {
        ConfigurationManager.save(draftConfig)
    }
}