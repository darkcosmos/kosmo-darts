package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone

/**
 * HM5, gate G1 (owner Kosmo, 2026-11): the heatmap colour scale is RELATIVE to the most hit zone of the board.
 * The value of a zone is its share of all the darts (HM2); its colour is given by `share / maxShare`, so the most
 * hit zone of each board gets the lightest colour. A zone with no dart gets [NEUTRAL], distinct from every step of
 * the ramp. The ramp has a strictly increasing luminance (readable in greyscale) and every step, the lowest included,
 * has at least 3:1 contrast on the background (owner decision U1); the tokens are in
 * `docs/design/design-system.md` section 2. The result feeds `Dartboard(colors, neutral, ...)`.
 */
object HeatmapScale {
    /** Colour of a zone with no dart (and of every zone without data); its frame in the legend is [NEUTRAL_FRAME]. */
    val NEUTRAL = Color(0xFF252B3F)
    val NEUTRAL_FRAME = Color(0xFF5C6580)

    /** The ramp, from the lowest positive value (0 of the scale) to the most hit zone (1), at even positions. */
    val STOPS: List<Color> = listOf(
        Color(0xFF6259BC),
        Color(0xFF2F85A0),
        Color(0xFF22A884),
        Color(0xFF7AD151),
        Color(0xFFFDE725),
    )

    /** Colour at [position] of the scale, from 0 (lowest) to 1 (highest); out of range values are clamped. */
    fun colorAt(position: Float): Color {
        val scaled = position.coerceIn(0f, 1f) * (STOPS.size - 1)
        val lower = scaled.toInt().coerceAtMost(STOPS.size - 2)
        return lerp(STOPS[lower], STOPS[lower + 1], scaled - lower)
    }

    /**
     * Colour of a zone holding [share] of the darts (0 to 1) when the most hit zone holds [maxShare]: [NEUTRAL] for
     * null, zero or a board without any dart, the ramp colour of `share / maxShare` otherwise.
     */
    fun color(share: Double?, maxShare: Double): Color =
        if (share == null || share <= 0.0 || maxShare <= 0.0) NEUTRAL else colorAt((share / maxShare).toFloat())

    /** The most hit zone's share of [shares], 0 for an empty map: the upper end of the legend. */
    fun maxShare(shares: Map<BoardZone, Double>): Double = shares.values.maxOrNull() ?: 0.0

    /** The colours of every zone with at least one dart; the others are drawn with [NEUTRAL] by the board. */
    fun colors(shares: Map<BoardZone, Double>): Map<BoardZone, Color> {
        val max = maxShare(shares)
        return shares.filterValues { it > 0.0 }.mapValues { color(it.value, max) }
    }
}
