package com.snaky.poker.cev.core.parsers

import java.io.BufferedReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader


/**
 * Interface with abort support for non-Twister files.
 */
interface IPokerXmlListener {
    // --- 1. Global Tournament Info (Called once at start) ---
    fun onTournamentStarted(tournamentCode: String)
    fun onHeroName(name: String)
    fun onTotalBuyIn(amount: Double)
    fun onRewardDrawn(amount: Double) // Multiplier info

    fun onTotalWin(amount: Double)   // Final financial result
    // --- 2. Hand Context (Called at the start of each <game>) ---
    // At this point, you know the hand ID and the players' initial state
    fun onNewHand(handId: String)
    fun onHandStartDate(datetime: String)
    fun onBigBlind(blind: Int)

    fun onPlayerInfo(name: String, chips: Int, bet: Int, win: Int)
    // --- 3. Street / Action Flow (Called multiple times per hand) ---
    // street 0/1 = Preflop, 2 = Flop, etc.
    fun onHoleCards(playerName: String, cards: String)

    fun onAction(street: Int, playerName: String, type: Int, amount: Int)

    fun onBoardCards(streetType: String, cards: String)
    // --- 4. Hand Completion ---
    fun onHandFinished()
    // --- 5. Security ---
    fun onAborted()
}

class IPokerXmlReader(private val listener: IPokerXmlListener) {

    private val factory = XMLInputFactory.newInstance()

    fun parse(bufferedReader: BufferedReader) {
        val reader = factory.createXMLStreamReader(bufferedReader)

        try {
            while (reader.hasNext()) {
                val event = reader.next()
                if (event == XMLStreamConstants.START_ELEMENT) {
                    when (reader.localName) {
                        "tablename" -> {
                            val name = reader.elementText
                            if (name == null
                                || !name.contains("Twister", ignoreCase = true)
                                || !name.contains('€')
                            ) {
                                // Security check: must contain "Twister and euro"
                                listener.onAborted()
                                return
                            } else {
                                listener.onTournamentStarted(name.substringAfterLast(", "))
                            }
                        }
                        "nickname" -> listener.onHeroName(reader.elementText)
                        "totalbuyin" -> listener.onTotalBuyIn(reader.elementText.toAmount())
                        "win" -> listener.onTotalWin(reader.elementText.toAmount())
                        "rewarddrawn" -> listener.onRewardDrawn(reader.elementText.toAmount())
                        "game" -> {
                            val handId = reader.getAttributeValue(null, "gamecode") ?: ""
                            parseHand(reader, handId)
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    private fun parseHand(reader: XMLStreamReader, handId: String) {
        var currentStreet = 0
        listener.onNewHand(handId)
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT) {
                when (reader.localName) {
                    "startdate" -> listener.onHandStartDate(reader.elementText)
                    "bigblind" -> listener.onBigBlind(reader.elementText.toAmount().toInt())
                    "player" -> {
                        listener.onPlayerInfo(
                            name = reader.getAttributeValue(null, "name") ?: "Unknown",
                            chips = reader.getAttributeValue(null, "chips").toAmount().toInt(),
                            bet = reader.getAttributeValue(null, "bet").toAmount().toInt(),
                            win = reader.getAttributeValue(null, "win").toAmount().toInt()
                        )
                    }
                    "round" -> currentStreet = reader.getAttributeValue(null, "no")?.toInt() ?: 0
                    "cards" -> {
                        val type = reader.getAttributeValue(null, "type") ?: ""
                        val player = reader.getAttributeValue(null, "player")
                        val content = reader.elementText
                        if (type == "Pocket") {
                            listener.onHoleCards(player, content)
                        } else {
                            listener.onBoardCards(type, content)
                        }
                    }
                    "action" -> {
                        listener.onAction(
                            street = currentStreet,
                            playerName = reader.getAttributeValue(null, "player"),
                            type = reader.getAttributeValue(null, "type")?.toInt() ?: -1,
                            amount = reader.getAttributeValue(null, "sum").toAmount().toInt()
                        )
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == "game") {
                listener.onHandFinished()
                return
            }
        }
    }
}

/**
 * Parses strings like "1 200,50€" directly into Double without allocating temporary strings.
 */
fun String?.toAmount(): Double {
    if (isNullOrBlank()) return 0.0

    var integerPart = 0
    var decimalPart = 0.0
    var divisor = 1.0
    var foundDecimalSeparator = false

    for (i in 0 until this.length) {
        when (val c = this[i]) {
            in '0'..'9' -> {
                val digit = (c - '0')
                if (!foundDecimalSeparator) {
                    integerPart = integerPart * 10 + digit
                } else {
                    divisor *= 10
                    decimalPart += digit / divisor
                }
            }

            '.', ',' -> foundDecimalSeparator = true

            // All other characters (spaces, symbols) are ignored
        }
    }

    return integerPart + decimalPart
}