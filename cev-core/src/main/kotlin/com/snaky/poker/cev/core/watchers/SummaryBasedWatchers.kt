package com.snaky.poker.cev.core.watchers

import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.parsers.AbstractRoomParser
import com.snaky.poker.cev.core.parsers.UnibetParser
import com.snaky.poker.cev.core.parsers.WinamaxParser
import com.snaky.poker.cev.core.useSafeInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.KotlinLogger
import org.apache.logging.log4j.kotlin.loggerOf
import java.io.File


abstract class SummaryBasedWatcher(
    parentScope: CoroutineScope,
    handHistoryRegex: String,
    tournamentSummaryRegex: String,
    private val parserFactory: () -> AbstractRoomParser,
    private val room: Room,
    private val log: KotlinLogger,
    private val eventPublisher: suspend (WatcherEvent) -> Unit,
) : RoomWatcher {

    private val mutex = Mutex() // needed to access mutable state
    private var staleHistoriesBySpinIds = hashMapOf<String, File>()
    private val spinContexts = hashMapOf<String, SpinWatcherContext>()
    private val hhRegex = Regex(handHistoryRegex, RegexOption.IGNORE_CASE)
    private val tsRegex = Regex(tournamentSummaryRegex, RegexOption.IGNORE_CASE)
    private val scope =
        parentScope + SupervisorJob(parentScope.coroutineContext[Job]) + CoroutineName(this::class.simpleName ?: "")

    final override suspend fun onFileCreated(file: File) {
        val spinId = extractSpinId(file) ?: return
        mutex.withLock {
            var context = spinContexts[spinId]
            val isSummary = tsRegex.matches(file.name)
            if(context == null){
                val historyFile = staleHistoriesBySpinIds.remove(spinId)
                context = if(historyFile != null){
                    // context was falsely declared as stale and destroyed, create a new one to properly finish the spin
                    createSpinContext(spinId, historyFile)
                } else if(!isSummary) {
                    // new spin started
                    createSpinContext(spinId, file)
                } else {
                    //summary file for an unknown spin -> ignore
                    return@withLock
                }
                spinContexts[spinId] = context
                publishInProgress()
            }
            if (isSummary) {
                context.apply {
                    attachSummary(file)
                    scope.notifyClosing()
                }
            }
        }
    }

    private fun extractSpinId(file: File): String? {
        val fileName = file.name
        return hhRegex.find(fileName)?.groupValues?.get(1)
            ?: tsRegex.find(fileName)?.groupValues?.get(1)
    }

    private fun createSpinContext(spinId: String, initialFile: File) = object : SpinWatcherContext(
        spinId = spinId,
        historyFile = initialFile,
        room = room,
    ){
        override suspend fun parseHistory(historyFile: File, summaryFile: File?): Spin? {
            try {
                parserFactory().use { parser ->
                    historyFile.useSafeInputStream { parser.parseFile(it, historyFile.name) }
                    summaryFile?.useSafeInputStream { parser.parseFile(it, summaryFile.name) }
                    parser.waitForResults()
                    return parser.spins[spinId]
                }
            } catch (e: Exception) {
                log.warn(e) { "cannot parse Spin $spinId" }
                return null
            }
        }

        override suspend fun onStaleDetected(spinId: String, historyFile: File) {
            log.warn { "Stop watching for stale Spin $spinId" }
            mutex.withLock {
                spinContexts.remove(spinId)
                staleHistoriesBySpinIds[spinId] = historyFile
                publishInProgress()
            }
        }

        override suspend fun onCompleted(spin: Spin) {
            mutex.withLock {
                spinContexts.remove(spin.id)
                publishInProgress()
                eventPublisher(WatcherEvent.SpinFinished(spin))
                log.info { "Spin ${spin.id} parsed" }
            }
        }

        override suspend fun onHeartbeat() = eventPublisher(WatcherEvent.HeartBeat)

    }.apply {
        scope.startContext()
        log.info { "start watching history for Spin $spinId" }
    }

    private suspend fun publishInProgress() {
        eventPublisher(
            WatcherEvent.InProgressUpdate(
                activeCount = spinContexts.size,
                staledCount = staleHistoriesBySpinIds.size
            )
        )
    }

}

class UnibetWatcher (
    parentScope: CoroutineScope,
    eventPublisher: suspend (WatcherEvent) -> Unit
) : SummaryBasedWatcher(
    parentScope = parentScope,
    handHistoryRegex = """^H\d+.*T(\d+).*Power Spin .*$""",
    tournamentSummaryRegex = """^TS\d+.*T(\d+) .*$""",
    room = Room.UNIBET,
    eventPublisher = eventPublisher,
    log = loggerOf(UnibetWatcher::class.java),
    parserFactory = { UnibetParser() },
)

class WinamaxWatcher (
    parentScope: CoroutineScope,
    eventPublisher: suspend (WatcherEvent) -> Unit
) : SummaryBasedWatcher(
    parentScope = parentScope,
    eventPublisher = eventPublisher,
    handHistoryRegex = """^.*_Expresso(?: Nitro)?\((\d+)\)_.*(?<!_summary)\.txt$""",
    tournamentSummaryRegex = """^.*_Expresso(?: Nitro)?\((\d+)\)_.*_summary\.txt$""",
    room = Room.WINAMAX,
    log = loggerOf(WinamaxWatcher::class.java),
    parserFactory = {WinamaxParser() },
)
