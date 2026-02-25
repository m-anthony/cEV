package com.snaky.poker.cev.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.core.Hand.Position
import com.snaky.poker.cev.core.Spin
import com.snaky.poker.cev.ui.config.ConfigurationManager
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds


class MainViewModel(private val api: PokerCalculatorAPI) {
    var isCalculating by mutableStateOf(false)
    var selectedStackFilter by mutableStateOf<Int?>(null)
    var currentSpinCount by mutableStateOf(0)

    private var allSpins = mutableStateOf<Map<String, Spin>>(emptyMap())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var calculationJob: Job? = null

    val statsRows: List<SpinStats> by derivedStateOf {
        val currentSpins = allSpins.value.values
        if (currentSpins.isEmpty()) return@derivedStateOf emptyList()

        val filteredSpins = if (selectedStackFilter == null) {
            currentSpins.toList()
        } else {
            currentSpins.filter { it.startingStack == selectedStackFilter }
        }
        val showCev = selectedStackFilter != null || availableFormats.size == 1
        transformToStats(filteredSpins, showCev)
    }

    val availableFormats: Map<Int, Int> by derivedStateOf {
        allSpins.value.values.groupBy { it.startingStack }.mapValues { (_, v) -> v.size }
    }

    fun runCalculation() {
        allSpins.value = emptyMap()
        selectedStackFilter = null
        val paths = ConfigurationManager.configuration.sources.filter { it.isActive }
            .map { File(it.path) }
            .filter { it.exists() }
        if (paths.isEmpty()) return
        isCalculating = true

        calculationJob = scope.launch {
            currentSpinCount = 0
            try {
                val results = withContext(Dispatchers.IO) {
                    api.calculateFromDirectories(paths)
                }

                allSpins.value = results
            } finally {
                isCalculating = false
            }
        }

        scope.launch {
            while(!isCalculating) delay(10.milliseconds)
            while(isCalculating) {
                currentSpinCount = api.currentSpinCount
                delay(200.milliseconds)
            }
        }
    }

    fun stopCalculation() {
        calculationJob?.cancel()
        allSpins.value = emptyMap()
        isCalculating = false
    }

    private fun transformToStats(
        spins: List<Spin>,
        showCev: Boolean
    ): List<SpinStats> {
        val grouped = spins.groupBy { it.buyIn }.toSortedMap()
        val rows = grouped.map { (bi, list) -> createStatsObject(formatBuyIn(bi), list, showCev) }
        val totalRow = createStatsObject("Total", spins, showCev)
        return rows + totalRow
    }

    private fun formatBuyIn(buyIn: Double): String {
        return if (buyIn % 1.0 == 0.0) "${buyIn.toInt()} €" else "%.2f €".format(buyIn)
    }


    private fun createStatsObject(label: String, spins: List<Spin>, showCev: Boolean): SpinStats {

        val posEntries = Position.entries
        var netGainCents = 0
        var prizePoolCents = 0
        val count = spins.size
        var ev = 0.0
        var sqrEv = 0.0
        var itmCount = 0
        var totalBuyInCents = 0
        val positionalEv = DoubleArray(posEntries.size) { 0.0 }

        for (spin in spins) {
            val buyIn = (100 * spin.buyIn).roundToInt()
            totalBuyInCents += buyIn
            prizePoolCents += buyIn * spin.multiplier
            if (spin.wins > 0) itmCount++
            netGainCents += (100 * spin.wins).roundToInt() - buyIn
            ev += spin.cev
            sqrEv += spin.cev * spin.cev
            spin.hands.forEach { positionalEv[it.position.ordinal] += it.cev }
        }

        val cev = ev / count

        return SpinStats(
            label = label,
            count = count,
            netGain = netGainCents / 100.0,
            itm = itmCount.toDouble() / count,
            roi = netGainCents.toDouble() / totalBuyInCents,
            effectiveRake = 1 - prizePoolCents / 3.0 / totalBuyInCents,
            cev = if (!showCev) Double.NaN else cev,
            cevStdDev = if (!showCev) Double.NaN else sqrt(max(0.0, (sqrEv / count) - (cev * cev))),
            positionalCev = if (!showCev) emptyMap() else posEntries.associateWithTo(EnumMap(Position::class.java)) { positionalEv[it.ordinal] / count }
        )
    }
}

interface PokerCalculatorAPI {
    val currentSpinCount: Int
    suspend fun calculateFromDirectories(directories: List<File>): Map<String, Spin>
}