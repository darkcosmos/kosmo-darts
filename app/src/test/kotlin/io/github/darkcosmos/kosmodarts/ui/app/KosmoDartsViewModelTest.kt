package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KosmoDartsViewModelTest {

    @Test
    fun theHookIsCalledOncePerCompletedTurn() {
        val turns = mutableListOf<X01Turn>()
        val viewModel = KosmoDartsViewModel(Random(0))
        viewModel.turnCompletedHook = TurnCompletedHook { turns += it }
        listOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.NameChanged(0, "Ann"),
            AppEvent.NameChanged(1, "Bob"),
            AppEvent.StartRequested,
        ).forEach(viewModel::onEvent)

        repeat(3) { viewModel.onEvent(AppEvent.NumberTapped(20)) }
        // Ignored: the keypad is disabled while the turn waits for confirmation.
        viewModel.onEvent(AppEvent.NumberTapped(20))
        viewModel.onEvent(AppEvent.NextPlayerTapped)
        repeat(2) {
            viewModel.onEvent(AppEvent.MultiplierTapped(Multiplier.TRIPLE))
            viewModel.onEvent(AppEvent.NumberTapped(20))
        }
        viewModel.onEvent(AppEvent.NumberTapped(20))
        // Undo reopens the turn: completing it again is a new completed turn.
        viewModel.onEvent(AppEvent.UndoTapped)
        viewModel.onEvent(AppEvent.NumberTapped(1))

        assertEquals(listOf(TurnOutcome.NORMAL, TurnOutcome.NORMAL, TurnOutcome.NORMAL), turns.map { it.outcome })
        assertEquals(listOf(0, 1, 1), turns.map { it.playerIndex })
        assertEquals(listOf(60, 140, 121), turns.map { it.remainingBefore - it.remainingAfter })
    }

    /** R4: the restored state is set directly: no hook and no write for an already completed turn. */
    @Test
    fun `R4 restoring a waiting turn fires no hook and writes nothing`() {
        val turns = mutableListOf<X01Turn>()
        val effects = mutableListOf<PersistenceEffect>()
        val restored = AppState(screen = Screen.Game(SaveFixtures.WAITING_NORMAL.toGameScreenState()))
        val viewModel = KosmoDartsViewModel(Random(0), restored) { effects += it }
        viewModel.turnCompletedHook = TurnCompletedHook { turns += it }

        viewModel.onEvent(AppEvent.NumberTapped(20)) // Ignored: the turn waits.

        assertEquals(restored, viewModel.state.value)
        assertEquals(emptyList<X01Turn>(), turns)
        assertEquals(emptyList<PersistenceEffect>(), effects)
        assertTrue(viewModel.isOnGameScreen)
    }

    /** R12: each event hands its storage changes over, in order. */
    @Test
    fun `R12 events hand their storage changes over`() {
        val effects = mutableListOf<List<PersistenceEffect>>()
        val home = AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN))
        val viewModel = KosmoDartsViewModel(Random(0), home) { effects += it }

        viewModel.onEvent(AppEvent.ResumeTapped)
        viewModel.onEvent(AppEvent.MissTapped)

        val saved = checkNotNull(savedGameOf(viewModel.state.value))
        assertEquals(listOf(listOf(PersistenceEffect.SaveGame(saved))), effects)
    }
}
