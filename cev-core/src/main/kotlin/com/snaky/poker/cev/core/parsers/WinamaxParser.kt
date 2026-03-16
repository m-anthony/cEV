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

class WinamaxParser : AbstractRoomParser() {
    private var state: ParserState = ParserState.INIT
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    private lateinit var firstLine: String
    private lateinit var smallBlind : Player
    private lateinit var bigBlind : Player

    override val room = Room.WINAMAX
    override val payoutProvider: (Spin) -> PayoutScheme = WinamaxPayouts

    override fun validateHeader(header: String): Boolean {
        return header.startsWith("Winamax Poker - Tournament \"Expresso") ||
                header.startsWith("Winamax Poker - Tournament summary : Expresso")
    }

    override fun parseFile(reader: BufferedReader) {
        var line = reader.readLine()
        while (line != null) {
            state = state.parseLine(line, this)
            line = reader.readLine()
        }
        if(state != ParserState.SKIPPED && state != ParserState.INIT) handFinished()
        state = ParserState.INIT
    }

    private fun parseHoleCards(line: String){
        hand.hero = hand.findPlayer(line.substringAfter("to ").substringBeforeLast(" [")).also {
            it.cards = CardSet.parse(line.substringAfterLast('['))
        }
        hand.position = when(hand.hero) {
            smallBlind -> if(hand.players.size == 2) Hand.Position.HUSB else Hand.Position.SB
            bigBlind -> if(hand.players.size == 2) Hand.Position.HUBB else Hand.Position.BB
            else -> Hand.Position.BU
        }
    }

    private fun parseBoard(line: String){
        //do nothing for preflop (already created at hand start)
        line.lastIndexOf('[').takeIf { it != -1 }?.let {
            val newCard = CardSet.parse(line.substring(1 + it))
            onNextRound(hand.currentRound().board.addCards(newCard))
        }
    }

    private fun parseAction(line: String) {
        //Winamax player name can include spaces and/or words that match action description
        var actionType = ActionType.Bet
        var actionIndex = line.lastIndexOf("raise")
        for (type in ActionType.entries) {
            val index = line.lastIndexOf(type.name, ignoreCase = true)
            if (index > actionIndex) {
                actionIndex = index
                actionType = type
            }
        }
        // the "collected XXX from pot" / "XXX shows" lines should be ignored, but the player name may contain an action verb
        if (actionIndex == -1
            || line.indexOf(" collected ", actionIndex + 1) != -1
            || line.indexOf(" shows ", actionIndex + 1) != -1) {
            return
        }
        val blind = actionType == ActionType.Blind

        val amount = when (actionType) {
            ActionType.Fold, ActionType.Check -> 0
            else -> line.substringBefore(" and is all-in").substringAfterLast(' ').toInt()
        }
        val allIn = line.endsWith("all-in")

        val playerEndIndex = if (blind) line.lastIndexOf(" post", actionIndex) else actionIndex - 1
        val player = hand.findPlayer(line.take(playerEndIndex))
        registerAction(Action(player, actionType, allIn, amount))

        if (blind) {
            if (line.indexOf("small", playerEndIndex) != -1) {
                smallBlind = player
            } else {
                bigBlind = player
            }
        }
    }

    private fun showdownCards(line: String){
        line.substringBefore(" shows", "").takeUnless { it.isEmpty() } //playerName
            ?.let { hand.findPlayer(it) } // player
            ?.cards = CardSet.parse(line.substringAfterLast('[').substringBefore(']'))
    }

    private fun potWinner(line: String){
        hand.winPot(
            playerName = line.substringAfter(": ").substringBefore(" ("),
            chips = line.substringAfterLast("won ").substringBefore(' ').toInt()
        )
    }

