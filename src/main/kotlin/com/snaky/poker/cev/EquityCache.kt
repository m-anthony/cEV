package com.snaky.poker.cev

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors


fun getHandClassIndex(hand: Long): Int {
    val c1 = hand.countTrailingZeroBits()
    val c2 = 63 - hand.countLeadingZeroBits()
    val r1 = c1 % Rank.entries.size
    val r2 = c2 % Rank.entries.size

    if (r1 == r2) return r1

    var handClass = 13
    val rankHigh = maxOf(r1, r2)
    val rankLow = minOf(r1, r2)
    handClass += (rankHigh * (rankHigh - 1)) / 2 + rankLow
    if (c1 / Rank.entries.size == c2 / Rank.entries.size) handClass += 78 //suited
    return handClass
}

/**
 * Calcule l'Index Brut (canonicalIndexValue) pour une paire de mains (H_A vs H_B).
 *
 * OPTIMISATION : La logique de mapping des couleurs utilisées et non utilisées est fusionnée
 * pour éviter la création de listes intermédiaires comme unusedSuitOrdinals.
 */
private const val RANK_FILTER = 1L or (1L shl 13) or (1L shl 26) or (1L shl 39)
private const val MAX_SUITS = 4
private const val MAX_RANK = 13

private data class SuitIndexResult(
    val index: Int,
    val inverted: Boolean
)

private fun calculateCanonicalSuitIndex(hA: Long, hB: Long): SuitIndexResult {

    val cards = hA or hB
    val realToCanonical = IntArray(MAX_SUITS) { -1 }
    var canonicalSuitCounter = 0
    var result = 0

    var cardsTmp = cards
    var ranks = 0
    while (cardsTmp != 0L) {
        val card = cardsTmp.countTrailingZeroBits()
        cardsTmp = cardsTmp and (1L shl card).inv()
        ranks = ranks or (1 shl (card % MAX_RANK))
    }

    var hASuit = 0
    var hBSuit = 0
    while (ranks != 0) {
        val rank = ranks.countTrailingZeroBits()
        ranks = ranks and (1 shl rank).inv()
        var filteredCards = (cards ushr rank) and RANK_FILTER
        while (filteredCards != 0L) {
            val suit = filteredCards.countTrailingZeroBits() / MAX_RANK
            filteredCards = filteredCards and ((1L shl (suit * MAX_RANK)).inv())
            var canonicalSuit = realToCanonical[suit]
            if (canonicalSuit == -1) {
                // Assignation de la prochaine couleur canonique (0, 1, 2, ...)
                canonicalSuit = canonicalSuitCounter++
                realToCanonical[suit] = canonicalSuit
            }

            if (hA and (1L shl (rank + MAX_RANK * suit)) != 0L) {
                hASuit = hASuit * MAX_SUITS + canonicalSuit
            } else {
                hBSuit = hBSuit * MAX_SUITS + canonicalSuit
            }
            result = result * MAX_SUITS + canonicalSuit
        }
    }
    return SuitIndexResult(result, hBSuit > hASuit)
}

fun generateCanonicalIndexesToHand(): MutableMap<Int, Pair<Long, Long>> {
    val canonicalIndexesToHands = mutableMapOf<Int, Pair<Long, Long>>()
    // 1. Boucler sur toutes les paires de mains (H_A vs H_B)
    for (i in 0 until CARDS - 1) {
        for (j in i + 1 until CARDS) {
            val hA = (1L shl i) or (1L shl j)
            for (k in 0 until CARDS - 1) {
                if (k == i || k == j) continue
                for (l in k + 1 until CARDS) {
                    if (l == i || l == j) continue
                    val hB = (1L shl k) or (1L shl l)
                    val canonicalIndex = computeIndex(hA, hB)
                    if (canonicalIndex == 367181) {
                        println("${toString(hA)} vs ${toString(hB)}")
                    }
                    if (canonicalIndex >= 0) canonicalIndexesToHands.putIfAbsent(canonicalIndex, Pair(hA, hB))
                }
            }
        }
    }
    return canonicalIndexesToHands
}

