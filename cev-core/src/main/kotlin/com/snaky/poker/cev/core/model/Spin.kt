package com.snaky.poker.cev.core.model

class Spin(
    val id: String,
    val room: Room
) {
    var startingStack = 500
    var buyIn = 0.0
    var multiplier = 0
    var wins = 0.0
    val hands = mutableSetOf<Hand>()
    lateinit var profile: SpinProfile
        private set

    var endTimestamp = 0L
        private set
    var startTimestamp = 0L
        private set
    var cev = 0.0
        private set

    var valid = true
        private set

    override fun equals(other: Any?): Boolean {
        if (this == other) return true
        if (other !is Spin) return false
        return id == other.id && room == other.room
    }

    override fun hashCode(): Int {
        return id.hashCode() + 31 * room.hashCode()
    }

    fun add(hand: Hand): Boolean {
        return hands.add(hand)
    }

    fun aggregateHands(schemeProvider: (Spin) -> PayoutScheme) {
        valid = buyIn > 0
        if(hands.isEmpty() || !valid) return //may happen on corrupted iPoker files
        val sortedHands = hands.sortedWith(compareBy<Hand> { it.timestamp }.thenBy { it.id })
        var heroStack = startingStack
        valid = sortedHands[0].players.all { it.stack == startingStack }
        for(hand in sortedHands){
            if(!valid) return
            valid = hand.hero.stack == heroStack
            heroStack += hand.chips
            cev += hand.cev
        }
        startTimestamp = sortedHands[0].timestamp
        endTimestamp = sortedHands.last().timestamp

        //valid if all hands are contiguous + hero wins/lose/made a deal
        valid = heroStack == 0 || heroStack == 3 * startingStack || (wins > 0 && wins < 0.7 * multiplier * buyIn)

        profile = SpinProfile(
            buyIn = buyIn,
            initialStack = startingStack,
            scheme = schemeProvider(this)
        )
    }
}

data class SpinProfile(
    val buyIn: Double,
    val initialStack: Int,
    val scheme: PayoutScheme,
)
