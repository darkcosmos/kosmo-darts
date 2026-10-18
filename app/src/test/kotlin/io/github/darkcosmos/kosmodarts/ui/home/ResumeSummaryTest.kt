package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The summary of the saved game on the home screen (rule R2; owner decision Q4). */
class ResumeSummaryTest {

    /** The summary must agree with the game screen that "Resume game" opens. */
    private fun assertAgreesWithGameScreen(saved: SavedGame) {
        val summary = ResumeSummary.of(saved)
        val screen = saved.toGameScreenState()
        val highlighted = saved.game.state.players[checkNotNull(screen.highlightedPlayerIndex)].name

        assertEquals(highlighted, summary.highlightedPlayer)
        assertEquals(screen.isCheckoutDialogShown, summary.isCheckoutToConfirm)
    }

    @Test
    fun `R2 mode, rule and every player with the remaining score, in order`() {
        val summary = ResumeSummary.of(SaveFixtures.WAITING_BUST)

        assertEquals(301, summary.startScore)
        assertEquals(CheckoutRule.DOUBLE_OUT, summary.checkoutRule)
        // Ann's bust restored her 121; Bob missed everything.
        val expected = listOf(ResumeSummary.PlayerScore("Ann", 121), ResumeSummary.PlayerScore("Bob", 301))
        assertEquals(expected, summary.players)
    }

    @Test
    fun `R2 the player to throw matches the game screen in every case`() {
        listOf(
            SaveFixtures.EMPTY,
            SaveFixtures.MID_TURN,
            SaveFixtures.WAITING_NORMAL,
            SaveFixtures.AFTER_NEXT_PLAYER,
            SaveFixtures.WAITING_BUST,
            SaveFixtures.WAITING_CHECKOUT,
        ).forEach(::assertAgreesWithGameScreen)
    }

    @Test
    fun `R2 an empty game shows the first player`() {
        assertEquals("Ann", ResumeSummary.of(SaveFixtures.EMPTY).highlightedPlayer)
    }

    @Test
    fun `R2 a waiting turn shows its thrower, after Next player the next one`() {
        assertEquals("Ann", ResumeSummary.of(SaveFixtures.WAITING_NORMAL).highlightedPlayer)
        assertEquals("Ann", ResumeSummary.of(SaveFixtures.WAITING_BUST).highlightedPlayer)
        assertEquals("Bob", ResumeSummary.of(SaveFixtures.AFTER_NEXT_PLAYER).highlightedPlayer)
    }

    @Test
    fun `R2 an open checkout shows the winner with the checkout mention`() {
        val summary = ResumeSummary.of(SaveFixtures.WAITING_CHECKOUT)

        assertEquals("Ann", summary.highlightedPlayer)
        assertTrue(summary.isCheckoutToConfirm)
        assertFalse(ResumeSummary.of(SaveFixtures.WAITING_NORMAL).isCheckoutToConfirm)
    }
}
