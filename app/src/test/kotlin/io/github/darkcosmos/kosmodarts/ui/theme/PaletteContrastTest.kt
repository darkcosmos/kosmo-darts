package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.Color
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Player colors (docs/rules/players.md, PC9; T4), contrast of the tokens (design system, sections 2 and 8; U8) and the
 * only animation (section 7). Pure JVM: WCAG 2 relative luminance.
 */
class PaletteContrastTest {

    private val colors = KosmoColors.Default

    @Test
    fun `PC9 slot n is playerN with onPlayerN`() {
        assertEquals(colors.player1, PlayerPalette.colorOf(ColorSlot.SLOT_1))
        assertEquals(colors.player2, PlayerPalette.colorOf(ColorSlot.SLOT_2))
        assertEquals(colors.player3, PlayerPalette.colorOf(ColorSlot.SLOT_3))
        assertEquals(colors.player4, PlayerPalette.colorOf(ColorSlot.SLOT_4))
        assertEquals(colors.onPlayer1, PlayerPalette.onColorOf(ColorSlot.SLOT_1))
        assertEquals(colors.onPlayer2, PlayerPalette.onColorOf(ColorSlot.SLOT_2))
        assertEquals(colors.onPlayer3, PlayerPalette.onColorOf(ColorSlot.SLOT_3))
        assertEquals(colors.onPlayer4, PlayerPalette.onColorOf(ColorSlot.SLOT_4))
        // Four distinct colors.
        assertEquals(4, ColorSlot.entries.map { PlayerPalette.colorOf(it) }.toSet().size)
    }

    /** Text pairs: at least 4.5:1 (section 8). */
    @Test
    fun `text pairs reach 4,5 to 1`() {
        val pairs = ColorSlot.entries.map { PlayerPalette.onColorOf(it) to PlayerPalette.colorOf(it) } + listOf(
            colors.onAccent to colors.accent,
            colors.onDanger to colors.danger,
            colors.onBackgroundMuted to colors.surface,
            colors.onBackground to colors.surfaceHigh,
            colors.onBackground to colors.danger,
            colors.onBackground to colors.background,
            colors.accent to colors.background,
        )
        pairs.forEach { (text, background) ->
            assertTrue("$text on $background: ${ratio(text, background)}", ratio(text, background) >= TEXT_RATIO)
        }
    }

    /**
     * Non-text indicators, at least 3:1 (U8): the active Double/Triple key (accent) against the inactive one and the
     * background, and the color strips and markers against the background they sit on.
     */
    @Test
    fun `U8 indicators reach 3 to 1`() {
        val pairs = listOf(
            colors.accent to colors.surfaceHigh,
            colors.accent to colors.background,
        ) + ColorSlot.entries.flatMap { slot ->
            listOf(PlayerPalette.colorOf(slot) to colors.background)
        }
        pairs.forEach { (indicator, background) ->
            assertTrue(
                "$indicator on $background: ${ratio(indicator, background)}",
                ratio(indicator, background) >= INDICATOR_RATIO,
            )
        }
    }

    @Test
    fun `section 7 the zone fade is 200 ms, or a snap when animations are off`() {
        val fade = zoneColorAnimationSpec(enabled = true)
        assertTrue(fade is TweenSpec)
        assertEquals(ZONE_FADE_MILLIS, (fade as TweenSpec).durationMillis)
        assertTrue(zoneColorAnimationSpec(enabled = false) is SnapSpec)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double =
            if (value <= 0.03928f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        const val TEXT_RATIO = 4.5
        const val INDICATOR_RATIO = 3.0
    }
}
