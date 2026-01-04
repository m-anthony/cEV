package com.snaky.poker.cev

import io.github.kennethshackleton.skpokereval.evaluator.SevenCardsEvaluator
import java.util.concurrent.ThreadLocalRandom


fun equity(heroCards: CardSet, contenders: List<CardSet>, deadCards: CardSet): Double {
    return when (val cardCount = heroCards.size()) {
        2 -> equityPreFlop(heroCards, contenders, deadCards)
        5 -> equityFlop(heroCards, contenders, deadCards)
        6 -> equityTurn(heroCards, contenders, deadCards)
        7 -> equityRiver(heroCards, contenders)
        else -> throw IllegalArgumentException("Invalid number of cards for hero: $cardCount")
    }
}

private fun equityPreFlop(heroCards: CardSet, contenders: List<CardSet>, deadCards: CardSet): Double {
    return if (contenders.size == 1 && deadCards.size() == 4) {
        equityPreFlopCached(heroCards, contenders[0])
    } else {
        computeEquityPreFlopMc(heroCards, contenders, deadCards)
    }

}

private const val MC_ITERATIONS = 100_000
private fun computeEquityPreFlopMc(heroCards: CardSet, contenders: List<CardSet>, deadCards: CardSet): Double {
    var wins = 0.0
    val liveCards = CardDeck.from(CardSet.all().removeCards(deadCards))
    val random = ThreadLocalRandom.current()
    repeat(MC_ITERATIONS) {
        var board = CardSet()
        for (i in 0 until 5) {
            val idx = i + random.nextInt(liveCards.size - i)
            val card = liveCards[idx]
            liveCards[idx] = liveCards[i]
            liveCards[i] = card
            board = board.addCard(card)
        }
        wins += equityRiver(heroCards, contenders, board)
    }
    return wins / MC_ITERATIONS
}

internal fun computeEquityPreFlop(heroCards: CardSet, contenders: List<CardSet>, deadCards: CardSet): Double {
    var boardCount = 0
    var wins = 0.0
    for (f1 in 0 until CARDS - 4) {
        val flop1 = Card(f1)
        if (deadCards.contains(flop1)) continue
        for (f2 in f1 + 1 until CARDS - 3) {
            val flop2 = Card(f2)
            if (deadCards.contains(flop2)) continue
            val board2 = CardSet().addCard(flop1).addCard(flop2)
            for (f3 in f2 + 1 until CARDS - 2) {
                val flop3 = Card(f3)
                if (deadCards.contains(flop3)) continue
                val board3 = board2.addCard(flop3)
                for (t in f3 + 1 until CARDS - 1) {
                    val turn = Card(t)
                    if (deadCards.contains(turn)) continue
                    val board4 = board3.addCard(turn)
                    for (r in t + 1 until CARDS) {
                        val river = Card(r)
                        if (deadCards.contains(river)) continue
                        boardCount++
                        wins += equityRiver(heroCards, contenders, board4.addCard(river))
                    }
                }
            }
        }
    }
    return wins / boardCount
}

private fun equityFlop(heroCards: CardSet, contenders: List<CardSet>, deadCards: CardSet): Double {
    var boardCount = 0
    var wins = 0.0
    for (t in 0 until CARDS - 1) {
        val turn = Card(t)
        if (deadCards.contains(turn)) continue
        for (r in t + 1 until CARDS) {
            val river = Card(r)
            if (deadCards.contains(river)) continue
            boardCount++
            wins += equityRiver(heroCards, contenders, CardSet().addCard(turn).addCard(river))
        }
    }
    return wins / boardCount
}

private fun equityTurn(heroCards: CardSet, contenders: List<CardSet>, deadCards: CardSet): Double {
    var boardCount = 0
    var wins = 0.0
    for (r in 0 until CARDS) {
        val river = Card(r)
        if (deadCards.contains(river)) continue
        boardCount++
        wins += equityRiver(heroCards, contenders, CardSet().addCard(river))
    }
    return wins / boardCount
}

private fun equityRiver(heroCards: CardSet, contenders: List<CardSet>, board: CardSet = CardSet()): Double {
    val heroValue = SevenCardsEvaluator.getRank(heroCards.addCards(board).cards)
    var ties = 0
    for (contender in contenders) {
        val contenderValue = SevenCardsEvaluator.getRank(contender.addCards(board).cards)
        if (contenderValue > heroValue) {
            return 0.0
        } else if (contenderValue == heroValue) {
            ties++
        }
    }
    return 1.0 / (1 + ties)
}