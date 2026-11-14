package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Heatmap
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.StatsFilter
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HM2, HM3, HM4, HM6, HM7, ST10: the Heatmap section state, without Android and without a database. */
class HeatmapSectionStateTest {
    private val both = ProfileTestData.data(listOf(ProfileTestData.x01Raw("a"), ProfileTestData.cricketRaw("b")))
    private val t20 = BoardZone(20, Ring.TRIPLE)
    private val t19 = BoardZone(19, Ring.TRIPLE)
    private val d2 = BoardZone(2, Ring.DOUBLE)

    private fun shown(data: ProfileStatsData = both, from: ProfileState = ProfileState()): ProfileState {
        val loaded = from.loaded(from.loadId, DbResult.Ok(data))
        return loaded.computed(loaded.figuresId, data.figures(loaded.query))
    }

    private fun ProfileState.select(selections: HeatmapSelections): ProfileState {
        val changed = heatmapSelected(selections, at = 5)
        return changed.computed(changed.figuresId, (content as ProfileContent.Loaded).data.figures(changed.query))
    }

    private fun section(state: ProfileState) = checkNotNull(state.heatmap)

    private fun figuresOf(counts: Map<BoardZone, Int>, misses: Int, isCricket: Boolean = false) =
        HeatmapFigures(1, Heatmap(counts, misses, counts.values.sum() + misses, isCricket))

    @Test
    fun `HM3 mode All hides the mode filters and counts every mode`() {
        val state = shown()
        assertEquals(HeatmapMode.ALL, state.heatmapSelections.mode)
        assertFalse(state.heatmapSelections.showsModeFilters)
        assertEquals(StatsFilter.AllModes, state.heatmapSelections.statsFilter)
        assertEquals(2, state.figures?.heatmap?.matchCount)
        assertEquals(14, section(state).heatmap.total)
    }

    @Test
    fun `HM3 X01 and Cricket modes show their filters and keep the period when the mode changes`() {
        val state = shown().select(HeatmapSelections(period = Period.LAST_10_MATCHES))
        val x01 = state.select(state.heatmapSelections.copy(mode = HeatmapMode.X01))
        assertTrue(x01.heatmapSelections.showsModeFilters)
        assertEquals(Period.LAST_10_MATCHES, x01.heatmapSelections.period)
        assertEquals(1, x01.figures?.heatmap?.matchCount)
        assertEquals(6, section(x01).heatmap.total)
        val cricket = x01.select(x01.heatmapSelections.copy(mode = HeatmapMode.CRICKET))
        assertEquals(Period.LAST_10_MATCHES, cricket.heatmapSelections.period)
        assertEquals(8, section(cricket).heatmap.total)
    }

    @Test
    fun `HM3 the filter of each mode is kept when the mode changes and applies to its mode`() {
        val state = shown().select(HeatmapSelections(mode = HeatmapMode.X01, x01Filter = X01Filter(startScore = 501)))
        assertEquals(0, state.figures?.heatmap?.matchCount)
        val cricket = state.select(state.heatmapSelections.copy(mode = HeatmapMode.CRICKET))
        assertEquals(X01Filter(startScore = 501), cricket.heatmapSelections.x01Filter)
        assertEquals(1, cricket.figures?.heatmap?.matchCount)
        val cutThroat = cricket.heatmapSelections.copy(cricketFilter = CricketFilter(CricketVariant.CUT_THROAT))
        val cut = cricket.select(cutThroat)
        assertEquals(0, cut.figures?.heatmap?.matchCount)
    }

    @Test
    fun `HM4 the miss label is Miss for X01 only and the combined one as soon as Cricket darts count`() {
        val all = shown()
        assertEquals(R.string.heatmap_miss_cricket, section(all).missLabelRes)
        val x01 = all.select(all.heatmapSelections.copy(mode = HeatmapMode.X01))
        assertEquals(R.string.heatmap_miss, section(x01).missLabelRes)
        val cricket = all.select(all.heatmapSelections.copy(mode = HeatmapMode.CRICKET))
        assertEquals(R.string.heatmap_miss_cricket, section(cricket).missLabelRes)
        val x01Only = shown(ProfileTestData.data(listOf(ProfileTestData.x01Raw("a"))))
        assertEquals(R.string.heatmap_miss, section(x01Only).missLabelRes)
    }

    @Test
    fun `HM5 the shares for the colour scale are ratios of the zones with darts only`() {
        val shares = section(shown()).zoneShares
        assertTrue(shares.keys.containsAll(setOf(t20, t19)))
        assertTrue(shares.values.all { it > 0.0 && it <= 1.0 })
        assertEquals(section(shown()).heatmap.share(t20)!! / 100.0, shares.getValue(t20), DELTA)
        assertTrue(HeatmapSectionState(HeatmapSelections(), HeatmapFigures.EMPTY).zoneShares.isEmpty())
    }

    @Test
    fun `HM6 nothing is selected at open and the band asks to touch a zone`() {
        val state = shown()
        assertNull(section(state).selected)
        assertEquals(R.string.heatmap_band_none, HeatmapSectionState.BAND_NONE_RES)
    }

    @Test
    fun `HM6 a tap selects a zone, a second tap on another replaces it`() {
        val first = shown().heatmapZoneTapped(t20)
        assertEquals(t20, section(first).selected?.zone)
        assertEquals(5, section(first).selected?.count)
        val second = first.heatmapZoneTapped(t19)
        assertEquals(t19, section(second).selected?.zone)
        assertEquals(2, section(second).selected?.count)
    }

