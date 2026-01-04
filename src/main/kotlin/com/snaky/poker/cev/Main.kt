package com.snaky.poker.cev

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.measureTimedValue

val parser = BetclicParser()

fun main() {


    val cfg = Properties()
    FileInputStream("cev.config.txt").use { cfg.load(it) }
    val basePath = cfg.getProperty("hh.path", "*")
    print("HH file (base path = $basePath): ")

    val fileName = readlnOrNull()

    val file = File(basePath, fileName ?: "")

    val spins = parser.spins.values
    val processingTime = measureTimedValue {
        processFileOrDirectory(file)
        spins.forEach { it.aggregateHands() }
    }

    println("----------------------------------------")
    println("Processing done in ${processingTime.duration}.")

    val spinsByBuyIn = spins.groupBy { it.buyIn }
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
        line.add("%.2f".format(stat.wins))
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

private fun processFileOrDirectory(file: File) {

    if (!file.exists()) throw FileNotFoundException("File '$file' does not exists")
    println("processing file $file")
    when {
        file.isDirectory -> file.listFiles()?.forEach { processFileOrDirectory(it) }
        file.isFile && file.extension.equals("zip", ignoreCase = true) -> processZipFile(file)
        file.isFile -> file.inputStream().use { parser.parseFile(it) }
    }
}

private fun processZipFile(zipFile: File) {
    zipFile.inputStream().use { processZipStream(it) }
}

private fun processZipStream(inputStream: InputStream) {

    ZipInputStream(inputStream).use { zipStream ->
        zipStream.entriesSequence()
            .filter { !it.isDirectory }
            .forEach { entry ->
                val entryName = entry.name
                if (entryName.endsWith(".zip", ignoreCase = true)) {
                    processZipStream(zipStream)
                } else {
                    parser.parseFile(zipStream)
                }
            }
    }
}

private fun ZipInputStream.entriesSequence(): Sequence<ZipEntry> = sequence {
    var entry = nextEntry
    while (entry != null) {
        yield(entry)
        entry = nextEntry
    }
}


private data class Stats(
    val count: Int,
    val wins: Double,
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
            var wins = 0.0
            var cev = 0.0
            var itm = 0
            var buyIns = 0.0
            var prizePool = 0.0
            var sqrCev = 0.0
            val positionalCev: MutableMap<Hand.Position, Double> = EnumMap(Hand.Position::class.java)
            Hand.Position.entries.forEach { positionalCev[it] = 0.0 }

            for (spin in spins) {
                wins += ((spin.wins / spin.buyIn).roundToInt() - 1) * spin.buyIn
                if (spin.wins > 0) itm++
                buyIns += spin.buyIn
                prizePool += spin.buyIn * spin.multiplier
                cev += spin.cev
                sqrCev += spin.cev * spin.cev
                spin.hands.forEach { positionalCev.computeIfPresent(it.position) { _, v -> v + it.cev } }
            }

            positionalCev.entries.forEach { it.setValue(it.value / spins.size) }
            val floatCev = (cev / spins.size).toFloat()
            return Stats(
                count = spins.size,
                wins = wins,
                roi = 100.0 * wins / buyIns,
                itm = 100.0 * itm  / spins.size,
                cev = (cev / spins.size).roundToInt(),
                positionalCev = positionalCev,
                effRake = (1 - prizePool / 3 / buyIns) * 100,
                cevStdDev = sqrt((sqrCev / spins.size) - (floatCev * floatCev)).roundToInt()
            )
        }
    }
}

