package com.snaky.poker.cev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption


fun equityPreFlopCached(heroHand: CardSet, vilainHand: CardSet): Double {
    val index = computeIndex(heroHand, vilainHand)
    val cachedIndex = if (index >= 0) index else -(index + 1)
    val equity = getPreFlopEquityCache()[cachedIndex]
        ?: throw IllegalStateException("index $index not in equity cache")
    return if (index >= 0) equity else 1 - equity
}

private lateinit var EQUITY_CACHE: Map<Int, Double>
private const val FILENAME = "preflop_equity.dat"
private fun getPreFlopEquityCache(): Map<Int, Double> {
    if (!::EQUITY_CACHE.isInitialized) {
        val stream = object {}::class.java.classLoader.getResourceAsStream(FILENAME)
        EQUITY_CACHE = readFromCache(stream)
        if (EQUITY_CACHE.isEmpty()) error("Cannot find pre-flop equity cache, build is corrupted")
    }
    return EQUITY_CACHE
}

/**
 * given 2 hands, compute the index in the preflop equity cache
 * @return if the hands are the expected order, the corresponding cache's key, otherwise (-key - 1) so the caller
 * knows that it correct value is (1 - equity)
 */
private fun computeIndex(hA: CardSet, hB: CardSet): Int {
    val indexA = getHandClassIndex(hA)
    val indexB = getHandClassIndex(hB)
    val colorIndex = calculateCanonicalSuitIndex(hA, hB)
    val inverted = (indexB > indexA) || (indexA == indexB && colorIndex.inverted)
    val classIndex = if (!inverted) {
        (indexA * (indexA + 1)) / 2 + indexB
    } else {
        (indexB * (indexB + 1)) / 2 + indexA
    }
    val canonicalIndex = colorIndex.index + 256 * classIndex
    return if (inverted) -canonicalIndex - 1 else canonicalIndex
}

/**
 * return a value identifying the hand class (such as 77, AKs, 72o)
 */
private fun getHandClassIndex(hand: CardSet): Int {
    val c1 = hand.firstCard()
    val c2 = hand.removeFirstCard().firstCard()
    val r1 = c1.rank()
    val r2 = c2.rank()

    if (r1 == r2) return r1.ordinal

    var handClass = 13
    val rankHigh = maxOf(r1, r2).ordinal
    val rankLow = minOf(r1, r2).ordinal
    handClass += (rankHigh * (rankHigh - 1)) / 2 + rankLow
    if (c1.suit() == c2.suit()) handClass += 78 //suited
    return handClass
}


private const val RANK_FILTER = 1L or (1L shl 13) or (1L shl 26) or (1L shl 39)
private const val MAX_SUITS = 4
private const val MAX_RANK = 13

private data class SuitIndexResult(
    val index: Int,
    val inverted: Boolean
)

/**
 * compute a suit index that has the following properties :
 * - calculateCanonicalSuitIndex(hA, hB).index = calculateCanonicalSuitIndex(hB, hA).index
 * - if hA and hB have the same hand class, calculateCanonicalSuitIndex(hA, hB).inverted =
 * calculateCanonicalSuitIndex(hB, hA).inverted only if they have the same equity (50%)
 * - if (hC, hD) have the same hand classes and the same suit index than (hA, hB), then (hA vs hB) is the same equity
 * as (hC vs hD)
 */
