package com.snaky.poker.cev.core

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
        // the "collected XXX from pot" line should be ignored, but the player name may contain an action verb
        if (actionIndex == -1 || line.indexOf(" collected ", actionIndex + 1) != -1) return
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
                if(parser.spin.buyIn == 0.0){
                    val net = first.substringAfter("buyIn: ").substringBefore('€')
                    val rake = first.substringAfter("+ ").substringBefore('€')
                    parser.spin.buyIn = (100 * rake.toDouble() + 100 * net.toDouble()).roundToInt() / 100.0
                }

                val handId = first.substringAfter('#').substringBefore(' ')
                parser.hand = Hand(handId, parser.spin)
                if(!parser.spin.add(parser.hand)) return SKIPPED

                parser.hand.blind = first.substringAfter('/').substringBefore(')').toInt()
                val dateTime = first.substringAfterLast("- ").substringBefore(" UTC")
                parser.hand.timestamp = LocalDateTime.parse(dateTime, parser.timeFormatter).toEpochSecond(ZoneOffset.UTC)

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