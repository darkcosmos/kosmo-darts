package io.github.darkcosmos.kosmodarts.ui.format

import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.stats.Heatmap
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapFigures
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapSectionState
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapSelections
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** HM5, HM6: display of a share, converted from a ratio exactly once. */
class PercentFormatTest {

    @Test
    fun `HM5 one decimal at most with the separator of the language`() {
        assertEquals("4.7 %", formatPercent(0.0467, Locale.ENGLISH))
        assertEquals("4,7 %", formatPercent(0.0467, Locale.FRENCH))
        assertEquals("30 %", formatPercent(0.30, Locale.ENGLISH))
        assertEquals("100 %", formatPercent(1.0, Locale.ENGLISH))
        assertEquals("0 %", formatPercent(0.0, Locale.ENGLISH))
    }

    @Test
    fun `HM5 rounding is to the tenth`() {
        assertEquals("12.5 %", formatPercent(0.125, Locale.ENGLISH))
        assertEquals("33.3 %", formatPercent(1.0 / 3, Locale.ENGLISH))
        assertEquals("0.1 %", formatPercent(0.0005, Locale.ENGLISH))
    }

    @Test
    fun `HM6 a whole percentage has no decimal`() {
        assertEquals("45 %", formatWholePercent(0.4545, Locale.ENGLISH))
    }

    @Test
    fun `HM2 HM6 the share goes from the engine to the display with one conversion`() {
        val t20 = BoardZone(20, BoardZone.Ring.TRIPLE)
        val heatmap = Heatmap(mapOf(t20 to 1), misses = 3, total = 4, includesCricket = false)
        val state = HeatmapSectionState(HeatmapSelections(), HeatmapFigures(1, heatmap))
        assertEquals("25 %", formatPercent(state.figureOf(t20).share, Locale.ENGLISH))
        assertEquals("25 %", formatPercent(state.topZones.single().share, Locale.ENGLISH))
        assertEquals("75 %", formatPercent(state.missShare!!, Locale.ENGLISH))
    }
}
