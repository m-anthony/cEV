package com.snaky.poker.cev.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.snaky.poker.cev.core.MetaParser
import com.snaky.poker.cev.core.Spin
import com.snaky.poker.cev.core.processFileOrDirectory
import java.io.File

fun main() = application {
    val myApi = object : PokerCalculatorAPI {
        override suspend fun calculateFromDirectory(directory: File): Map<String, Spin> {
            val parser = MetaParser()
            processFileOrDirectory(directory, parser)
            parser.waitForResults()
            parser.close()
            return parser.spins
        }
    }

    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 700.dp
    )

    val viewModel = MainViewModel(myApi)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Spin cEV Calculator v${AppConfig.version}",
        state = windowState
    ) {
        MainView(viewModel)
    }
}