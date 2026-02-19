package com.snaky.poker.cev.ui

import com.snaky.poker.cev.core.Hand

// UI Model representing a row in our table
data class SpinStats(
    val label: String,
    val count: Int,
    val netGain: Double,
    val cev: Double,
    val itm: Double,
    val roi: Double,
    val cevStdDev: Double,
    val effectiveRake: Double,
    val positionalCev: Map<Hand.Position, Double>

)
