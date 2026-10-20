package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapping of visit totals to box texts (docs/rules/visit-total.md, VT6 and VT8). */
class VisitBoxTextTest {

    private val twoPlayers = X01Config(listOf("Ann", "Bob"), startScore = 301)

    private fun start(config: X01Config = twoPlayers) = GameScreenState(X01Game(config))

    private fun GameScreenState.numbers(vararg numbers: Int) = numbers.fold(this) { game, n -> game.throwNumber(n) }

    private fun GameScreenState.triples(vararg numbers: Int) =
        numbers.fold(this) { game, n -> game.toggleMultiplier(Multiplier.TRIPLE).throwNumber(n) }

    private fun inProgress(points: Int) =
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_in_progress_description, listOf(points))

    private fun lastVisit(points: Int) =
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_last_description, listOf(points))

    private val notPlayed =
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_not_played_description, listOf(0))

    private val bust = VisitBoxText(R.string.game_bust, R.string.game_visit_last_bust_description, emptyList())

    /** Ann leaves 121, Bob scores 3, both turns confirmed: Ann throws next. */
    private fun annAt121() = start().triples(20, 20, 20).nextPlayer().numbers(1, 1, 1).nextPlayer()

    @Test
    fun `VT8 VT2 VT5 game start`() {
        assertEquals(listOf(inProgress(0), notPlayed), visitBoxTexts(start()))
    }

    @Test
    fun `VT8 VT2 running total`() {
        assertEquals(listOf(inProgress(35), notPlayed), visitBoxTexts(start().numbers(20, 15)))
    }

    @Test
    fun `VT8 VT6 a waiting normal turn keeps the next player on his last completed visit`() {
        val waiting = start().numbers(20, 15, 15)
        assertTrue(waiting.isAwaitingConfirmation)
        assertEquals(listOf(lastVisit(50), notPlayed), visitBoxTexts(waiting))
        assertEquals(listOf(lastVisit(50), inProgress(0)), visitBoxTexts(waiting.nextPlayer()))
    }

    @Test
    fun `VT13 VT6 a waiting bust hides the thrower box and keeps the next player on his last visit`() {
        // Ann has 121: T20 T20 leaves 1, a bust in double out.
        val waiting = annAt121().triples(20, 20)
        assertTrue(waiting.isBustShown)
        assertEquals(listOf(null, lastVisit(3)), visitBoxTexts(waiting))
        assertEquals(listOf(bust, inProgress(0)), visitBoxTexts(waiting.nextPlayer()))
    }

    @Test
    fun `VT13 VT7 undoing the bust dart brings the box back`() {
        val reopened = annAt121().triples(20, 20).undo()
        assertEquals(listOf(inProgress(60), lastVisit(3)), visitBoxTexts(reopened))
    }

    @Test
    fun `VT13 one player waiting a bust has no box until Next player`() {
        // Ann alone has 121 after T20 T20 T20 and Next player; T20 T20 leaves 1, a bust.
        val waiting = start(X01Config(listOf("Ann"), startScore = 301)).triples(20, 20, 20).nextPlayer().triples(20, 20)
        assertTrue(waiting.isBustShown)
        assertEquals(listOf(null), visitBoxTexts(waiting))
        assertEquals(listOf(inProgress(0)), visitBoxTexts(waiting.nextPlayer()))
    }

    @Test
    fun `VT6 one player waiting shows his waiting turn`() {
        val waiting = start(X01Config(listOf("Ann"), startScore = 301)).numbers(20, 15, 15)
        assertEquals(listOf(lastVisit(50)), visitBoxTexts(waiting))
        assertEquals(listOf(inProgress(0)), visitBoxTexts(waiting.nextPlayer()))
    }

    @Test
    fun `VT6 VT7 undo of a waiting turn reopens it`() {
        val reopened = start().numbers(20, 15, 15).undo()
        assertEquals(listOf(inProgress(35), notPlayed), visitBoxTexts(reopened))
    }

    @Test
    fun `VT6 VT3 the checkout dialog shows the winning total`() {
        val won = annAt121().triples(20, 19).toggleMultiplier(Multiplier.DOUBLE).throwNumber(2)
        assertTrue(won.isCheckoutDialogShown)
        assertEquals(listOf(lastVisit(121), lastVisit(3)), visitBoxTexts(won))
    }
}
