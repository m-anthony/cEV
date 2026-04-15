package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.MultiplierTier
import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.SpinProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.agrona.collections.IntArrayList
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.nanoseconds

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
        val samples = Array(stacks.size) { IntArrayList() }
        var currentIteration = 0

        val start = System.nanoTime()
        var seconds = 0L
        try {
            val sample = IntArray(stacks.size) { 0 }
            while (currentIteration < maxIterations) {
                repeat(1000) {
                    if (currentIteration < maxIterations) {
                        sample.fill(0)
                        preparedProfiles.forEach { it.simulate(sample) }
                        sample.forEachIndexed { stackIndex, result -> samples[stackIndex].addInt(result) }
                        currentIteration++
                    }
                }
                val newSeconds = (System.nanoTime() - start).nanoseconds.inWholeSeconds
                if (newSeconds != seconds) {
                    // Intermediate publishing
                    seconds = newSeconds
                    val intermediateResult = buildResult(buyInCents, samples, totalTheoreticalAvg, stacks)
                    onResult(intermediateResult)
                }
                yield()
            }
        } catch (_: CancellationException) {
            logger.info { "Simulation cancelled at $currentIteration for buy-in ${"%.2f".format(buyInCents / 100.0)}" }
        }

        // The return value of withContext is determined here
        withContext(NonCancellable) {
            if (samples.isNotEmpty()) {
                val finalResult = buildResult(
                    buyInCents = buyInCents,
                    samples = samples,
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

    private fun buildResult(
        buyInCents: Int,
        samples: Array<IntArrayList>,
        theoreticalAvg: Double,
        stacks: IntArray
    ): SimulationResult {
        val size = samples[0].size
        if (size == 0) throw IllegalStateException("Simulation was interrupted before any iteration completed.")

        val sortedResultsPerStack = samples.map { resultsPerStack -> resultsPerStack.toIntArray().also { it.sort() } }
        val samples = listOf(
            (size * 0.05).toInt(),
            size / 2,
            (size * 0.95).toInt()
        ).map { i ->
            SimulationSample(sortedResultsPerStack.map { it[i] })
        }
        return SimulationResult(
            buyInCents = buyInCents,
            avgProfitCents = theoreticalAvg,
            p5Sample = samples[0],
            medianSample = samples[1],
            p95Sample = samples[2],
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
