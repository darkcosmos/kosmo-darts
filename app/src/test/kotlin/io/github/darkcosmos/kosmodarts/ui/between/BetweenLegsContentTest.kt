package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_2
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonByScoring20
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The between-legs model of both modes (docs/rules/match.md, M16; docs/rules/cricket.md, CR26). */
class BetweenLegsContentTest {

    /** M16: the X01 model keeps its figures (average, no points) and gains only the mode and the starter's color. */
    @Test
    fun `M16 the X01 mapper keeps the average and shows no points`() {
        val confirmed = MatchFixtures.match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(1)
        val state = BetweenLegsState.of(confirmed)

        assertEquals(GameMode.X01, state.mode)
        assertNull(state.winnerPoints)
        assertEquals(confirmed.playerStats.map { it.threeDartAverage }, state.rows.map { it.average })
        assertEquals("Bob", state.nextStarterName)
        assertEquals(confirmed.colorSlots[1], state.nextStarterSlot)
    }

    /** CR26: the winner's points of the leg that just ended, and the marks per round of the match. */
    @Test
    fun `CR26 the Cricket mapper gives the winner points and the MPR`() {
        val confirmed = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 3)
            .playLegWonByScoring20(0).confirmLeg()
        val state = BetweenLegsState.of(confirmed)

        assertEquals(GameMode.CRICKET, state.mode)
        assertEquals("Ann", state.winnerName)
        assertEquals(20, state.winnerPoints)
        assertEquals(confirmed.playerStats.map { it.marksPerRound }, state.rows.map { it.average })
        assertEquals(listOf(1, 0), state.rows.map { it.legsWon })
        assertEquals(listOf(true, false), state.rows.map { it.isLegWinner })
        assertEquals("Bob", state.nextStarterName)
        assertEquals(SLOT_2, state.nextStarterSlot)
    }

    /** CR26: nothing under the winner's name in a solo game. */
    @Test
    fun `CR26 a solo game shows no points`() {
        val confirmed = CricketSaveFixtures.match("Ann", legsPerSet = 2).playLegWonByScoring20(0).confirmLeg()

        assertNull(BetweenLegsState.of(confirmed).winnerPoints)
    }

    /** CR26, M19: a set win is announced as such and the sets column appears. */
    @Test
    fun `CR26 a Cricket set win`() {
        val confirmed = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 1, sets = 2).confirmLegsWonBy(0)
        val state = BetweenLegsState.of(confirmed)

        assertEquals(true, state.isSetWon)
        assertEquals(true, state.hasSets)
        assertEquals(listOf(1, 0), state.rows.map { it.setsWon })
    }

    /** CR26: without a confirmed leg there is nothing to show. */
    @Test(expected = IllegalArgumentException::class)
    fun `a Cricket match without a confirmed leg is refused`() {
        BetweenLegsState.of(CricketSaveFixtures.match("Ann", "Bob"))
    }
}
