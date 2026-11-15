package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.engine.stats.summarize
import io.github.darkcosmos.kosmodarts.engine.stats.trendSeries
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** PS2 to PS6, ST2 to ST10: the Profile state, without Android and without a database. */
class ProfileStateTest {
    private val both = ProfileTestData.data(listOf(ProfileTestData.x01Raw("a"), ProfileTestData.cricketRaw("b")))

    /** The state after the read and the first computation. */
    private fun shown(data: ProfileStatsData = both, from: ProfileState = ProfileState()): ProfileState {
        val loaded = from.loaded(from.loadId, DbResult.Ok(data))
        return loaded.computed(loaded.figuresId, data.figures(loaded.query))
    }

    @Test
    fun `PS3 a new Profile opens on X01, since always, every filter at All`() {
        val state = ProfileState()
        assertEquals(ProfileSection.X01, state.section)
        assertEquals(Period.SINCE_ALWAYS, state.x01Period)
        assertEquals(Period.SINCE_ALWAYS, state.cricketPeriod)
        assertEquals(X01Filter(), state.x01Filter)
        assertEquals(CricketFilter(), state.cricketFilter)
        assertEquals(ProfileView.LOADING, state.view)
    }

    @Test
    fun `PS6 load success shows the content once the figures are computed`() {
        val loaded = ProfileState().loaded(1, DbResult.Ok(both))
        assertEquals(ProfileView.LOADING, loaded.view)
        assertEquals(1, loaded.figuresId)

        val state = shown()
        assertEquals(ProfileView.CONTENT, state.view)
        assertEquals("Kevin", state.name)
        assertEquals(2, state.figures?.eligibleCount)
        assertFalse(state.isSelectionEmpty)
    }

    @Test
    fun `PS6 no match at all gives the empty state`() {
        val state = shown(ProfileTestData.data(emptyList()))
        assertEquals(ProfileView.NO_MATCH, state.view)
    }

    @Test
    fun `PS6 matches before the real reset do not count (ST2), so the profile can be empty again`() {
        val state = shown(ProfileTestData.data(listOf(ProfileTestData.x01Raw()), resetAt = 5_000))
        assertEquals(ProfileView.NO_MATCH, state.view)
    }

    @Test
    fun `PS6 no match for the filters keeps the content with an empty selection`() {
        val onlyX01 = ProfileTestData.data(listOf(ProfileTestData.x01Raw()))
        val state = shown(onlyX01).sectionSelected(ProfileSection.CRICKET)
        assertEquals(ProfileView.CONTENT, state.view)
        assertTrue(state.isSelectionEmpty)
        assertFalse(state.sectionSelected(ProfileSection.X01).isSelectionEmpty)
        assertFalse(state.sectionSelected(ProfileSection.HEATMAP).isSelectionEmpty)
    }

    @Test
    fun `PS6 a failed read shows the failure, retry reads again and an older answer is ignored`() {
        val failed = ProfileState().loaded(1, DbResult.Failed)
        assertEquals(ProfileView.FAILED, failed.view)

        val retried = failed.retry(at = 9)
        assertEquals(ProfileView.LOADING, retried.view)
        assertEquals(2, retried.loadId)
        assertSame(retried, retried.loaded(1, DbResult.Ok(both)))
        assertEquals(ProfileView.LOADING, retried.loaded(2, DbResult.Ok(both)).view)
        val content = shown()
        assertSame(content, content.retry(at = 9))
    }

    @Test
    fun `PS3 a filter or period change computes again without reading again`() {
        val start = shown()
        val filtered = start.x01FilterSelected(X01Filter(startScore = 501), at = 7)
        assertEquals(start.loadId, filtered.loadId)
        assertEquals(start.figuresId + 1, filtered.figuresId)
        assertEquals(X01Filter(startScore = 501), filtered.x01Filter)
        assertEquals(7L, filtered.query.now)

        val computed = filtered.computed(filtered.figuresId, both.figures(filtered.query))
        assertEquals(0, computed.figures?.x01?.matchCount)
        assertEquals(1, computed.figures?.cricket?.matchCount)

        val period = start.cricketPeriodSelected(Period.LAST_10_MATCHES, at = 7)
        assertEquals(start.figuresId + 1, period.figuresId)
        assertEquals(Period.LAST_10_MATCHES, period.cricketPeriod)
        assertEquals(start.figuresId + 1, start.x01PeriodSelected(Period.LAST_30_DAYS, 7).figuresId)
        val cutThroat = CricketFilter(CricketVariant.CUT_THROAT)
        assertEquals(start.figuresId + 1, start.cricketFilterSelected(cutThroat, 7).figuresId)
    }

