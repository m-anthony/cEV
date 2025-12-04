package com.snaky.poker

import kotlin.math.max
import kotlin.math.min

class Hand(
    val id: String,
    val spin: Spin
) {
    var timestamp: Long = 0L
    var chips = 0
    var cev = 0.0
    var blind = 0

    val position: Position get() = hero.position

    private val players = mutableListOf<Player>() //sorted by stack size
    private lateinit var hero: Player
    private lateinit var bettor: Player
    private var currentBet = 0
    private var matchedBet = 0

    private var activeCount = 0
    private var pots = mutableListOf(0)
    private var deadPot = 0
    private var lastBoard = lazy { 0L }

    enum class Position {
        //ordered by action order to deal with oddChip
        SB,
        BB,
        HUBB,
        HUSB,
        BU
    }

    fun addPlayer(name: String, stack: Int, positionName: String, hero: Boolean) {
        val position = when (positionName) {
            "BB" -> Position.HUBB
            "BTN SB" -> Position.HUSB
            "BTN" -> Position.BU
            "SB" -> Position.SB
            else -> throw IllegalStateException("Unknown position $positionName")
        }

        val player = Player(name, stack, position)
        var index = players.binarySearch(player, compareBy { it.position.ordinal })
        if (index < 0) index = -(index + 1)
        players.add(index, player)
        activeCount++

        if (hero) this.hero = player
        if (players.size == 3 && this.hero.position == Position.HUBB) {
            this.hero.position = Position.BB
        }
    }

    private fun findPlayer(name: String): Player {
        return players.first { it.name == name }
    }

    fun post(name: String, bet: Int) {
        if (bet <= currentBet) {
            // posts BB but stack < SB -> it's a call
            val player = findPlayer(name)
            player.currentBet = player.remaining
            matchedBet = player.remaining
        } else bet(name, bet)
    }

    fun bet(name: String, bet: Int) {
        matchedBet = currentBet
        currentBet = bet
        bettor = findPlayer(name)
        bettor.currentBet = bet
    }

    fun call(name: String) {
        val caller = findPlayer(name)
        if (currentBet < blind && caller.stack == caller.remaining && caller.remaining > currentBet) {
            //incomplete BB posted, call whole BB instead of current bet
            bet(name, min(caller.remaining, blind))
            return
        }
        caller.currentBet = min(caller.remaining, currentBet)
        matchedBet = max(matchedBet, caller.currentBet)
        if (bettor.remaining == matchedBet && caller.stack > bettor.stack) bettor = caller
    }

    fun fold(name: String) {
        val player = findPlayer(name)
        player.active = false
        activeCount--
        deadPot += player.stack - (player.remaining - player.currentBet)
    }

    fun bettingRoundFinished(newBoard: Lazy<Long>) {
        var allIn = 0
        var remainingCount = activeCount

        for (player in players) {
            if (player != bettor) {
                if (player.remaining == player.currentBet) {
                    remainingCount--
                    allIn = max(allIn, player.stack)
                    if (player.remaining < matchedBet && pots.size == 1) pots.addFirst(player.stack)
                    if (player.currentBet == 0) {
                        //all in from previous round => no equity computation
                        player.cards = 0
                    } else if (player.cards > 0) {
                        player.cards = player.cards or lastBoard.value
                    }
                }
                player.remaining -= player.currentBet
                player.currentBet = 0
            }
        }
        //reduce to matchedBet if there is no side pot after biggest all in
        if (allIn > 0 && bettor.remaining - matchedBet == bettor.stack - allIn) {
            bettor.currentBet = matchedBet
            if(remainingCount == 1 && bettor.cards > 0) bettor.cards = bettor.cards or lastBoard.value
        }
        bettor.remaining -= bettor.currentBet
        bettor.currentBet = 0
        pots[pots.lastIndex] += matchedBet
        matchedBet = 0
        currentBet = 0
        lastBoard = newBoard
    }

    fun holecards(playerName: String, cards: Long) {
        findPlayer(playerName).cards = cards
    }

    fun winPot(playerName: String, chips: Int) {
        findPlayer(playerName).remaining += chips
    }

    fun handFinished() {
        val chipsAfter = players.sumOf { it.remaining }
        val chipsBefore = players.sumOf { it.stack }

        if (chipsAfter != chipsBefore) throw IllegalStateException("Wrong chip count ($chipsBefore -> $chipsAfter) for hand $id")
        chips = hero.remaining - hero.stack
        if(hero.handValue == 0) cev = chips.toDouble()
    }

    fun playerFinished(player: String, wins: Double) {
        if (findPlayer(player) == hero) spin.wins = wins
    }

    fun showdown(playerName: String, handValue: Int) {
        val player = findPlayer(playerName)
        player.handValue = handValue
    }

    fun showdownEnd() {
        //compute hero cEV
        cev = max(hero.remaining - hero.stack, -pots.last()).toDouble()
        if(!hero.active) return //if hero folds, nothing to claim from pots
        var lastPot = 0

        var computeEquity: Boolean
        while (!pots.isEmpty()) {
            val currentPot = pots.removeFirst()
            val potPlayers = players.filter { it.active && (it.stack - it.remaining >= currentPot) }
            val chips = (currentPot - lastPot) * potPlayers.size + min(deadPot, currentPot)
            deadPot -= min(deadPot, currentPot - lastPot)
            lastPot = currentPot

            computeEquity = potPlayers.size > 1
                    && hero.cards.countOneBits() in 2 until 7
                    && potPlayers.contains(hero)
                    && potPlayers.all { it.cards.countOneBits() == hero.cards.countOneBits() }
                    && potPlayers.any { it.stack == currentPot }

            if(!computeEquity) {
                val maxHandValue = potPlayers.maxOf { it.handValue }
                if (hero.handValue == maxHandValue) cev += chips * 1f / potPlayers.count { it.handValue == maxHandValue }
            } else {
                var deadCards = 0L
                val contenders = mutableListOf<Long>()
                players.forEach { p ->
                    deadCards = deadCards or p.cards
                    if(p != hero && potPlayers.contains(p)) contenders.add(p.cards)
                }
                cev += chips * equity(hero.cards, contenders, deadCards)
            }
        }
    }

    private data class Player(
        val name: String,
        val stack: Int,
        var position: Position
    ) {
        var cards = 0L
        var handValue = 0
        var remaining = stack
        var currentBet = 0
        var active = true
    }
}

