package com.snaky.poker.cev.ui

import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.snaky.poker.cev.core.FileCrawler.processFileOrDirectory
import com.snaky.poker.cev.core.parsers.MetaParser
import com.snaky.poker.cev.ui.config.PathManager
import com.snaky.poker.cev.ui.model.MainViewModel
import com.snaky.poker.cev.ui.model.PokerCalculatorAPI
import com.snaky.poker.cev.ui.model.ProcessingResults
import com.snaky.poker.cev.ui.model.ProcessingStats
import com.snaky.poker.cev.ui.theme.DefaultTheme
import com.snaky.poker.cev.ui.view.MainView
import kotlinx.coroutines.sync.Mutex
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import java.io.File
import java.lang.invoke.MethodHandles
import javax.imageio.ImageIO

private val logger : Logger by lazy {
    LogManager.getLogger(MethodHandles.lookup().lookupClass())
}

fun main() = application {

    val logDir = File(PathManager.appDataDir, "logs").apply {
        if (!exists()) mkdirs()
    }
    System.setProperty("logDir", logDir.absolutePath)
    Configurator.reconfigure()
    logger.info("Application started, version = {}", AppConfig.version)

    val myApi = object : PokerCalculatorAPI {

        private var parser : MetaParser? = null
        private val mutex = Mutex()

        override val currentSpinCount get() = parser?.currentSpinCount ?: 0

        override suspend fun calculateFromDirectories(directories: List<File>): ProcessingResults {
            if(!mutex.tryLock()) throw IllegalStateException("Busy")
            try {
                with(MetaParser()){
                    parser = this
                    directories.forEach { processFileOrDirectory(it, this) }
                    waitForResults()
                    close()
                    return ProcessingResults(
                        spins, ProcessingStats(
                            incompleteSpinCount = invalidSpins,
                            duplicateHandCount = duplicateHands,
                            validSpinCount = spins.size
                        )
                    )
                }
            } finally {
                parser = null
                mutex.unlock()
            }
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
        DefaultTheme.initialize()
        MainView(viewModel)
    }
}