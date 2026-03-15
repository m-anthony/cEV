package com.snaky.poker.cev.core.parsers

import com.snaky.poker.cev.core.BetTracker
import com.snaky.poker.cev.core.equitiesMultiWay
import com.snaky.poker.cev.core.equityHeadsUp
import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStream
import kotlin.concurrent.Volatile

abstract class AbstractRoomParser: AutoCloseable {

    abstract val payoutProvider: (Spin) -> PayoutScheme
    abstract val room: Room
    val spins: Map<String, Spin> get() = _spins

    @Volatile
    var currentSpinCount = 0 //can be observed by UI
        private set
    var invalidSpins = 0
        private set
    var duplicateHands = 0
        protected set

    protected val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    protected lateinit var betTracker: BetTracker
    protected lateinit var spin: Spin
    protected lateinit var hand: Hand

    private var asyncTasks = mutableListOf<() -> Unit>()
    private val _spins = hashMapOf<String, Spin>()


    fun parseFile(s: InputStream) {
        try {
            parseFile(s.bufferedReader())
            currentSpinCount = spins.size
        } catch (e: Exception) {
            if(this::hand.isInitialized) println("Exception in hand ${hand.id}")
            println(e)
        }
        if (asyncTasks.isNotEmpty()) {
            val tasks = asyncTasks
            scope.launch {
                tasks.forEach {
                    ensureActive()
                    it.invoke()
                }
            }
            asyncTasks = mutableListOf()
        }
    }

    override fun close() {
        scope.cancel()
    }

    suspend fun waitForResults() = withContext(Dispatchers.Default) {
        scope.coroutineContext.job.children.toList().joinAll()
        val iterator = _spins.values.iterator()
        while (iterator.hasNext()){
            iterator.next().run {
                aggregateHands(payoutProvider)
                if (!valid) {
                    iterator.remove()
                    invalidSpins++
                }
            }
        }
    }


    protected abstract fun parseFile(reader: BufferedReader)

    protected fun registerAsyncTask(t: () -> Unit) = asyncTasks.add(t)
    protected fun registerSpin(id: String) {
        spin = _spins.computeIfAbsent(id) {
            Spin(it, room)
        }
    }
    protected fun registerAction(action: Action, updateRemaining: Boolean = true){
        val player = action.player
        with(hand.currentRound()) {
            addAction(action)
            val previousBet = betTracker.getBet(player.seatId)
            betTracker.registerAction(action, street)
            if(updateRemaining && action.amount != 0) {
                player.remaining -= betTracker.getBet(player.seatId) - previousBet
            }
        }
    }

    protected fun onNextRound(board: CardSet){
        hand.nextRound(board)
        betTracker.nextRound()
    }

    protected fun handFinished() {
        if(!this::hand.isInitialized) return
        val diff = hand.players.sumOf { it.stack - it.remaining }
        if(diff != 0) hand.players[betTracker.uncalledBettor()].remaining += diff //return uncalled bet

        val heroSeat = hand.hero.seatId
        val firstAllInStreet = hand.rounds.find { it.actions.any(Action::allIn) }?.street
        val computeEquity = with(betTracker) {
            isActiveSeat(heroSeat) && lastActiveStreet != Street.River && lastActiveStreet == firstAllInStreet && isContested()
        }
        if(!computeEquity){
            hand.cev = hand.hero.remaining - hand.hero.stack.toDouble()
        } else {
            hand.cev = -betTracker.getBet(heroSeat).toDouble()
            var potCount = betTracker.pots.size
            while (!betTracker.pots[potCount - 1].isEligible(heroSeat)) potCount--
            while (betTracker.pots[potCount - 1].eligiblePlayerCount() == 1) {
                hand.cev += betTracker.pots[--potCount].amount.toDouble()
            }
            if(potCount == 1 && betTracker.pots[0].eligiblePlayerCount() == 2) {
                hand.cev += betTracker.pots[0].amount * equityHeadsUp(hand, betTracker).also { hand.equity = it }
            } else if(firstAllInStreet == Street.Preflop) {
                val hand = this.hand
                val betTracker = this.betTracker
                val initCev = hand.cev
                registerAsyncTask {
                    val equities = equitiesMultiWay(hand, betTracker, potCount)
                    var potSharesCev = 0.0
                    hand.equity = equities[0]
                    for(i in 0 until potCount){
                        potSharesCev += equities[i] * betTracker.pots[i].amount
                    }
                    hand.cev = initCev + potSharesCev
                }
            } else {
                val equities = equitiesMultiWay(hand, betTracker, potCount)
                hand.equity = equities[0]
                for(i in 0 until potCount){
                    hand.cev += equities[i] * betTracker.pots[i].amount
                }
            }
        }
    }

    abstract fun validateHeader(header: String): Boolean

}