    @Test
    fun `PS3 selecting what is already selected changes nothing`() {
        val start = shown()
        assertSame(start, start.x01FilterSelected(X01Filter(), 1))
        assertSame(start, start.x01PeriodSelected(Period.SINCE_ALWAYS, 1))
        assertSame(start, start.cricketFilterSelected(CricketFilter(), 1))
        assertSame(start, start.cricketPeriodSelected(Period.SINCE_ALWAYS, 1))
    }

    @Test
    fun `an answer of an older computation is ignored`() {
        val start = shown()
        val changed = start.x01PeriodSelected(Period.LAST_10_MATCHES, 1)
        val stale = both.figures(start.query)
        assertSame(changed, changed.computed(start.figuresId, stale))
    }

    @Test
    fun `a selection made while loading is used by the first computation`() {
        val state = ProfileState().x01FilterSelected(X01Filter(checkoutRule = CheckoutRule.SINGLE_OUT), at = 3)
        assertEquals(0, state.figuresId)
        val loaded = state.loaded(1, DbResult.Ok(both))
        assertEquals(CheckoutRule.SINGLE_OUT, loaded.query.x01Filter.checkoutRule)
        assertEquals(1, loaded.figuresId)
    }

    @Test
    fun `ST9 the header says since always until a real reset`() {
        assertEquals(ProfileSince.Always, shown(ProfileTestData.data(emptyList())).since)
        val reset = ProfileTestData.data(emptyList(), resetAt = 4_000)
        assertEquals(ProfileSince.Date(4_000), shown(reset).since)
        // A merge gives the profile a reset date equal to its creation date when neither was really reset (PM4).
        val merged = ProfileTestData.data(emptyList(), resetAt = ProfileTestData.CREATED_AT)
        assertEquals(ProfileSince.Always, shown(merged).since)
        assertNull(ProfileState().since)
    }

    @Test
    fun `ST10 no ignored match gives no notice`() {
        val state = shown()
        assertEquals(0, state.ignoredMatches)
        assertFalse(state.isIgnoredNoticeShown)
    }

    @Test
    fun `ST10 two ignored matches give the notice with count 2 and the figures exclude them`() {
        val raws = listOf(
            ProfileTestData.x01Raw("a"),
            ProfileTestData.corruptRaw("bad1"),
            ProfileTestData.corruptRaw("bad2"),
        )
        val state = shown(ProfileTestData.data(raws))
        assertEquals(2, state.ignoredMatches)
        assertTrue(state.isIgnoredNoticeShown)
        assertEquals(1, state.figures?.eligibleCount)
        assertEquals(1, state.figures?.x01?.matchCount)
    }

    @Test
    fun `PS5 the trend summary in the state is the engine summary of the selection`() {
        val raws = listOf(
            ProfileTestData.x01Raw("a", startedAt = 2_000, endedAt = 3_000),
            ProfileTestData.x01Raw("b", startedAt = 4_000, endedAt = 5_000),
        )
        val data = ProfileTestData.data(raws)
        val x01 = shown(data).figures?.x01
        assertNotNull(x01)
        val expected = summarize(trendSeries(data.matches, data.profileId))
        assertEquals(expected, x01?.trendSummary)
        assertEquals(2, x01?.trendSummary?.count)
        assertEquals(3_000L, x01?.trendSummary?.firstDate)
        assertEquals(5_000L, x01?.trendSummary?.lastDate)
    }

    @Test
    fun `ST6 a figure that cannot be computed stays null for an empty selection`() {
        val state = shown().x01FilterSelected(X01Filter(startScore = 501), 1)
        val empty = state.computed(state.figuresId, both.figures(state.query)).figures?.x01
        assertEquals(0, empty?.matchCount)
        assertNull(empty?.figures?.average)
        assertEquals(0, empty?.trendSummary?.count)
    }
}
