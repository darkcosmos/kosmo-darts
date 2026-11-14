package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Tests of the trend series (ST7, PS5) and its summary; rule ids of docs/rules. */
class TrendSeriesTest {

    private val profile = 1L

    private fun point(session: String, end: Long, value: Double) = TrendPoint(session, end, value)

    @Test
    fun `ST7 points follow the end order whatever the input order`() {
        val late = StatsMatchFixtures.x01(sessionId = "late", startedAt = 100, endedAt = 900)
        val early = StatsMatchFixtures.x01(sessionId = "early", startedAt = 50, endedAt = 300)
        val mid = StatsMatchFixtures.x01(sessionId = "mid", startedAt = 10, endedAt = 500)

        val series = trendSeries(listOf(late, early, mid), profile)

        assertEquals(listOf("early", "mid", "late"), series.map { it.sessionId })
        assertEquals(listOf(300L, 500L, 900L), series.map { it.endedAt })
    }

    @Test
    fun `ST7 equal end times break by start time`() {
        val a = StatsMatchFixtures.x01(sessionId = "a", startedAt = 200, endedAt = 900)
        val b = StatsMatchFixtures.x01(sessionId = "b", startedAt = 100, endedAt = 900)

        assertEquals(listOf("b", "a"), trendSeries(listOf(a, b), profile).map { it.sessionId })
    }

    @Test
    fun `STX1 the X01 value is the 3-dart average of the match`() {
        val match = StatsMatchFixtures.x01(startScore = 501)

        val value = trendSeries(listOf(match), profile).single().value

        assertEquals(x01Figures(listOf(match), profile).average, value)
    }

    @Test
    fun `STC2 the Cricket value is the MPR of the match`() {
        val match = StatsMatchFixtures.cricket(variant = CricketVariant.CUT_THROAT)

        val value = trendSeries(listOf(match), profile).single().value

        assertEquals(cricketFigures(listOf(match), profile).mpr, value)
    }

    @Test
    fun `ST6 an empty selection gives an empty series and a match without the profile gives no point`() {
        assertEquals(emptyList<TrendPoint>(), trendSeries(emptyList(), profile))
        assertEquals(emptyList<TrendPoint>(), trendSeries(listOf(StatsMatchFixtures.x01(players = 1)), 99L))
    }

    @Test
    fun `ST7 one match gives one point`() {
        assertEquals(1, trendSeries(listOf(StatsMatchFixtures.cricket()), profile).size)
    }

    @Test
    fun `PS5 summary of an empty series has no value and no date`() {
        val summary = summarize(emptyList())

        assertEquals(0, summary.count)
        assertNull(summary.first)
        assertNull(summary.last)
        assertNull(summary.lowest)
        assertNull(summary.highest)
        assertNull(summary.firstDate)
        assertNull(summary.lastDate)
    }

    @Test
    fun `PS5 summary of one point has equal first, last, lowest, highest and dates`() {
        val summary = summarize(listOf(point("a", 700, 42.5)))

        assertEquals(TrendSummary(1, 42.5, 42.5, 42.5, 42.5, 700, 700), summary)
    }

    @Test
    fun `PS5 summary reads first and last by order, lowest and highest by value, with their dates`() {
        val summary = summarize(listOf(point("a", 100, 50.0), point("b", 200, 20.0), point("c", 300, 80.0)))

        assertEquals(TrendSummary(3, 50.0, 80.0, 20.0, 80.0, 100, 300), summary)
    }

    @Test
    fun `PS5 summary with equal values keeps one lowest and one highest`() {
        val summary = summarize(listOf(point("a", 100, 30.0), point("b", 200, 30.0)))

        assertEquals(TrendSummary(2, 30.0, 30.0, 30.0, 30.0, 100, 200), summary)
    }

    @Test
    fun `PS5 the curve needs 2 points, the figures need 1`() {
        val none = summarize(emptyList())
        val one = summarize(listOf(point("a", 100, 30.0)))
        val two = summarize(listOf(point("a", 100, 30.0), point("b", 200, 40.0)))

        assertEquals(listOf(false, true, true), listOf(none, one, two).map { it.hasPoints })
        assertEquals(listOf(false, false, true), listOf(none, one, two).map { it.hasCurve })
        assertEquals(2, MIN_CURVE_POINTS)
    }
}
