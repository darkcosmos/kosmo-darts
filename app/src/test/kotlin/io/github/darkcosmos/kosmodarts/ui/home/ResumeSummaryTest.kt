package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
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
        assertEquals(saved.match.format, summary.format)
    }

    @Test
    fun `R2 mode, rule and every player with the remaining score, in order`() {
        val summary = ResumeSummary.of(SaveFixtures.WAITING_BUST)

        assertEquals(301, summary.startScore)
        assertEquals(CheckoutRule.DOUBLE_OUT, summary.checkoutRule)
        // Ann's bust restored her 121; Bob missed everything.
        val expected = listOf(
            ResumeSummary.PlayerScore("Ann", 121, ColorSlot.SLOT_1),
            ResumeSummary.PlayerScore("Bob", 301, ColorSlot.SLOT_2),
        )
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

    /**
     * M18, owner Q11 of issue #49: a pending checkout shows the winner as the player to throw, like any other case;
     * there is no "checkout to confirm" mention any more.
     */
    @Test
    fun `M18 a pending checkout names the winner as the player to throw`() {
        val summary = ResumeSummary.of(SaveFixtures.WAITING_CHECKOUT)

        assertEquals("Ann", summary.highlightedPlayer)
        assertTrue(SaveFixtures.WAITING_CHECKOUT.toGameScreenState().isCheckoutPending)
    }

    /** PC9, R17: the summary gives each player his own color slot. */
    @Test
    fun `R17 the summary carries the saved color slots`() {
        val saved = SavedGame(
            MatchFixtures.match("Ann", "Bob", colorSlots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1)),
            isAwaitingNextPlayer = false,
        )

        assertEquals(listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1), ResumeSummary.of(saved).players.map { it.slot })
        assertFalse(ResumeSummary.of(saved).players.isEmpty())
    }
}