private fun calculateCanonicalSuitIndex(hA: CardSet, hB: CardSet): SuitIndexResult {

    //algorithm :
    // - sort cards involved by rank
    // - everytime a new suit is used, map it to the next canonical suit
    // - encode the list of canonical colors encountered
    // - do the same for each hand : when computing ATo vs ATo for example, order of hand matters if there are
    // several suit for the same rank

    val cards = hA.addCards(hB)
    val realToCanonical = IntArray(MAX_SUITS) { -1 }
    var canonicalSuitCounter = 0
    var result = 0

    // compute a bitset of ranks to iterate in the correct order
    var cardsTmp = cards
    var ranks = 0
    while (!cardsTmp.isEmpty()) {
        val card = cardsTmp.firstCard()
        cardsTmp = cardsTmp.removeFirstCard()
        ranks = ranks or (1 shl (card.rank().ordinal))
    }

    var hASuit = 0
    var hBSuit = 0
    //FIXME: improve readability, shouldn't know about Card/CardSet implementation
    while (ranks != 0) {
        val rank = ranks.countTrailingZeroBits()
        ranks = ranks and (ranks - 1) // clear 'rank' bit
        var filteredCards = (cards.cards ushr rank) and RANK_FILTER
        while (filteredCards != 0L) {
            val suit = filteredCards.countTrailingZeroBits() / MAX_RANK // next suit used for that given rank
            filteredCards = filteredCards and (filteredCards - 1)
            // verify if the suit has been already seen, otherwise map it to a new canonical suit
            var canonicalSuit = realToCanonical[suit]
            if (canonicalSuit == -1) {
                canonicalSuit = canonicalSuitCounter++
                realToCanonical[suit] = canonicalSuit
            }

            if (hA.contains(Card(rank + MAX_RANK * suit))) {
                hASuit = hASuit * MAX_SUITS + canonicalSuit
            } else {
                hBSuit = hBSuit * MAX_SUITS + canonicalSuit
            }
            result = result * MAX_SUITS + canonicalSuit
        }
    }
    return SuitIndexResult(result, hBSuit > hASuit)
}

/**
 * for each valid index in cache, get a sample pair of hands
 */
private fun generateCanonicalIndexesToHand(): MutableMap<Int, Pair<CardSet, CardSet>> {
    val canonicalIndexesToHands = mutableMapOf<Int, Pair<CardSet, CardSet>>()
    // 1. Boucler sur toutes les paires de mains (H_A vs H_B)
    for (i in 0 until CARDS - 1) {
        for (j in i + 1 until CARDS) {
            val hA = CardSet().addCard(Card(i)).addCard(Card(j))
            for (k in 0 until CARDS - 1) {
                if (k == i || k == j) continue
                for (l in k + 1 until CARDS) {
                    if (l == i || l == j) continue
                    val hB = CardSet().addCard(Card(k)).addCard(Card(l))
                    val canonicalIndex = computeIndex(hA, hB)
                    if (canonicalIndex >= 0) canonicalIndexesToHands.putIfAbsent(canonicalIndex, Pair(hA, hB))
                }
            }
        }
    }
    return canonicalIndexesToHands
}

fun main(args: Array<String>) {
    buildEquityCache(File(args[0], FILENAME))
}

private const val RECORD_SIZE = Int.SIZE_BYTES + Double.SIZE_BYTES
private fun buildEquityCache(file: File) {
    if (file.exists()) {
        FileInputStream(file).use { if (!readFromCache(it).isEmpty()) return }
    }
    val indexesToHand = generateCanonicalIndexesToHand()
    println("Precomputing equities for ${indexesToHand.size} canonical pair of hands")
    val cache = runBlocking(Dispatchers.Default) { computeEquities(indexesToHand) }
    println("Saving pre-flop equities in $file")
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
private suspend fun computeEquities(indexesToHand: Map<Int, Pair<CardSet, CardSet>>): Map<Int, Double> = coroutineScope {
    indexesToHand.entries.map { (index, hands) ->
        async {
            index to computeEquityPreFlop(hands.first, listOf(hands.second), hands.first.addCards(hands.second))
        }
    }.awaitAll().toMap()
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

private fun InputStream.readFully(byteArray: ByteArray): Int {
    var totalRead = 0
    while (totalRead < byteArray.size) {
        val read = this.read(byteArray, totalRead, byteArray.size - totalRead)
        if (read == -1) return read
        totalRead += read
    }
    return totalRead
}


