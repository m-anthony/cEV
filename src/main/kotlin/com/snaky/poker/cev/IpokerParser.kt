package com.snaky.poker.cev

import java.io.BufferedReader
import kotlin.math.roundToInt

class IpokerParser : AbstractRoomParser(), IPokerXmlListener {

    private val xmlReader = IPokerXmlReader(this)
    private var prizePool = 0.0
    private var heroName: String = ""
    private var validTournament = false

    override fun parseFile(reader: BufferedReader) {
        xmlReader.parse(reader)
    }

    override fun validateHeader(header: String): Boolean = header.contains("<session sessioncode=")

    override fun onHeroName(name: String) = run { heroName = name}

    override fun onTournamentStarted(tournamentCode: String) {
        validTournament = true
        registerSpin(tournamentCode)
        prizePool = spin.buyIn * spin.multiplier
    }

    override fun onTotalBuyIn(amount: Double) {
        spin.buyIn = amount
        spin.multiplier = (prizePool / amount).roundToInt()
    }

    override fun onRewardDrawn(amount: Double) {
        prizePool = amount
        if(spin.buyIn != 0.0) spin.multiplier = (amount / spin.buyIn).roundToInt()
    }
    override fun onTotalWin(amount: Double) = run {spin.wins = amount}


    override fun onNewHand(handId: String) {
        //TODO: handle duplicated hand
        hand = Hand(handId, spin)
        spin.add(hand)
    }

    override fun onPlayerInfo(name: String, chips: Int, bet: Int, win: Int) {
        val player = hand.addPlayer(name, chips)
        if(name == heroName){
            hand.hero = player
            hand.position = Hand.Position.BU //will change when posting blinds
        }
        player.remaining += win - bet //uncalled bets are missing, will be added when hand is done
    }

    override fun onHoleCards(playerName: String, cards: String) {
        if(cards.contains('X')) return
        hand.findPlayer(playerName).cards = cards.toCardSet()
    }

    override fun onAction(street: Int, playerName: String, type: Int, amount: Int) {
        val player = hand.findPlayer(playerName)
        when(type) {
            0 -> registerAction(Action(player, ActionType.Fold), false)
            1 -> {
                //SB
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
        val board = if (hand.rounds.size == 1) cards.toCardSet() else hand.currentRound().board.addCard(cards.toCard())
        onNextRound(board)
    }

    override fun onHandFinished() = handFinished()

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

