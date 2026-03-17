package com.snaky.poker.cev.core.model

import kotlin.math.roundToInt

data class PayoutScheme(
    val name: String,
    val room: Room,
    val tiers: List<MultiplierTier>,
    val availableBuyInCents: List<Int>,
) {
    val weightSum: Int = tiers.sumOf { it.weight }
    init {
        val avgMultiplier = tiers.sumOf { it.multiplier * it.weight } / weightSum.toDouble()
        val rakeStr = "%.2f".format(100 * (1 - (avgMultiplier / 3)))
        println("new repartition for $room($name), rake = $rakeStr%")
    }
}

@ConsistentCopyVisibility
data class MultiplierTier private constructor (
    val multiplier: Int,
    val weight: Int,
    val prizes: List<Double>
) {
    constructor(multiplier: Int, weight: Int) : this(
        multiplier = multiplier,
        weight = weight,
        prizes = listOf(multiplier.toDouble(), 0.0, 0.0)
    )

    constructor(prizes: List<Number>, weight: Int) : this(
        multiplier = prizes.sumOf { it.toDouble() }.roundToInt(),
        weight = weight,
        prizes = prizes.map { it.toDouble() }
    )
}

