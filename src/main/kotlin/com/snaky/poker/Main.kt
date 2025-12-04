package com.snaky.poker

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

val parser = BetclicParser()

fun main() {
    print("Veuillez saisir le nom du fichier à lire : ")

    var fileName = readlnOrNull()

    if (fileName.isNullOrBlank()) {
        //fileName = "D:\\poker\\PT4_archive\\BC\\Betclic_15926432_ExportHH_2025-11-30_23-22-25.zip" //novembre
        fileName = "D:\\poker\\PT4_archive\\BC"
        //fileName = "D:\\poker\\PT4_archive\\BC\\decembre"
    }

    val spins = parser.spins.values
    val processingTime = measureTimedValue {
        processFileOrDirectory(fileName)
        spins.forEach { it.aggregateHands() }
    }

    println("----------------------------------------")
    println("Lecture du fichier terminée avec succès en ${processingTime.duration}.")

    val spinsByBuyIn = spins.groupBy { it.buyIn }
    spinsByBuyIn.mapValues { (_, l) -> Stats.fromSpins(l) }.toSortedMap().forEach { println(it) }
    println("total : ${Stats.fromSpins(spins)}")

    //spins.sortedBy { it.cev }.forEach { println("ID: ${it.id}, cEV: ${it.cev}") }
    //parser.spins["01KAYD81Y7YRXZ24V5CM98MEPN"]!!.hands.sortedBy { it.id }.forEach { println("ID: ${it.id}, cEV: ${it.cev}") }

    dumpEquityStats()

}

private fun processFileOrDirectory(path: String) {
    val file = File(path)

    if (!file.exists()) throw FileNotFoundException("Le fichier '$path' est introuvable.")

    when {
        file.isDirectory -> file.listFiles()?.forEach { subFile -> processFileOrDirectory(subFile.absolutePath) }
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
    val cev: Double,
    val positionalCev : Map<Hand.Position, Double>
) {
    companion object {
        fun fromSpins(spins: Collection<Spin>): Stats {
            var wins = 0.0
            var cev = 0.0
            val positionalCev: MutableMap<Hand.Position, Double> = EnumMap(Hand.Position::class.java)
            Hand.Position.entries.forEach { positionalCev[it] = 0.0 }
            for (spin in spins) {
                wins += ((spin.wins/spin.buyIn).roundToInt() - 1) * spin.buyIn
                cev += spin.cev
                spin.hands.forEach { positionalCev.computeIfPresent(it.position) { _, v -> v + it.cev } }
            }
            positionalCev.entries.forEach { it.setValue(it.value / spins.size) }
            return Stats(spins.size, wins, cev / spins.size, positionalCev)
        }
    }
}

