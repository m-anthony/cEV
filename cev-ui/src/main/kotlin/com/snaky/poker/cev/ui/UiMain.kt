package com.snaky.poker.cev.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Poker EV Calculator v${AppConfig.version}",
        state = windowState
    ) {
        MainView()
    }
}