package com.snaky.poker.cev.core.model

import org.agrona.collections.ObjectHashSet
import org.apache.logging.log4j.kotlin.logger

class Spin(
    val id: String,
    val room: Room
) {
    var detailedStackMultiplier = 1
    var startingStack = 500
    var buyInCents = 0
    var multiplier = 0
    var winCents = 0
    private var _hands: MutableCollection<Hand> = ObjectHashSet()
    val hands: Collection<Hand> get() = _hands
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
        return !hands.contains(hand) && _hands.add(hand)
    }

    fun remove(hand: Hand): Boolean {
        return _hands.remove(hand)
    }

    fun aggregateHands(schemeProvider: (Spin) -> PayoutScheme) {

        if(buyInCents == 0) {
            //may happen on corrupted iPoker files or winamax summary without HH
            logger.warn { "spin $id invalid : unknown buy in" }
            valid = false
            return
        } else if(hands.isEmpty()){
            logger.warn { "spin $id invalid : no hands" }
            valid = false
            return
        }
        val sortedHands = hands.sortedWith(compareBy<Hand> { it.timestamp }.thenBy { it.id })
        var heroStack = startingStack * detailedStackMultiplier
        if(!sortedHands[0].players.all { it.stack == heroStack }) {
            logger.warn { "spin $id invalid : first hand is missing (uneven stacks)" }
            valid = false
            return
        }
        for(hand in sortedHands){
            if(hand.hero.stack != heroStack) {
                logger.warn { "spin $id invalid : hero stack on hand ${hand.id} is ${hand.hero.stack} instead of $heroStack from previous hand" }
                valid = false
                return
            }
            heroStack += hand.hero.let { it.remaining - it.stack }
            hand.cev /= detailedStackMultiplier
            cev += hand.cev
        }
        startTimestamp = sortedHands[0].timestamp
        endTimestamp = sortedHands.last().timestamp

        //valid if all hands are contiguous + hero wins/lose/made a deal
        valid = heroStack == 0 || heroStack == 3 * startingStack * detailedStackMultiplier || (winCents > 0 && winCents < 0.7 * multiplier * buyInCents)
        if(!valid) {
            logger.warn { "spin $id invalid : hero final stack = $heroStack, startingStack = $startingStack" }
        }

        profile = SpinProfile(
            buyInCents = buyInCents,
            initialStack = startingStack,
            scheme = schemeProvider(this)
        )
    }

    fun toLightModel(): Spin {
        val lightSpin = Spin(id, room)
        lightSpin.startingStack = startingStack
        lightSpin.valid = valid
        lightSpin.cev = cev
        lightSpin.buyInCents = buyInCents
        lightSpin.multiplier = multiplier
        lightSpin.winCents = winCents
        if(this::profile.isInitialized) lightSpin.profile = profile
        lightSpin._hands = hands.mapTo(ArrayList(hands.size)) { it.toLightModel(lightSpin) }

        return lightSpin
    }
}

data class SpinProfile(
    val buyInCents: Int,
    val initialStack: Int,
    val scheme: PayoutScheme,
)