private fun computeIndex(hA: Long, hB: Long): Int {
    val indexA = getHandClassIndex(hA)
    val indexB = getHandClassIndex(hB)
    val colorIndex = calculateCanonicalSuitIndex(hA, hB)
    val inverted = (indexB > indexA) || (indexA == indexB && colorIndex.inverted)
    val handClassIndex = if (inverted) getHandPairClassIndex(indexB, indexA) else getHandPairClassIndex(indexA, indexB)
    val canonicalIndex = colorIndex.index + 256 * handClassIndex
    return if (inverted) -canonicalIndex - 1 else canonicalIndex
}

fun equityPreflopCached(heroHand: Long, vilainHand: Long): Double {
    val index = computeIndex(heroHand, vilainHand)
    val equity = getPreflopEquityCache()[if (index < 0) -(index + 1) else index]
        ?: throw IllegalStateException("index $index not in equity cache")
    return if (index >= 0) equity else 1 - equity
}

private fun getHandPairClassIndex(indexA: Int, indexB: Int): Int = (indexA * (indexA + 1)) / 2 + indexB

private const val FILENAME = "preflop_equity.dat"

fun main(args: Array<String>) {
    buildEquityCache(File(args[0], FILENAME))
}

private fun buildEquityCache(file: File) {
    if (file.exists()) {
        FileInputStream(file).use { if (!readFromCache(it).isEmpty()) return }
    }
    val indexesToHand = generateCanonicalIndexesToHand()
    println("Precomputing equities for ${indexesToHand.size} canonical pair of hands")
    val cache = indexesToHand.entries.parallelStream()
        .map {
            Pair(
                it.key,
                computeEquityPreflop(it.value.first, listOf(it.value.second), it.value.first or it.value.second)
            )
        }
        .collect(Collectors.toConcurrentMap({ it.first }, { it.second }))
    println("Saving preflop equities in $file")
    FileChannel.open(
        file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING
    ).use { channel ->
        val buffer = ByteBuffer.allocateDirect(16384)

        fun flushBuffer() {
            buffer.flip()
            while (buffer.hasRemaining()) channel.write(buffer)
            buffer.clear()
        }

        cache.forEach { (index, equity) ->
            if (buffer.remaining() < RECORD_SIZE) flushBuffer()
            buffer.putInt(index)
            buffer.putDouble(equity)
        }
        flushBuffer()
        channel.force(true)
    }

}

private const val RECORD_SIZE = Int.SIZE_BYTES + Double.SIZE_BYTES
private lateinit var EQUITY_CACHE: Map<Int, Double>
fun getPreflopEquityCache(): Map<Int, Double> {
    if (!::EQUITY_CACHE.isInitialized) {
        val stream = object {}::class.java.classLoader.getResourceAsStream(FILENAME)
        EQUITY_CACHE = readFromCache(stream)
        if (EQUITY_CACHE.isEmpty()) error("Cannot find preflop equity cache, build is corrupted")
    }
    return EQUITY_CACHE
}


private fun InputStream.readFully(byteArray: ByteArray): Int {
    var totalRead = 0
    while (totalRead < byteArray.size) {
        val read = this.read(byteArray, totalRead, byteArray.size - totalRead)
        if (read == -1) return read
        totalRead += read
    }
    return totalRead
}

private fun readFromCache(stream: InputStream?): Map<Int, Double> {
    if (stream == null) throw FileNotFoundException("EquityCache file not found")
    val indexesToHand = generateCanonicalIndexesToHand()
    val cache = mutableMapOf<Int, Double>()
    val bufferArray = ByteArray(RECORD_SIZE)
    val buffer = ByteBuffer.wrap(bufferArray)
    stream.use {
        while (it.readFully(bufferArray) != -1) {
            buffer.clear()
            val index = buffer.getInt()
            if (indexesToHand.remove(index) == null) {
                error("EquityCache is corrupted, no canonical hand for index $index")
            }
            cache[index] = buffer.getDouble()
        }
    }
    if (indexesToHand.isEmpty()) {
        return cache
    } else {
        error("${indexesToHand.size} canonical hands were not mapped")
    }
}


