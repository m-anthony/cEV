package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.Hand
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.parsers.MetaParser
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintStream
import kotlin.math.roundToInt
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Invalid number of arguments: ${args.size} (expecting source directory + output file)")
        exitProcess(-1)
    }

    val source =
        File(args[0]).also { if (!it.exists()) throw FileNotFoundException("Invalid source directory: ${it.path}") }
    val outputFile = File(args[1]).also { it.parentFile?.mkdirs() }
    val parser = MetaParser()

    runBlocking {
        FileCrawler.processFileOrDirectory(source, parser)
        parser.waitForResults()
        parser.close()
        PrintStream(outputFile).use {
            it.println("id;cev;cevBU;cevSB;cevBB;cevSBHU;cevBBHU")
            parser.spins.values.forEach { spin -> it.println(spin.getDataList().joinToString(separator = ";")) }
        }
        //uses err because stdout not available in CLI mode
        System.err.println("${parser.spins.count()} spins exported to ${outputFile.absolutePath}")
    }
}

private fun Spin.getDataList() : List<Any> {
    val cevByPosition = hands.groupingBy { it.position }.fold(0.0) { cev, spin -> cev + spin.cev}
    return listOf(
        id,
        cev.roundToInt(),
        cevByPosition[Hand.Position.BU]?.roundToInt() ?: 0,
        cevByPosition[Hand.Position.SB]?.roundToInt() ?: 0,
        cevByPosition[Hand.Position.BB]?.roundToInt() ?: 0,
        cevByPosition[Hand.Position.HUSB]?.roundToInt() ?: 0,
        cevByPosition[Hand.Position.HUBB]?.roundToInt() ?: 0,
    )
}