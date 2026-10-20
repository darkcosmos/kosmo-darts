package io.github.darkcosmos.kosmodarts.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

/** Phrase variants: random, never the same twice in a row (docs/rules/announcements.md, AN26, E1). */
class PhraseVariantsTest {

    @Test
    fun `AN26 a fixed draw never gives the same variant twice in a row`() {
        val variants = PhraseVariants(FirstVariant)
        assertEquals(listOf(0, 1, 0, 1), List(4) { variants.next("next_turn", 4) })
    }

    @Test
    fun `AN26 each line has its own memory`() {
        val variants = PhraseVariants(FirstVariant)
        assertEquals(0, variants.next("game_start", 3))
        assertEquals(0, variants.next("next_turn", 4))
        assertEquals(1, variants.next("game_start", 3))
    }

    @Test
    fun `AN26 a line with one variant always gives it`() {
        val variants = PhraseVariants(FirstVariant)
        assertEquals(listOf(0, 0, 0), List(3) { variants.next("only", 1) })
    }

    @Test
    fun `AN26 a seeded random draw over many turns never repeats twice in a row and uses every variant`() {
        val variants = PhraseVariants(RandomVariantPicker(Random(SEED)))
        val drawn = List(1_000) { variants.next("next_turn", 4) }
        drawn.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
        assertEquals(setOf(0, 1, 2, 3), drawn.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a picker that chooses a forbidden variant is rejected`() {
        val variants = PhraseVariants { 0 }
        variants.next("next_turn", 4)
        variants.next("next_turn", 4)
    }

    private companion object {
        const val SEED = 32
    }
}
