package com.snaky.poker

import io.github.kennethshackleton.skpokereval.evaluator.FiveCardsEvaluator
import java.io.BufferedReader
import java.io.InputStream

class BetclicParser {
    val spins = mutableMapOf<String, Spin>()

    private var state = ParserState.INIT
    private lateinit var spin: Spin
    private lateinit var hand: Hand
    private var activeBettingRound = false

    fun parseFile(s: InputStream) {
        s.bufferedReader().lineSequence().forEach { state = state.parseLine(it, this) }
        hand.handFinished()
        state = ParserState.INIT
    }

    fun BufferedReader.lineSequence(): Sequence<String> = sequence {
        var line = readLine()
        while (line != null) {
            yield(line) // 'line' est non-null ici
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
        val cards = toLongHand(l.substringAfterLast('['))
        hand.holecards(player, cards)
    }

    private fun parseAction(l: String) {
        activeBettingRound = true
        val name = l.substringAfter("- ").substringBefore(':')
        val action = l.substringAfter(": ").substringBefore(" and is all-in")
        if(action.startsWith("Posts")) {
            hand.post(name, action.substringAfterLast(' ').toInt())
        } else if (action.startsWith("Raises") || action.startsWith("Bets")) {
            hand.bet(name, action.substringAfterLast(' ').toInt())
        } else if (action.startsWith("Calls")) {
            hand.call(name)
        } else if (action.startsWith("Folds")) {
            hand.fold(name)
        } // else check/disconnect/reconnect -> nothing to do
    }

    private fun bettingRoundFinished(l: String) {
        if(activeBettingRound) hand.bettingRoundFinished(lazy { toLongHand(l.substringAfter('[', "")) })
        activeBettingRound = false
    }

    private fun showdown(l: String) {
        val player = l.substringBefore(" shows [")
        val handValue = FiveCardsEvaluator.getRank(toLongHand(l.substringAfterLast('['))).toInt()
        hand.showdown(player, handValue)
    }

    private fun showdownEnd() {
        hand.showdownEnd()
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
                    parser.spin.add(parser.hand)
                } else if(l.startsWith("Blinds")) {
                    parser.hand.blind = l.substringAfter('/').toInt()
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