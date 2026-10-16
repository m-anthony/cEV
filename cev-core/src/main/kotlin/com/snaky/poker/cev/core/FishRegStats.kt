package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import com.snaky.poker.cev.core.parsers.MetaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/**
 * Result structure containing weighted scores and confidence metrics per player.
 */
data class PlayerResult(
    val pseudo: String,
    val room: Room,
    val score: Int,
    val rawScore: Double,
    val totalGames: Int,
    val avgGamesPerActiveDay: Double,
    val maxTables: Int,
    val activeDays: Int,
    val heroDaysInSpan: Int,
    val presenceRatio: Double,
    val multiTableScore: Int,
    val presenceScore: Int,
    val intensityScore: Int,
    val regularityScore: Int,
    val volumeScore: Int,
    val mtConfidence: Double,
    val presenceConfidence: Double
)

/**
 * Key identifying a specific field by room and buy-in amount in cents.
 */
data class FieldKey(
    val room: Room,
    val buyInCents: Int
) : Comparable<FieldKey> {
    override fun compareTo(other: FieldKey): Int =
        compareValuesBy(this, other, FieldKey::buyInCents, { it.room.name })
}

/**
 * Aggregated metrics for a given field (room + buy-in).
 */
data class FieldMetrics(
    val totalGames: Int,
    val avgRegsPerTable: Double,
    val avgTableScore: Double
)

