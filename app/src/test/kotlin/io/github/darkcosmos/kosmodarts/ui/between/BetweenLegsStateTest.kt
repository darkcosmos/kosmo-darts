package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The between-legs screen model (docs/rules/match.md, M6, M14, M16; U7). */
class BetweenLegsStateTest {

    /** M16: after a leg: who won it, the legs of the set, no set column with one set, the next starter. */
    @Test
    fun `M16 after a leg with one set`() {
        val state = BetweenLegsState.of(match("Ann", "Bob", "Cid", legsPerSet = 3).confirmLegsWonBy(2))

        assertEquals("Cid", state.winnerName)
        assertFalse(state.isSetWon)
        assertFalse(state.hasSets)
        assertEquals(
            listOf(
                BetweenLegsState.Row("Ann", null, 0),
                BetweenLegsState.Row("Bob", null, 0),
                BetweenLegsState.Row("Cid", null, 1),
            ),
            state.rows,
        )
        assertEquals("Bob", state.nextStarterName)
    }

    /** M3, M16, U7: after a set: "Set won by", sets counted, legs of the new set back to 0. */
    @Test
    fun `M16 after a set`() {
        val state = BetweenLegsState.of(match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(1, 1))

        assertEquals("Bob", state.winnerName)
        assertTrue(state.isSetWon)
        assertTrue(state.hasSets)
        assertEquals(listOf(BetweenLegsState.Row("Ann", 0, 0), BetweenLegsState.Row("Bob", 1, 0)), state.rows)
        assertEquals("Ann", state.nextStarterName)
    }
}
