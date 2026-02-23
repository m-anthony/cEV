package com.snaky.poker.cev.ui.config

import kotlinx.serialization.Serializable

@Serializable
data class UserConfiguration(
    val version: String,
    val sources: List<HistorySource> = emptyList()
)

@Serializable
data class HistorySource(
    val name: String,
    val path: String,
    val isActive: Boolean = true
)