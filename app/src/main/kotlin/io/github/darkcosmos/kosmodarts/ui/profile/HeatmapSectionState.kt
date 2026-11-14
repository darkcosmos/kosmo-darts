package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.annotation.StringRes
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Heatmap
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.StatsFilter
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter

/** The mode filter of the Heatmap section (HM3): every mode, X01 only or Cricket only. */
enum class HeatmapMode { ALL, X01, CRICKET }

/**
 * What the user chose in the Heatmap section (HM3, ST3, ST4): the mode, the filters of each mode (kept when the mode
 * changes), the period shared by the modes, and the zone shown in the band (HM6), if any.
 */
data class HeatmapSelections(
    val mode: HeatmapMode = HeatmapMode.ALL,
    val x01Filter: X01Filter = X01Filter(),
    val cricketFilter: CricketFilter = CricketFilter(),
    val period: Period = Period.SINCE_ALWAYS,
    val selectedZone: BoardZone? = null,
) {
    /** The filter the engine applies for the chosen mode: no filter of its own for All modes (HM3). */
    val statsFilter: StatsFilter
        get() = when (mode) {
            HeatmapMode.ALL -> StatsFilter.AllModes
            HeatmapMode.X01 -> x01Filter
            HeatmapMode.CRICKET -> cricketFilter
        }

    /** Whether the mode filters show: none for All modes (HM3). */
    val showsModeFilters: Boolean
        get() = mode != HeatmapMode.ALL
}

/** The heatmap of one selection, with the number of matches it comes from (HM2, HM3, ST2). */
data class HeatmapFigures(val matchCount: Int, val heatmap: Heatmap) {
    companion object {
        val EMPTY: HeatmapFigures = HeatmapFigures(0, Heatmap.EMPTY)
    }
}

/** The name of a zone as a string resource and its argument, so that the screen builds the text (HM6, PS8). */
data class ZoneName(@param:StringRes val resId: Int, val number: Int? = null)

/** The name of this zone: "Triple 20", "Single 5", "Outer bull (25)", "Bull". */
fun BoardZone.displayName(): ZoneName = when (ring) {
    BoardZone.Ring.SINGLE -> ZoneName(R.string.heatmap_zone_single, number)
    BoardZone.Ring.DOUBLE -> ZoneName(R.string.heatmap_zone_double, number)
    BoardZone.Ring.TRIPLE -> ZoneName(R.string.heatmap_zone_triple, number)
    BoardZone.Ring.OUTER_BULL -> ZoneName(R.string.heatmap_zone_outer_bull)
    BoardZone.Ring.BULL -> ZoneName(R.string.heatmap_zone_bull)
}

/** A zone with its darts and its percentage of all darts, misses included (HM2). [percent] is 0 to 100, unrounded. */
data class ZoneFigure(val zone: BoardZone, val name: ZoneName, val count: Int, val percent: Double)

/**
 * The Heatmap section as the screen shows it (HM2 to HM7), all computed here. Pure. A selected zone with no dart left
 * after a filter change is dropped by [ProfileState], so [selected] only is `null` when nothing is selected.
 */
data class HeatmapSectionState(val selections: HeatmapSelections, val figures: HeatmapFigures) {
    val heatmap: Heatmap
        get() = figures.heatmap

    /** HM7: no dart in the selection, which asks for the explicit empty state. */
    val isEmpty: Boolean
        get() = heatmap.total == 0

    /** HM4: the miss label is the combined one as soon as Cricket darts are counted. */
    @get:StringRes
    val missLabelRes: Int
        get() = if (heatmap.includesCricket) R.string.heatmap_miss_cricket else R.string.heatmap_miss

    /** HM2: the misses over all darts, 0 to 100, or `null` without a dart. */
    val missPercent: Double?
        get() = heatmap.missShare

    /** HM6: the five most hit zones, most first; ties follow the order of the board zones. */
    val topZones: List<ZoneFigure>
        get() = heatmap.topZones().map { figureOf(it.zone) }

    /** HM6: the zone of the band, or `null` for the "Touchez une zone" text ([BAND_NONE_RES]). */
    val selected: ZoneFigure?
        get() = selections.selectedZone?.takeIf { !isEmpty }?.let(::figureOf)

    /** HM6, PS8: the text of any zone of the board, for TalkBack: name, darts and percentage. */
    fun figureOf(zone: BoardZone): ZoneFigure =
        ZoneFigure(zone, zone.displayName(), heatmap.counts[zone] ?: 0, heatmap.share(zone) ?: 0.0)

    companion object {
        /** HM6: the band text when no zone is selected. */
        @StringRes
        val BAND_NONE_RES: Int = R.string.heatmap_band_none
    }
}
