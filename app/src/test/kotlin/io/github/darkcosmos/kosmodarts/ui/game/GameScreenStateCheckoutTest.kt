package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CS4 to CS6 (docs/rules/checkout-suggestions.md): which suggestion the highlighted card shows. */
class GameScreenStateCheckoutTest {

    private val onePlayer = X01Config(listOf("Ann"), startScore = 301)
    private val twoPlayers = X01Config(listOf("Ann", "Bob"), startScore = 301)

    private val t20 = Dart.triple(20)
    private val bull = Dart.BULL

    /** 1 player, 301: T20, T20, 11 leaves 170, then "Next player". */
    private fun at170(): GameScreenState =
        GameScreenState(X01Game(onePlayer)).throwing(t20, t20, Dart.single(11)).nextPlayer()

    @Test
    fun suggestionIsShownMidTurnAndFollowsEachDart() {
        assertNull(GameScreenState(X01Game(onePlayer)).checkoutSuggestion)
        assertEquals(listOf(t20, t20, bull), at170().checkoutSuggestion)
        assertEquals(listOf(t20, bull), at170().throwing(t20).checkoutSuggestion)
        assertNull(at170().throwing(t20, Dart.single(20)).checkoutSuggestion)
    }

    @Test
    fun hiddenWhileANormalTurnWaitsThenBackAfterNextPlayer() {
        val waiting = at170().throwing(t20, t20, Dart.single(20))

        assertTrue(waiting.isAwaitingConfirmation)
        assertNull(waiting.checkoutSuggestion)
        assertEquals(listOf(Dart.double(15)), waiting.nextPlayer().checkoutSuggestion)
    }

    @Test
    fun hiddenWhileABustWaitsThenBackAfterNextPlayerOrUndo() {
        val bust = at170().throwing(t20, t20, t20)

        assertTrue(bust.isBustPending)
        assertNull(bust.checkoutSuggestion)
        assertEquals(listOf(t20, t20, bull), bust.nextPlayer().checkoutSuggestion)
        assertEquals(listOf(bull), bust.undo().checkoutSuggestion)
    }

    @Test
    fun hiddenWhileTheCheckoutDialogIsOpen() {
        val won = at170().throwing(t20, t20, bull)

        assertTrue(won.isCheckoutPending)
        assertNull(won.checkoutSuggestion)
        assertEquals(listOf(bull), won.undo().checkoutSuggestion)
    }

    @Test
    fun undoAcrossNextPlayerGivesTheRightDartsLeft() {
        // Ann leaves 170, Bob misses three times, Ann: T20 (110), miss, miss.
        val annTurn = GameScreenState(X01Game(twoPlayers))
            .throwing(t20, t20, Dart.single(11)).nextPlayer()
            .throwing(Dart.MISS, Dart.MISS, Dart.MISS).nextPlayer()
            .throwing(t20, Dart.MISS, Dart.MISS)
        assertNull(annTurn.checkoutSuggestion)

        val bobTurn = annTurn.nextPlayer()
        assertNull(bobTurn.checkoutSuggestion)

        val annOneDartLeft = bobTurn.undo()
        assertEquals(0, annOneDartLeft.highlightedPlayerIndex)
        assertNull(annOneDartLeft.checkoutSuggestion)
        assertEquals(listOf(t20, bull), annOneDartLeft.undo().checkoutSuggestion)
        assertEquals(listOf(t20, t20, bull), annOneDartLeft.undo().undo().checkoutSuggestion)
    }

    @Test
    fun restoredGameMidTurnGivesTheSameSuggestion() {
        val played = at170().throwing(t20)
        val restored = GameScreenState(X01Game(onePlayer, played.game.darts))

        assertEquals(listOf(t20, bull), restored.checkoutSuggestion)
        assertEquals(played.checkoutSuggestion, restored.checkoutSuggestion)
    }

    private fun GameScreenState.throwing(vararg darts: Dart): GameScreenState = darts.fold(this) { game, dart ->
        when {
            dart == Dart.BULL -> game.throwBull()
            dart == Dart.OUTER_BULL -> game.throwOuterBull()
            dart == Dart.MISS -> game.throwMiss()
            dart.multiplier == Multiplier.SINGLE -> game.throwNumber(dart.segment)
            else -> game.toggleMultiplier(dart.multiplier).throwNumber(dart.segment)
        }
    }
}
