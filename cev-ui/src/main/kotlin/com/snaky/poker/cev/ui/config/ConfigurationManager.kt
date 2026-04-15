package com.snaky.poker.cev.ui.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.ui.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.kotlin.logger
import java.util.*
import kotlin.system.exitProcess


private const val VERSION_V1_5 = "1.5"

object ConfigurationManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val CURRENT_APP_VERSION = AppConfig.version

    private val _configFlow = MutableStateFlow(load())
    val configFlow = _configFlow.asStateFlow()

    var configuration by mutableStateOf(_configFlow.value)
        private set

    fun save(config: UserConfiguration) {
        writeToDisk(config)
        configuration = config
        _configFlow.value =  config
    }

    fun update(configModifier: (UserConfiguration) -> UserConfiguration) = save(configModifier(configuration))

    private fun writeToDisk(config: UserConfiguration) = PathManager.configFile.writeText(json.encodeToString(config))

    private fun load(): UserConfiguration {
        val configFile = PathManager.configFile

        return if (configFile.exists()) {
            try {
                val loaded = json.decodeFromString<UserConfiguration>(configFile.readText())
                handleVersionMigration(loaded).also { writeToDisk(it) }
            } catch (e: Exception) {
                logger.warn(e) {"Cannot load existing config, please delete the current one if it cannot be fixed"}
                exitProcess(1)
            }
        } else {
            createNewConfig()
        }

    }

    private fun handleVersionMigration(loaded: UserConfiguration): UserConfiguration {
        if (loaded.version == CURRENT_APP_VERSION || CURRENT_APP_VERSION.contains("SNAPSHOT")) {
            return loaded
        }
        var migrated = loaded
        if(isVersionOlder(loaded.version, VERSION_V1_5)) migrated = migrated.addSourceUUID()
        
        return migrated.copy(version = CURRENT_APP_VERSION)
    }

    private fun createNewConfig(): UserConfiguration {
        return UserConfiguration(
            version = CURRENT_APP_VERSION,
            isLiveMigrationDismissed = true
        ).also { save(it) }
    }

    private fun isVersionOlder(current: String, target: String): Boolean {
        if (current == target) return false

        val currentParts = current.split('.', '-').filter { it.isNotBlank() }
        val targetParts = target.split('.', '-').filter { it.isNotBlank() }

        // On compare jusqu'au bout de la version la plus courte
        val minLength = minOf(currentParts.size, targetParts.size)

        for (i in 0 until minLength) {
            val curr = currentParts[i]
            val targ = targetParts[i]

            if (curr == targ) continue
            val currNum = curr.toIntOrNull()
            val targNum = targ.toIntOrNull()

            return if (currNum != null && targNum != null) {
                currNum < targNum
            } else {
                curr < targ
            }
        }

        return currentParts.size < targetParts.size
    }


    fun dismissLiveMigration() {
        update { it.copy(isLiveMigrationDismissed = true) }
    }
}

private fun UserConfiguration.addSourceUUID(): UserConfiguration {
    val migratedSources = sources.map { source ->
        if (source.id.isBlank()) {
            source.copy(id = UUID.randomUUID().toString())
        } else {
            source
        }
    }
    return copy(sources = migratedSources)
}
