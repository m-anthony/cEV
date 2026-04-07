package com.snaky.poker.cev.core.parsers

import com.snaky.poker.cev.core.BetTracker
import com.snaky.poker.cev.core.model.MultiplierTier
import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.*
import java.io.BufferedReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class IpokerParser : AbstractRoomParser(), IPokerXmlListener {

    private val xmlReader = IPokerXmlReader(this)
    private var prizePoolCents = 0
    private var heroName: String = ""
    private var validTournament = false
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var validHand = true

    override val room = Room.IPOKER
    override val payoutProvider: (Spin) -> PayoutScheme = IpokerPayouts
    override val getAllPayoutScheme: List<PayoutScheme> = IpokerPayouts.ALL

    override fun parseFile(reader: BufferedReader) {
        xmlReader.parse(reader)
    }

    override fun validateHeader(header: String, fileName: String): Boolean =
        header.contains("<session sessioncode=") && header.contains("Twister")

    override fun onHeroName(name: String) = run { heroName = name}

    override fun onTournamentStarted(tournamentCode: String) {
        validTournament = true
        registerSpin(tournamentCode)
        prizePoolCents = spin.buyInCents * spin.multiplier
    }

    override fun onTotalBuyIn(amount: Double) {
        spin.buyInCents = (amount * 100).roundToInt()
        spin.multiplier = prizePoolCents / spin.buyInCents
    }

    override fun onRewardDrawn(amount: Double) {
        prizePoolCents = (amount * 100).roundToInt()
        if(spin.buyInCents != 0) spin.multiplier = prizePoolCents / spin.buyInCents
    }
    override fun onTotalWin(amount: Double) = run {spin.winCents = (amount * 100).roundToInt()}


    override fun onNewHand(handId: String) {
        hand = Hand(handId, spin)
        validHand = validTournament && spin.add(hand).also { if(!it) duplicateHands++ }
    }

    override fun onHandStartDate(datetime: String) {
        hand.timestamp = LocalDateTime.parse(datetime, timeFormatter).toEpochSecond(ZoneOffset.UTC)
    }

    override fun onBigBlind(blind: Int) {
        hand.blind = blind
    }

    override fun onPlayerInfo(name: String, chips: Int, bet: Int, win: Int) {
        if(!validHand) return
        if(spin.hands.size == 1) spin.startingStack = chips
        val player = hand.addPlayer(name, chips)
        if(name == heroName){
            hand.hero = player
            hand.position = Hand.Position.BU //will change when posting blinds
        }
        player.remaining += win - bet //uncalled bets are missing, will be added when hand is done
    }

    override fun onHoleCards(playerName: String, cards: String) {
        if(!validHand) return
        if(cards.contains('X')) return
        hand.findPlayer(playerName).cards = cards.toCardSet()
    }

    override fun onAction(street: Int, playerName: String, type: Int, amount: Int) {
        if(!validHand) return
        val player = hand.findPlayer(playerName)
        when(type) {
            0 -> registerAction(Action(player, ActionType.Fold), false)
            1 -> {
                if(!hand.heroDetected){
                    spin.remove(hand)
                    validHand = false
                    return
                }
                this.betTracker = BetTracker(hand.players.size)
                registerAction(Action(player, ActionType.Blind, amount >= player.stack, amount), false)
                if (player == hand.hero) {
                    hand.position = if (hand.players.size == 2) Hand.Position.HUSB else Hand.Position.SB
                }
            }
            2 -> {
                //BB
                registerAction(Action(player, ActionType.Blind, amount >= player.stack, amount), false)
                if (player == hand.hero) {
                    hand.position = if (hand.players.size == 2) Hand.Position.HUBB else Hand.Position.BB
                }
            }
            3 -> registerAction(Action(player, ActionType.Call, false, amount), false)
            7 -> registerAction(Action(player, ActionType.Call, true, amount), false)
            4 -> registerAction(Action(player, ActionType.Check), false)
            5,23 -> {
                val remaining = player.stack - betTracker.getBet(player.seatId)
                registerAction(Action(player, ActionType.Bet, amount >= remaining, amount), false)
            }
            else -> IllegalArgumentException("unknown action type '$type' in spin/hand '${spin.id}/${hand.id}'")
        }
    }

    override fun onBoardCards(streetType: String, cards: String) {
        if(!validHand) return
        val board = if (hand.rounds.size == 1) cards.toCardSet() else hand.currentRound().board.addCard(cards.toCard())
        onNextRound(board)
    }

    override fun onHandFinished() {
        if(validHand) handFinished()
        validHand = false
    }

    override fun onAborted() {
        validTournament = false
    }

}

private fun String.toCard(): Card {
    val rank = when(val rankChar = this[1].uppercaseChar()) {
        '1' -> Rank.TEN
        else -> Rank.valueOf(rankChar)
    }
    val suit = Suit.valueOf(this[0].lowercaseChar())
    return Card.of(rank, suit)
}

private fun String.toCardSet(): CardSet {
    var result = CardSet()
    this.split(" ")
        .filter { it.isNotBlank() }
        .forEach { result = result.addCard(it.toCard()) }
    return result
}

private object IpokerPayouts: (Spin) -> PayoutScheme {

    override fun invoke(spin: Spin): PayoutScheme = when {
        spin.buyInCents < 20000 -> STANDARD
        else -> SPIN200
    }

    val STANDARD = PayoutScheme(
        name = "Standard",
        room = Room.IPOKER,
        availableBuyInCents = listOf(1_00, 2_00, 5_00, 10_00, 20_00, 50_00, 100_00),
        tiers = listOf(
            MultiplierTier(2, 45_508),
            MultiplierTier(3, 38_128),
            MultiplierTier(4, 13_000),
            MultiplierTier(5, 3_000),
            MultiplierTier(listOf(7.5, 1.5, 1), 300),
            MultiplierTier(listOf(15, 3, 2), 50),
            MultiplierTier(listOf(75, 15, 10), 10),
            MultiplierTier(listOf(150, 30, 20), 3),
            MultiplierTier(listOf(750, 150, 100), 1)
        )
    )

    val SPIN200 = PayoutScheme(
        name = "200 EUR",
        room = Room.IPOKER,
        availableBuyInCents = listOf(200_00),
        tiers = listOf(
            MultiplierTier(2, 49_999),
            MultiplierTier(3, 31_743),
            MultiplierTier(4, 13_412),
            MultiplierTier(5, 4_000),
            MultiplierTier(listOf(7.5, 1.5, 1), 750),
            MultiplierTier(listOf(18.75, 3.75, 2.5), 75),
            MultiplierTier(listOf(37.5, 7.5, 5), 15),
            MultiplierTier(listOf(75, 15, 10), 5),
            MultiplierTier(listOf(1875, 375, 250), 1)
        )
    )

    val ALL = listOf(STANDARD, SPIN200)
}