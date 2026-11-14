package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MatchSelectionTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun ids(matches: List<StatsMatch>) = matches.map { it.sessionId }

    private fun pick(
        matches: List<StatsMatch>,
        filter: StatsFilter = StatsFilter.AllModes,
        period: Period = Period.SINCE_ALWAYS,
        resetAt: Long = 0,
        profileId: Long = 1,
    ) = select(matches, profileId, resetAt, filter, period, now)

    private val x01Matches = listOf(
        StatsMatchFixtures.x01(startScore = 501, checkoutRule = CheckoutRule.DOUBLE_OUT, sessionId = "501d"),
        StatsMatchFixtures.x01(startScore = 501, checkoutRule = CheckoutRule.SINGLE_OUT, sessionId = "501s"),
        StatsMatchFixtures.x01(startScore = 301, checkoutRule = CheckoutRule.DOUBLE_OUT, sessionId = "301d"),
        StatsMatchFixtures.x01(startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT, sessionId = "301s"),
    )
    private val cricketMatches = listOf(
        StatsMatchFixtures.cricket(variant = CricketVariant.STANDARD, sessionId = "std"),
        StatsMatchFixtures.cricket(variant = CricketVariant.CUT_THROAT, sessionId = "cut"),
    )
    private val all = x01Matches + cricketMatches

    @Test
    fun `ST2 a match started exactly at the reset date counts, one millisecond before does not`() {
        val atReset = StatsMatchFixtures.x01(sessionId = "at", startedAt = 5_000, endedAt = 6_000)
        val before = StatsMatchFixtures.x01(sessionId = "before", startedAt = 4_999, endedAt = 6_000)
        assertEquals(listOf("at"), ids(pick(listOf(atReset, before), resetAt = 5_000)))
    }

    @Test
    fun `ST2 only the matches of the profile are selected`() {
        assertEquals(emptyList<String>(), ids(pick(all, profileId = 9)))
    }

    @Test
    fun `ST3 every X01 filter combination`() {
        val expected = mapOf(
            X01Filter(null, null) to listOf("501d", "501s", "301d", "301s"),
            X01Filter(501, null) to listOf("501d", "501s"),
            X01Filter(301, null) to listOf("301d", "301s"),
            X01Filter(null, CheckoutRule.DOUBLE_OUT) to listOf("501d", "301d"),
            X01Filter(null, CheckoutRule.SINGLE_OUT) to listOf("501s", "301s"),
            X01Filter(501, CheckoutRule.DOUBLE_OUT) to listOf("501d"),
            X01Filter(501, CheckoutRule.SINGLE_OUT) to listOf("501s"),
            X01Filter(301, CheckoutRule.DOUBLE_OUT) to listOf("301d"),
            X01Filter(301, CheckoutRule.SINGLE_OUT) to listOf("301s"),
        )
        expected.forEach { (filter, ids) -> assertEquals(ids, ids(pick(all, filter)), "$filter") }
    }

    @Test
    fun `ST3 Cricket filters, and a Cricket match never passes an X01 filter`() {
        assertEquals(listOf("std", "cut"), ids(pick(all, CricketFilter(null))))
        assertEquals(listOf("std"), ids(pick(all, CricketFilter(CricketVariant.STANDARD))))
        assertEquals(listOf("cut"), ids(pick(all, CricketFilter(CricketVariant.CUT_THROAT))))
        assertEquals(4, pick(all, X01Filter()).size)
        assertEquals(2, pick(all, CricketFilter()).size)
        assertEquals(6, pick(all, StatsFilter.AllModes).size)
    }

    @Test
    fun `ST4 a match ended exactly 30 x 24 h ago counts, older does not`() {
        val edge = StatsMatchFixtures.x01(sessionId = "edge", startedAt = 1, endedAt = now - PERIOD_DAYS * day)
        val older = StatsMatchFixtures.x01(sessionId = "older", startedAt = 1, endedAt = now - PERIOD_DAYS * day - 1)
        val recent = StatsMatchFixtures.x01(sessionId = "recent", startedAt = 1, endedAt = now)
        val result = pick(listOf(older, edge, recent), period = Period.LAST_30_DAYS)
        assertEquals(listOf("edge", "recent"), ids(result))
    }

    private fun numbered(count: Int, ended: (Int) -> Long = { it * 1_000L + 10_000 }) = List(count) {
        StatsMatchFixtures.x01(sessionId = "m$it", startedAt = 1, endedAt = ended(it))
    }

    @Test
    fun `ST4 last 10 matches with 9, 10 and 11 matches`() {
        assertEquals(9, pick(numbered(9), period = Period.LAST_10_MATCHES).size)
        assertEquals(10, pick(numbered(10), period = Period.LAST_10_MATCHES).size)
        val eleven = pick(numbered(11), period = Period.LAST_10_MATCHES)
        assertEquals(10, eleven.size)
        assertEquals((1..10).map { "m$it" }, ids(eleven))
        assertEquals(LAST_MATCHES, eleven.size)
    }

    @Test
    fun `ST4 last 10 is taken after the filters and the reset date`() {
        val cricketNewest = List(5) {
            StatsMatchFixtures.cricket(sessionId = "c$it", startedAt = 1, endedAt = 50_000L + it)
        }
        val x01 = numbered(10)
        val result = pick(x01 + cricketNewest, X01Filter(), Period.LAST_10_MATCHES)
        assertEquals(10, result.size)
        assertEquals(x01.map { it.sessionId }, ids(result))
        val beforeReset = StatsMatchFixtures.x01(sessionId = "old", startedAt = 0, endedAt = 99_000)
        val afterReset = pick(x01 + beforeReset, X01Filter(), Period.LAST_10_MATCHES, resetAt = 1)
        assertEquals(x01.map { it.sessionId }, ids(afterReset))
    }

    @Test
    fun `ST4 since always keeps every match after ST2`() {
        assertEquals(6, pick(all).size)
    }
}
