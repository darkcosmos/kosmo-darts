package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** CS4 and CS6 (docs/rules/checkout-suggestions.md): the suggestion of a game for its engine current player. */
class X01CheckoutSuggestionTest {

    private fun route(vararg codes: String): List<Dart> = codes.map(::dart)

    /** CS4, CS6: the owner's phone scenario (issue #38, steps 1 to 3), 1 player, 301, double out. */
    @Test
    fun `CS4 CS6 suggestion follows each dart and each undo`() {
        val start = newGame(startScore = 301)
        assertNull(start.checkoutSuggestion)
        assertNull(start.throwing("T20").checkoutSuggestion)
        assertNull(start.throwing("T20", "T20").checkoutSuggestion)

        val at170 = start.throwing("T20", "T20", "11")
        assertEquals(route("T20", "T20", "BULL"), at170.checkoutSuggestion)
        val at110 = at170.throwing("T20")
        assertEquals(route("T20", "BULL"), at110.checkoutSuggestion)
        val at90 = at110.throwing("20")
        assertNull(at90.checkoutSuggestion)

        assertEquals(route("T20", "BULL"), at90.undo().checkoutSuggestion)
        assertEquals(route("T20", "T20", "BULL"), at90.undo().undo().checkoutSuggestion)
    }

    /** CS4: after a bust the engine has moved on: the next turn of the same player starts from the restored score. */
    @Test
    fun `CS4 after a bust the next turn gets three darts again`() {
        val busted = gameAtRemaining(170).throwing("T20", "T20", "T20")
        assertEquals(170, busted.player(0).remaining)
        assertEquals(route("T20", "T20", "BULL"), busted.checkoutSuggestion)
    }

    /** CS4: single out uses any finishing dart. */
    @Test
    fun `CS4 single out`() {
        val game = gameAtRemaining(180, checkoutRule = CheckoutRule.SINGLE_OUT)
        assertEquals(route("T20", "T20", "T20"), game.checkoutSuggestion)
    }

    /** CS4: a finished leg has no suggestion, never an exception, also when restored from its darts. */
    @Test
    fun `CS4 finished leg has no suggestion`() {
        val finished = gameAtRemaining(40).throwing("D20")
        assertEquals(true, finished.state.isFinished)
        assertNull(finished.checkoutSuggestion)
        assertNull(X01Game(finished.config, finished.darts).checkoutSuggestion)
    }

    /** CS4: a game restored mid-turn from its darts gives the same suggestion as the game played dart by dart. */
    @Test
    fun `CS4 restored game mid turn`() {
        val played = gameAtRemaining(170).throwing("T20")
        val restored = X01Game(played.config, played.darts)
        assertEquals(route("T20", "BULL"), restored.checkoutSuggestion)
        assertEquals(played.checkoutSuggestion, restored.checkoutSuggestion)
    }

    /** CS4: the suggestion is for the engine current player. */
    @Test
    fun `CS4 suggestion follows the engine current player`() {
        val aliceAt170 = newGame("Alice", "Bob", startScore = 301).throwing("T20", "T20", "11")
        assertNull(aliceAt170.checkoutSuggestion, "Bob throws next with 301")
        val backToAlice = aliceAt170.throwing("MISS", "MISS", "MISS")
        assertEquals(route("T20", "T20", "BULL"), backToAlice.checkoutSuggestion)
        assertEquals(route("T20", "BULL"), backToAlice.throwing("T20").checkoutSuggestion)
    }
}
