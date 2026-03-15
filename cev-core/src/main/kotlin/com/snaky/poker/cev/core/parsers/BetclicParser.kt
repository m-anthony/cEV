package com.snaky.poker.cev.core.parsers

import com.snaky.poker.cev.core.model.Action
import com.snaky.poker.cev.core.model.ActionType
import com.snaky.poker.cev.core.BetTracker
import com.snaky.poker.cev.core.model.MultiplierTier
import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.CardSet
import com.snaky.poker.cev.core.model.Hand
import com.snaky.poker.cev.core.model.Hand.Position
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import java.io.BufferedReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class BetclicParser : AbstractRoomParser() {

    private var state = ParserState.INIT
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    override val room: Room = Room.BETCLIC
    override val payoutProvider: (Spin) -> PayoutScheme = BetclicPayouts

    override fun parseFile(reader: BufferedReader) {
        var line = reader.readLine()
        while (line != null) {
            state = state.parseLine(line, this)
            line = reader.readLine()
        }
        //there is no delimiter for the last hand of the file, so EOF should trigger some actions
        if (state != ParserState.SKIPPED) handFinished()
        state = ParserState.INIT

    }

    override fun validateHeader(header: String): Boolean = header.contains("Site: Betclic.fr")


    private fun parseSeat(l: String) {
        val name = l.substringAfter(": ").substringBefore(" (")
        val stack = l.substringAfter('(').substringBefore(')').toInt()
        val player = hand.addPlayer(name, stack)

        var position = l.substringAfter('[').substringBefore("]", "")
        val hero = position.endsWith("Hero")
        if (hero) {
            position = position.substringBefore(" Hero")
            hand.hero = player
            hand.position = when (position) {
                "BB" -> Position.HUBB
                "BTN SB" -> Position.HUSB
                "BTN" -> Position.BU
                "SB" -> Position.SB
                else -> throw IllegalStateException("Unknown position $position")
            }
        }
        if (hand.players.size == 3 && hand.position == Position.HUBB) hand.position = Position.BB
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
            registerAction(Action(hand.findPlayer(name), type, allIn, amount))
        }
    }

    private fun bettingRoundFinished(l: String) {
        onNextRound(CardSet.parse(l.substringAfter('[', "")))
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
                    parser.registerSpin(l.substringAfterLast(' '))
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
                        parser.duplicateHands++
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

private object BetclicPayouts: (Spin) -> PayoutScheme{

    override fun invoke(spin: Spin): PayoutScheme = when {
        spin.buyIn < 200.0 -> STANDARD
        else -> SPIN200
    }

    val STANDARD = PayoutScheme(
        name = "Standard",
        room = Room.BETCLIC,
        tiers = listOf(
            MultiplierTier(2, 478_185),
            MultiplierTier(3, 346_210),
            MultiplierTier(4, 130_000),
            MultiplierTier(5, 40_000),
            MultiplierTier(10,5_000),
            MultiplierTier(listOf(15, 3, 2), 500),
            MultiplierTier(listOf(75, 15, 10), 100),
            MultiplierTier(listOf(750, 150, 100), 5)
        )
    )

    val SPIN200 = PayoutScheme(
        name = "200 EUR",
        room = Room.BETCLIC,
        tiers = listOf(
            MultiplierTier(2, 479_337),
            MultiplierTier(3, 345_442),
            MultiplierTier(4, 130_000),
            MultiplierTier(5, 40_000),
            MultiplierTier(10, 5_000),
            MultiplierTier(listOf(37.5, 7.5, 5), 200),
            MultiplierTier(listOf(375, 75, 50), 20),
            MultiplierTier(listOf(3750, 750, 500), 1)
        )
    )
}