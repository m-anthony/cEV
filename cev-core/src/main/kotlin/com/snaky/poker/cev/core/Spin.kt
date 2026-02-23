package com.snaky.poker.cev.core

class Spin(
    val id: String
) {
    var startingStack = 500
    var buyIn = 0.0
    var multiplier = 0
    var endMillis = 0L
    var startMillis = Long.MAX_VALUE
    var cev = 0.0
    var wins = 0.0
    val hands = mutableSetOf<Hand>()

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
        cev = hands.sumOf { it.cev }
        endMillis = hands.maxOf { it.timestamp }
        startMillis = hands.minOf { it.timestamp }
    }

}
