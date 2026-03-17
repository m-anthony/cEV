package com.snaky.poker.cev.ui.model

import androidx.compose.runtime.*
import com.snaky.poker.cev.core.PlayerStat
import com.snaky.poker.cev.core.SimulationResult
import com.snaky.poker.cev.core.VarianceSimulator
import com.snaky.poker.cev.core.model.Hand.Position
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.model.SpinProfile
import com.snaky.poker.cev.ui.config.ConfigurationManager
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class ProcessingResults (
    val spins: Map<String, Spin>,
    val processingStats : ProcessingStats,
)

data class ProcessingStats (
    val validSpinCount: Int = 0,
    val incompleteSpinCount: Int = 0,
    val duplicateHandCount: Int = 0,
)

class ResultsViewModel(private val api: PokerCalculatorAPI) {

    var selectedStackFilter by mutableStateOf<Int?>(null)
    var currentSpinCount by mutableStateOf(0)
        private set
    var processingStats by mutableStateOf(ProcessingStats())
        private set
    var isCalculating by mutableStateOf(false)
        private set

    private var allSpins = mutableStateOf<Map<String, Spin>>(emptyMap())

    val statsRows: List<SpinStats> by derivedStateOf {
        val currentSpins = allSpins.value.values
        if (currentSpins.isEmpty()) return@derivedStateOf emptyList()

        val filteredSpins = if (selectedStackFilter == null) {
            currentSpins.toList()
        } else {
            currentSpins.filter { it.startingStack == selectedStackFilter }
        }
        val showCev = selectedStackFilter != null || availableFormats.size == 1
        val rows = transformToStats(filteredSpins, showCev)

        rows.map { row ->
            row.copy(varianceResult = varianceResults[row.label])
        }
    }

    val availableFormats: Map<Int, Int> by derivedStateOf {
        allSpins.value.values.groupBy { it.startingStack }.mapValues { (_, v) -> v.size }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var calculationJob: Job? = null
    private var varianceResults = mutableStateMapOf<String, SimulationResult>()
    private var varianceJob: Job? = null

    fun runCalculation() {
        clearResults()
        val paths = ConfigurationManager.configuration.sources.filter { it.isActive }
            .map { File(it.path) }
            .filter { it.exists() }
        if (paths.isEmpty()) return
        isCalculating = true

        calculationJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.calculateFromDirectories(paths)
                }.also { results ->
                    processingStats = results.processingStats
                    allSpins.value = results.spins.also { it.values.computeVariance() }
                }
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
        clearResults()
        isCalculating = false
    }

    fun clearResults(){
        allSpins.value = emptyMap()
        processingStats = ProcessingStats()
        currentSpinCount = 0
        selectedStackFilter = null
    }

    private fun transformToStats(
        spins: List<Spin>,
        showCev: Boolean
    ): List<SpinStats> {
        val grouped = spins.groupBy { it.buyInCents }.toSortedMap()
        val rows = grouped.map { (bi, list) -> createStatsObject(formatBuyIn(bi), list, showCev) }
        val totalRow = createStatsObject("Total", spins, showCev)
        return rows + totalRow
    }

    private fun Collection<Spin>.computeVariance() {
        varianceJob?.cancel()
        varianceResults.clear()

        class StatAccumulator(var totalCEV: Double = 0.0, var count: Int = 0){
            fun toPlayerStat() = PlayerStat(totalCEV / count, count)
        }

        //group by profile and precompute cEV
        val accumulators = this.groupingBy { it.profile }.fold(
            initialValueSelector = { _, s -> StatAccumulator(totalCEV = s.cev, count = 1) },
            operation = { _, acc, spin ->
                acc.apply {
                    totalCEV += spin.cev
                    count++
                }
            }
        )
        // then prepare 1 simulation task per buy in
        val simuPerBuyIn = mutableMapOf<Int, MutableMap<SpinProfile, PlayerStat>>()
        accumulators.forEach { (profile, acc) ->
            simuPerBuyIn.getOrPut(profile.buyInCents) { mutableMapOf() }[profile] = acc.toPlayerStat()
        }

        varianceJob = scope.launch {
            simuPerBuyIn.forEach { (buyInCents, profilePairs) ->
                val label = formatBuyIn(buyInCents)
                launch {
                    withTimeout(10.seconds) {
                        VarianceSimulator.run(
                            distribution = profilePairs,
                            buyInCents = buyInCents,
                            onResult = { varianceResults[label] = it}
                        )
                    }
                }
            }
        }
    }

}

private fun createStatsObject(label: String, spins: List<Spin>, showCev: Boolean): SpinStats {

    val posEntries = Position.entries
    var netGainCents = 0
    var prizePoolCents : Int? = 0
    val count = spins.size
    var ev = 0.0
    var sqrEv = 0.0
    var itmCount = 0
    var totalBuyInCents = 0
    val positionalEv = DoubleArray(posEntries.size) { 0.0 }

    for (spin in spins) {
        val bi = spin.buyInCents
        totalBuyInCents += bi
        prizePoolCents = spin.multiplier.let { if (it == 0) null else prizePoolCents?.plus(it * bi) }
        if (spin.winCents > 0) itmCount++
        netGainCents += spin.winCents - bi
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
        effectiveRake = if(prizePoolCents != null) 1 - prizePoolCents / 3.0 / totalBuyInCents else Double.NaN,
        cev = if (!showCev) Double.NaN else cev,
        cevStdDev = if (!showCev) Double.NaN else sqrt(max(0.0, (sqrEv / count) - (cev * cev))),
        positionalCev = if (!showCev) emptyMap() else posEntries.associateWithTo(EnumMap(Position::class.java)) { positionalEv[it.ordinal] / count }
    )
}


private fun formatBuyIn(buyInCents: Int): String {
    return if (buyInCents % 100 == 0) "${buyInCents / 100} €" else "%.2f €".format(buyInCents / 100.0)
}
