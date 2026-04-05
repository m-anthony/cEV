package com.snaky.poker.cev.core.model

class Spin(
    val id: String,
    val room: Room
) {
    var detailedStackMultiplier = 1
    var startingStack = 500
    var buyInCents = 0
    var multiplier = 0
    var winCents = 0
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
        valid = buyInCents > 0 && !hands.isEmpty()
        if(!valid) return //may happen on corrupted iPoker files or winamax summary without HH
        val sortedHands = hands.sortedWith(compareBy<Hand> { it.timestamp }.thenBy { it.id })
        var heroStack = startingStack * detailedStackMultiplier
        valid = sortedHands[0].players.all { it.stack == heroStack }
        for(hand in sortedHands){
            if(!valid) return
            valid = hand.hero.stack == heroStack
            heroStack += hand.hero.let { it.remaining - it.stack }
            hand.cev /= detailedStackMultiplier
            cev += hand.cev
        }
        startTimestamp = sortedHands[0].timestamp
        endTimestamp = sortedHands.last().timestamp

        //valid if all hands are contiguous + hero wins/lose/made a deal
        valid = heroStack == 0 || heroStack == 3 * startingStack * detailedStackMultiplier || (winCents > 0 && winCents < 0.7 * multiplier * buyInCents)

        profile = SpinProfile(
            buyInCents = buyInCents,
            initialStack = startingStack,
            scheme = schemeProvider(this)
        )
    }
}

data class SpinProfile(
    val buyInCents: Int,
    val initialStack: Int,
    val scheme: PayoutScheme,
)
