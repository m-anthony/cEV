package com.snaky.poker.cev.ui

import com.snaky.poker.cev.core.model.PayoutScheme
import kotlinx.coroutines.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.LongAdder
import kotlin.math.roundToInt

// Ce que l'UI envoie au moteur
data class VarianceParams(
    val gamesCount: Int,
    val simulationCount: Int,
    val cEV: Int,
    val initialStack: Int,
    val buyInCents: Int,
    val rakeBackRate: Double, // ex: 10.0 pour 10%
    val rakeBackThresholdCents: Int, // ex: 10000 pour un versement tous les 100€
    val payoutScheme: PayoutScheme
)

data class PercentilePoint(
    val percentile: Int,
    val netProfitCents: Int,
    val roi: Double,
    val maxSwingCents: Int,
    val longestBreakeven: Int,
    val lowPointCents: Int,
    val effectiveRake: Double   // Pourcentage (ex: 0.07 pour 7%)
)

data class VarianceReport(
    val theoreticalEv: Int,
    val theoreticalRake: Double,
    val points: List<PercentilePoint>,
)

private const val PLACE_CHANCE_TOTAL = 10_000
object VarianceSimulationEngine {

    suspend fun run(params: VarianceParams, progressAdder: LongAdder): VarianceReport = withContext(Dispatchers.Default) {
        this@VarianceSimulationEngine.logger.info { "Simulation Params: $params" }
        val scheme = params.payoutScheme
        val avgMultiplier = scheme.tiers.sumOf { it.multiplier * it.weight } / scheme.weightSum.toDouble()
        val avgRake = 1 - avgMultiplier / 3.0

        if (params.simulationCount <= 0) return@withContext emptyReport(avgRake)

        val placeChances = IntArray(3)
        val p1 = (params.initialStack +  params.cEV) / (3.0 * params.initialStack)
        placeChances[0] = (p1 * PLACE_CHANCE_TOTAL).roundToInt()
        val survivalRatio = p1 / (p1 + (1.0 / 3.0))
        placeChances[1] = ((1.0 - p1) * survivalRatio * PLACE_CHANCE_TOTAL).roundToInt()
        placeChances[2] = PLACE_CHANCE_TOTAL - placeChances[0] - placeChances[1]
        val rakebackPerGame = params.buyInCents * avgRake * params.rakeBackRate
        val weightSum = scheme.weightSum
        val cumulativeWeights = IntArray(params.payoutScheme.tiers.size)
        val prizePoolPerTier = LongArray(params.payoutScheme.tiers.size)
        val netProfitsCents = Array(params.payoutScheme.tiers.size) { IntArray(3) }
        var currentSum = 0
        var avgRoi = 0.0
        params.payoutScheme.tiers.forEachIndexed { index, tier ->
            currentSum += tier.weight
            cumulativeWeights[index] = currentSum
            prizePoolPerTier[index] = (params.buyInCents * tier.multiplier).toLong()
            tier.prizes.forEachIndexed { place, prize ->
                val net = (params.buyInCents * prize).toInt() - params.buyInCents
                netProfitsCents[index][place] = net
                avgRoi += prize * tier.weight * placeChances[place] / PLACE_CHANCE_TOTAL
            }
        }
        avgRoi = (avgRoi / weightSum) - 1 + avgRake * params.rakeBackRate

        val nCores = Runtime.getRuntime().availableProcessors()
        val numChunks = (nCores * 8).coerceAtLeast(1)
        val simsPerChunk = params.simulationCount / numChunks
        val remainder = params.simulationCount % numChunks

        val deferredResults = (0 until numChunks).map { chunkIdx ->
            async {
                val count = simsPerChunk + (if (chunkIdx == numChunks - 1) remainder else 0)
                val subList = ArrayList<SimulationResult>(count)

                for (i in 0 until count) {
                    // Check d'annulation (CoroutineScope.ensureActive)
                    ensureActive()

                    // Exécution unitaire
                    val result = simulateOneTrajectory(
                        params = params,
                        cumulativeWeights = cumulativeWeights,
                        weightSum = weightSum,
                        placeChances = placeChances,
                        netProfitsCents = netProfitsCents,
                        rakebackPerGame = rakebackPerGame,
                        prizePoolPerTier = prizePoolPerTier
                    )

                    subList.add(result)

                    // Reporting de progression
                    progressAdder.increment()

                    // Point de respiration toutes les 128 itérations
                    if (i and 127 == 0) yield()
                }
                subList
            }
        }
        val allResults = deferredResults.awaitAll().flatten()
        return@withContext buildReport(allResults, params, avgRake, avgRoi)
    }

