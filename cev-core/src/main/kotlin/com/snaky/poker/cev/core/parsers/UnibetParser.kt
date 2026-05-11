package com.snaky.poker.cev.core.parsers

import com.snaky.poker.cev.core.BetTracker
import com.snaky.poker.cev.core.model.*
import com.snaky.poker.cev.core.model.Hand.Position.*
import java.io.BufferedReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter



class UnibetParser : AbstractRoomParser() {

    private var state = ParserState.INIT
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss yyyy/MM/dd")
    private var validSpinHistory = false
    override val room: Room = Room.UNIBET
    override val getAllPayoutScheme = UnibetPayouts.ALL
    override val payoutProvider: (Spin) -> PayoutScheme = UnibetPayouts


    override fun parseFile(reader: BufferedReader) {
        state = ParserState.INIT
        var line = reader.readLine()
        while (line != null) {
            state = state.parseLine(line, this)
            line = reader.readLine()
        }
        //there is no delimiter for the last hand of the file, so EOF should trigger some actions
        if (state != ParserState.SKIPPED && state != ParserState.TOURNAMENT_SUMMARIES) handFinished()

    }

    override fun validateHeader(header: String, fileName: String): Boolean {
        return if(fileName.contains("Spin")) {
            header.startsWith("Unibet Hand #")
        } else {
            header.contains("=== HAND HISTORIES ===") || header.contains("=== TOURNAMENT SUMMARIES ===")
        }
    }

    private fun parseSeat(l: String){
        val playerName = l.substringAfter(": ").substringBefore('[').substringBefore(" (")
        val player = hand.addPlayer(playerName, l.substringAfter('(').substringBefore(')').toStack())
        if(l.contains('[')) hand.hero = player
    }

    private fun parseBlinds(l: String){
        if(l.endsWith("button")) {
            hand.position = BU //if player is not BU, will be overriden later
            return
        }

        //posts
        val name = l.substringBefore(" posts").substringBefore('[')
        val blind = l.substringAfter("blind ").substringBefore(",").toStack()
        registerAction(Action(hand.findPlayer(name), ActionType.Blind, l.endsWith("all-in"), blind))

        if(!l.contains('[')) return
        val sb = l.substringBefore(" blind").endsWith("small")
        val hu = hand.players.size == 2
        hand.position = if (sb) {
            if (hu) HUSB else SB
        } else {
            if (hu) HUBB else BB
        }
    }

    private fun parseCards(l: String){
        val cardsString = l.substringAfterLast('[', "").also { if(it.isEmpty()) return }
        val player = hand.findPlayer(l.substringAfter("Dealt to ").substringBefore(' ').substringBefore('['))
        if(player == hand.hero && spin.hands.size == 1) spin.startingStack = player.stack / spin.detailedStackMultiplier
        player.cards = CardSet.parse(cardsString)
    }

    private fun parseBoard(l: String){
        val cardsString = l.substringAfterLast('[', "").also { if(it.isEmpty()) return }
        onNextRound(hand.rounds.last().board.addCards(CardSet.parse(cardsString)))
    }

    private fun parseAction(line: String) {
        if(line.startsWith("Uncalled bet")) return
        //UB player name can include spaces and/or words that match action description
        // so we find action verb from right to left
        var actionType = ActionType.Bet
        var actionIndex = line.lastIndexOf("raise")
        for (type in ActionType.entries) {
            val index = line.lastIndexOf(type.name, ignoreCase = true)
            if (index > actionIndex) {
                actionIndex = index
                actionType = type
            }
        }
        // some line should be ignored, but the player name may contain an action verb
        if (actionIndex == -1
            || line.indexOf(" is disconnected", actionIndex + 1) != -1
            || line.indexOf(" is reconnected", actionIndex + 1) != -1
            || line.indexOf(" wins ", actionIndex + 1) != -1
            || line.indexOf(" shows ", actionIndex + 1) != -1) {
            return
        }

        val amount = when (actionType) {
            ActionType.Fold, ActionType.Check -> 0
            else -> line.substringBefore(", and is all-in").substringAfterLast(' ').toStack()
        }
        val allIn = line.endsWith("all-in")
        val player = hand.findPlayer(line.take(actionIndex - 1).substringBefore('['))

        registerAction(Action(player, actionType, allIn, amount))
    }

    private fun parseTournamentSummaryLine(l: String) {
        if(l.startsWith("Unibet Tournament")) {
            validSpinHistory = false
            val spinId = l.substringAfter('#').substringBefore(',')
            spins[spinId]?.let {
                validSpinHistory = true
                spin = it
            }
        } else if(validSpinHistory) when {
            l.startsWith("Buy-In") -> spin.buyInCents = parseBuyInCents(l)
            l.contains("Prize Pool") -> spin.multiplier =
                (l.substringAfter('€').toFloat() * 100 / spin.buyInCents).toInt()
        }
    }

    private enum class ParserState {

        INIT {
            override fun parseLine(l: String, parser: UnibetParser) = when {
                l.contains("Hand #") -> parser.initHand(l)
                l.contains("=== TOURNAMENT SUMMARIES ===") -> TOURNAMENT_SUMMARIES
                else -> INIT
            }
        },

