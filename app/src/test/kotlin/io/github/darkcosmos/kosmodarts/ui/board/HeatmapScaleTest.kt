package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.ui.graphics.Color
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HM5 (relative scale chosen at gate G1), HM2, HM8, PC11: the ramp, the neutral colour and the mapping. */
class HeatmapScaleTest {

    private val background = Color(0xFF0A0F1E)

    private fun channel(value: Float): Double =
        if (value <= LINEAR_THRESHOLD) value / LINEAR_DIVISOR else ((value + OFFSET) / SCALE).toDouble().pow(GAMMA)

    private fun luminance(c: Color): Double =
        R_WEIGHT * channel(c.red) + G_WEIGHT * channel(c.green) + B_WEIGHT * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (high, low) = luminance(a).coerceAtLeast(luminance(b)) to luminance(a).coerceAtMost(luminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    @Test
    fun endsOfTheScaleAreTheFirstAndTheLastStop() {
        assertEquals(Color(0xFF6259BC), HeatmapScale.colorAt(0f))
        assertEquals(Color(0xFFFDE725), HeatmapScale.colorAt(1f))
        assertEquals(Color(0xFF22A884), HeatmapScale.colorAt(0.5f))
    }

    @Test
    fun theMostHitZoneIsTheLightestStopWhateverItsShare() {
        assertEquals(Color(0xFFFDE725), HeatmapScale.color(share = 0.047, maxShare = 0.047))
        assertEquals(Color(0xFFFDE725), HeatmapScale.color(share = 0.30, maxShare = 0.30))
        assertEquals(Color(0xFFFDE725), HeatmapScale.color(share = 1.0, maxShare = 1.0))
    }

    @Test
    fun smallestPositiveShareIsNearTheFirstStopAndDistinctFromNeutral() {
        val color = HeatmapScale.color(share = 1.0 / 150, maxShare = 45.0 / 150)
        assertNotEquals(HeatmapScale.NEUTRAL, color)
        assertTrue(contrast(color, HeatmapScale.STOPS.first()) < 1.1)
    }

    @Test
    fun zeroNullAndEmptyBoardGiveNeutral() {
        assertEquals(HeatmapScale.NEUTRAL, HeatmapScale.color(share = 0.0, maxShare = 0.3))
        assertEquals(HeatmapScale.NEUTRAL, HeatmapScale.color(share = null, maxShare = 0.3))
        assertEquals(HeatmapScale.NEUTRAL, HeatmapScale.color(share = 0.1, maxShare = 0.0))
    }

    @Test
    fun midShareIsInterpolatedBetweenTwoStops() {
        val color = HeatmapScale.color(share = 4.0 / 150, maxShare = 7.0 / 150)
        assertTrue(color.red in 0x22 / 255f..0x7A / 255f)
        assertEquals(HeatmapScale.colorAt((4.0 / 7.0).toFloat()), color)
    }

    @Test
    fun outOfRangePositionsAreClamped() {
        assertEquals(HeatmapScale.colorAt(0f), HeatmapScale.colorAt(-1f))
        assertEquals(HeatmapScale.colorAt(1f), HeatmapScale.colorAt(2f))
    }

    @Test
    fun luminanceIncreasesStrictlyAlongTheRampAndNeutralIsDarker() {
        val luminances = HeatmapScale.STOPS.map(::luminance)
        assertEquals(luminances.sorted(), luminances)
        assertEquals(luminances.size, luminances.toSet().size)
        assertTrue(luminance(HeatmapScale.NEUTRAL) < luminances.first())
        val samples = (0..STEPS).map { luminance(HeatmapScale.colorAt(it / STEPS.toFloat())) }
        assertEquals(samples.sorted(), samples)
    }

    @Test
    fun everyStopIsReadableOnTheBackground() {
        // Owner decision U1: every stop, the lowest one included, has at least 3:1 on the background, and so has every
        // colour of the interpolated scale (the luminance is increasing).
        HeatmapScale.STOPS.forEach { assertTrue(contrast(it, background) >= MIN_CONTRAST) }
        (0..STEPS).forEach {
            assertTrue(contrast(HeatmapScale.colorAt(it / STEPS.toFloat()), background) >= MIN_CONTRAST)
        }
    }

    @Test
    fun neutralIsDistinctFromTheFirstStop() {
        assertTrue(contrast(HeatmapScale.NEUTRAL, HeatmapScale.STOPS.first()) >= MIN_DISTINCT_CONTRAST)
    }

    @Test
    fun colorsKeepOnlyZonesWithDartsAndScaleToTheMax() {
        val triple20 = BoardZone(20, Ring.TRIPLE)
        val single20 = BoardZone(20, Ring.SINGLE)
        val colors = HeatmapScale.colors(mapOf(triple20 to 0.30, single20 to 0.15, BoardZone(1, Ring.SINGLE) to 0.0))
        assertEquals(setOf(triple20, single20), colors.keys)
        assertEquals(Color(0xFFFDE725), colors.getValue(triple20))
        assertEquals(HeatmapScale.colorAt(0.5f), colors.getValue(single20))
        assertTrue(HeatmapScale.colors(emptyMap()).isEmpty())
        assertEquals(0.0, HeatmapScale.maxShare(emptyMap()), 0.0)
    }

    private companion object {
        const val STEPS = 100
        const val MIN_CONTRAST = 3.0
        const val MIN_DISTINCT_CONTRAST = 2.0
        const val LINEAR_THRESHOLD = 0.03928f
        const val LINEAR_DIVISOR = 12.92
        const val OFFSET = 0.055f
        const val SCALE = 1.055f
        const val GAMMA = 2.4
        const val R_WEIGHT = 0.2126
        const val G_WEIGHT = 0.7152
        const val B_WEIGHT = 0.0722
    }
}
