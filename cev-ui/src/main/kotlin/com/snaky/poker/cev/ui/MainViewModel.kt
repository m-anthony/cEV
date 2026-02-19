package com.snaky.poker.cev.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.core.Hand.Position
import com.snaky.poker.cev.core.Spin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.prefs.Preferences
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val KEY_LAST_DIR = "last_directory"

class MainViewModel(private val api: PokerCalculatorAPI) {
    private val prefs = Preferences.userRoot().node(AppConfig.name)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    var selectedDirectory by mutableStateOf<File?>(null)
    var isCalculating by mutableStateOf(false)
    var statsRows = mutableStateListOf<SpinStats>()

    init {
        val savedPath = prefs.get(KEY_LAST_DIR, null)
        if (!savedPath.isNullOrBlank()) {
            val file = File(savedPath)
            if (file.exists() && file.isDirectory) {
                selectedDirectory = file
            }
        }
    }

    fun selectDirectory(file: File) {
        selectedDirectory = file
        prefs.put(KEY_LAST_DIR, file.absolutePath)
    }

    fun runCalculation() {
        val dir = selectedDirectory ?: return
        isCalculating = true


        scope.launch {
            val results = withContext(Dispatchers.IO) {
                // This runs on a background thread
                api.calculateFromDirectory(dir)
            }

            // Back on Main thread to update UI
            val computedStats = transformToStats(results)
            statsRows.clear()
            statsRows.addAll(computedStats)
            isCalculating = false
        }
    }

    private fun transformToStats(
        spins: Map<String, Spin>,
    ): List<SpinStats> {
        val grouped = spins.values.groupBy { it.buyIn }.toSortedMap()
        val rows = grouped.map { (bi, list) -> createStatsObject(formatBuyIn(bi), list)}
        val totalRow = createStatsObject("Total", spins.values.toList())
        return rows + totalRow
    }

    private fun formatBuyIn(buyIn: Double): String {
        return if (buyIn % 1.0 == 0.0) "${buyIn.toInt()} €" else "%.2f €".format(buyIn)
    }


    private fun createStatsObject(label: String, spins: List<Spin>): SpinStats {

        val posEntries = Position.entries
        var netGainCents = 0
        var prizePoolCents = 0
        val count = spins.size
        var ev = 0.0
        var sqrEv = 0.0
        var itmCount = 0
        var totalBuyInCents = 0
        val positionalEv = DoubleArray(posEntries.size) {0.0}

        for(spin in spins) {
            val buyIn = (100 * spin.buyIn).roundToInt()
            totalBuyInCents += buyIn
            prizePoolCents += buyIn * spin.multiplier
            if(spin.wins > 0) itmCount++
            netGainCents += (100 * spin.wins).roundToInt() - buyIn
            ev += spin.cev
            sqrEv += spin.cev * spin.cev
            spin.hands.forEach { positionalEv[it.position.ordinal] += it.cev}
        }

        val cev = ev / count

        return SpinStats(
            label = label,
            count = count,
            netGain = netGainCents / 100.0,
            cev = cev,
            itm = itmCount.toDouble() / count,
            roi = netGainCents.toDouble() / totalBuyInCents,
            cevStdDev = sqrt(max(0.0, (sqrEv / count) - (cev * cev))),
            effectiveRake = 1 - prizePoolCents / 3.0 / totalBuyInCents,
            positionalCev = posEntries.associateWithTo(EnumMap(Position::class.java)) { positionalEv[it.ordinal] / count }
        )
    }
}

interface PokerCalculatorAPI {
    suspend fun calculateFromDirectory(directory: File): Map<String, Spin>
}