fun main(args: Array<String>) {
    if (args.size != 1 || !args[0].startsWith("--sources=")) {
        System.err.println("usage : --sources=<path1,path2>")
        exitProcess(-1)
    }

    val paths = args[0].substringAfter('=').split(',').map { File(it) }

    val dataReader = object : SpinDataReader<Spin> {
        override fun getSpinId(spin: Spin) = spin.id
        override fun getRoom(spin: Spin) = spin.room
        override fun getBuyInCents(spin: Spin) = spin.buyInCents
        override fun getStartTime(spin: Spin) = Instant.ofEpochSecond(spin.startTimestamp)
        override fun getEndTime(spin: Spin) = Instant.ofEpochSecond(
            spin.hands.maxOfOrNull { it.timestamp } ?: spin.startTimestamp
        )

        override fun getOpponentPseudos(spin: Spin): List<String> {
            val firstHand = spin.hands.minByOrNull { it.timestamp } ?: return emptyList()
            return firstHand.players
                .filter { it != firstHand.hero }
                .map { it.name.trim().lowercase() }
                .toList()
        }
    }

    val eval = PlayerScorer(dataReader)
    val spins = runBlocking(Dispatchers.Default) {
        with(MetaParser()) {
            paths.forEach { FileCrawler.processFileOrDirectory(it, this) }
            waitForResults()
            this.spins
        }
    }

    val scores = eval.computePlayerScores(spins.values)
    val buckets = scores.groupBy { it.score }.toSortedMap()

    System.err.println("\n=== SUMMARY PER SCORE BUCKET (STEP 10) ===")
    System.err.println(
        "%-8s | %-8s | %-12s | %-10s | %-12s | %-10s | %-22s".format(
            "Score", "Players", "Total Games", "Min Games", "Median Games", "Max Games", "Avg Games/Active Day"
        )
    )
    System.err.println("-".repeat(100))

    for ((score, players) in buckets) {
        val playerCount = players.size
        val totalGamesSum = players.sumOf { it.totalGames }
        val minGames = players.minOf { it.totalGames }
        val maxGames = players.maxOf { it.totalGames }

        val sortedGames = players.map { it.totalGames }.sorted()
        val medianGames = if (playerCount % 2 == 1) {
            sortedGames[playerCount / 2].toDouble()
        } else {
            (sortedGames[playerCount / 2 - 1] + sortedGames[playerCount / 2]) / 2.0
        }

        val avgDailyGames = players.map { it.avgGamesPerActiveDay }.average()

        System.err.println(
            "%-8d | %-8d | %-12d | %-10d | %-12.1f | %-10d | %-22.2f".format(
                score, playerCount, totalGamesSum, minGames, medianGames, maxGames, avgDailyGames
            )
        )
    }

    System.err.println("\n\n=== MIN / MAX VOLUME BREAKDOWN PER BUCKET ===")
    for ((score, players) in buckets) {
        val minPlayer = players.minByOrNull { it.totalGames }!!
        val maxPlayer = players.maxByOrNull { it.totalGames }!!

        System.err.println("\n--------------------------------------------------------------------------------")
        System.err.println("BUCKET SCORE $score (${players.size} players)")
        System.err.println("--------------------------------------------------------------------------------")

        printPlayerReport("MIN Volume Player", minPlayer)
        if (minPlayer.pseudo != maxPlayer.pseudo) {
            printPlayerReport("MAX Volume Player", maxPlayer)
        } else {
            System.err.println("  (Only 1 player in this bucket or Min/Max are identical)")
        }
    }

    // === FIELD ANALYSIS PER ROOM & BUY-IN ===
    val regScoreThreshold = 30
    val playerScoresMap = scores.associateBy { PlayerKey(it.pseudo, it.room) }

    val fieldMetricsMap = spins.values
        .groupBy { FieldKey(dataReader.getRoom(it), dataReader.getBuyInCents(it)) }
        .toSortedMap()
        .mapValues { (fieldKey, fieldSpins) ->
            var totalRegs = 0
            var totalTableScoresSum = 0.0

            for (spin in fieldSpins) {
                val opponents = dataReader.getOpponentPseudos(spin)
                val opponentScores = opponents.map { pseudo ->
                    playerScoresMap[PlayerKey(pseudo, fieldKey.room)]?.score ?: 0
                }

                val regsInGame = opponentScores.count { it >= regScoreThreshold }
                val gameAvgScore = if (opponentScores.isNotEmpty()) opponentScores.average() else 0.0

                totalRegs += regsInGame
                totalTableScoresSum += gameAvgScore
            }

            val gameCount = fieldSpins.size
            FieldMetrics(
                totalGames = gameCount,
                avgRegsPerTable = if (gameCount > 0) totalRegs.toDouble() / gameCount else 0.0,
                avgTableScore = if (gameCount > 0) totalTableScoresSum / gameCount else 0.0
            )
        }

    System.err.println("\n\n=== FIELD ANALYSIS PER ROOM & BUY-IN ===")
    System.err.println(
        "%-12s | %-10s | %-12s | %-18s | %-16s".format(
            "Room", "Buy-in", "Total Games", "Avg Regs / Table", "Avg Table Score"
        )
    )
    System.err.println("-".repeat(78))

    for ((fieldKey, metrics) in fieldMetricsMap) {
        val buyInFormatted = "%.2f €".format(fieldKey.buyInCents / 100.0)
        System.err.println(
            "%-12s | %-10s | %-12d | %-18s | %-16s".format(
                fieldKey.room,
                buyInFormatted,
                metrics.totalGames,
                "%.2f / 2".format(metrics.avgRegsPerTable),
                "%.1f / 100".format(metrics.avgTableScore)
            )
        )
    }

    // === MONTHLY EVOLUTION FOR LARGEST FIELD ===
    val largestFieldEntry = fieldMetricsMap.maxByOrNull { it.value.totalGames }
    if (largestFieldEntry != null) {
        val (topFieldKey, topFieldMetrics) = largestFieldEntry
        val topFieldSpins = spins.values.filter {
            dataReader.getRoom(it) == topFieldKey.room &&
                    dataReader.getBuyInCents(it) == topFieldKey.buyInCents
        }

        val monthlyMetrics = topFieldSpins
            .groupBy { dataReader.getStartTime(it).toString().substring(0, 7) } // Group by "YYYY-MM"
            .toSortedMap()
            .mapValues { (_, monthSpins) ->
                var totalRegs = 0
                var totalTableScoresSum = 0.0

                for (spin in monthSpins) {
                    val opponents = dataReader.getOpponentPseudos(spin)
                    val opponentScores = opponents.map { pseudo ->
                        playerScoresMap[PlayerKey(pseudo, topFieldKey.room)]?.score ?: 0
                    }

                    val regsInGame = opponentScores.count { it >= regScoreThreshold }
                    val gameAvgScore = if (opponentScores.isNotEmpty()) opponentScores.average() else 0.0

                    totalRegs += regsInGame
                    totalTableScoresSum += gameAvgScore
                }

                val gameCount = monthSpins.size
                FieldMetrics(
                    totalGames = gameCount,
                    avgRegsPerTable = if (gameCount > 0) totalRegs.toDouble() / gameCount else 0.0,
                    avgTableScore = if (gameCount > 0) totalTableScoresSum / gameCount else 0.0
                )
            }

        val topBuyInFormatted = "%.2f €".format(topFieldKey.buyInCents / 100.0)
        System.err.println("\n\n=== MONTHLY EVOLUTION FOR LARGEST FIELD: ${topFieldKey.room} $topBuyInFormatted (${topFieldMetrics.totalGames} games) ===")
        System.err.println(
            "%-10s | %-12s | %-18s | %-16s".format(
                "Month", "Total Games", "Avg Regs / Table", "Avg Table Score"
            )
        )
        System.err.println("-".repeat(66))

        for ((month, metrics) in monthlyMetrics) {
            System.err.println(
                "%-10s | %-12d | %-18s | %-16s".format(
                    month,
                    metrics.totalGames,
                    "%.2f / 2".format(metrics.avgRegsPerTable),
                    "%.1f / 100".format(metrics.avgTableScore)
                )
            )
        }
    }
}

