package com.snaky.poker.cev.ui

import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.snaky.poker.cev.core.MetaParser
import com.snaky.poker.cev.core.Spin
import com.snaky.poker.cev.core.processFileOrDirectory
import java.io.File
import javax.imageio.ImageIO

fun main() = application {
    val myApi = object : PokerCalculatorAPI {
        override suspend fun calculateFromDirectories(directories: List<File>): Map<String, Spin> {
            val parser = MetaParser()
            directories.forEach { processFileOrDirectory(it, parser) }
            parser.waitForResults()
            parser.close()
            return parser.spins
        }
    }

    val iconStream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
    val iconPainter = iconStream?.use { ImageIO.read(it).toPainter() }

    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 700.dp
    )

    val viewModel = MainViewModel(myApi)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Spin cEV Calculator v${AppConfig.version}",
        state = windowState,
        icon = iconPainter
    ) {
        MainView(viewModel)
    }
}