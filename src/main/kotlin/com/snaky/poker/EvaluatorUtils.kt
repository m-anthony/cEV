package com.snaky.poker

import io.github.kennethshackleton.skpokereval.evaluator.SevenCardsEvaluator
import java.util.concurrent.ThreadLocalRandom

internal enum class Rank(val ch: Char) {
    TWO('2'),
    THREE('3'),
    FOUR('4'),
    FIVE('5'),
    SIX('6'),
    SEVEN('7'),
    EIGHT('8'),
    NINE('9'),
    TEN('T'),
    JACK('J'),
    QUEEN('Q'),
    KING('K'),
    ACE('A');

    companion object {
        private val MAP = entries.associateBy(Rank::ch)
        fun valueOf(ch: Char): Rank = MAP[ch] ?: throw IllegalArgumentException()
    }

}

internal enum class Suit(val ch: Char) {
    SPADE('s'),
    HEART('h'),
    CLUB('c'),
    DIAMOND('d');

    companion object {
        private val MAP = Suit.entries.associateBy(Suit::ch)
        fun valueOf(ch: Char): Suit = MAP[ch] ?: throw IllegalArgumentException()
    }
}

val CARDS = Suit.entries.size * Rank.entries.size

fun toLongHand(hand: String): Long {
    var h = 0L
    for(i in 0 until hand.length - 1 step 3){
        val face = Rank.valueOf(hand[i])
        val suit = Suit.valueOf(hand[i+1])
        val card = face.ordinal + suit.ordinal * Rank.entries.size
        h = h or (1L shl card)
    }
    return h
}

fun toString(hand : Long): String {
    var handCopy = hand
    return buildString {
        append('[')
        while(handCopy != 0L){
            val card = handCopy.countTrailingZeroBits()
            handCopy = handCopy and ((1L shl card).inv())
            append(Rank.entries[card % Rank.entries.size].ch)
            append(Suit.entries[card / Rank.entries.size].ch)
            if(handCopy != 0L) append(' ')
        }
        append(']')
    }
}

fun equity(heroCards: Long, contenders: List<Long>, deadCards: Long): Double {
    return when(val cardCount = heroCards.countOneBits()){
        2 -> equityPreflop(heroCards, contenders, deadCards)
        5 -> equityFlop(heroCards, contenders, deadCards)
        6 -> equityTurn(heroCards, contenders, deadCards)
        7 -> equityRiver(heroCards, contenders, 0L)
        else -> throw IllegalArgumentException("Invalid number of cards for hero: $cardCount")
    }
}

var equityPreflopCount = 0
var equityPreflopCached = 0
fun equityPreflop(heroCards: Long, contenders: List<Long>, deadCards: Long): Double {
    equityPreflopCount++
    if(contenders.size == 1 && deadCards.countOneBits() == 4){
        equityPreflopCached++
        val cached = equityPreflopCached(heroCards, contenders[0])
        return cached
    } else {
        return computeEquityPreflopMc(heroCards, contenders, deadCards)
    }

}

private const val MC_ITERATIONS = 100_000
private fun computeEquityPreflopMc(heroCards: Long, contenders: List<Long>, deadCards: Long): Double {
    var wins = 0.0
    val liveCards = (0 until 52).filter { (1L shl it) and deadCards == 0L }.toIntArray()
    val random = ThreadLocalRandom.current()
    repeat(MC_ITERATIONS) {
        var board = 0L
        for(i in  0 until 5) {
            val idx = i + random.nextInt(liveCards.size - i)
            val card = liveCards[idx]
            liveCards[idx] = liveCards[i]
            liveCards[i] = card
            board = board or (1L shl card)
        }
        wins += equityRiver(heroCards, contenders, board)
    }
    return wins / MC_ITERATIONS
}

internal fun computeEquityPreflop(heroCards: Long, contenders: List<Long>, deadCards: Long): Double {
    var boardCount = 0
    var wins = 0.0
    for (f1 in 0 until CARDS - 4) {
        val flop1 = 1L shl f1
        if ((deadCards and flop1) != 0L) continue
        for (f2 in f1 + 1 until CARDS - 3) {
            val flop2 = 1L shl f2
            if ((deadCards and flop2) != 0L) continue
            val board2 = flop1 or flop2
            for (f3 in f2 + 1 until CARDS - 2) {
                val flop3 = 1L shl f3
                if ((deadCards and flop3) != 0L) continue
                val board3 = board2 or flop3
                for (t in f3 + 1 until CARDS - 1) {
                    val turn = 1L shl t
                    if ((deadCards and turn) != 0L) continue
                    val board4 = board3 or turn
                    for (r in t + 1 until CARDS) {
                        val river = 1L shl r
                        if ((deadCards and river) != 0L) continue
                        boardCount++
                        wins += equityRiver(heroCards, contenders, board4 or river)
                    }
                }
            }
        }
    }
    return wins / boardCount
}

fun equityFlop(heroCards: Long, contenders: List<Long>, deadCards: Long): Double {
    var boardCount = 0
    var wins = 0.0
    for(t in 0 until CARDS - 1){
        val turn = 1L shl t
        if((deadCards and turn) != 0L) continue
        for(r in t+1 until CARDS) {
            val river = 1L shl r
            if ((deadCards and river) != 0L) continue
            boardCount++
            wins += equityRiver(heroCards, contenders, turn or river)
        }
    }
    return wins / boardCount
}

fun dumpEquityStats(){
    println("All in preflop requested : $equityPreflopCount")
    println("All in preflop cached : $equityPreflopCached")
}

fun equityTurn(heroCards: Long, contenders: List<Long>, deadCards: Long): Double {
    var boardCount = 0
    var wins = 0.0
    for(r in 0 until CARDS){
        val river = 1L shl r
        if((deadCards and river) != 0L) continue
        boardCount++
        wins += equityRiver(heroCards, contenders, river)
    }
    return wins / boardCount
}

fun equityRiver(heroCards: Long, contenders: List<Long>, board: Long): Double {
    val heroValue = SevenCardsEvaluator.getRank(heroCards or board)
    var ties = 0
    for(contender in contenders){
        val contenderValue = SevenCardsEvaluator.getRank(contender or board)
        if(contenderValue > heroValue) {
            return 0.0
        } else if(contenderValue == heroValue){
            ties++
        }
    }
    return 1.0 / (1 + ties)
}