package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Tests of HH1 to HH6 (head-to-head), ST1, ST3, ST4 and ST10. Profile ids are position + 1 unless remapped. */
class HeadToHeadTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    /** A raw match whose players get [ids] (by position) and [ranks] (by position); legs won follow the stored ones. */
    private fun withPlayers(raw: RawStatsMatch, ids: List<Long>, ranks: List<Int>): RawStatsMatch =
        raw.copy(players = raw.players.mapIndexed { i, p -> p.copy(profileId = ids[i], rank = ranks[i]) })

    private fun x01(
        id: String,
        ids: List<Long> = listOf(1, 2),
        ranks: List<Int> = listOf(1, 2),
        startedAt: Long = 1_000,
        startScore: Int = 301,
        legs: Int = 1,
    ): StatsMatch = StatsMatchFixtures.valid(
        withPlayers(
            StatsMatchFixtures.x01Raw(
                players = ids.size,
                startScore = startScore,
                legs = legs,
                sessionId = id,
                startedAt = startedAt,
                endedAt = startedAt + 1_000,
            ),
            ids,
            ranks,
        ),
    )

    private fun cricket(id: String, startedAt: Long = 1_000): StatsMatch = StatsMatchFixtures.cricket(
        sessionId = id,
        startedAt = startedAt,
        endedAt = startedAt + 1_000,
    )

    private fun h2h(
        matches: List<StatsMatch>,
        filter: StatsFilter = StatsFilter.AllModes,
        period: Period = Period.SINCE_ALWAYS,
        resetA: Long = 0,
        resetB: Long = 0,
        b: Long = 2,
    ) = headToHead(matches, 1, resetA, b, resetB, filter, period, now)

    private fun ids(matches: List<StatsMatch>) = matches.map { it.sessionId }

    @Test
    fun `HH2 common matches at 2, 3 and 4 players exclude a match with only one of the two`() {
        val matches = listOf(
            x01("two"),
            x01("three", ids = listOf(1, 2, 3), ranks = listOf(1, 2, 3)),
            x01("four", ids = listOf(1, 2, 3, 4), ranks = listOf(2, 1, 3, 4)),
            x01("onlyA", ids = listOf(1, 3)),
            x01("onlyB", ids = listOf(4, 2)),
        )
        assertEquals(listOf("two", "three", "four"), ids(commonMatches(matches, 1, 0, 2, 0)))
        assertEquals(emptyList<StatsMatch>(), commonMatches(matches, 1, 0, 1, 0))
    }

    @Test
    fun `HH2 the latest of both reset dates applies and a start equal to it counts`() {
        val matches = listOf(
            x01("early", startedAt = 500),
            x01("equal", startedAt = 2_000),
            x01("late", startedAt = 3_000),
        )
        assertEquals(listOf("equal", "late"), ids(commonMatches(matches, 1, 2_000, 2, 100)))
        assertEquals(listOf("equal", "late"), ids(commonMatches(matches, 1, 100, 2, 2_000)))
        assertEquals(listOf("late"), ids(commonMatches(matches, 1, 2_001, 2, 2_500)))
    }

    @Test
    fun `HH3 duel results add up to the common matches`() {
        val matches = listOf(
            x01("aWins", ids = listOf(1, 2, 3), ranks = listOf(1, 2, 3)),
            x01("aRank1bRank3", ids = listOf(1, 3, 2), ranks = listOf(1, 2, 3)),
            x01("bWins", ids = listOf(2, 1), ranks = listOf(1, 2)),
            x01("draw", ids = listOf(3, 1, 2), ranks = listOf(1, 2, 2)),
        )
        val figures = h2h(matches)
        assertEquals(2, figures.winsA)
        assertEquals(1, figures.winsB)
        assertEquals(1, figures.draws)
        assertEquals(figures.winsA + figures.winsB + figures.draws, figures.commonCount)
    }

    @Test
    fun `HH3 A rank 1 against B rank 3 is a win for A`() {
        val figures = h2h(listOf(x01("m", ids = listOf(1, 3, 2), ranks = listOf(1, 2, 3))))
        assertEquals(listOf(1, 0, 0), listOf(figures.winsA, figures.winsB, figures.draws))
    }

    @Test
    fun `HH3 equal ranks 2 and 2 are a draw`() {
        val figures = h2h(listOf(x01("m", ids = listOf(3, 1, 2), ranks = listOf(1, 2, 2))))
        assertEquals(listOf(0, 0, 1), listOf(figures.winsA, figures.winsB, figures.draws))
    }

    @Test
    fun `HH4 legs won come from the stored values of the common matches only`() {
        val matches = listOf(
            x01("m1", legs = 3),
            x01("m2", ids = listOf(2, 1), legs = 2),
            x01("solo", ids = listOf(1, 3), legs = 5),
        )
        val figures = h2h(matches)
        assertEquals(3, figures.legsWonA)
        assertEquals(2, figures.legsWonB)
    }

    @Test
    fun `HH5 figures use the common matches only`() {
        val common = x01("common", startScore = 301)
        val soloOfA = x01("soloA", ids = listOf(1, 3), startScore = 501)
        val figures = h2h(listOf(common, soloOfA))
        assertEquals(x01Figures(listOf(common), 1), figures.x01A)
        assertEquals(x01Figures(listOf(common), 2), figures.x01B)
        assertEquals(1, figures.x01A.matchesWon)
        assertEquals(1, figures.x01B.matchesLost)
    }

    @Test
    fun `HH6 Last 10 takes the last 10 common matches`() {
        val common = List(12) { x01("c$it", startedAt = 1_000L + it * 10_000) }
        val others = List(3) { x01("o$it", ids = listOf(1, 3), startedAt = 500_000L + it) }
        val figures = h2h(common + others, period = Period.LAST_10_MATCHES)
        assertEquals(10, figures.commonCount)
        assertEquals(10, figures.x01A.matchesWon)
        val picked = selectCommon(common + others, 1, 0, 2, 0, StatsFilter.AllModes, Period.LAST_10_MATCHES, now)
        assertEquals((2..11).map { "c$it" }, ids(picked))
    }

    @Test
    fun `HH6 the 30 days period is applied to the common matches`() {
        val old = x01("old", startedAt = 1_000)
        val recent = x01("recent", startedAt = 95 * day)
        val figures = h2h(listOf(old, recent), period = Period.LAST_30_DAYS)
        assertEquals(1, figures.commonCount)
    }

    @Test
    fun `HH6 mode All counts every mode then gives X01 and Cricket figures apart`() {
        val matches = listOf(x01("x"), cricket("c1"), cricket("c2"))
        val all = h2h(matches)
        assertEquals(3, all.commonCount)
        assertEquals(3, all.winsA)
        assertEquals(1, all.x01A.matchesWon)
        assertEquals(cricketFigures(listOf(matches[1], matches[2]), 1).mpr, all.cricketA.mpr)
        val onlyCricket = h2h(matches, filter = CricketFilter())
        assertEquals(2, onlyCricket.commonCount)
        assertEquals(0, onlyCricket.x01A.matchesWon)
        assertNull(onlyCricket.x01A.average)
    }

    @Test
    fun `HH6 ST3 filters of X01 and Cricket apply to the common matches`() {
        val matches = listOf(
            x01("s301", startScore = 301),
            x01("s501", startScore = 501),
            cricket("c"),
        )
        assertEquals(1, h2h(matches, filter = X01Filter(startScore = 501)).commonCount)
        assertEquals(0, h2h(matches, filter = X01Filter(checkoutRule = CheckoutRule.SINGLE_OUT)).commonCount)
        assertEquals(1, h2h(matches, filter = CricketFilter(CricketVariant.STANDARD)).commonCount)
        assertEquals(0, h2h(matches, filter = CricketFilter(CricketVariant.CUT_THROAT)).commonCount)
    }

    @Test
    fun `HH1 common counts of every profile of the picker, zero included`() {
        val matches = listOf(
            x01("a"),
            x01("b", ids = listOf(1, 3)),
            x01("c", ids = listOf(1, 3), startedAt = 5_000),
        )
        val counts = commonCounts(matches, 1, 0, mapOf(2L to 0L, 3L to 0L, 4L to 0L))
        assertEquals(mapOf(2L to 1, 3L to 2, 4L to 0), counts)
        assertEquals(1, commonCounts(matches, 1, 0, mapOf(3L to 5_000L)).getValue(3))
    }

    @Test
    fun `HH9 unknown profile gives empty figures without exception`() {
        val figures = h2h(listOf(x01("m")), b = 99)
        assertEquals(0, figures.commonCount)
        assertEquals(0, figures.legsWonA + figures.legsWonB)
        assertNull(figures.x01A.average)
        assertNull(figures.cricketA.mpr)
        assertEquals(0, h2h(emptyList()).commonCount)
    }

    @Test
    fun `ST10 one corrupt common match leaves the figures of the valid matches alone`() {
        val validRaw = listOf(
            StatsMatchFixtures.x01Raw(sessionId = "v1", startedAt = 1_000, endedAt = 2_000),
            StatsMatchFixtures.cricketRaw(sessionId = "v2", startedAt = 3_000, endedAt = 4_000),
        )
        val corrupt = StatsMatchFixtures.x01Raw(sessionId = "bad", startedAt = 5_000, endedAt = 6_000)
            .copy(settings = null)
        val withCorrupt = StatsMatchValidator.validateAll(listOf(validRaw[0], corrupt, validRaw[1]))
        val alone = StatsMatchValidator.validateAll(validRaw)
        assertEquals(1, withCorrupt.ignoredCount)
        assertEquals(h2h(alone.matches), h2h(withCorrupt.matches))
        assertEquals(2, h2h(withCorrupt.matches).commonCount)
    }
}
