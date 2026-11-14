package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.ActionType
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.model.Street
import com.snaky.poker.cev.core.parsers.MetaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.sqrt
import kotlin.system.exitProcess

/**
 * Result structure containing weighted scores and confidence metrics per player.
 */
private data class PlayerReactFull(
    val pseudo: String,
    val totalHands: Int = 0,
    val totalPre: Int = 0,
    val totalFlop: Int = 0,
    val totalTurn: Int = 0,
    val totalRiver: Int = 0,
    val reactSumPre: Int = 0,
    val reactSumFlop: Int = 0,
    val reactSumTurn: Int = 0,
    val reactSumRiver: Int = 0,
    val sqrReactSumPre: Long = 0L,
    val sqrReactSumFlop: Long = 0L,
    val sqrReactSumTurn: Long = 0L,
    val sqrReactSumRiver: Long = 0L,
)

private data class PlayerReactSummary(
    val pseudo: String,
    val totalHands: Int,
    val avg: Double,
    val stdDev: Double,
    val avgPre: Double,
    val stdDevPre: Double,
    val avgFlop: Double,
    val stdDevFlop: Double,
    val avgTurn: Double,
    val stdDevTurn: Double,
    val avgRiver: Double,
    val stdDevRiver: Double,
)

private fun PlayerReactFull.toSummary(): PlayerReactSummary {
    val totalSpots = (totalPre + totalFlop + totalTurn + totalRiver).toDouble()
    val avg = (reactSumPre + reactSumTurn + reactSumTurn + reactSumRiver) / totalSpots
    val avgPre = totalPre.takeIf { it > 0 }?.let { reactSumPre / it.toDouble() }
    val avgFlop = totalFlop.takeIf { it > 0 }?.let { reactSumFlop / it.toDouble() }
    val avgTurn = totalTurn.takeIf { it > 0 }?.let { reactSumTurn / it.toDouble() }
    val avgRiver = totalRiver.takeIf { it > 0 }?.let { reactSumRiver / it.toDouble() }
    return PlayerReactSummary(
        pseudo = pseudo,
        totalHands = totalHands,
        avg = avg,
        stdDev = sqrt((sqrReactSumPre + sqrReactSumFlop + sqrReactSumTurn + sqrReactSumRiver) / totalSpots - avg * avg),
        avgPre = avgPre ?: 0.0,
        stdDevPre = avgPre?.let { sqrt(sqrReactSumPre / totalPre.toDouble() - it * it) } ?: 0.0,
        avgFlop = avgFlop ?: 0.0,
        stdDevFlop = avgFlop?.let { sqrt(sqrReactSumFlop / totalFlop.toDouble() - it * it) } ?: 0.0,
        avgTurn = avgTurn ?: 0.0,
        stdDevTurn = avgTurn?.let { sqrt(sqrReactSumTurn / totalTurn.toDouble() - it * it) } ?: 0.0,
        avgRiver = avgRiver ?: 0.0,
        stdDevRiver = avgRiver?.let { sqrt(sqrReactSumRiver / totalRiver.toDouble() - it * it) } ?: 0.0,
    )
}