private fun printPlayerReport(label: String, player: PlayerResult) {
    System.err.println("  > $label: ${player.pseudo} (${player.room})")
    System.err.println("    - Total Games         : ${player.totalGames}")
    System.err.println(
        "    - Max Simultaneous    : ${player.maxTables} tables (Conf: %.2f) -> MultiTable Score: ${player.multiTableScore}/35".format(
            player.mtConfidence
        )
    )
    System.err.println(
        "    - Active Days Ratio   : ${player.activeDays}/${player.heroDaysInSpan} days (%.1f%%, Conf: %.2f) -> Presence Score: ${player.presenceScore}/20".format(
            player.presenceRatio * 100,
            player.presenceConfidence
        )
    )
    System.err.println(
        "    - Daily Intensity     : %.2f games/active day -> Intensity Score: ${player.intensityScore}/15".format(
            player.avgGamesPerActiveDay
        )
    )
    System.err.println("    - Volume Threshold    : Sample size -> Volume Score: ${player.volumeScore}/30")
    System.err.println("    - RAW TOTAL SCORE     : %.2f / 100".format(player.rawScore))
    System.err.println("    - BUCKET SCORE        : ${player.score} / 100")
}

interface SpinDataReader<T> {
    fun getSpinId(spin: T): String
    fun getRoom(spin: T): Room
    fun getBuyInCents(spin: T): Int
    fun getStartTime(spin: T): Instant
    fun getEndTime(spin: T): Instant
    fun getOpponentPseudos(spin: T): List<String>
}

data class PlayerKey(val pseudo: String, val room: Room)

data class PlayerActivitySpan(
    val firstSeen: Instant,
    val lastSeen: Instant,
    val activeDaysCount: Int,
    val heroActiveDaysInSpanCount: Int
)

class PlayerScorer<T>(private val reader: SpinDataReader<T>) {

    fun computePlayerScores(spins: Collection<T>): List<PlayerResult> {
        if (spins.isEmpty()) return emptyList()

        val sortedSpins = spins.sortedBy { reader.getStartTime(it) }
        val playerActivity = computePlayerActivitySpans(sortedSpins)
        val maxSimultaneousTables = computeMaxSimultaneousTables(sortedSpins)

        val playerSpinsMap = mutableMapOf<PlayerKey, MutableList<T>>()
        for (spin in sortedSpins) {
            val room = reader.getRoom(spin)
            for (pseudo in reader.getOpponentPseudos(spin)) {
                val key = PlayerKey(pseudo, room)
                playerSpinsMap.getOrPut(key) { mutableListOf() }.add(spin)
            }
        }

        val results = mutableListOf<PlayerResult>()

        for ((playerKey, playerSpins) in playerSpinsMap) {
            val totalGames = playerSpins.size
            val maxTables = maxSimultaneousTables[playerKey] ?: 1
            val span = playerActivity[playerKey]!!

            val activeDays = span.activeDaysCount.coerceAtLeast(1)
            val heroDays = span.heroActiveDaysInSpanCount.coerceAtLeast(1)
            val presenceRatio = activeDays.toDouble() / heroDays
            val avgGamesPerActiveDay = totalGames.toDouble() / activeDays

            val mtConfidence = (totalGames / 12.0).coerceIn(0.0, 1.0)
            val presenceConfidence = (activeDays / 5.0).coerceIn(0.0, 1.0)
            val intensityConfidence = (totalGames / 10.0).coerceIn(0.0, 1.0)

            val rawMultiTableScore = when {
                maxTables >= 3 -> 25
                maxTables == 2 -> 15
                else -> 0
            }
            val weightedMultiTableScore = (rawMultiTableScore * mtConfidence).roundToInt()

            val rawPresenceScore = when {
                activeDays >= 15 || presenceRatio >= 0.15 -> 30
                activeDays >= 8 || presenceRatio >= 0.08 -> 20
                activeDays >= 3 || presenceRatio >= 0.03 -> 10
                else -> 0
            }
            val weightedPresenceScore = (rawPresenceScore * presenceConfidence).roundToInt()

            val rawIntensityScore = when {
                avgGamesPerActiveDay >= 2.5 -> 15
                avgGamesPerActiveDay >= 1.5 -> 8
                else -> 0
            }
            val weightedIntensityScore = (rawIntensityScore * intensityConfidence).roundToInt()

            val regularityScore = weightedPresenceScore + weightedIntensityScore

            val volumeScore = when {
                totalGames >= 50 -> 30
                totalGames >= 20 -> 20
                totalGames >= 5 -> 10
                else -> 0
            }

            val rawTotalScore = (weightedMultiTableScore + regularityScore + volumeScore)
                .toDouble()
                .coerceAtMost(100.0)

            val bucketedScore = (rawTotalScore / 10.0).roundToInt() * 10

            results.add(
                PlayerResult(
                    pseudo = playerKey.pseudo,
                    room = playerKey.room,
                    score = bucketedScore,
                    rawScore = rawTotalScore,
                    totalGames = totalGames,
                    avgGamesPerActiveDay = avgGamesPerActiveDay,
                    maxTables = maxTables,
                    activeDays = activeDays,
                    heroDaysInSpan = heroDays,
                    presenceRatio = presenceRatio,
                    multiTableScore = weightedMultiTableScore,
                    presenceScore = weightedPresenceScore,
                    intensityScore = weightedIntensityScore,
                    regularityScore = regularityScore,
                    volumeScore = volumeScore,
                    mtConfidence = mtConfidence,
                    presenceConfidence = presenceConfidence
                )
            )
        }

        return results
    }

