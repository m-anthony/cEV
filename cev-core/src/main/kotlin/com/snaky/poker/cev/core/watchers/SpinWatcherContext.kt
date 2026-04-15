package com.snaky.poker.cev.core.watchers

import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import kotlinx.coroutines.*
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class SpinWatcherContext(
    val spinId: String,
    val historyFile: File,
    private val staleThreshold: Long = 5.minutes.inWholeMilliseconds,
    private val quietPeriod: Long = 2.seconds.inWholeMilliseconds,
    private val room: Room,
) {
    @Volatile private var isClosingSignalReceived = false
    protected var summaryFile: File? = null

    /**
     * Logique de parsing propre à la Room.
     */
    abstract suspend fun parseHistory(historyFile: File, summaryFile: File?): Spin?

    /**
     * Appelé quand le spin est officiellement marqué comme Stale.
     */
    abstract suspend fun onStaleDetected(spinId: String, historyFile: File)

    abstract suspend fun onCompleted(spin: Spin)

    abstract suspend fun onHeartbeat()


    fun CoroutineScope.startContext() = launch(CoroutineName("SpinWatcher-$room-$spinId")) { runWatcherLoop() }

    fun CoroutineScope.notifyClosing() {
        launch {
            delay(quietPeriod)
            isClosingSignalReceived = true
        }
    }

    fun attachSummary(file: File) {
        summaryFile = file
    }

    /**
     * override this for room where notifyClosing() can't be called
     */
    open fun shouldAttemptParseOnEveryUpdate(): Boolean = false

    private suspend fun runWatcherLoop() {
        var lastFileSize = historyFile.length()
        var lastActivityTime = System.currentTimeMillis()
        val pollingInterval = 5.seconds

        while (currentCoroutineContext().isActive) {
            delay(pollingInterval)
            val currentTime = System.currentTimeMillis()
            val currentSize = historyFile.length()

            if (isClosingSignalReceived) {
                if(executeFinalParse(historyFile)) return
            } else if (currentSize != lastFileSize) {
                lastFileSize = currentSize
                lastActivityTime = historyFile.lastModified()
                onHeartbeat()
                if (shouldAttemptParseOnEveryUpdate() && currentSize > 0) {
                    if (executeFinalParse(historyFile)) return
                }
            } else if (currentTime - lastActivityTime > staleThreshold) {
                onStaleDetected(spinId, historyFile)
                break
            }
        }
    }

    private suspend fun executeFinalParse(file: File) =  parseHistory(file, summaryFile)?.also { onCompleted(it) } != null
}