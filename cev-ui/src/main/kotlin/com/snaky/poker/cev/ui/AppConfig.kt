package com.snaky.poker.cev.ui

import java.util.Properties

object AppConfig {
    val version: String by lazy {
        val props = Properties()
        val inputStream = AppConfig::class.java.getResourceAsStream("/version.properties")
        if (inputStream != null) {
            props.load(inputStream)
            props.getProperty("version") ?: "0.0.0-dev"
        } else {
            "dev"
        }
    }
}