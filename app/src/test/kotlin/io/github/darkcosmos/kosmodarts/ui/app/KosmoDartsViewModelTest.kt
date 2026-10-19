package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.settings.inMemoryAudioSettings
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KosmoDartsViewModelTest {

    /** AN2, AN6: each completed turn gives its score once; an undone turn completed again gives it again. */
    @Test
    fun `AN2 each completed turn gives its score once`() {
        val cues = mutableListOf<X01Cue>()
        val viewModel = KosmoDartsViewModel(Random(0), audioSettings = inMemoryAudioSettings())
        viewModel.addCueSink { cues += it }
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

        val turnScores = cues.filterIsInstance<X01Cue.TurnScore>()
        assertEquals(listOf(X01Cue.TurnScore(60), X01Cue.TurnScore(140), X01Cue.TurnScore(121)), turnScores)
        val turnStarts = cues.filterIsInstance<X01Cue.TurnStart>()
        assertEquals(listOf(X01Cue.TurnStart(0, "Ann", null), X01Cue.TurnStart(1, "Bob", null)), turnStarts)
    }

    /** R4, AN7: the restored state is set directly: no cue and no write for an already completed turn. */
    @Test
    fun `R4 AN7 restoring a waiting turn gives no cue and writes nothing`() {
        val cues = mutableListOf<X01Cue>()
        val effects = mutableListOf<PersistenceEffect>()
        val restored = AppState(screen = Screen.Game(SaveFixtures.WAITING_NORMAL.toGameScreenState()))
        val viewModel = KosmoDartsViewModel(Random(0), restored, inMemoryAudioSettings()) { effects += it }
        viewModel.addCueSink { cues += it }

        viewModel.onEvent(AppEvent.NumberTapped(20)) // Ignored: the turn waits.

        assertEquals(restored, viewModel.state.value)
        assertEquals(emptyList<X01Cue>(), cues)
        assertEquals(emptyList<PersistenceEffect>(), effects)
        assertTrue(viewModel.isOnGameScreen)
    }

    /** R12: each event hands its storage changes over, in order. */
    @Test
    fun `R12 events hand their storage changes over`() {
        val effects = mutableListOf<List<PersistenceEffect>>()
        val home = AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN))
        val viewModel = KosmoDartsViewModel(Random(0), home, inMemoryAudioSettings()) { effects += it }

        viewModel.onEvent(AppEvent.ResumeTapped)
        viewModel.onEvent(AppEvent.MissTapped)

        val saved = checkNotNull(savedGameOf(viewModel.state.value))
        assertEquals(listOf(listOf(PersistenceEffect.SaveGame(saved))), effects)
    }
}
