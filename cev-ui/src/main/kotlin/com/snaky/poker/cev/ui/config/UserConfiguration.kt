package com.snaky.poker.cev.ui.config

import com.snaky.poker.cev.core.model.Room
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class UserConfiguration(
    val version: String,
    val sources: List<HistorySource> = emptyList(),
    val liveWidgetConfig: LiveWidgetConfig? = LiveWidgetConfig(),
    val showLiveResults: Boolean = false,
    val isLiveMigrationDismissed: Boolean = false,
)

@Serializable
data class HistorySource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val isActive: Boolean = true,
    val liveRoom: Room? = null
)

@Serializable
data class LiveWidgetConfig(
    val x: Int = 0,
    val y: Int = 0,
)