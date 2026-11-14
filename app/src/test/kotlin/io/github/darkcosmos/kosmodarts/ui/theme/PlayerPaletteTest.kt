package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PlayerPalette] maps an engine [PlayerColor] to Compose (docs/rules/players.md, PC9, PC13): the four palette colors
 * draw exactly as the former tokens `playerN` and `onPlayerN`, so the swap from slots to colors changes no pixel.
 */
class PlayerPaletteTest {

    private val tokens = KosmoColors.Default

    private val playerTokens = listOf(tokens.player1, tokens.player2, tokens.player3, tokens.player4)

    private val onPlayerTokens = listOf(tokens.onPlayer1, tokens.onPlayer2, tokens.onPlayer3, tokens.onPlayer4)

    @Test
    fun `PC9 each palette color is drawn as its playerN token`() {
        PlayerColor.PALETTE.forEachIndexed { index, color ->
            assertEquals("player${index + 1}", playerTokens[index], PlayerPalette.colorOf(color))
        }
    }

    @Test
    fun `PC13 text on each palette color is its former onPlayerN token`() {
        PlayerColor.PALETTE.forEachIndexed { index, color ->
            assertEquals("onPlayer${index + 1}", onPlayerTokens[index], PlayerPalette.onColorOf(color))
        }
    }

    @Test
    fun `PC9 a color outside the palette is drawn with its own ARGB`() {
        val generated = PlayerColor(0xFFF2D94E.toInt())

        assertEquals(generated.argb, PlayerPalette.colorOf(generated).toArgb())
    }

    @Test
    fun `PC13 text is dark on a light color and white on a dark one`() {
        assertEquals(tokens.background, PlayerPalette.onColorOf(PlayerColor(0xFFF2D94E.toInt())))
        assertEquals(Color.White, PlayerPalette.onColorOf(PlayerColor(0xFF2A2F8C.toInt())))
    }
}
