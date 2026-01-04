package com.snaky.poker.cev

import io.github.kennethshackleton.skpokereval.evaluator.FiveCardsEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
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
    private var activeBettingRound = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parseFile(s: InputStream) {
        s.bufferedReader().lineSequence().forEach { state = state.parseLine(it, this) }
        //there is no delimiter for the last hand of the file, so EOF should trigger some actions
        if(state != ParserState.SKIPPED) hand.handFinished()
        state = ParserState.INIT
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
        val action = l.substringAfter(": ").substringBefore(" and is all-in")
        var ignored = false
        if(action.startsWith("Posts")) {
            hand.post(name, action.substringAfterLast(' ').toInt())
        } else if (action.startsWith("Raises") || action.startsWith("Bets")) {
            hand.bet(name, action.substringAfterLast(' ').toInt())
        } else if (action.startsWith("Calls")) {
            hand.call(name)
        } else if (action.startsWith("Folds")) {
            hand.fold(name)
        } else if (!action.startsWith("Checks")) {
            ignored = true // disconnect/reconnect
        } // else check/
        activeBettingRound = activeBettingRound or !ignored
    }

    private fun bettingRoundFinished(l: String) {
        if(activeBettingRound) hand.bettingRoundFinished(lazy { CardSet.parse(l.substringAfter('[', "")) })
        activeBettingRound = false
    }

    private fun showdown(l: String) {
        val player = l.substringBefore(" shows [")
        val handValue = FiveCardsEvaluator.getRank(CardSet.parse(l.substringAfterLast('[')).cards).toInt()
        hand.showdown(player, handValue)
    }

    private fun showdownEnd() {
        with(hand){
            scope.showdownEnd()
        }
    }

    private fun handFinished() {
        hand.handFinished()
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
                if (l == "*** SUMMARY ***"){
                    parser.showdownEnd()
                    SUMMARY
                } else {
                    parser.showdown(l)
                    SHOWDOWN
                }
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