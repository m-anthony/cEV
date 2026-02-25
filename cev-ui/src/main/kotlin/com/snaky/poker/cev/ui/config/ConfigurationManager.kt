package com.snaky.poker.cev.ui.config

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.ui.AppConfig
import kotlinx.serialization.json.Json

object ConfigurationManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val CURRENT_APP_VERSION = AppConfig.version

    var configuration by mutableStateOf(load())
        private set

    fun save(config: UserConfiguration = configuration) {
        PathManager.configFile.writeText(json.encodeToString(config))
        configuration = config
    }

    private fun load(): UserConfiguration {
        val configFile = PathManager.configFile

        return if (configFile.exists()) {
            try {
                val loaded = json.decodeFromString<UserConfiguration>(configFile.readText())
                handleVersionMigration(loaded)
            } catch (_: Exception) {
                createNewConfig()
            }
        } else {
            createNewConfig()
        }

    }

    private fun handleVersionMigration(loaded: UserConfiguration): UserConfiguration {
        if (loaded.version == CURRENT_APP_VERSION || CURRENT_APP_VERSION.contains("SNAPSHOT")) {
            return loaded
        }
        return loaded.copy(version = CURRENT_APP_VERSION).also { save(it) }
    }

    private fun createNewConfig() = UserConfiguration(version = CURRENT_APP_VERSION).also { save(it) }



}