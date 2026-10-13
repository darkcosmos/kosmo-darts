package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class TurnCompletionTest {

    private val reducer = AppReducer(Random(0))
    private val config = X01Config(listOf("Ann", "Bob"), startScore = 301)

    private fun inGame(darts: List<Dart> = emptyList()) =
        AppState(screen = Screen.Game(GameScreenState(X01Game(config, darts))))

    private fun completedBy(before: AppState, event: AppEvent): X01Turn? =
        completedTurn(before, reducer.reduce(before, event))

    @Test
    fun theDartThatEndsATurnCompletesIt() {
        val before = inGame(listOf(Dart.single(1), Dart.single(2)))

        val turn = completedTurn(before, reducer.reduce(before, AppEvent.NumberTapped(3)))

        assertEquals(TurnOutcome.NORMAL, (turn ?: error("no turn")).outcome)
        assertEquals(listOf(Dart.single(1), Dart.single(2), Dart.single(3)), turn.darts)
    }

    @Test
    fun theWinningDartCompletesATurn() {
        val single = X01Config(listOf("Ann"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        val before = AppState(screen = Screen.Game(GameScreenState(X01Game(single, List(5) { Dart.triple(20) }))))

        val turn = completedBy(before, AppEvent.NumberTapped(1))

        assertEquals(TurnOutcome.CHECKOUT, turn?.outcome)
        assertEquals(listOf(Dart.triple(20), Dart.triple(20), Dart.single(1)), turn?.darts)
    }

    @Test
    fun otherDartsCompleteNothing() {
        assertNull(completedBy(inGame(), AppEvent.NumberTapped(20)))
    }

    @Test
    fun confirmingOrUndoingAPendingTurnCompletesNothing() {
        val pending = reducer.reduce(inGame(listOf(Dart.single(1), Dart.single(2))), AppEvent.NumberTapped(3))

        assertNull(completedBy(pending, AppEvent.NextPlayerTapped))
        assertNull(completedBy(pending, AppEvent.UndoTapped))
        assertNull(completedBy(pending, AppEvent.Back))
        assertNull(completedBy(pending, AppEvent.NumberTapped(20)))
        assertNull(completedBy(pending, AppEvent.FinishGameTapped))
    }

    @Test
    fun outsideTheGameNothingIsCompleted() {
        assertNull(completedBy(AppState(), AppEvent.NewGame))
    }
}
