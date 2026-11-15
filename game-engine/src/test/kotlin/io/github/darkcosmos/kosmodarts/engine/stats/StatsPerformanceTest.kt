package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * PS5 / ST10 performance: validating 1,000 generated matches and computing every figure of one profile must stay well
 * under a second. The deterministic checks do not depend on the clock; the budget is generous to avoid flakiness.
 */
class StatsPerformanceTest {

    private val profile = 1L
    private val matchCount = 1_000
    private val budgetMillis = 1_000L
    private val warmUps = 2
    private val runs = 5

    private fun generate(): List<RawStatsMatch> {
        val random = Random(SEED)
        return List(matchCount) { index ->
            val players = random.nextInt(1, MAX_PLAYERS + 1)
            val legs = random.nextInt(1, MAX_LEGS + 1)
            val started = index * STEP
            val ended = started + STEP / 2
            if (random.nextBoolean()) {
                val score = if (random.nextBoolean()) 501 else 301
                val rule = CheckoutRule.entries[random.nextInt(CheckoutRule.entries.size)]
                StatsMatchFixtures.x01Raw(players, score, rule, legs, "m$index", started, ended)
            } else {
                val variant = CricketVariant.entries[random.nextInt(CricketVariant.entries.size)]
                StatsMatchFixtures.cricketRaw(players, variant, legs, "m$index", started, ended)
            }
        }
    }

    private class Result(
        val ignored: Int,
        val x01: X01Figures,
        val cricket: CricketFigures,
        val trend: List<TrendPoint>,
    )

    private fun computeAll(raws: List<RawStatsMatch>): Result {
        val validated = StatsMatchValidator.validateAll(raws)
        val all = select(validated.matches, profile, 0L, StatsFilter.AllModes, Period.SINCE_ALWAYS, Long.MAX_VALUE / 2)
        val x01 = select(validated.matches, profile, 0L, X01Filter(), Period.SINCE_ALWAYS, Long.MAX_VALUE / 2)
        val cricket = select(validated.matches, profile, 0L, CricketFilter(), Period.SINCE_ALWAYS, Long.MAX_VALUE / 2)
        return Result(
            validated.ignoredCount,
            x01Figures(x01, profile),
            cricketFigures(cricket, profile),
            trendSeries(all, profile),
        )
    }

    @Test
    fun `ST10 every generated match is valid and each figure covers the whole selection`() {
        val raws = generate()
        val result = computeAll(raws)

        assertEquals(0, result.ignored)
        assertEquals(matchCount, result.trend.size)
        assertEquals(result.trend.map { it.endedAt }.sorted(), result.trend.map { it.endedAt })
        val x01Matches = raws.count { it.settings is ArchivedSettings.X01 }
        val cricketResults = result.cricket.results.sumOf { it.matchesWon + it.matchesLost }
        val x01Results = result.x01.matchesWon + result.x01.matchesLost
        val soloX01 = raws.count { it.players.size == 1 && it.settings is ArchivedSettings.X01 }
        val soloCricket = raws.count { it.players.size == 1 && it.settings !is ArchivedSettings.X01 }
        assertEquals(x01Matches - soloX01, x01Results)
        assertEquals(matchCount - x01Matches - soloCricket, cricketResults)
    }

    @Test
    fun `PS5 validation, selection, X01, Cricket and trend of 1000 matches run under the budget`() {
        val raws = generate()
        repeat(warmUps) { computeAll(raws) }

        val timings = List(runs) {
            val start = System.nanoTime()
            computeAll(raws)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }.sorted()
        val median = timings[runs / 2]

        println("StatsPerformanceTest: median $median ms over $runs runs ($timings ms)")
        assertTrue(median < budgetMillis, "median $median ms is over the $budgetMillis ms budget")
    }

    private companion object {
        const val SEED = 52_053L
        const val MAX_PLAYERS = 4
        const val MAX_LEGS = 3
        const val STEP = 10_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