    private enum class ParserState {
        INIT {
            override fun parseLine(line: String, parser: WinamaxParser): ParserState {
                parser.firstLine = line
                return if(!line.startsWith("Winamax Poker")) {
                    INIT
                } else if(line.contains("summary")) {
                    parser.registerSpin(line.substringAfter('(').substringBefore(')'))
                    TOURNAMENT_SUMMARY
                } else {
                    TABLE
                }
            }
        },
        TOURNAMENT_SUMMARY {
            override fun parseLine(line: String, parser: WinamaxParser): ParserState {
                if(line.isBlank() or line.startsWith("Winamax Poker")) return INIT.parseLine(line, parser)

                if (line.startsWith("Buy-In :") && parser.spin.buyIn == 0.0) {
                    val rake = line.substringAfter("+ ").substringBefore('€')
                    val net = line.substringAfter(": ").substringBefore('€')
                    parser.spin.buyIn = (100 * rake.toDouble() + 100 * net.toDouble()).roundToInt() / 100.0
                } else if(line.startsWith("Prizepool :")) {
                    val prizepool = line.substringAfter(": ").substringBefore('€')
                    parser.spin.multiplier = (prizepool.toDouble() / parser.spin.buyIn).roundToInt()
                } else if(line.startsWith("You won")) {
                    parser.spin.wins += line.substringAfter("won ").substringBefore('€').toFloat()
                }
                return TOURNAMENT_SUMMARY
            }
        },
        TABLE {
            override fun parseLine(line: String, parser: WinamaxParser): ParserState {
                parser.registerSpin(line.substringAfter('(').substringBefore(')'))
                val first = parser.firstLine
                if (parser.spin.buyIn == 0.0) parser.spin.apply {
                    val net = first.substringAfter("buyIn: ").substringBefore('€')
                    val rake = first.substringAfter("+ ").substringBefore('€')
                    buyIn = (100 * rake.toDouble() + 100 * net.toDouble()).roundToInt() / 100.0
                    startingStack = if(line.contains("nitro", ignoreCase = true)) 300 else 500
                }

                val handId = first.substringAfter('#').substringBefore(' ')
                parser.hand = Hand(handId, parser.spin).apply {
                    if(!parser.spin.add(this)) {
                        parser.duplicateHands++
                        return SKIPPED
                    }
                    blind = first.substringAfter('/').substringBefore(')').toInt()
                    val dateTime = first.substringAfterLast("- ").substringBefore(" UTC")
                    timestamp = LocalDateTime.parse(dateTime, parser.timeFormatter).toEpochSecond(ZoneOffset.UTC)
                }

                return SEATS
            }
        },
        SEATS {
            override fun parseLine(line: String, parser: WinamaxParser): ParserState =
                if (line.startsWith("*** ANTE/BLINDS ***")) {
                    parser.betTracker = BetTracker(parser.hand.players.size)
                    ACTIONS
                } else {
                    parser.hand.addPlayer(
                        name = line.substringAfter(": ").substringBefore(" ("),
                        stack = line.substringAfter('(').substringBefore(')').toInt()
                    )
                    SEATS
                }
        },
        ACTIONS {
            override fun parseLine(line: String, parser: WinamaxParser): ParserState {
                if(line == "*** SUMMARY ***") return SUMMARY
                if(line == "*** SHOW DOWN ***") return SHOWDOWN
                if (line.startsWith("Dealt to ")) {
                    parser.parseHoleCards(line)
                } else if (line.startsWith("***")) {
                    parser.parseBoard(line)
                } else {
                    parser.parseAction(line)
                }
                return ACTIONS
            }

        },
        SHOWDOWN {
            override fun parseLine(line: String, parser: WinamaxParser) = when(line) {
                "*** SUMMARY ***" -> SUMMARY
                else -> {
                    parser.showdownCards(line)
                    SHOWDOWN
                }
            }
        },
        SUMMARY {
            override fun parseLine(line: String, parser: WinamaxParser) = when {
                line.startsWith("Winamax") -> {
                    parser.handFinished()
                    INIT.parseLine(line, parser)
                }
                else -> {
                    if (line.indexOf(" won ", line.lastIndexOf(')')) != -1) parser.potWinner(line)
                    SUMMARY
                }
            }
        },
        SKIPPED{
            override fun parseLine(line: String, parser: WinamaxParser) = when {
                line.startsWith("Winamax") -> INIT.parseLine(line, parser)
                else -> SKIPPED
            }
        };

        abstract fun parseLine(line: String, parser: WinamaxParser): ParserState
    }
}

private object WinamaxPayouts: (Spin) -> PayoutScheme {

    override fun invoke(spin: Spin): PayoutScheme = when(spin.buyIn) {
        0.25, 0.50 -> NANO
        1.0, 10.0 -> SPIN1_10
        2.0 -> SPIN2
        5.0 -> SPIN5
        25.0 -> SPIN25
        50.0 -> SPIN50
        100.0 -> SPIN100
        else -> HIGH
    }

