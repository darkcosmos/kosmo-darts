package io.github.darkcosmos.kosmodarts.engine.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ColorChoicesTest {
    private val blue = PlayerColor.BLUE
    private val orange = PlayerColor.ORANGE
    private val teal = PlayerColor.TEAL
    private val lilac = PlayerColor.LILAC

    /** A generated-looking color just off [base], within MIN_COLOR_DISTANCE of it. */
    private fun nearOf(base: PlayerColor): PlayerColor {
        val tweaked = PlayerColor(base.argb - 0x010101)
        assertTrue(PlayerColorRules.distance(base, tweaked) < PlayerColorRules.MIN_COLOR_DISTANCE)
        return tweaked
    }

    private fun assertConforming(swatch: PlayerColor, others: List<PlayerColor>) {
        assertTrue(PlayerColorRules.isAcceptable(swatch, others), "$swatch against $others")
    }

    @Test
    fun `PC2 empty gives blue`() {
        assertEquals(blue, ColorChoices.defaultFor(emptyList()))
    }

    @Test
    fun `PC2 blue and teal taken gives orange`() {
        assertEquals(orange, ColorChoices.defaultFor(listOf(blue, teal)))
    }

    @Test
    fun `PC2 skips a palette color close to a generated color of another player`() {
        assertEquals(teal, ColorChoices.defaultFor(listOf(blue, nearOf(orange))))
    }

    @Test
    fun `PC2 generates an acceptable color when every free palette color is too close`() {
        val taken = listOf(nearOf(blue), nearOf(orange), nearOf(teal), nearOf(lilac))
        val color = ColorChoices.defaultFor(taken, Random(1))
        assertFalse(color in PlayerColor.PALETTE)
        assertConforming(color, taken)
    }

    @Test
    fun `PC14 a palette color close to another player's generated color is not offered`() {
        val near = nearOf(orange)
        val choices = ColorChoices.choicesFor(0, listOf(blue, near), Random(2))
        assertFalse(orange in choices)
        assertEquals(blue, choices.first())
    }

    @Test
    fun `PC14 PC15 own color first, none of the others, at least 4 swatches`() {
        for (count in 1..4) {
            val colors = PlayerColor.PALETTE.take(count)
            val choices = ColorChoices.choicesFor(0, colors, Random(count))
            val others = colors.drop(1)
            assertEquals(colors[0], choices.first())
            assertTrue(choices.size >= PlayerColorRules.MIN_CHOICES)
            assertEquals(choices.size, choices.toSet().size)
            choices.drop(1).forEach {
                assertFalse(it in others)
                assertConforming(it, others)
            }
        }
    }

    @Test
    fun `PC15 200 seeds with 4 players always reach MIN_CHOICES with conforming swatches`() {
        val colors = PlayerColor.PALETTE
        for (seed in 0 until 200) {
            for (index in colors.indices) {
                val choices = ColorChoices.choicesFor(index, colors, Random(seed))
                val others = colors.filterIndexed { i, _ -> i != index }
                assertTrue(choices.size >= PlayerColorRules.MIN_CHOICES, "seed $seed index $index")
                assertEquals(colors[index], choices.first())
                choices.drop(1).forEach { assertConforming(it, others) }
                for (a in choices.indices) {
                    for (b in a + 1 until choices.size) {
                        assertNotEquals(choices[a], choices[b])
                    }
                }
            }
        }
    }

    @Test
    fun `PC15 a constant random gives fewer swatches, only his own color, never a duplicate`() {
        var draws = 0
        val constant = object : Random() {
            override fun nextBits(bitCount: Int): Int {
                draws++
                return 0
            }
        }
        val colors = PlayerColor.PALETTE
        val choices = ColorChoices.choicesFor(0, colors, constant)
        assertEquals(choices.size, choices.toSet().size)
        // Only his own color: the constant candidate is never acceptable, so every attempt fails.
        assertEquals(1, choices.size)
        // Three doubles per attempt, two bit draws each, at most the attempts of one generation.
        assertTrue(draws <= 3 * 2 * PlayerColorRules.MAX_GENERATION_ATTEMPTS)
        choices.drop(1).forEach { assertConforming(it, colors.drop(1)) }
    }
}
