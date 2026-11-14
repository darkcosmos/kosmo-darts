package io.github.darkcosmos.kosmodarts.ui.components

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Test

class SwatchNameTest {
    @Test
    fun `PC14 a palette color is named by its color`() {
        PlayerColor.PALETTE.forEachIndexed { index, color ->
            assertEquals(SwatchName.Palette(color), swatchName(color, index + 1, 6))
        }
    }

    @Test
    fun `PC15 a generated color is told by its position among the swatches`() {
        assertEquals(SwatchName.Generated(5, 6), swatchName(PlayerColor(0xFF123456.toInt()), 5, 6))
    }
}
