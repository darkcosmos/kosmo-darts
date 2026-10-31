package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.ui.graphics.Color
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Contrast of the board buttons (docs/rules/cricket.md CR15, CR24): the muted label of a dead row and the "Miss" label
 * stay readable (WCAG 2, 4.5:1) on the key and on the board. Pure JVM, from the dark-theme tokens.
 */
class CricketBoardStyleTest {

    private val colors = KosmoColors.Default

    @Test
    fun `the tokens are the ones the contrast is computed from`() {
        assertEquals(Color(0xFFAEB8D6), colors.onBackgroundMuted)
        assertEquals(Color(0xFF18213F), colors.surfaceHigh)
        assertEquals(Color(0xFFFFFFFF), colors.onBackground)
    }

    @Test
    fun `CR15 the muted label of a dead row reads on the key`() {
        assertTrue(ratio(colors.onBackgroundMuted, colors.surfaceHigh) >= TEXT_RATIO)
    }

    @Test
    fun `CR24 the muted Miss label reads on the board background`() {
        assertTrue(ratio(colors.onBackgroundMuted, colors.surface) >= TEXT_RATIO)
        assertTrue(ratio(colors.onBackgroundMuted, colors.background) >= TEXT_RATIO)
    }

    @Test
    fun `CR24 the normal label reads on the key`() {
        assertTrue(ratio(colors.onBackground, colors.surfaceHigh) >= TEXT_RATIO)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double =
            if (value <= LINEAR_THRESHOLD) value / LINEAR_DIVISOR else ((value + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        const val TEXT_RATIO = 4.5
        const val LINEAR_THRESHOLD = 0.03928f
        const val LINEAR_DIVISOR = 12.92
    }
}
