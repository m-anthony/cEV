package com.snaky.poker.cev.ui.config

import com.snaky.poker.cev.ui.AppConfig
import java.io.File

object PathManager {
    private val APP_NAME = AppConfig.name

    /**
     * OS-dependant user data folder
     * Windows : %APPDATA%/Spin-cEV
     * macOS   : ~/Library/Application Support/Spin-cEV
     * Linux   : ~/.Spin-cEV
     */
    val appDataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) File(appData, APP_NAME)
                else File(userHome, "AppData/Roaming/${APP_NAME}")
            }

            os.contains("mac") -> {
                File(userHome, "Library/Application Support/${APP_NAME}")
            }

            else -> { // Linux and others
                File(userHome, ".${APP_NAME}")
            }
        }.also { if (!it.exists()) it.mkdirs() }
    }

    val configFile: File by lazy {
        File(appDataDir, "config.json")
    }
}