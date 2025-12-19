package com.snaky.poker.cev

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.collections.forEach
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
    spinsByBuyIn.mapValues { (_, l) -> Stats.fromSpins(l) }.toSortedMap().forEach { println(it) }
    println("total : ${Stats.fromSpins(spins)}")

    println("press ENTER to finish ...")
    readlnOrNull()
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
    val wins: Float,
    val cev: Float,
    val roi: Float,
    val itm: Float,
    val positionalCev: Map<Hand.Position, Float>,
    val effRakePct: Float,
    val cevStd: Int,
    val ci95: Int = 2 * cevStd / sqrt(count.toDouble()).toInt()
) {

    companion object {
        fun fromSpins(spins: Collection<Spin>): Stats {
            // use double to do computation then cnvert to float for better user readability
            var wins = 0.0
            var cev = 0.0
            var itm = 0
            var buyIns = 0.0
            var prizePool = 0.0
            var sqrCev = 0.0
            val positionalCev: MutableMap<Hand.Position, Double> = EnumMap(Hand.Position::class.java)
            Hand.Position.entries.forEach { positionalCev[it] = 0.0 }
            for (spin in spins) {
                wins += ((spin.wins/spin.buyIn).roundToInt() - 1) * spin.buyIn
                if(spin.wins > 0) itm++
                buyIns += spin.buyIn
                prizePool += spin.buyIn * spin.multiplier
                cev += spin.cev
                sqrCev += spin.cev * spin.cev
                spin.hands.forEach { positionalCev.computeIfPresent(it.position) { _, v -> v + it.cev } }
            }

            val floatCevs: MutableMap<Hand.Position, Float> = EnumMap(Hand.Position::class.java)
            positionalCev.forEach { (p, ev) -> floatCevs[p] = (ev / spins.size).toFloat() }
            val floatCev = (cev / spins.size).toFloat()
            return Stats(
                count = spins.size,
                wins = (100 * wins).toInt() / 100f,
                roi = (wins / buyIns * 100).toFloat(),
                itm = itm * 100f / spins.size,
                cev = floatCev,
                positionalCev = floatCevs,
                effRakePct = ((1 - prizePool / 3 / buyIns) * 100).toFloat(),
                cevStd = sqrt((sqrCev / spins.size) - (floatCev * floatCev)).toInt()
            )
        }
    }
}

