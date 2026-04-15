package com.snaky.poker.cev.core.watchers

import com.snaky.poker.cev.core.model.Room

enum class RoomLiveSupport {
    READY, PLANNED, IMPOSSIBLE
}

fun Room.getLiveSupport(): RoomLiveSupport = when(this) {
    Room.UNIBET, Room.WINAMAX -> RoomLiveSupport.READY
    Room.IPOKER -> RoomLiveSupport.PLANNED
    Room.BETCLIC -> RoomLiveSupport.IMPOSSIBLE
}