package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.Hand
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.parsers.MetaParser
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.measureTimedValue

fun main() {


    val cfg = Properties()
    FileInputStream("cev.config.txt").use { cfg.load(it) }
    val basePath = cfg.getProperty("hh.path", "./")
    print("HH file (base path = $basePath): ")

    val file = File(basePath, readlnOrNull() ?: "")

    val parser = MetaParser()
    val processingTime = measureTimedValue {
        runBlocking {
            FileCrawler.processFileOrDirectory(file, parser)
            parser.waitForResults()
        }
        parser.close()
    }

    println("----------------------------------------")
    println("Processing done in ${processingTime.duration}.")

    val spins = parser.spins.values
    val spinsByBuyIn = spins.groupBy { it.buyInCents }
    val statsByBuyIn = spinsByBuyIn.mapValues { (_, l) -> Stats.fromSpins(l) }
    displayStats(statsByBuyIn, "Buy-in", Stats.fromSpins(spins))

    println("press ENTER to finish ...")
    readlnOrNull()
}

private fun <K : Comparable<K>> displayStats(stats: Map<K, Stats>, @Suppress("SameParameterValue") keyName: String, globalStats: Stats) {
    val displayed = mutableListOf<List<String>>()
    displayed.add(listOf(keyName, "Tournaments", "Winnings", "cEV", "ITM%", "ROI%", "cEV BU", "cEV SB", "cEV BB" , "cEV HUSB", "cEV HUBB", "cEV Std Dev", "cEV CI 95% +/-", "Eff. Rake %"))

    fun statsToStringList(key : String, stat : Stats) : List<String> {
        val line = mutableListOf(key)
        line.add(stat.count.toString())
        line.add("%.2f".format(stat.winningCents / 100.0))
        line.add(stat.cev.toString())
        line.add("%.1f".format(stat.itm))
        line.add("%.2f".format(stat.roi))
        line.add("%.1f".format(stat.positionalCev[Hand.Position.BU]))
        line.add("%.1f".format(stat.positionalCev[Hand.Position.SB]))
        line.add("%.1f".format(stat.positionalCev[Hand.Position.BB]))
        line.add("%.1f".format(stat.positionalCev[Hand.Position.HUSB]))
        line.add("%.1f".format(stat.positionalCev[Hand.Position.HUBB]))
        line.add(stat.cevStdDev.toString())
        line.add(stat.ci95.toString())
        line.add("%.2f".format(stat.effRake))
        return line
    }
    stats.toSortedMap().forEach { (k, v) -> displayed.add(statsToStringList(k.toString(), v)) }
    displayed.add(statsToStringList("Total", globalStats))
    val maxWidth = IntArray(displayed[0].size)
    displayed.forEach {
        for (i in 0 until maxWidth.size) {
            maxWidth[i] = max(maxWidth[i], it[i].length)
        }
    }

    displayed.forEach { line -> println((0 until maxWidth.size).joinToString(" | ") { i -> line[i].padEnd(maxWidth[i]) }) }
}


private data class Stats(
    val count: Int,
    val winningCents: Int,
    val cev: Int,
    val roi: Double,
    val itm: Double,
    val positionalCev: Map<Hand.Position, Double>,
    val effRake: Double,
    val cevStdDev: Int,
    val ci95: Int = 2 * cevStdDev / sqrt(count.toDouble()).toInt()
) {

    companion object {
        fun fromSpins(spins: Collection<Spin>): Stats {
            var winCents = 0
            var cev = 0.0
            var itm = 0
            var buyInCents = 0
            var prizePoolCents = 0
            var sqrCev = 0.0
            val positionalCev: MutableMap<Hand.Position, Double> = EnumMap(Hand.Position::class.java)
            Hand.Position.entries.forEach { positionalCev[it] = 0.0 }

            for (spin in spins) {
                winCents += spin.winCents - spin.buyInCents
                if (spin.winCents > 0) itm++
                buyInCents += spin.buyInCents
                prizePoolCents += spin.buyInCents * spin.multiplier
                cev += spin.cev
                sqrCev += spin.cev * spin.cev
                spin.hands.forEach { positionalCev.computeIfPresent(it.position) { _, v -> v + it.cev } }
            }

            positionalCev.entries.forEach { it.setValue(it.value / spins.size) }
            val floatCev = (cev / spins.size).toFloat()
            return Stats(
                count = spins.size,
                winningCents = winCents,
                roi = 100.0 * winCents / buyInCents,
                itm = 100.0 * itm  / spins.size,
                cev = (cev / spins.size).roundToInt(),
                positionalCev = positionalCev,
                effRake = (1 - prizePoolCents / 3.0 / buyInCents) * 100,
                cevStdDev = sqrt(max(0.0, (sqrCev / spins.size) - (floatCev * floatCev))).roundToInt()
            )
        }
    }
}

