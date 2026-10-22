package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScreenStateTest {

    private val twoPlayers = X01Config(listOf("Ann", "Bob"), startScore = 301)

    private fun start(config: X01Config = twoPlayers) = GameScreenState(X01Game(config))

    @Test
    fun aNumberWithoutModifierIsASingle() {
        val game = start().throwNumber(20)

        assertEquals(listOf(Dart.single(20)), game.game.darts)
    }

    @Test
    fun doubleThenNumberIsADouble() {
        val game = start().toggleMultiplier(Multiplier.DOUBLE).throwNumber(20)

        assertEquals(listOf(Dart.double(20)), game.game.darts)
    }

    @Test
    fun tripleThenNumberIsATriple() {
        val game = start().toggleMultiplier(Multiplier.TRIPLE).throwNumber(19)

        assertEquals(listOf(Dart.triple(19)), game.game.darts)
    }

    @Test
    fun theModifierIsResetAfterEachDart() {
        val game = start().toggleMultiplier(Multiplier.DOUBLE).throwNumber(20)

        assertEquals(Multiplier.SINGLE, game.multiplier)
        assertEquals(Dart.single(5), game.throwNumber(5).game.darts.last())
    }

    @Test
    fun tappingTheActiveModifierAgainCancelsIt() {
        val game = start().toggleMultiplier(Multiplier.TRIPLE).toggleMultiplier(Multiplier.TRIPLE)

        assertEquals(Multiplier.SINGLE, game.multiplier)
    }

    @Test
    fun tappingTheOtherModifierReplacesIt() {
        val game = start().toggleMultiplier(Multiplier.DOUBLE).toggleMultiplier(Multiplier.TRIPLE)

        assertEquals(Multiplier.TRIPLE, game.multiplier)
    }

    @Test
    fun bullKeysAreDisabledWhileAModifierIsActive() {
        val doubled = start().toggleMultiplier(Multiplier.DOUBLE)

        assertTrue(start().areBullKeysEnabled)
        assertFalse(doubled.areBullKeysEnabled)
        assertEquals(doubled, doubled.throwOuterBull())
        assertEquals(doubled, doubled.throwBull())
    }

    @Test
    fun bullKeysRecordTheOuterBullAndTheBull() {
        val game = start().throwOuterBull().throwBull()

        assertEquals(listOf(Dart.OUTER_BULL, Dart.BULL), game.game.darts)
    }

    @Test
    fun missIsRecordedAsAMissAndCancelsTheModifier() {
        val game = start().toggleMultiplier(Multiplier.TRIPLE).throwMiss()

        assertEquals(listOf(Dart.MISS), game.game.darts)
        assertEquals(Multiplier.SINGLE, game.multiplier)
    }

    @Test
    fun noTurnIsPendingDuringATurn() {
        val game = start().throwNumber(1).throwNumber(2)

        assertFalse(game.isAwaitingConfirmation)
        assertNull(game.pendingTurn)
        assertTrue(game.isKeypadEnabled)
        assertFalse(game.canGoToNextPlayer)
    }

    @Test
    fun theThirdDartWaitsForConfirmation() {
        val game = start().throwNumber(1).throwNumber(2).throwNumber(3)

        assertTrue(game.isAwaitingConfirmation)
        assertTrue(game.canGoToNextPlayer)
        assertFalse(game.isCheckoutPending)
        assertEquals(TurnOutcome.NORMAL, game.pendingTurn?.outcome)
        assertEquals(listOf(Dart.single(1), Dart.single(2), Dart.single(3)), game.pendingTurn?.darts)
        assertEquals(295, game.pendingTurn?.remainingAfter)
    }

    @Test
    fun theWholeKeypadIsDisabledWhileATurnIsPending() {
        val pending = start().throwNumber(1).throwNumber(2).throwNumber(3)

        assertFalse(pending.isKeypadEnabled)
        assertFalse(pending.areBullKeysEnabled)
        assertEquals(pending, pending.throwNumber(20))
        assertEquals(pending, pending.throwOuterBull())
        assertEquals(pending, pending.throwBull())
        assertEquals(pending, pending.throwMiss())
        assertEquals(pending, pending.toggleMultiplier(Multiplier.DOUBLE))
    }

    @Test
    fun thePlayerWhoThrewStaysHighlightedWhileTheTurnIsPending() {
        val pending = start().throwNumber(1).throwNumber(2).throwNumber(3)

        assertEquals(0, pending.highlightedPlayerIndex)
        assertEquals(listOf(Dart.single(1), Dart.single(2), Dart.single(3)), pending.highlightedTurnDarts)
    }

    @Test
    fun confirmingMovesPlayOnToTheNextPlayer() {
        val confirmed = start().throwNumber(1).throwNumber(2).throwNumber(3).nextPlayer()

        assertFalse(confirmed.isAwaitingConfirmation)
        assertTrue(confirmed.isKeypadEnabled)
        assertEquals(1, confirmed.highlightedPlayerIndex)
        assertTrue(confirmed.highlightedTurnDarts.isEmpty())
    }

    @Test
    fun aBustWaitsForConfirmation() {
        val busted = bustedGame()

        assertTrue(busted.isAwaitingConfirmation)
        assertEquals(TurnOutcome.BUST, busted.pendingTurn?.outcome)
        assertEquals(121, busted.pendingTurn?.remainingAfter)
        assertEquals(0, busted.highlightedPlayerIndex)
    }

    @Test
    fun theWinningDartWaitsForConfirmation() {
        val single = X01Config(listOf("Ann"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        val almost = GameScreenState(X01Game(single, List(5) { Dart.triple(20) }))

        val won = almost.throwNumber(1)

        assertTrue(won.isAwaitingConfirmation)
        assertTrue(won.isCheckoutPending)
        assertFalse(won.canGoToNextPlayer)
        assertEquals(won, won.nextPlayer())
        assertEquals(TurnOutcome.CHECKOUT, won.pendingTurn?.outcome)
        assertEquals(0, won.highlightedPlayerIndex)
        assertEquals(listOf(Dart.triple(20), Dart.triple(20), Dart.single(1)), won.highlightedTurnDarts)
    }

    @Test
    fun undoFromThePendingTurnReopensItWithItsFirstTwoDarts() {
        val pending = start().throwNumber(1).throwNumber(2).throwNumber(3)

        val reopened = pending.undo()

        assertFalse(reopened.isAwaitingConfirmation)
        assertEquals(0, reopened.game.state.currentPlayerIndex)
        assertEquals(listOf(Dart.single(1), Dart.single(2)), reopened.highlightedTurnDarts)
        assertEquals(reopened, reopened.throwNumber(4).undo())
    }

    @Test
    fun undoRemovesTheLastDartAcrossPlayersAndResetsTheModifier() {
        val bobToThrow = start().throwNumber(1).throwNumber(2).throwNumber(3).nextPlayer()
        assertEquals(1, bobToThrow.game.state.currentPlayerIndex)

        val undone = bobToThrow.toggleMultiplier(Multiplier.DOUBLE).undo()

        assertEquals(0, undone.game.state.currentPlayerIndex)
        assertEquals(listOf(Dart.single(1), Dart.single(2)), undone.game.darts)
        assertEquals(Multiplier.SINGLE, undone.multiplier)
        assertFalse(undone.isAwaitingConfirmation)
    }

    @Test
    fun undoIsOnlyPossibleAfterADart() {
        assertFalse(start().canUndo)
        assertTrue(start().throwMiss().canUndo)
    }

    @Test
    fun undoOnTheCheckoutClosesTheDialogAndReopensTheTurn() {
        val single = X01Config(listOf("Ann"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        val won = GameScreenState(X01Game(single, List(5) { Dart.triple(20) })).throwNumber(1)

        val reopened = won.undo()

        assertFalse(reopened.isCheckoutPending)
        assertFalse(reopened.game.state.isFinished)
        assertEquals(listOf(Dart.triple(20), Dart.triple(20)), reopened.highlightedTurnDarts)
        assertTrue(reopened.isKeypadEnabled)
    }

    @Test
    fun nextPlayerWithoutWaitingTurnChangesNothing() {
        val game = start().throwNumber(1)

        assertEquals(game, game.nextPlayer())
    }

    @Test
    fun abandonDialogCanBeShownAndHidden() {
        val shown = start().withAbandonDialog(shown = true)

        assertTrue(shown.isAbandonDialogShown)
        assertFalse(shown.withAbandonDialog(shown = false).isAbandonDialogShown)
    }

    @Test
    fun noDartIsRecordedOnceTheLegIsFinished() {
        val single = X01Config(listOf("Ann"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        val finished = GameScreenState(X01Game(single, List(5) { Dart.triple(20) } + Dart.single(1)))
        assertTrue(finished.game.state.isFinished)

        assertEquals(finished, finished.throwNumber(1))
        assertEquals(finished, finished.throwMiss())
    }

    /** 301 double out: Ann leaves 121, Bob leaves 121, then Ann's T20 T20 leaves 1, which is a bust. */
    private fun bustedGame(): GameScreenState =
        start().throwTriples(20, 20, 20).nextPlayer().throwTriples(20, 20, 20).nextPlayer().throwTriples(20, 20)

    private fun GameScreenState.throwTriples(vararg numbers: Int): GameScreenState =
        numbers.fold(this) { game, number -> game.toggleMultiplier(Multiplier.TRIPLE).throwNumber(number) }
}
