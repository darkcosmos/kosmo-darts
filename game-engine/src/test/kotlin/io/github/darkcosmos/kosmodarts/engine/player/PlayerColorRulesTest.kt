package io.github.darkcosmos.kosmodarts.engine.player

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColorRules.BACKGROUND
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColorRules.TEXT_DARK
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColorRules.TEXT_LIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlayerColorRulesTest {
    private val black = PlayerColor(0xFF000000.toInt())

    @Test
    fun `WCAG white on black is 21 to 1`() {
        assertEquals(21.0, PlayerColorRules.contrastRatio(TEXT_LIGHT, black), 1e-9)
    }

    @Test
    fun `WCAG a color with itself is 1 to 1`() {
        assertEquals(1.0, PlayerColorRules.contrastRatio(PlayerColor.BLUE, PlayerColor.BLUE), 1e-9)
    }

    @Test
    fun `WCAG ratio is symmetric`() {
        assertEquals(
            PlayerColorRules.contrastRatio(PlayerColor.TEAL, TEXT_LIGHT),
            PlayerColorRules.contrastRatio(TEXT_LIGHT, PlayerColor.TEAL),
            1e-12,
        )
    }

    @Test
    fun `PC11 PC13 hold for every palette color`() {
        PlayerColor.PALETTE.forEach {
            assertTrue(PlayerColorRules.isReadable(it), "PC11 $it")
            assertTrue(PlayerColorRules.contrastRatio(it, PlayerColorRules.textOn(it)) >= 4.5, "PC13 $it")
        }
    }

    @Test
    fun `PC13 text is white on blue and teal and dark on orange and lilac`() {
        assertEquals(TEXT_LIGHT, PlayerColorRules.textOn(PlayerColor.BLUE))
        assertEquals(TEXT_LIGHT, PlayerColorRules.textOn(PlayerColor.TEAL))
        assertEquals(TEXT_DARK, PlayerColorRules.textOn(PlayerColor.ORANGE))
        assertEquals(TEXT_DARK, PlayerColorRules.textOn(PlayerColor.LILAC))
    }

    @Test
    fun `PC10 background and surface values`() {
        assertEquals(0xFF0A0F1E.toInt(), BACKGROUND.argb)
        assertEquals(0xFF121A33.toInt(), PlayerColorRules.SURFACE.argb)
    }

    @Test
    fun `PC12 the palette colors are pairwise distinct enough and acceptable`() {
        val palette = PlayerColor.PALETTE
        palette.forEach { a ->
            palette.filter { it != a }.forEach {
                assertTrue(PlayerColorRules.distance(a, it) >= PlayerColorRules.MIN_COLOR_DISTANCE, "$a $it")
            }
            assertTrue(PlayerColorRules.isAcceptable(a, palette.filter { it != a }))
        }
    }

    @Test
    fun `PC11 a color too dark is not acceptable`() {
        assertFalse(PlayerColorRules.isAcceptable(PlayerColor(0xFF101830.toInt()), emptyList()))
    }

    @Test
    fun `PC13 a mid-luminance color without a readable text is not acceptable`() {
        val gray = PlayerColor(0xFF777777.toInt())
        assertTrue(PlayerColorRules.isReadable(gray))
        assertFalse(PlayerColorRules.isAcceptable(gray, emptyList()))
    }

    @Test
    fun `PC12 a color too close to another player is not acceptable`() {
        assertFalse(PlayerColorRules.isAcceptable(PlayerColor.ORANGE, listOf(PlayerColor.ORANGE)))
    }

    @Test
    fun `fromStored with alpha 00 gives the opaque color`() {
        assertEquals(PlayerColor.BLUE, PlayerColor.fromStored(0x001F63D6))
    }

    @Test
    fun `PlayerColor rejects a non opaque value`() {
        assertThrows<IllegalArgumentException> { PlayerColor(0x001F63D6) }
    }
}
