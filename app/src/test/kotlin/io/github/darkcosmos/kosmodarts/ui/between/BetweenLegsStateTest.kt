package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_1
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_3
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_4
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The between-legs screen model (docs/rules/match.md, M6, M14, M16, M19; docs/rules/players.md, PC6, PC9). */
class BetweenLegsStateTest {

    /** M16: after a leg: who won it, the legs of the set, no set column with one set, the next starter. */
    @Test
    fun `M16 after a leg with one set`() {
        val confirmed = match("Ann", "Bob", "Cid", legsPerSet = 3).confirmLegsWonBy(2)
        val state = BetweenLegsState.of(confirmed)

        assertEquals("Cid", state.winnerName)
        assertEquals(2, state.winnerIndex)
        assertFalse(state.isSetWon)
        assertFalse(state.hasSets)
        assertEquals(listOf(null, null, null), state.rows.map { it.setsWon })
        assertEquals(listOf(0, 0, 1), state.rows.map { it.legsWon })
        assertEquals(listOf(false, false, true), state.rows.map { it.isLegWinner })
        assertEquals(confirmed.playerStats.map { it.threeDartAverage }, state.rows.map { it.average })
        assertEquals("Bob", state.nextStarterName)
    }

    /** M3, M16: after a set: the set is won, sets counted, legs of the new set back to 0. */
    @Test
    fun `M16 after a set`() {
        val state = BetweenLegsState.of(match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(1, 1))

        assertEquals("Bob", state.winnerName)
        assertTrue(state.isSetWon)
        assertTrue(state.hasSets)
        assertEquals(listOf(0, 1), state.rows.map { it.setsWon })
        assertEquals(listOf(0, 0), state.rows.map { it.legsWon })
        assertEquals("Ann", state.nextStarterName)
    }

    /** M19: the title and the info bar read the engine's numbers: the leg just won, then the next one. */
    @Test
    fun `M19 positions of the leg won and of the next leg come from the engine`() {
        val confirmed = match("Ann", "Bob", legsPerSet = 3, sets = 2).confirmLegsWonBy(0, 1)
        val score = confirmed.score

        val state = BetweenLegsState.of(confirmed)

        assertEquals(MatchPosition(score.lastLegSetNumber, score.lastLegNumber), state.wonPosition)
        assertEquals(MatchPosition(1, 2), state.wonPosition)
        assertEquals(MatchPosition(score.currentSetNumber, score.currentLegNumber), state.nextPosition)
        assertEquals(MatchPosition(1, 3), state.nextPosition)
    }

    /** M14, M19: with one set, positions have no set number. */
    @Test
    fun `M19 legs only positions have no set number`() {
        val state = BetweenLegsState.of(match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(0))

        assertEquals(MatchPosition(null, 1), state.wonPosition)
        assertEquals(MatchPosition(null, 2), state.nextPosition)
    }

    /** PC6, PC9: the winner's zone and every row take the players' own slots. */
    @Test
    fun `PC9 rows and winner carry the color slots`() {
        val confirmed = match("Ann", "Bob", "Cid", legsPerSet = 2, colorSlots = listOf(SLOT_4, SLOT_1, SLOT_3))
            .confirmLegsWonBy(1)

        val state = BetweenLegsState.of(confirmed)

        assertEquals(SLOT_1, state.winnerSlot)
        assertEquals(listOf(SLOT_4, SLOT_1, SLOT_3), state.rows.map { it.slot })
    }

    /** The winning checkout is shown when known, and absent after a resume on this screen (not saved). */
    @Test
    fun `the winning checkout is shown only when known`() {
        val confirmed = match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)
        val checkout = CheckoutSummary(41, listOf(Dart.single(1), Dart.double(20)))

        assertEquals(checkout, BetweenLegsState.of(confirmed, checkout).checkout)
        assertNull(BetweenLegsState.of(confirmed).checkout)
    }
}