    private fun simulateOneTrajectory(
        params: VarianceParams,
        cumulativeWeights: IntArray,
        weightSum: Int,
        placeChances: IntArray,
        netProfitsCents: Array<IntArray>,
        prizePoolPerTier: LongArray,
        rakebackPerGame: Double
    ): SimulationResult {
        var currentProfit = 0
        var totalPrizepoolCents = 0L
        var maxReached = 0
        var maxSwing = 0
        var lowPoint = 0
        var lastMaxIndex = 0
        var longestBreakeven = 0
        val p1 = placeChances[0]
        val p2 = p1 + placeChances[1]


        // Suivi précis du Rakeback
        var totalRakebackPaidCents = 0
        val rbStep = params.rakeBackThresholdCents

        val random = ThreadLocalRandom.current()
        val buyIn = params.buyInCents

        for (i in 0 until params.gamesCount) {
            // --- TIRAGE ---
            val roll = random.nextInt(weightSum)
            var tierIdx = 0
            while (roll >= cumulativeWeights[tierIdx]) {
                tierIdx++
            }
            val rollPlace = random.nextInt(PLACE_CHANCE_TOTAL)
            val placeIdx = when {
                rollPlace < p1 -> 0
                rollPlace < p2 -> 1
                else -> 2
            }

            // --- GAIN NET ---
            currentProfit += netProfitsCents[tierIdx][placeIdx]
            totalPrizepoolCents += prizePoolPerTier[tierIdx]

            // --- RAKEBACK (Version Multiples de Palier) ---
            if (rbStep > 0) {
                val totalEarnedRb = rakebackPerGame * (i + 1)
                val currentDebt = totalEarnedRb - totalRakebackPaidCents
                if (currentDebt >= rbStep) {
                    // On calcule combien de paliers entiers ont été franchis
                    val numberOfSteps = (currentDebt / rbStep).toInt()
                    val toPay = numberOfSteps * rbStep
                    currentProfit += toPay
                    totalRakebackPaidCents += toPay
                }
            }

            // --- MÉTRIQUES ---
            if (currentProfit < lowPoint) lowPoint = currentProfit
            if (currentProfit > maxReached) {
                val duration = i - lastMaxIndex
                if (duration > longestBreakeven) longestBreakeven = duration
                maxReached = currentProfit
                lastMaxIndex = i
            } else {
                val currentSwing = maxReached - currentProfit
                if (currentSwing > maxSwing) maxSwing = currentSwing
            }
        }
        // Calcul du rake effectif : 1 - (Prizepool / (3 * Volume)) pour du 3-Max
        val totalVolume = 3L * buyIn * params.gamesCount
        val effectiveRake = 1.0 - (totalPrizepoolCents.toDouble() / totalVolume)

        return SimulationResult(
            netProfitCents = currentProfit,
            maxSwingCents = maxSwing,
            longestBreakeven = longestBreakeven,
            lowPointCents = lowPoint,
            effectiveRake = effectiveRake
        )
    }

    private fun buildReport(
        results: List<SimulationResult>,
        params: VarianceParams,
        rake: Double,
        avgRoi: Double
    ): VarianceReport {
        logger.info { "building report on ${results.size} simulations" }
        val count = results.size
        val pLevels = listOf(1, 10, 25, 50, 75, 90, 99)

        // Tris pour extraire les percentiles
        val sortedProfits = results.map { it.netProfitCents }.sorted()
        val sortedSwings = results.map { it.maxSwingCents }.sortedDescending()
        val sortedBreakevens = results.map { it.longestBreakeven }.sortedDescending()
        val sortedLowPoints = results.map { it.lowPointCents }.sorted()
        // On trie le rake du plus mauvais (haut) au meilleur (bas) pour le p5%
        val sortedRake = results.map { it.effectiveRake }.sortedDescending()

        val points = pLevels.map { p ->
            val idx = ((count * p) / 100).coerceIn(0, count - 1)
            PercentilePoint(
                percentile = p,
                netProfitCents = sortedProfits[idx],
                roi = sortedProfits[idx].toDouble() / params.buyInCents / params.gamesCount,
                maxSwingCents = sortedSwings[idx],
                longestBreakeven = sortedBreakevens[idx],
                lowPointCents = sortedLowPoints[idx],
                effectiveRake = sortedRake[idx]
            )
        }

        return VarianceReport(
            theoreticalEv = (avgRoi * params.gamesCount * params.buyInCents).roundToInt(),
            theoreticalRake = rake,
            points = points,
        )
    }
    private fun emptyReport(rake: Double) = VarianceReport(
        theoreticalEv = 0,
        theoreticalRake = rake,
        points = emptyList()
    )
}

/**
 * Représente le résultat brut d'une seule trajectoire de simulation.
 * Toutes les valeurs monétaires sont exprimées en centimes (Cents).
 */
private data class SimulationResult(
    val netProfitCents: Int,      // Profit total (incluant gains aux tables + rakeback)
    val maxSwingCents: Int,    // Le pire "swing" (sommet - creux) durant la session
    val longestBreakeven: Int,    // Le plus grand nombre de tournois sans battre son record de profit
    val lowPointCents: Int,       // Le point le plus bas atteint par la bankroll (souvent négatif)
    val effectiveRake: Double     // Le taux de rake réel constaté (ex: 0.0712 pour 7.12%)
)