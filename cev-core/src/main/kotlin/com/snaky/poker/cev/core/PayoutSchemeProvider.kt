package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.parsers.BetclicParser
import com.snaky.poker.cev.core.parsers.IpokerParser
import com.snaky.poker.cev.core.parsers.UnibetParser
import com.snaky.poker.cev.core.parsers.WinamaxParser

object PayoutSchemeProvider {
    //TODO proper registering of known room
    private val schemesSupported = mutableMapOf<Room, List<PayoutScheme>>()
    fun getBuyInsForRoom(selectedRoom: Room): List<Int> {
        return schemesSupported[selectedRoom]?.flatMap(PayoutScheme::availableBuyInCents)?.sorted() ?: listOf()
    }

    fun getScheme(selectedRoom: Room, selectedBuyInCents: Int): PayoutScheme? {
        return schemesSupported[selectedRoom]?.find { it.availableBuyInCents.contains(selectedBuyInCents) }
    }

    fun registerRoomSchemes(room: Room, schemes: List<PayoutScheme>){
        schemesSupported[room] = schemes
    }

    //TODO: rework this
    init {
        registerRoomSchemes(Room.BETCLIC, BetclicParser().getAllPayoutScheme)
        registerRoomSchemes(Room.WINAMAX, WinamaxParser().getAllPayoutScheme)
        registerRoomSchemes(Room.IPOKER, IpokerParser().getAllPayoutScheme)
        registerRoomSchemes(Room.UNIBET, UnibetParser().getAllPayoutScheme)
    }
}


