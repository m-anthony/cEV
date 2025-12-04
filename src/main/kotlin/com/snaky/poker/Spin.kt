package com.snaky.poker

class Spin(
    val id: String
) {
    var buyIn = 0.0
    var multiplier = 0
    var endMillis = 0L
    var startMillis = Long.MAX_VALUE
    var cev = 0.0
    var wins = 0.0
    val hands = mutableListOf<Hand>()

    override fun equals(other: Any?): Boolean {
        if (this == other) return true
        if (other !is Spin) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun add(hand: Hand) {
        hands.add(hand)
    }

    fun aggregateHands() {
        cev = hands.sumOf { it.cev }
    }

}