    private enum class EventType { START, END }

    private data class SpinEvent(
        val time: Instant,
        val type: EventType,
        val key: PlayerKey
    )

    private fun computeMaxSimultaneousTables(spins: List<T>): Map<PlayerKey, Int> {
        if (spins.isEmpty()) return emptyMap()

        val events = ArrayList<SpinEvent>(spins.size * 4)

        for (spin in spins) {
            val room = reader.getRoom(spin)
            val startTime = reader.getStartTime(spin)
            val endTime = reader.getEndTime(spin)
            val opponents = reader.getOpponentPseudos(spin)

            for (pseudo in opponents) {
                val key = PlayerKey(pseudo, room)
                events.add(SpinEvent(startTime, EventType.START, key))
                events.add(SpinEvent(endTime, EventType.END, key))
            }
        }

        events.sortWith(
            compareBy<SpinEvent> { it.time }
                .thenBy { if (it.type == EventType.END) 0 else 1 }
        )

        val activeTablesMap = mutableMapOf<PlayerKey, Int>()
        val maxTablesMap = mutableMapOf<PlayerKey, Int>()

        for (event in events) {
            val currentActive = activeTablesMap.getOrDefault(event.key, 0)

            if (event.type == EventType.START) {
                val newActive = currentActive + 1
                activeTablesMap[event.key] = newActive

                val currentMax = maxTablesMap.getOrDefault(event.key, 1)
                if (newActive > currentMax) {
                    maxTablesMap[event.key] = newActive
                }
            } else {
                activeTablesMap[event.key] = (currentActive - 1).coerceAtLeast(0)
            }
        }

        return maxTablesMap
    }

    private fun computePlayerActivitySpans(sortedSpins: List<T>): Map<PlayerKey, PlayerActivitySpan> {
        val firstSeen = mutableMapOf<PlayerKey, Instant>()
        val lastSeen = mutableMapOf<PlayerKey, Instant>()
        val playerDaysMap = mutableMapOf<PlayerKey, MutableSet<String>>()
        val heroActiveDaysByRoom = mutableMapOf<Room, MutableSet<String>>()

        for (spin in sortedSpins) {
            val room = reader.getRoom(spin)
            val startTime = reader.getStartTime(spin)
            val dayKey = startTime.toString().substring(0, 10)

            heroActiveDaysByRoom.getOrPut(room) { mutableSetOf() }.add(dayKey)

            for (pseudo in reader.getOpponentPseudos(spin)) {
                val key = PlayerKey(pseudo, room)
                if (!firstSeen.containsKey(key)) {
                    firstSeen[key] = startTime
                }
                lastSeen[key] = startTime
                playerDaysMap.getOrPut(key) { mutableSetOf() }.add(dayKey)
            }
        }

        val resultMap = mutableMapOf<PlayerKey, PlayerActivitySpan>()

        for ((key, start) in firstSeen) {
            val end = lastSeen[key]!!
            val roomHeroDays = heroActiveDaysByRoom[key.room] ?: emptySet()

            val startDay = start.toString().substring(0, 10)
            val endDay = end.toString().substring(0, 10)

            val heroDaysInSpan = roomHeroDays.count { day ->
                day in startDay..endDay
            }

            resultMap[key] = PlayerActivitySpan(
                firstSeen = start,
                lastSeen = end,
                activeDaysCount = playerDaysMap[key]?.size ?: 1,
                heroActiveDaysInSpanCount = heroDaysInSpan
            )
        }

        return resultMap
    }
}