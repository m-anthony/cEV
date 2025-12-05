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
    val positionalCev : Map<Hand.Position, Float>
) {
    companion object {
        fun fromSpins(spins: Collection<Spin>): Stats {
            // use double to do computation then cnvert to float for better user readability
            var wins = 0.0
            var cev = 0.0
            val positionalCev: MutableMap<Hand.Position, Double> = EnumMap(Hand.Position::class.java)
            Hand.Position.entries.forEach { positionalCev[it] = 0.0 }
            for (spin in spins) {
                wins += ((spin.wins/spin.buyIn).roundToInt() - 1) * spin.buyIn
                cev += spin.cev
                spin.hands.forEach { positionalCev.computeIfPresent(it.position) { _, v -> v + it.cev } }
            }

            val floatCevs: MutableMap<Hand.Position, Float> = EnumMap(Hand.Position::class.java)
            positionalCev.forEach { (p, ev) -> floatCevs[p] = (ev / spins.size).toFloat() }
            return Stats(spins.size, (100 * wins).toInt() / 100f, (cev / spins.size).toFloat(), floatCevs)
        }
    }
}

