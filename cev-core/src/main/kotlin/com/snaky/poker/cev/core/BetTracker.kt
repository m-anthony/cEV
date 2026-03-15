package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.Action
import com.snaky.poker.cev.core.model.ActionType
import com.snaky.poker.cev.core.model.Street

class BetTracker {

    constructor(playerCount: Int) {
        this.activeSeatsMask = (1 shl playerCount) - 1
        this.currentBets = Array(playerCount) { 0 }
        this.totalBets = Array(playerCount) { 0 }
    }

    val pots: List<Pot> get() = if(_pots.isEmpty()) computePots() else _pots
    var lastActiveStreet: Street? = null
        private set

    fun isContested() = activeSeatsMask.countOneBits() != 1

    private val _pots: MutableList<Pot> = mutableListOf()
    private var activeSeatsMask: Int
    private val currentBets: Array<Int>

    private val totalBets: Array<Int>

    fun getBet(seatId: Int) = totalBets[seatId]

    fun nextRound() = currentBets.fill(0)

    fun isActiveSeat(seatId: Int) = ((1 shl seatId) and activeSeatsMask) != 0

    fun registerAction(action: Action, street: Street) {
        if(street != lastActiveStreet) lastActiveStreet = street
        val seat = action.player.seatId
        val amount = when(action.type) {
            ActionType.Fold -> {
                markPlayerFolded(seat)
                0
            }
            ActionType.Bet -> action.amount - currentBets[seat]
            ActionType.Call, ActionType.Blind -> action.amount
            ActionType.Check -> 0
        }
        currentBets[seat] += amount
        totalBets[seat] += amount
    }

    fun uncalledBettor(): Int {
        val lastPotMask = pots.last().eligibleMask
        return if(lastPotMask.countOneBits() == 1) lastPotMask.countTrailingZeroBits() else -1
    }

    private fun markPlayerFolded(seatId: Int){
        activeSeatsMask = activeSeatsMask and (1 shl seatId).inv()
    }

    private fun computePots(): List<Pot>{
        val levels = Array(totalBets.size) { 0 }
        var levelIdx = 0
        var mask = activeSeatsMask
        while(mask != 0){
            val seat = mask.countTrailingZeroBits()
            levels[levelIdx++] = totalBets[seat]
            mask = mask and (mask - 1) //clear lowest bit
        }

        levels.sort()
        var lastLevelBet = 0
        for(levelBet in levels){
            if(levelBet == lastLevelBet) continue

            var potTotal = 0
            var potMask = 0
            for(seat in 0 until totalBets.size){
                val bet = totalBets[seat]
                if(bet > lastLevelBet){
                    //contribute to this pot
                    potTotal += minOf(bet, levelBet) - lastLevelBet
                    if(isActiveSeat(seat)) potMask = potMask or (1 shl seat)
                }
            }
            _pots.add(Pot(potTotal, potMask))
            lastLevelBet = levelBet
        }
        return _pots
    }
}

data class Pot(val amount: Int, val eligibleMask: Int) {
    fun isEligible(seatId: Int) = ((1 shl seatId) and eligibleMask) != 0
    fun eligiblePlayerCount() = eligibleMask.countOneBits()
}