    val NANO = PayoutScheme(
        name = "0.25/0.50 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 631_328),
            MultiplierTier(3, 232_448),
            MultiplierTier(4, 80_000),
            MultiplierTier(5, 40_000),
            MultiplierTier(10, 15_000),
            MultiplierTier(listOf(40, 6, 4), 1000), //50
            MultiplierTier(listOf(80, 12, 8), 200), //100
            MultiplierTier(listOf(800, 120, 80), 20), //1000
            MultiplierTier(listOf(8000, 1200, 800), 4), //10K
        )
    )

    val SPIN1_10 = PayoutScheme(
        name = "1/10 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 5_938_688),
            MultiplierTier(3, 2_674_208),
            MultiplierTier(4, 825_000),
            MultiplierTier(5, 400_000),
            MultiplierTier(10, 150_000),
            MultiplierTier(listOf(40, 6, 4), 10_000), //50
            MultiplierTier(listOf(80, 12, 8), 2_000), //100
            MultiplierTier(listOf(800, 120, 80), 100), //1000
            MultiplierTier(listOf(80_000, 12_000, 8_000), 4), //100K
        )
    )

    val SPIN2 = PayoutScheme(
        name = "2 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 5_966_697),
            MultiplierTier(3, 2_672_202),
            MultiplierTier(4, 800_000),
            MultiplierTier(5, 400_000),
            MultiplierTier(10, 150_000),
            MultiplierTier(listOf(40, 6, 4), 9_000), //50
            MultiplierTier(listOf(80, 12, 8), 2_000), //100
            MultiplierTier(listOf(800, 120, 80), 100), //1000
            MultiplierTier(listOf(400_000, 60_000, 40_000), 1), //500K
        )
    )

    val SPIN5 = PayoutScheme(
        name = "5 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 5_938_694),
            MultiplierTier(3, 2_674_204),
            MultiplierTier(4, 825_000),
            MultiplierTier(5, 400_000),
            MultiplierTier(10, 150_000),
            MultiplierTier(listOf(40, 6, 4), 10_000), //50
            MultiplierTier(listOf(80, 12, 8), 2_000), //100
            MultiplierTier(listOf(800, 120, 80), 100), //1000
            MultiplierTier(listOf(160_000, 24_000, 16_000), 2), //200K
        )
    )

    val SPIN25 = PayoutScheme(
        name = "25 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 5_938_670),
            MultiplierTier(3, 2_674_220),
            MultiplierTier(4, 825_000),
            MultiplierTier(5, 400_000),
            MultiplierTier(10, 150_000),
            MultiplierTier(listOf(40, 6, 4), 10_000), //50
            MultiplierTier(listOf(80, 12, 8), 2_000), //100
            MultiplierTier(listOf(800, 120, 80), 100), //1000
            MultiplierTier(listOf(32_000, 4_800, 3_200), 10), //40K
        )
    )

    val SPIN50 = PayoutScheme(
        name = "50 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 5_938_640),
            MultiplierTier(3, 2_674_240),
            MultiplierTier(4, 825_000),
            MultiplierTier(5, 400_000),
            MultiplierTier(10, 150_000),
            MultiplierTier(listOf(40, 6, 4), 10_000), //50
            MultiplierTier(listOf(80, 12, 8), 2_000), //100
            MultiplierTier(listOf(800, 120, 80), 100), //1000
            MultiplierTier(listOf(16_000, 2400, 1600), 20), //20K
        )
    )

    val SPIN100 = PayoutScheme(
        name = "100 EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 601_328),
            MultiplierTier(3, 262_448),
            MultiplierTier(4, 80_000),
            MultiplierTier(5, 40_000),
            MultiplierTier(10, 15_000),
            MultiplierTier(listOf(40, 6, 4), 1000), //50
            MultiplierTier(listOf(80, 12, 8), 200), //100
            MultiplierTier(listOf(800, 120, 80), 20), //1000
            MultiplierTier(listOf(8000, 1200, 800), 4), //10K
        )
    )

    val HIGH = PayoutScheme(
        name = "250+ EUR",
        room = Room.WINAMAX,
        tiers = listOf(
            MultiplierTier(2, 591_613),
            MultiplierTier(3, 260_258),
            MultiplierTier(4, 80_000),
            MultiplierTier(5, 50_000),
            MultiplierTier(10, 17_000),
            MultiplierTier(listOf(40, 6, 4), 1000), //50
            MultiplierTier(listOf(80, 12, 8), 100), //100
            MultiplierTier(listOf(320, 48, 32), 25), //400
            MultiplierTier(listOf(3200, 480, 320), 4), //4K
        )
    )
}