    @Test
    fun `HM6 a filter change keeps the zone selected with its new values`() {
        val tapped = shown().heatmapZoneTapped(t20)
        val x01 = tapped.select(tapped.heatmapSelections.copy(mode = HeatmapMode.X01))
        val selected = checkNotNull(section(x01).selected)
        assertEquals(t20, selected.zone)
        assertEquals(4, selected.count)
        assertEquals(4 * 100.0 / 6, selected.percent, DELTA)
    }

    @Test
    fun `HM6 a selected zone that falls to 0 darts goes back to the nothing selected state`() {
        val tapped = shown().heatmapZoneTapped(BoardZone(18, Ring.TRIPLE))
        assertEquals(1, section(tapped).selected?.count)
        val x01 = tapped.select(tapped.heatmapSelections.copy(mode = HeatmapMode.X01))
        assertNull(x01.heatmapSelections.selectedZone)
        assertNull(section(x01).selected)
    }

    @Test
    fun `HM6 a zone change alone does not ask for new figures`() {
        val state = shown()
        assertEquals(state.figuresId, state.heatmapZoneTapped(t20).figuresId)
        val zoneOnly = state.heatmapSelected(state.heatmapSelections.copy(selectedZone = t19), 9)
        assertEquals(state.figuresId, zoneOnly.figuresId)
        val period = state.heatmapSelected(state.heatmapSelections.copy(period = Period.LAST_30_DAYS), 9)
        assertEquals(state.figuresId + 1, period.figuresId)
    }

    @Test
    fun `HM6 top 5 orders by count then by zone order with exact percentages, misses in the total`() {
        val zones = mapOf(
            t20 to 5,
            t19 to 2,
            BoardZone(15, Ring.TRIPLE) to 1,
            d2 to 1,
            BoardZone(16, Ring.TRIPLE) to 1,
            BoardZone(18, Ring.TRIPLE) to 1,
        )
        val top = HeatmapSectionState(HeatmapSelections(), figuresOf(zones, misses = 6)).topZones
        // Total 11 + 6 misses = 17; D2 comes before T15, T16 on the tie (zone order: number, then ring).
        assertEquals(listOf(t20, t19, d2, BoardZone(15, Ring.TRIPLE), BoardZone(16, Ring.TRIPLE)), top.map { it.zone })
        assertEquals(listOf(5, 2, 1, 1, 1), top.map { it.count })
        assertEquals(5 * 100.0 / 17, top[0].percent, DELTA)
        assertEquals(2 * 100.0 / 17, top[1].percent, DELTA)
        assertEquals(100.0 / 17, top[2].percent, DELTA)
        assertEquals(6 * 100.0 / 17, HeatmapSectionState(HeatmapSelections(), figuresOf(zones, 6)).missPercent!!, DELTA)
    }

    @Test
    fun `HM6 fewer than 5 zones hit give fewer rows`() {
        val state = shown(ProfileTestData.data(listOf(ProfileTestData.x01Raw("a"))))
        assertEquals(listOf(t20, d2, t19), section(state).topZones.map { it.zone })
    }

    @Test
    fun `HM6 the TalkBack text of a zone has its name, count and percentage`() {
        val figure = section(shown()).figureOf(t20)
        assertEquals(ZoneName(R.string.heatmap_zone_triple, 20), figure.name)
        assertEquals(5, figure.count)
        assertEquals(5 * 100.0 / 14, figure.percent, DELTA)
        val empty = section(shown()).figureOf(BoardZone(1, Ring.SINGLE))
        assertEquals(ZoneName(R.string.heatmap_zone_single, 1), empty.name)
        assertEquals(0, empty.count)
        assertEquals(0.0, empty.percent, DELTA)
        assertEquals(ZoneName(R.string.heatmap_zone_bull), BoardZone(Dart.BULL_SEGMENT, Ring.BULL).displayName())
        val outer = BoardZone(Dart.OUTER_BULL_SEGMENT, Ring.OUTER_BULL)
        assertEquals(ZoneName(R.string.heatmap_zone_outer_bull), outer.displayName())
        assertEquals(ZoneName(R.string.heatmap_zone_double, 2), d2.displayName())
    }

    @Test
    fun `HM7 a selection without a match or a dart gives the empty state`() {
        val state = shown().select(HeatmapSelections(mode = HeatmapMode.X01, x01Filter = X01Filter(startScore = 501)))
        assertTrue(section(state).isEmpty)
        val heatmapShown = state.sectionSelected(ProfileSection.HEATMAP)
        assertTrue(heatmapShown.isSelectionEmpty)
        assertNull(section(heatmapShown).selected)
        assertTrue(section(heatmapShown).topZones.isEmpty())
        assertNull(section(heatmapShown).missPercent)
        // A selected zone of an empty selection shows as nothing selected.
        assertNull(section(heatmapShown.heatmapZoneTapped(t20)).selected)
    }

    @Test
    fun `ST10 the ignored matches of the Profile are also left out of the heatmap`() {
        val raws = listOf(
            ProfileTestData.x01Raw("a"),
            ProfileTestData.corruptRaw("bad1"),
            ProfileTestData.corruptRaw("bad2"),
        )
        val state = shown(ProfileTestData.data(raws))
        assertEquals(2, state.ignoredMatches)
        assertTrue(state.isIgnoredNoticeShown)
        assertEquals(1, state.figures?.heatmap?.matchCount)
        assertEquals(6, section(state).heatmap.total)
    }

    private companion object {
        const val DELTA = 1e-9
    }
}
