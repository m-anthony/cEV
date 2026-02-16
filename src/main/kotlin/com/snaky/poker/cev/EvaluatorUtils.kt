package com.snaky.poker.cev

import io.github.kennethshackleton.skpokereval.evaluator.SevenCardsEvaluator
import java.util.concurrent.ThreadLocalRandom

fun equityHeadsUp(hand: Hand, betTracker: BetTracker): Double {

    val heroSeat = hand.hero.seatId
    val heroCards = hand.hero.cards
    val villainSeat = (betTracker.pots[0].eligibleMask and (1 shl heroSeat).inv()).countTrailingZeroBits()
    val villainCards = hand.players[villainSeat].cards
    val board = hand.rounds.first { it.street == betTracker.lastActiveStreet}.board
    val heroCardsAndBoard = heroCards.addCards(board)
    val villainCardsAndBoard = villainCards.addCards(board)
    val deadCards = heroCards.addCards(villainCardsAndBoard)
    return when(val cardCount = board.size()){
        0 -> equityHeadsUpPreFlopCached(heroCards, villainCards)
        3 -> equityHeadsUpFlop(heroCardsAndBoard, villainCardsAndBoard, deadCards)
        4 -> equityHeadsUpTurn(heroCardsAndBoard, villainCardsAndBoard, deadCards)
        else -> throw IllegalArgumentException("Invalid number of cards for board: $cardCount")
    }
}

fun equitiesMultiWay(hand: Hand, betTracker: BetTracker, potCount: Int = betTracker.pots.size): DoubleArray {
    val board = hand.rounds.first { it.street == betTracker.lastActiveStreet}.board
    var deadCards = board
    val handsAndBoard =
        Array(hand.players.size) { i -> board.addCards(hand.players[i].cards.also { deadCards = deadCards.addCards(it) }) }
    val heroSeat = hand.hero.seatId
    return when(val cardCount = board.size()){
        0 -> equitiesMultiWayPreFlopMc(heroSeat, handsAndBoard, deadCards, betTracker.pots, potCount)
        3 -> equitiesMultiWayFlop(heroSeat, handsAndBoard, deadCards, betTracker.pots, potCount)
        4 -> equitiesMultiWayTurn(heroSeat, handsAndBoard, deadCards, betTracker.pots, potCount)
        else -> throw IllegalArgumentException("Invalid number of cards for board: $cardCount")
    }
}

private const val MC_ITERATIONS = 100_000

fun equitiesMultiWayPreFlopMc(
    heroSeat: Int,
    handsAndBoard: Array<CardSet>,
    deadCards: CardSet,
    pots: List<Pot>,
    potCount: Int
): DoubleArray {
    val equities = DoubleArray(potCount) { 0.0 }
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
        updateEquities(board, equities, handsAndBoard, heroSeat, pots, potCount)
    }
    equities.forEachIndexed { i, eq -> equities[i] = eq / MC_ITERATIONS }
    return equities
}

fun equitiesMultiWayFlop(
    heroSeat: Int,
    handsAndBoard: Array<CardSet>,
    deadCards: CardSet,
    pots: List<Pot>,
    potCount: Int
): DoubleArray {
    val equities = DoubleArray(potCount) { 0.0 }
    var boardCount = 0
    for (t in 0 until CARDS - 1) {
        val turn = Card(t)
        if (deadCards.contains(turn)) continue
        for (r in t + 1 until CARDS) {
            val river = Card(r)
            if (deadCards.contains(river)) continue
            boardCount++
            updateEquities(CardSet().addCard(turn).addCard(river), equities, handsAndBoard, heroSeat, pots, potCount)
        }
    }
    equities.forEachIndexed { i, eq -> equities[i] = eq / boardCount }
    return equities
}

private fun equitiesMultiWayTurn(
    heroSeat: Int,
    handsAndBoard: Array<CardSet>,
    deadCards: CardSet,
    pots: List<Pot>,
    potCount: Int
): DoubleArray {
    val equities = DoubleArray(potCount) { 0.0 }
    var boardCount = 0
    for (r in 0 until CARDS) {
        val river = Card(r)
        if (deadCards.contains(river)) continue
        boardCount++
        updateEquities(CardSet().addCard(river), equities, handsAndBoard, heroSeat, pots, potCount)
    }
    equities.forEachIndexed { i, eq -> equities[i] = eq / boardCount }
    return equities
}

private fun updateEquities(
    board: CardSet,
    equities: DoubleArray,
    hands: Array<CardSet>,
    heroSeat: Int,
    pots: List<Pot>,
    potCount: Int
) {
    val heroHand = hands[heroSeat].addCards(board).cards
    val heroScore = SevenCardsEvaluator.getRank(heroHand)
    var winners = 1
    var previousMask = 1 shl heroSeat
    for(i in potCount - 1 downTo 0) {
        var currentMask = pots[i].eligibleMask and (previousMask).inv()
        while(currentMask != 0 && winners != 0){
            val villainSeat = currentMask.countTrailingZeroBits()
            currentMask = currentMask and (currentMask - 1)
            val villainScore = SevenCardsEvaluator.getRank(hands[villainSeat].addCards(board).cards)
            when {
                villainScore > heroScore -> return
                villainScore == heroScore -> winners++
            }
        }
        equities[i] += 1.0 / winners
        previousMask = pots[i].eligibleMask
    }
}

internal fun computeEquityPreFlop(heroCards: CardSet, contenders: CardSet, deadCards: CardSet): Double {
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

private fun equityHeadsUpFlop(heroCards: CardSet, villainCards: CardSet, deadCards: CardSet): Double {
    var boardCount = 0
    var wins = 0.0
    for (t in 0 until CARDS - 1) {
        val turn = Card(t)
        if (deadCards.contains(turn)) continue
        for (r in t + 1 until CARDS) {
            val river = Card(r)
            if (deadCards.contains(river)) continue
            boardCount++
            wins += equityRiver(heroCards, villainCards, CardSet().addCard(turn).addCard(river))
        }
    }
    return wins / boardCount
}

private fun equityHeadsUpTurn(heroCards: CardSet, contenders: CardSet, deadCards: CardSet): Double {
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

private fun equityRiver(heroCards: CardSet, vilainCards: CardSet, board: CardSet = CardSet()): Double {
    val heroValue = SevenCardsEvaluator.getRank(heroCards.addCards(board).cards)
    val contenderValue = SevenCardsEvaluator.getRank(vilainCards.addCards(board).cards)
    return when {
        contenderValue > heroValue -> 0.0
        contenderValue == heroValue -> 0.5
        else -> 1.0
    }
}