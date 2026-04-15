package com.snaky.poker.cev.ui.model

import androidx.compose.ui.text.AnnotatedString
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.watchers.HistoryWatcher
import com.snaky.poker.cev.core.watchers.HistoryWatcherConfiguration
import com.snaky.poker.cev.core.watchers.RoomLiveSupport
import com.snaky.poker.cev.core.watchers.WatcherEvent
import com.snaky.poker.cev.core.watchers.getLiveSupport
import com.snaky.poker.cev.ui.amountAnnotatedString
import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.LiveWidgetConfig
import com.snaky.poker.cev.ui.formatBuyIn
import com.snaky.poker.cev.ui.formatStartingStack
import com.snaky.poker.cev.ui.model.SessionStatus.*
import com.snaky.poker.cev.ui.toAmountAnnotatedString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class LiveSessionUiState(
    val liveWidgetConfig: LiveWidgetConfig? = null,
    val showResults: Boolean = true,
    val status: SessionStatus = READY,
    val startTimeMillis: Long? = null,
    val accumulatedTimeMillis: Long = 0L,
    val lastUpdateMillis: Long? = null,
    val sessionStartWallTime: String? = null,
    val runningSpinsCount: Int = 0,
    val staledSpinsCount: Int = 0,
    val lastTick: Long = 0L,
    val rows: List<SessionRowUiState> = emptyList(),
    val totalRow: SessionRowUiState? = null,
) {
    val totalGames: Int get() = runningSpinsCount + staledSpinsCount + (totalRow?.count ?: 0)
    val displayDuration: String get() = formatDuration(getActiveMillis())
    val hasStaleWarnings: Boolean get() = staledSpinsCount > 0

    private fun getActiveMillis(): Long {
        return accumulatedTimeMillis + if (status == TRACKING && startTimeMillis != null) lastTick - startTimeMillis else 0
    }

}

data class SessionRowUiState(
    val label: String,      // "5€ Nitro"
    val count: Int,      // "14"
    val netWon: AnnotatedString,     // "+2€"
    val roi: AnnotatedString,
    val itm: String,
    val cev: String
)

class LiveSessionViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val watcher: HistoryWatcher = HistoryWatcher(viewModelScope, WatcherConfiguration)
    private val _uiState = MutableStateFlow(LiveSessionUiState())

    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()
    val enabled: Boolean
        get() = ConfigurationManager.configuration.sources.any { it.liveRoom?.getLiveSupport() == RoomLiveSupport.READY }

    private val allSpins = mutableListOf<Spin>()
    private val widgetHandler = LiveSessionWidgetHandler()
    init {
        startHistoryWatcher()
        startGlobalTicker()

        //synchronize config and uiState
        viewModelScope.launch {
            ConfigurationManager.configFlow.collect { config ->
                _uiState.update { it.copy(
                    liveWidgetConfig = config.liveWidgetConfig,
                    showResults = config.showLiveResults
                ) }
            }
        }
    }

    fun onSessionFinished(action: () -> Unit) {
        uiState
            .map { it.runningSpinsCount == 0 && it.totalGames > 0 }
            .distinctUntilChanged()
            .filter { it } // On ne garde que le passage à 'true'
            .onEach { action() }
            .launchIn(viewModelScope)
    }

    fun resetStats(){
        allSpins.clear()
        viewModelScope.launch {
            _uiState.update {
                LiveSessionUiState(
                    liveWidgetConfig = it.liveWidgetConfig,
                    showResults = it.showResults
                )
            }
        }
    }

    fun toggleLiveWidget(enabled: Boolean) = widgetHandler.toggle(enabled)

    fun updateWidgetPosition(x: Int, y: Int) = widgetHandler.updatePosition(x, y)
    fun showResults(enabled: Boolean) = ConfigurationManager.update { it.copy(showLiveResults = enabled) }
    private fun pauseSession(lastActivityMillis: Long) {
        _uiState.update { state ->
            val currentSegment = lastActivityMillis - (state.startTimeMillis ?: lastActivityMillis)
            state.copy(
                status = PAUSED,
                accumulatedTimeMillis = state.accumulatedTimeMillis + currentSegment,
                startTimeMillis = null
            )
        }
    }

    private fun startGlobalTicker() {
        viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (state.status == TRACKING) {

                    val now = System.currentTimeMillis()
                    _uiState.update { it.copy(lastTick = now) }

                    val lastActivity = state.lastUpdateMillis ?: now
                    if (state.runningSpinsCount == 0 && (now - lastActivity) > 180_000) { // pause if nothing since 3 minutes
                        pauseSession(lastActivity)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun startHistoryWatcher() {
        viewModelScope.launch {
            watcher.events.collect { handleWatcherEvent(it) }
        }
    }

    private fun handleWatcherEvent(event: WatcherEvent) {
        val now = System.currentTimeMillis()
        if (_uiState.value.status != TRACKING) {
            _uiState.update { it.copy(
                status = TRACKING,
                startTimeMillis = now,
                lastTick = now,
                sessionStartWallTime = it.sessionStartWallTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            )}
        }
        when (event) {
            is WatcherEvent.InProgressUpdate -> {
                _uiState.update {
                    it.copy(
                        runningSpinsCount = event.activeCount,
                        staledSpinsCount = event.staledCount,
                    )
                }
            }
            is WatcherEvent.SpinFinished -> {
                allSpins.add(event.spin)
                _uiState.update { computeRows(it).copy(lastUpdateMillis = now) }
            }
            is WatcherEvent.HeartBeat -> _uiState.update { it.copy(lastUpdateMillis = now) }
        }
    }

    private fun computeRows(state: LiveSessionUiState): LiveSessionUiState {
        val spinsByLabel = allSpins.groupBy { "${formatBuyIn(it.buyInCents)} ${formatStartingStack(it.startingStack)}" }.toSortedMap()
        val rows = spinsByLabel.mapValues { (label, spins) ->
            val totalWon = spins.sumOf { it.winCents}
            val totalBuyIn = spins.first().buyInCents * spins.size
            val netWon = totalWon - totalBuyIn
            SessionRowUiState(
                label = label,
                count = spins.size,
                netWon = amountAnnotatedString(netWon / 100.0),
                roi = "%.2f%%".format(netWon * 100.0 / totalBuyIn).toAmountAnnotatedString(netWon.toDouble()),
                itm = "%.1f%%".format(spins.count {it.winCents > 0} * 100.0 / spins.size),
                cev = (spins.sumOf { it.cev } / spins.size).roundToInt().toString()
            )
        }

        val totalWon = allSpins.sumOf { it.winCents }
        val totalBuyIn = allSpins.sumOf { it.buyInCents }
        val netWon = totalWon - totalBuyIn
        val totalCev = allSpins.groupBy{ it.startingStack }
            .mapValues { (_, spins) -> spins.sumOf { it.cev } }
            .let { if(it.size == 1) it.values.first() / allSpins.size else null}
        val totalRow = SessionRowUiState(
            label = "TOTAL",
            count = allSpins.size,
            netWon = amountAnnotatedString(netWon / 100.0),
            roi = "%.2f%%".format(netWon * 100.0 / totalBuyIn).toAmountAnnotatedString(netWon.toDouble()),
            itm = "%.1f%%".format(allSpins.count { it.winCents > 0 } * 100.0 / allSpins.size),
            cev = totalCev?.roundToInt()?.toString() ?: "N/A"
        )
        return state.copy(
            rows = rows.values.toList(),
            totalRow = totalRow
        )
    }
}


private object WatcherConfiguration : HistoryWatcherConfiguration {

    override val sourcesFlow: Flow<Map<Room, List<Path>>> = ConfigurationManager.configFlow
        .map { config ->
            config.sources
                .filter { it.liveRoom?.getLiveSupport() == RoomLiveSupport.READY }
                .groupBy(
                    keySelector = { it.liveRoom!! },
                    valueTransform = { source -> Path.of(source.path) }
                )
        }.distinctUntilChanged()
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

enum class SessionStatus {
    READY,
    TRACKING,
    PAUSED
}