fun main(args: Array<String>) {
    if (args.size != 1 || !args[0].startsWith("--sources=")) {
        System.err.println("usage : --sources=<path1,path2>")
        exitProcess(-1)
    }

    val paths = args[0].substringAfter('=').split(',').map { File(it) }

    val eval = ReactParser()
    val spins = runBlocking(Dispatchers.Default) {
        with(MetaParser()) {
            paths.forEach { FileCrawler.processFileOrDirectory(it, this) }
            waitForResults()
            this.spins
        }
    }

    val stats = eval.computePlayerReact(spins.values)
    val heroStats = stats[HERO]

    System.err.println("Hero stats : $heroStats")

    val vilainStats = (stats - HERO).values.toSortedSet(compareByDescending<PlayerReactSummary> { it.totalHands }.thenComparing { it.pseudo }).toList()
    val vilainTotalHands = vilainStats.sumOf { it.totalHands }
    var regCutIdx = 0
    var regHands = 0
    while(regHands < 0.1 * vilainTotalHands){
        regHands += vilainStats[regCutIdx++].totalHands
    }


    val regAvgTimes = vilainStats.subList(0, regCutIdx).map { it.avg.toFloat() }.sorted()
    val avgRegReact = regAvgTimes.average().toFloat()
    System.err.println("Reg avg time : $avgRegReact among $regCutIdx players ($regHands hands out of $vilainTotalHands, minHands = ${vilainStats[regCutIdx - 1].totalHands})")
    System.err.println("Min = ${regAvgTimes.first()}, P25 = ${regAvgTimes[regAvgTimes.size / 4]}, med = ${regAvgTimes[regAvgTimes.size / 2]}, P75 = ${regAvgTimes[3* regAvgTimes.size / 4]}, max = ${regAvgTimes.last()})")

    System.err.println()
    System.err.println("------ REGS -----")
    System.err.println()
    vilainStats.subList(0, regCutIdx)
        .sortedBy { it.avg }
        .forEach(System.err::println)

    System.err.println()
    System.err.println("------ RECREA -----")
    System.err.println()
    vilainStats.subList(regCutIdx, vilainStats.size).filter { it.totalHands > 50 }
        .sortedBy { it.avg }
        .forEach(System.err::println)

}



private const val HERO = "_hero_"

private class ReactParser {

    fun computePlayerReact(spins: Collection<Spin>): Map<String, PlayerReactSummary> {
        if (spins.isEmpty()) return emptyMap()
        val detailMap = mutableMapOf<String, PlayerReactFull>()
        var heroDetail = PlayerReactFull(HERO)
        spins.forEach { heroDetail = processSpin(it, detailMap, heroDetail) }
        return detailMap.mapValues { (_, v) -> v.toSummary() } + (heroDetail.pseudo to heroDetail.toSummary())
    }

    private fun processSpin(spin: Spin, detailMap: MutableMap<String, PlayerReactFull>, heroDetail: PlayerReactFull) : PlayerReactFull {
        if (spin.room != Room.BETCLIC) return heroDetail
        var newHeroDetail = heroDetail
        for (hand in spin.hands) {
            newHeroDetail = newHeroDetail.copy(totalHands = newHeroDetail.totalHands + 1)
            for (player in hand.players) {
                if (player != hand.hero) detailMap.compute(player.name) { name, detail ->
                    with(detail ?: PlayerReactFull(name)) { copy(totalHands = totalHands + 1) }
                }
            }
            var lastActionTime = hand.timestamp
            for (round in hand.rounds) {
                for ((player, type, _, _, timeStamp) in round.actions) {
                    val delay = timeStamp - lastActionTime
                    lastActionTime = timeStamp
                    if (type != ActionType.Blind) {
                        if(player == hand.hero) {
                            newHeroDetail = processDelay(newHeroDetail, delay.toInt(), round.street)
                        } else {
                            val detail = detailMap[player.name] ?: throw AssertionError()
                            detailMap[detail.pseudo] = processDelay(detail, delay.toInt(), round.street)
                        }
                    }
                }
            }
        }
        return newHeroDetail
    }
    private fun processDelay(detail: PlayerReactFull, delay: Int, street: Street): PlayerReactFull {
        val delaySqr = delay * 1L * delay
        return with(detail) {
            when (street) {
                Street.Preflop -> copy(
                    totalPre = totalPre + 1,
                    reactSumPre = reactSumPre + delay,
                    sqrReactSumPre = sqrReactSumPre + delaySqr
                )

                Street.Flop -> copy(
                    totalFlop = totalFlop + 1,
                    reactSumFlop = reactSumFlop + delay,
                    sqrReactSumFlop = sqrReactSumFlop + delaySqr
                )

                Street.Turn -> copy(
                    totalTurn = totalTurn + 1,
                    reactSumTurn = reactSumTurn + delay,
                    sqrReactSumTurn = sqrReactSumTurn + delaySqr
                )

                Street.River -> copy(
                    totalRiver = totalRiver + 1,
                    reactSumRiver = reactSumRiver + delay,
                    sqrReactSumRiver = sqrReactSumRiver + delaySqr
                )
            }
        }
    }


}