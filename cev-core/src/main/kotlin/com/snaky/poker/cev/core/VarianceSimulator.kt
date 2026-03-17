package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.MultiplierTier
import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.SpinProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Simulator dedicated to analyzing the impact of variance on poker results.
 * It simulates thousands of possible paths to determine the distribution
 * of potential outcomes (median, swings, and confidence intervals).
 */
object VarianceSimulator {

    suspend fun run(
        distribution: Map<SpinProfile, PlayerStat>,
        buyInCents: Int,
        maxIterations: Int = 100_000,
        onResult: (SimulationResult) -> Unit
    ): Unit = withContext(Dispatchers.Default) {

        // --- STEP 1: PRE-CALCULATION ---
        // Pre-calculate P1/P2 probabilities once per profile to optimize the core loop
        val stacks = distribution.entries
            .groupingBy { it.key.initialStack }
            .fold(0) { count, (_, stats) -> count + stats.gameCount } //Map<Stack, Count>
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .toIntArray() //Stack ordered by highest gameCount
        val preparedProfiles = distribution.map { (profile, stats) ->
            val p1 = (profile.initialStack + stats.avgCEV) / (profile.initialStack * 3.0)
            PreparedProfile(
                scheme = profile.scheme,
                p1 = p1,
                p2 = (1.0 - p1) * (p1 / (p1 + (1.0 / 3.0))),
                count = stats.gameCount,
                buyInCents = profile.buyInCents,
                stackSizeIndex = stacks.indexOf(profile.initialStack)
            )
        }

        // Calculate global theoretical EV (weighted sum of each profile's theoretical EV)
        val totalTheoreticalAvg = preparedProfiles.sumOf { it.calculateTheoreticalAvgCents() }
        val samples = mutableListOf<SimulationSample>()
        var currentIteration = 0

        try {
            while (currentIteration < maxIterations) {
                repeat(1000) {
                    if (currentIteration < maxIterations) {
                        val sample = IntArray(stacks.size) { 0 }
                        preparedProfiles.forEach { it.simulate(sample) }
                        samples.add(SimulationSample(sample.toList()))
                        currentIteration++
                    }
                }
                // Intermediate publishing
                val currentSnapshot = samples.toMutableList().apply { sort() }
                val intermediateResult = buildResult(buyInCents, currentSnapshot.toList(), totalTheoreticalAvg, stacks)
                onResult(intermediateResult)
                yield()
            }
        } catch (_: CancellationException) {
            println("!!! Simulation cancelled at $currentIteration")
        }

        // The return value of withContext is determined here
        withContext(NonCancellable) {
            if (samples.isNotEmpty()) {
                val finalResult = buildResult(
                    buyInCents = buyInCents,
                    samples = samples.apply { sort() },
                    theoreticalAvg = totalTheoreticalAvg,
                    stacks = stacks,
                )
                onResult(finalResult)
            }
        }
    }

    /**
     * Optimized internal representation of a profile for the simulation loop.
     */
    private data class PreparedProfile(
        val scheme: PayoutScheme,
        val p1: Double,
        val p2: Double,
        val count: Int,
        val buyInCents: Int,
        val stackSizeIndex: Int
    ) {
        fun simulate(sample: IntArray){
            var centsWon = 0L
            val random = ThreadLocalRandom.current()

            repeat(count) {
                // Draw multiplier
                val rollM = random.nextInt(scheme.weightSum)
                val tier = findTier(scheme.tiers, rollM)

                // Draw finishing position
                val rollP = random.nextDouble()
                val multiplierValue = when {
                    rollP < p1 -> tier.prizes[0]
                    rollP < (p1 + p2) -> tier.prizes[1]
                    else -> tier.prizes[2]
                }
                centsWon += (multiplierValue * buyInCents).roundToInt()
            }

            // Net profit = Total Prizes - Cost of all entries (1 BI per game)
            sample[stackSizeIndex] += (centsWon - count * buyInCents).toInt()
        }

        fun calculateTheoreticalAvgCents(): Double {

            var avgPrizePerGame = scheme.tiers.sumOf {
                tier -> tier.weight * (tier.prizes[0] * p1 + tier.prizes[1] * p2 + tier.prizes[2] * (1 - p1 - p2))
            }
            avgPrizePerGame /= scheme.weightSum
            return (avgPrizePerGame - 1) * count * buyInCents
        }
    }

    /**
     * Finds the corresponding tier based on a random roll.
     * Optimized with an indexed loop to avoid Iterator allocation.
     */
    private fun findTier(tiers: List<MultiplierTier>, roll: Int): MultiplierTier {
        var cursor = 0
        // Using indices to avoid Iterator overhead
        for (i in tiers.indices) {
            val tier = tiers[i]
            cursor += tier.weight
            if (roll < cursor) return tier
        }
        return tiers[tiers.size - 1] // Faster than .last()
    }

    private fun buildResult(buyInCents: Int, samples: List<SimulationSample>, theoreticalAvg: Double, stacks: IntArray): SimulationResult {
        val size = samples.size
        if (size == 0) throw IllegalStateException("Simulation was interrupted before any iteration completed.")

        var sum = 0
        var sumSqr = 0
        samples.forEach {
            sum += it.totalProfitCents
            sumSqr += it.totalProfitCents * it.totalProfitCents
        }
        val mean = sum / size.toDouble()
        val stdDev = sqrt(sumSqr / size.toDouble() - mean * mean)

        return SimulationResult(
            buyInCents = buyInCents,
            avgProfitCents = theoreticalAvg,
            medianSample = samples[size / 2],
            p5Sample = samples[(size * 0.05).toInt()],
            p95Sample = samples[(size * 0.95).toInt()],
            standardDeviationCents = stdDev,
            iterations = size,
            stacks = stacks.toList(),
        )
    }
}

data class SimulationResult(
    val buyInCents: Int,
    val stacks: List<Int>,
    val medianSample: SimulationSample,
    val p5Sample: SimulationSample,
    val p95Sample: SimulationSample,
    val avgProfitCents: Double,
    val standardDeviationCents: Double,
    val iterations: Int,
){
    fun profitSelector(stack: Int?): (SimulationSample) -> Int {
        val index = stack?.let { stacks.indexOf(it) } ?: -1
        return if(index == -1) {
            SimulationSample::totalProfitCents
        } else {
            s -> s.profitsByStack[index]
        }
    }
}

data class SimulationSample(val profitsByStack: List<Int>) : Comparable<SimulationSample> {
    val totalProfitCents: Int = profitsByStack.sum()

    override fun compareTo(other: SimulationSample) = compareValuesBy(this, other,
        { it.totalProfitCents },
        { it.profitsByStack[0] }
    )
}

/**
 * Encapsulates the historical data recorded for a specific profile.
 */
data class PlayerStat(
    val avgCEV: Double,
    val gameCount: Int
)
