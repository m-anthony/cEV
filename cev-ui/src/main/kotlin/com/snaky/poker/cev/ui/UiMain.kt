package com.snaky.poker.cev.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.snaky.poker.cev.core.FileCrawler.processFileOrDirectory
import com.snaky.poker.cev.core.parsers.MetaParser
import com.snaky.poker.cev.ui.config.PathManager
import com.snaky.poker.cev.ui.model.*
import com.snaky.poker.cev.ui.theme.DefaultTheme
import com.snaky.poker.cev.ui.view.LiveWidgetView
import com.snaky.poker.cev.ui.view.MainView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import java.awt.Window
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.invoke.MethodHandles
import javax.imageio.ImageIO

private val logger : Logger by lazy {
    LogManager.getLogger(MethodHandles.lookup().lookupClass())
}

fun main(args: Array<String>)  {

    val logDir = File(PathManager.appDataDir, "logs").apply {
        if (!exists()) mkdirs()
    }

    val errFile = File(logDir, "console_log.txt")
    val printStream = PrintStream(FileOutputStream(errFile, false))
    System.setErr(printStream)

    // Check if the CLI mode is requested
    val cliIndex = args.indexOf("--cli")

    if (cliIndex != -1 && args.size > cliIndex + 1) {

        val fqcn = args[cliIndex + 1]
        val toolArgs = args.drop(cliIndex + 2).toTypedArray()

        try {
            invokeExternalTool(fqcn, toolArgs)
        } catch (e: Exception) {
            // We print the error and exit to avoid launching the UI in case of failure
            System.err.println("Error: Failed to invoke CLI tool '$fqcn'")
            e.printStackTrace(System.err)
        }
        return
    }



    System.setProperty("logDir", logDir.absolutePath)
    Configurator.reconfigure()
    logger.info("Application started, version = {}", AppConfig.version)

    application {
        val myApi = object : PokerCalculatorAPI {

            private var parser: MetaParser? = null
            private val mutex = Mutex()

            override val currentSpinCount get() = parser?.currentSpinCount ?: 0

            override suspend fun calculateFromDirectories(directories: List<File>): ProcessingResults {
                if (!mutex.tryLock()) throw IllegalStateException("Busy")
                try {
                    with(MetaParser()) {
                        parser = this
                        directories.forEach { processFileOrDirectory(it, this) }
                        waitForResults()
                        close()
                        return ProcessingResults(
                            spins.mapTo(ArrayList(spins.size)) { (_, s) -> s.toLightModel() },
                            ProcessingStats(
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
        var mainWindowInstance: Window? by remember { mutableStateOf(null) }

        val viewModel = remember { MainViewModel(myApi) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Spin cEV Calculator v${AppConfig.version}",
            state = windowState,
            icon = iconPainter
        ) {
            LaunchedEffect(Unit) {
                mainWindowInstance = window
            }

            DefaultTheme.initialize()
            MainView(viewModel)
        }



        val liveModel = viewModel.liveSessionModel

        val showWidget by remember {
            liveModel.uiState
                .map { it.liveWidgetConfig != null && it.status == SessionStatus.TRACKING }
                .distinctUntilChanged()
        }.collectAsState(false)

        if (showWidget && liveModel.enabled) {
            val initialConfig = remember { liveModel.uiState.value.liveWidgetConfig!! }

            val startPosition = remember {
                if (initialConfig.x == 0 && initialConfig.y == 0 && mainWindowInstance != null) {
                    // Default position = centered in main window
                    val mainX = mainWindowInstance!!.x
                    val mainY = mainWindowInstance!!.y
                    val mainW = mainWindowInstance!!.width
                    val mainH = mainWindowInstance!!.height

                    WindowPosition(
                        (mainX + (mainW - 180) / 2).dp,
                        (mainY + (mainH - 60) / 2).dp
                    )
                } else {
                    WindowPosition(initialConfig.x.dp, initialConfig.y.dp)
                }
            }

            val widgetState = rememberWindowState(
                position = startPosition,
                size = DpSize(180.dp, 60.dp)
            )

            Window(
                onCloseRequest = { liveModel.toggleLiveWidget(false) },
                state = widgetState,
                alwaysOnTop = true,
                transparent = true,
                undecorated = true,
                resizable = false,
                focusable = true,
                title = "Live Stats",
                icon = iconPainter
            ) {
                val currentState by liveModel.uiState.collectAsState()

                LaunchedEffect(widgetState.position) {
                    val pos = widgetState.position
                    if (pos is WindowPosition.Absolute) {
                        liveModel.updateWidgetPosition(pos.x.value.toInt(), pos.y.value.toInt())
                    }
                }

                LiveWidgetView(
                    uiState = currentState,
                    onDoubleClick = {
                        (mainWindowInstance as? javax.swing.JFrame)?.let { frame ->
                            val isMinimized = frame.extendedState == javax.swing.JFrame.ICONIFIED

                            if (isMinimized || !frame.isVisible) {
                                frame.extendedState = javax.swing.JFrame.NORMAL
                                frame.isVisible = true
                                frame.toFront()
                            } else {
                                frame.extendedState = javax.swing.JFrame.ICONIFIED
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun invokeExternalTool(fqcn: String, toolArgs: Array<String>) {
    try {
        System.err.println("[DEBUG] Tentative de chargement : $fqcn")
        val clazz = Class.forName(fqcn)
        val mainMethod = clazz.getMethod("main", Array<String>::class.java)

        System.err.println("[DEBUG] Methode trouvee. Args: ${toolArgs.size}")

        // Cast explicite vers Array<Any?> pour satisfaire la signature
        // invoke(Object obj, Object... args) de Java
        val argsForInvoke = arrayOf<Any?>(toolArgs)

        mainMethod.invoke(null, *argsForInvoke)

        System.err.println("[DEBUG] Execution terminee.")
    } catch (e: Exception) {
        System.err.println("[ERREUR] Echec lors de l'invocation")
        val cause = e.cause ?: e
        cause.printStackTrace(System.err)
    }
}
