package com.snaky.poker.cev.core

class Spin(
    val id: String
) {
    var startingStack = 500
    var buyIn = 0.0
    var multiplier = 0
    var wins = 0.0
    val hands = mutableSetOf<Hand>()

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
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun add(hand: Hand): Boolean {
        return hands.add(hand)
    }

    fun aggregateHands() {
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
    }

}
