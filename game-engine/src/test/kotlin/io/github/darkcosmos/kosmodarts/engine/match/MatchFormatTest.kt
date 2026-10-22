package io.github.darkcosmos.kosmodarts.engine.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** M1, M5, M12 (docs/rules/match.md). */
class MatchFormatTest {

    /** M1: the default format is 1 leg and 1 set, a single game (M5). */
    @Test
    fun `M1 M5 default format is a single game`() {
        val format = MatchFormat()

        assertEquals(1, format.legsPerSet)
        assertEquals(1, format.sets)
        assertTrue(format.isSingleGame)
        assertFalse(format.hasSets)
    }

    /** M1: 1 and 9 are accepted for both values. */
    @Test
    fun `M1 bounds are accepted`() {
        listOf(MatchFormat.MIN, MatchFormat.MAX).forEach { legs ->
            listOf(MatchFormat.MIN, MatchFormat.MAX).forEach { sets ->
                val format = MatchFormat(legs, sets)
                assertEquals(legs, format.legsPerSet)
                assertEquals(sets, format.sets)
            }
        }
        assertEquals(1, MatchFormat.MIN)
        assertEquals(9, MatchFormat.MAX)
    }

    /** M1: 0 and 10 are rejected for both values, with an explicit error. */
    @Test
    fun `M1 values outside 1 to 9 are rejected`() {
        listOf(0, 10, -1).forEach { bad ->
            assertThrows<InvalidMatchFormatException> { MatchFormat(legsPerSet = bad) }
            assertThrows<InvalidMatchFormatException> { MatchFormat(sets = bad) }
        }
    }

    /** M14: only a format with more than one set has sets; any other format than 1 and 1 is a match. */
    @Test
    fun `M1 single game and sets flags`() {
        assertFalse(MatchFormat(3, 1).isSingleGame)
        assertFalse(MatchFormat(3, 1).hasSets)
        assertFalse(MatchFormat(1, 2).isSingleGame)
        assertTrue(MatchFormat(1, 2).hasSets)
    }

    /** M12: (n(L-1)+1) x (n(S-1)+1) legs at most. */
    @Test
    fun `M12 maximum number of legs`() {
        assertEquals(1, MatchFormat().maxLegs(4))
        assertEquals(81, MatchFormat(9, 9).maxLegs(1))
        assertEquals(5, MatchFormat(3, 1).maxLegs(2))
        assertEquals(9, MatchFormat(2, 2).maxLegs(2))
        assertEquals(1_089, MatchFormat(9, 9).maxLegs(4))
    }
}