        SKIPPED {
            override fun parseLine(l: String, parser: UnibetParser): ParserState {
                return if (l.startsWith("Unibet Hand #")) parser.initHand(l) else SKIPPED
            }
        },
        INIT_HAND {
            override fun parseLine(l: String, parser: UnibetParser) = when {
                l.contains("Table") && !l.contains("3-max") -> SKIPPED //not a spin
                l == "*** Seated players ***" -> SEATS
                else -> INIT_HAND
            }
        },
        SEATS {
            override fun parseLine(l: String, parser: UnibetParser): ParserState {
                return if(l == "*** Blinds and button ***") {
                    parser.betTracker = BetTracker(parser.hand.players.size)
                    BLINDS
                } else {
                    parser.parseSeat(l)
                    SEATS
                }
            }
        },
        BLINDS {
            override fun parseLine(l: String, parser: UnibetParser): ParserState {
                return if(l == "*** Hole cards ***") {
                    CARDS
                } else {
                    parser.parseBlinds(l)
                    BLINDS
                }
            }
        },
        CARDS{
            override fun parseLine(l: String, parser: UnibetParser): ParserState {
                return if(l == "*** Preflop ***") {
                    ACTIONS
                } else {
                    parser.parseCards(l)
                    CARDS
                }
            }
        },
        ACTIONS {
            override fun parseLine(l: String, parser: UnibetParser): ParserState {
                if(l == "*** Summary ***") return SUMMARY
                if (l.startsWith("***")) {
                    parser.parseBoard(l)
                } else if(l.contains("finished the tournament")){
                    if(l.contains('[')) {
                        l.substringAfterLast('€',"").takeUnless { it.isEmpty() }?.let {
                            parser.spin.winCents = (it.toFloat() * 100).toInt()
                        }
                    }
                } else {
                    parser.parseAction(l)
                }
                return ACTIONS
            }
        },
        SUMMARY {
            override fun parseLine(l: String, parser: UnibetParser): ParserState {
                return if (l.isEmpty()) {
                    parser.handFinished()
                    SKIPPED
                } else {
                    val name = l.substringAfter(": ", "").substringBefore(":").substringBefore('[')
                    name.takeUnless { it.isEmpty() }?.let {
                        parser.hand.winPot(
                            playerName = it,
                            chips = l.substringAfterLast(" won ").substringBefore(",").toStack()
                        )
                    }
                    SUMMARY
                }
            }
        },
        TOURNAMENT_SUMMARIES {
            override fun parseLine(
                l: String,
                parser: UnibetParser
            ): ParserState {
                parser.parseTournamentSummaryLine(l)
                return TOURNAMENT_SUMMARIES
            }


        };

        abstract fun parseLine(l: String, parser: UnibetParser): ParserState




        fun UnibetParser.initHand(l: String): ParserState { //TODO private
            //Unibet Hand #1458229914, Tournament #78861092, €4.65 + €0.35 - 10.00/20.00 - No Limit Hold'Em - Total prize €15 - UTC 08:43:54 2026/03/24
            registerSpin(l.substringAfterLast('#').substringBefore(','))
            if(spin.buyInCents == 0){
                spin.detailedStackMultiplier = 100 // UB can split chips in two ....
                spin.buyInCents = parseBuyInCents(l.substringBefore('-'))
                if(spin.buyInCents > 0) {
                    //bugged in zipped HH, need to fill with tournament summaries
                    val prizePool = l.substringAfterLast('€').substringBefore(' ').toFloat()
                    spin.multiplier = (prizePool * 100 / spin.buyInCents).toInt()
                }
            }
            hand = Hand(l.substringAfter('#').substringBefore(','), spin)
            if(!spin.add(hand)){
                duplicateHands++
                return SKIPPED
            }
            hand.blind = l.substringAfter('/').substringBefore(' ').toFloat().toInt()
            hand.timestamp =  LocalDateTime.parse(l.substringAfter("UTC "), timeFormatter).toEpochSecond(ZoneOffset.UTC)
            return INIT_HAND
        }
    }

}

// extract main bi + rake, there should be only 2 '€' sign in the string
// return -1 with illegal strings
private fun parseBuyInCents(buyIn: String) : Int {
    if(!buyIn.contains('€')) return -1
    var res = buyIn.substringAfter('€').substringBefore(' ').toFloat() //main
    res += buyIn.substringAfterLast('€').substringBefore(' ').toFloat() //rake
    return (100 * res).toInt()
}

private object UnibetPayouts : (Spin) -> PayoutScheme {

    override fun invoke(spin: Spin): PayoutScheme {
        return if(spin.buyInCents == 250_00) SPIN_250 else STANDARD
    }

    val STANDARD = PayoutScheme(
        name = "1 - 100 EUR",
        room = Room.UNIBET,
        availableBuyInCents = listOf(1_00, 2_00, 5_00, 10_00, 25_00, 50_00, 100_00),
        tiers = listOf(
            MultiplierTier(2, 49_982),
            MultiplierTier(3, 33_512),
            MultiplierTier(4, 12_000),
            MultiplierTier(5, 3_500),
            MultiplierTier(listOf(8, 1, 1), 1_000),
            MultiplierTier(listOf(80, 12, 8), 5), //100
            MultiplierTier(listOf(2000, 300, 200), 1), //2500
        )
    )

    val SPIN_250 = PayoutScheme(
        name = "250 EUR",
        room = Room.UNIBET,
        availableBuyInCents = listOf(250_00),
        tiers = listOf(
            MultiplierTier(2, 50_503),
            MultiplierTier(3, 33_470),
            MultiplierTier(4, 12_021),
            MultiplierTier(5, 3_000),
            MultiplierTier(listOf(8, 1, 1), 1_000),
            MultiplierTier(listOf(80, 12, 8), 5), //100
            MultiplierTier(listOf(3200, 480, 320), 1), //4000
        )
    )

    val ALL = listOf(STANDARD, SPIN_250)
}

private fun String.toStack() = (toFloat() * 100).toInt()