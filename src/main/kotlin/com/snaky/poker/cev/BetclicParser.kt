package com.snaky.poker.cev

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class BetclicParser : AutoCloseable {
    val spins = mutableMapOf<String, Spin>()

    private var state = ParserState.INIT
    private lateinit var spin: Spin
    private lateinit var hand: Hand
    private lateinit var betTracker: BetTracker
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var asyncTasks = mutableListOf<() -> Unit>()

    fun parseFile(s: InputStream) {
        try {
            s.bufferedReader().lineSequence().forEach { state = state.parseLine(it, this) }
            //there is no delimiter for the last hand of the file, so EOF should trigger some actions
            if (state != ParserState.SKIPPED) handFinished()
            state = ParserState.INIT
        } catch (e: Exception){
            println("Exception in hand ${hand.id}")
            throw e
        }
        if(asyncTasks.isNotEmpty()){
            val tasks = asyncTasks
            scope.launch { tasks.forEach { it.invoke() } }
            asyncTasks = mutableListOf()
        }
    }

    override fun close() {
        runBlocking {
            scope.coroutineContext.job.children.toList().joinAll()
        }
        spins.values.forEach { it.aggregateHands() }
    }

    private fun BufferedReader.lineSequence(): Sequence<String> = sequence {
        var line = readLine()
        while (line != null) {
            yield(line)
            line = readLine()
        }
    }


    private fun parseSeat(l: String) {
        val name = l.substringAfter(": ").substringBefore(" (")
        val stack = l.substringAfter('(').substringBefore(')').toInt()
        var position = l.substringAfter('[').substringBefore("]", "")
        val hero = position.endsWith("Hero")
        if(hero) position = position.substringBefore(" Hero")
        hand.addPlayer(name, stack, position, hero)
    }

    private fun parseHoleCards(l: String) {
        val player = l.substringBefore(": [")
        val cards = CardSet.parse(l.substringAfterLast('['))
        hand.holeCards(player, cards)
    }

    private fun parseAction(l: String) {
        val name = l.substringAfter("- ").substringBefore(':')
        val allIn = l.endsWith(" and is all-in")
        val actionString = l.substringAfter(": ").substringBefore(" and is all-in")
        var type: ActionType? = null
        var amount = 0
        if(actionString.startsWith("Posts")) {
            type = ActionType.Blind
            amount = actionString.substringAfterLast(' ').toInt()
        } else if (actionString.startsWith("Raises") || actionString.startsWith("Bets")) {
            type = ActionType.Bet
            amount = actionString.substringAfterLast(' ').toInt()
        } else if (actionString.startsWith("Calls")) {
            type = ActionType.Call
            amount = actionString.substringAfterLast(' ').toInt()
        } else if (actionString.startsWith("Folds")) {
            type = ActionType.Fold
        } else if (actionString.startsWith("Checks")) {
            type = ActionType.Check
        } // else disconnect/reconnect -> ignore
        if (type != null) {
            val round = hand.currentRound()
            Action(hand.findPlayer(name), type, allIn, amount).also {
                round.addAction(it)
                betTracker.registerAction(it, round.street)
            }
        }
    }

    private fun bettingRoundFinished(l: String) {
        val board = l.substringAfter('[', "")
        hand.nextRound(board)
        betTracker.nextRound()
    }

    private fun handFinished() {
        for(i in 0 until hand.players.size){
            hand.players[i].remaining -= betTracker.getBet(i)
        }
        val diff = hand.players.sumOf { it.stack - it.remaining }
        if(diff != 0) hand.players[betTracker.uncalledBettor()].remaining += diff //return uncalled bet

        val heroSeat = hand.hero.seatId
        val firstAllInStreet = hand.rounds.find { it.actions.any(Action::allIn) }?.street
        val computeEquity = with(betTracker) {
            isActiveSeat(heroSeat) && lastActiveStreet != Street.River && lastActiveStreet == firstAllInStreet && isContested()
        }
        if(!computeEquity){
            hand.cev = hand.hero.remaining - hand.hero.stack.toDouble()
        } else {
            hand.cev = -betTracker.getBet(heroSeat).toDouble()
            var potCount = betTracker.pots.size
            while (!betTracker.pots[potCount - 1].isEligible(heroSeat)) potCount--
            while (betTracker.pots[potCount - 1].eligiblePlayerCount() == 1) {
                hand.cev += betTracker.pots[--potCount].amount.toDouble()
            }
            if(potCount == 1 && betTracker.pots[0].eligiblePlayerCount() == 2) {
                hand.cev += betTracker.pots[0].amount * equityHeadsUp(hand, betTracker)
            } else if(firstAllInStreet == Street.Preflop) {
                val hand = this.hand
                val betTracker = this.betTracker
                asyncTasks.add  {
                    val equities = equitiesMultiWay(hand, betTracker, potCount)
                    for(i in 0 until potCount){
                        hand.cev += equities[i] * betTracker.pots[i].amount
                    }
                }
            } else {
                val equities = equitiesMultiWay(hand, betTracker, potCount)
                for(i in 0 until potCount){
                    hand.cev += equities[i] * betTracker.pots[i].amount
                }
            }
        }
    }

    private fun winPot(l: String) {
        val player = l.substringBefore(" wins ")
        val chips = l.substringAfterLast(" of ").toInt()
        hand.winPot(player, chips)
    }

    private fun playerFinished(l: String) {
        val player = l.substringBefore(" finished ")
        val wins = l.substringAfter(" wins ", "").substringBefore(" EUR")
        if (!wins.isEmpty()) hand.playerFinished(player, wins.toDouble())
    }


    private enum class ParserState {
        INIT {
            override fun parseLine(l: String, parser: BetclicParser): ParserState {
                return if (l.startsWith("Game Mode:") && !l.endsWith("Spin")) {
                    SKIPPED
                } else if (!l.startsWith("Game ID:")) {
                    INIT
                } else {
                    parser.spin = parser.spins.computeIfAbsent(l.substringAfterLast(' '), ::Spin)
                    if (parser.spin.multiplier == 0) FILL_SPIN else INIT_HAND
                }
            }
        },
        SKIPPED {
            override fun parseLine(l: String, parser: BetclicParser): ParserState =
                if (l == "------------") INIT else SKIPPED
        },
        FILL_SPIN {
            override fun parseLine(l: String, parser: BetclicParser): ParserState {
                return if (l.startsWith("Buy In:")) {
                    parser.spin.buyIn = l.substringAfter(": ").substringBefore('€').toDouble()
                    INIT_HAND
                } else {
                    if (l.startsWith("Multiplier")) parser.spin.multiplier = l.substringAfter('x').toInt()
                    FILL_SPIN
                }
            }
        },
        INIT_HAND {
            override fun parseLine(l: String, parser: BetclicParser): ParserState {
                if (l == "*** PLAYERS ***") return FILL_SEATS
                if (l.startsWith("Hand ID: ")) {
                    parser.hand = Hand(l.substringAfterLast(' '), parser.spin)
                    if(!parser.spin.add(parser.hand)){
                        return SKIPPED //duplicated hand
                    }
                } else if(l.startsWith("Blinds")) {
                    parser.hand.blind = l.substringAfter('/').toInt()
                } else if (l.startsWith("Date & Time:")) {
                    val dateTime = l.substringAfter(": ").substringBefore(" (UTC)")
                    parser.hand.timestamp = LocalDateTime.parse(dateTime, parser.timeFormatter).toEpochSecond(ZoneOffset.UTC)
                }
                return INIT_HAND
            }
        },
        FILL_SEATS {
            override fun parseLine(l: String, parser: BetclicParser): ParserState {
                return if (l == "*** HOLE CARDS ***") {
                    parser.betTracker = BetTracker(parser.hand.players.size)
                    FILL_CARDS
                } else {
                    parser.parseSeat(l)
                    FILL_SEATS
                }
            }
        },
        FILL_CARDS {
            override fun parseLine(l: String, parser: BetclicParser): ParserState =
                if (l != "*** PRE-FLOP ***") {
                    parser.parseHoleCards(l)
                    FILL_CARDS
                } else FILL_ACTIONS
        },
        FILL_ACTIONS {
            override fun parseLine(l: String, parser: BetclicParser): ParserState {
                if (l.startsWith("***")) {
                    parser.bettingRoundFinished(l)
                } else {
                    parser.parseAction(l)
                }
                return when (l) {
                    "*** SHOWDOWN ***" -> SHOWDOWN
                    "*** SUMMARY ***" -> SUMMARY
                    else -> FILL_ACTIONS
                }
            }
        },
        SHOWDOWN {
            override fun parseLine(l: String, parser: BetclicParser): ParserState =
                if (l == "*** SUMMARY ***") SUMMARY else SHOWDOWN

        },
        SUMMARY {
            override fun parseLine(l: String, parser: BetclicParser): ParserState {
                if (l.isEmpty()) {
                    parser.handFinished()
                    return INIT
                } else if (l.substringAfter(" wins ","").contains("pot")) {
                    parser.winPot(l)
                } else {
                    parser.playerFinished(l)
                }
                return SUMMARY
            }
        };

        abstract fun parseLine(l: String, parser: BetclicParser): ParserState
    }

}