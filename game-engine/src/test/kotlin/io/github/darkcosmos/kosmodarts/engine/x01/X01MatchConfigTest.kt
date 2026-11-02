package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.InvalidPlayerColorsException
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.BLUE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.LILAC
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.ORANGE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.TEAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** PC1, PC5 (docs/rules/players.md): the colors of a match configuration. */
class X01MatchConfigTest {

    private val three = X01Config(listOf("Ann", "Bob", "Cid"))

    /** PC5: without colors, each player gets the palette color of his position. */
    @Test
    fun `PC5 default colors follow the positions`() {
        assertEquals(listOf(BLUE, ORANGE, TEAL), X01MatchConfig(three).colors)
    }

    /** PC5: any distinct colors, one per player, are accepted. */
    @Test
    fun `PC5 distinct colors in any order are accepted`() {
        val config = X01MatchConfig(three, MatchFormat(), listOf(LILAC, BLUE, ORANGE))

        assertEquals(listOf(LILAC, BLUE, ORANGE), config.colors)
    }

    /** PC1, PC5: a color outside the palette is accepted when each player has his own. */
    @Test
    fun `PC5 a color outside the palette is accepted`() {
        val generated = PlayerColor(0xFFF2D94E.toInt())

        val config = X01MatchConfig(three, MatchFormat(), listOf(generated, BLUE, ORANGE))

        assertEquals(listOf(generated, BLUE, ORANGE), config.colors)
    }

    /** PC5: too few or too many colors are rejected. */
    @Test
    fun `PC5 wrong color count is rejected`() {
        assertThrows<InvalidPlayerColorsException> { X01MatchConfig(three, MatchFormat(), listOf(BLUE, ORANGE)) }
        assertThrows<InvalidPlayerColorsException> {
            X01MatchConfig(three, MatchFormat(), listOf(BLUE, ORANGE, TEAL, LILAC))
        }
    }

    /** PC1, PC5: a repeated color is rejected. */
    @Test
    fun `PC5 repeated colors are rejected`() {
        assertThrows<InvalidPlayerColorsException> {
            X01MatchConfig(three, MatchFormat(), listOf(BLUE, TEAL, BLUE))
        }
    }
}
