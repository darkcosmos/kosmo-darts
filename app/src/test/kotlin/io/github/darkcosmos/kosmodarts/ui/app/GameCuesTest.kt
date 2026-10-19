package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cues
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The five hook points of the game ViewModel (docs/rules/announcements.md, AN1 to AN7). */
class GameCuesTest {

    private val reducer = AppReducer(Random(0))
    private val config = X01Config(listOf("Ann", "Bob"), startScore = 301)

    private fun inGame(darts: List<Dart> = emptyList(), config: X01Config = this.config) =
        AppState(screen = Screen.Game(GameScreenState(X01Game(config, darts))))

    /** Applies [events] in order and returns the cues of the last one. */
    private fun cuesOf(start: AppState, vararg events: AppEvent): List<X01Cue> {
        var state = start
        var cues = emptyList<X01Cue>()
        events.forEach { event ->
            val next = reducer.reduce(state, event)
            cues = gameCues(state, event, next)
            state = next
        }
        return cues
    }

    private fun reduceAll(start: AppState, vararg events: AppEvent): AppState =
        events.fold(start) { state, event -> reducer.reduce(state, event) }

    /** AN1: a dart entry gives its dart cue. */
    @Test
    fun `AN1 a dart gives its dart cue`() {
        assertEquals(listOf(X01Cue.DartHit), cuesOf(inGame(), AppEvent.NumberTapped(20)))
        assertEquals(listOf(X01Cue.DartMiss), cuesOf(inGame(), AppEvent.MissTapped))
        assertEquals(listOf(X01Cue.DartHit), cuesOf(inGame(), AppEvent.BullTapped))
    }

    /** AN2: the third dart gives the end-of-turn cue after its dart cue. */
    @Test
    fun `AN2 the third dart gives the turn score`() {
        val twoDarts = inGame(listOf(Dart.triple(20), Dart.triple(20)))
        val cues = cuesOf(twoDarts, AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(20))
        assertEquals(listOf(X01Cue.DartHit, X01Cue.OneEighty), cues)
    }

    /** AN4: Start opens the game and announces the first player. */
    @Test
    fun `AN4 start announces the first player`() {
        val setup = AppState(screen = Screen.Setup(SetupState.prefilled(config)))
        assertEquals(listOf(X01Cue.TurnStart(0, "Ann", null)), cuesOf(setup, AppEvent.StartRequested))
    }

    /** AN4: an invalid setup does not start a game, so nothing is announced. */
    @Test
    fun `AN4 an invalid start announces nothing`() {
        val setup = AppState(screen = Screen.Setup(SetupState.prefilled(null)))
        assertEquals(emptyList<X01Cue>(), cuesOf(setup, AppEvent.StartRequested))
    }

    /** AN4: Rematch announces the new first player (the second player of the last game). */
    @Test
    fun `AN4 rematch announces the new first player`() {
        val end = AppState(screen = Screen.End(SaveFixtures.WAITING_CHECKOUT.game))
        val first = SaveFixtures.WAITING_CHECKOUT.game.config.players.let { if (it.size > 1) it[1] else it[0] }
        val cues = cuesOf(end, AppEvent.Rematch)
        assertEquals(1, cues.size)
        assertEquals(first, (cues.single() as X01Cue.TurnStart).name)
    }

    /** AN4: "Next player" after a normal turn announces the next player. */
    @Test
    fun `AN4 next player announces the next player`() {
        val waiting = reduceAll(inGame(listOf(Dart.single(1), Dart.single(1))), AppEvent.NumberTapped(1))
        assertEquals(listOf(X01Cue.TurnStart(1, "Bob", null)), cuesOf(waiting, AppEvent.NextPlayerTapped))
    }

    /** AN4: "Next player" after a bust announces the next player too. */
    @Test
    fun `AN4 next player after a bust`() {
        val bust = AppState(screen = Screen.Game(SaveFixtures.WAITING_BUST.toGameScreenState()))
        val expected = listOfNotNull(X01Cues.turnStart(SaveFixtures.WAITING_BUST.game))
        assertEquals(1, expected.size)
        assertEquals(expected, cuesOf(bust, AppEvent.NextPlayerTapped))
    }

    /** AN5: "Finish game" in the checkout dialog announces the win. */
    @Test
    fun `AN5 finish game announces the win`() {
        val checkout = AppState(screen = Screen.Game(SaveFixtures.WAITING_CHECKOUT.toGameScreenState()))
        val cues = cuesOf(checkout, AppEvent.FinishGameTapped)
        assertEquals(1, cues.size)
        assertEquals(X01Cues.checkoutConfirmed(SaveFixtures.WAITING_CHECKOUT.game), cues.single())
    }

    /** AN5, AN6: Undo in the checkout dialog gives the undo cue only, no win. */
    @Test
    fun `AN5 AN6 undo in the checkout dialog gives no win`() {
        val checkout = AppState(screen = Screen.Game(SaveFixtures.WAITING_CHECKOUT.toGameScreenState()))
        assertEquals(listOf(X01Cue.DartUndone), cuesOf(checkout, AppEvent.UndoTapped))
    }

    /** AN6: undo of a checkout dart, then the same dart again: its dart cue is produced again. */
    @Test
    fun `AN6 an undone dart entered again gives its cue again`() {
        val checkout = AppState(screen = Screen.Game(SaveFixtures.WAITING_CHECKOUT.toGameScreenState()))
        val undone = reduceAll(checkout, AppEvent.UndoTapped)
        val lastDart = SaveFixtures.WAITING_CHECKOUT.game.darts.last()
        val again = if (lastDart.multiplier == Multiplier.SINGLE) {
            cuesOf(undone, numberEvent(lastDart))
        } else {
            cuesOf(undone, AppEvent.MultiplierTapped(lastDart.multiplier), numberEvent(lastDart))
        }
        assertEquals(listOf(X01Cue.DartHit), again)
    }

    /** AN6: undo of a third dart, then the same dart: the end-of-turn cue is produced again. */
    @Test
    fun `AN6 an undone third dart entered again gives the turn score again`() {
        val waiting = reduceAll(inGame(listOf(Dart.single(1), Dart.single(1))), AppEvent.NumberTapped(1))
        assertEquals(listOf(X01Cue.DartUndone), cuesOf(waiting, AppEvent.UndoTapped))
        val reopened = reduceAll(waiting, AppEvent.UndoTapped)
        assertEquals(listOf(X01Cue.DartHit, X01Cue.TurnScore(3)), cuesOf(reopened, AppEvent.NumberTapped(1)))
    }

    /** AN6: undo of the first dart of a turn. */
    @Test
    fun `AN6 undo of the first dart of a turn`() {
        assertEquals(listOf(X01Cue.DartUndone), cuesOf(inGame(listOf(Dart.single(5))), AppEvent.UndoTapped))
    }

    /** AN6: undo after "Next player" reopens the previous turn; its third dart again gives its cues again. */
    @Test
    fun `AN6 undo after next player`() {
        val twoDarts = inGame(listOf(Dart.single(1), Dart.single(1)))
        val next = reduceAll(twoDarts, AppEvent.NumberTapped(1), AppEvent.NextPlayerTapped)
        assertEquals(listOf(X01Cue.DartUndone), cuesOf(next, AppEvent.UndoTapped))
        val reopened = reduceAll(next, AppEvent.UndoTapped)
        assertEquals(listOf(X01Cue.DartHit, X01Cue.TurnScore(3)), cuesOf(reopened, AppEvent.NumberTapped(1)))
    }

    /** AN6: undo with no dart does nothing, so no cue. */
    @Test
    fun `AN6 undo without a dart gives nothing`() {
        assertEquals(emptyList<X01Cue>(), cuesOf(inGame(), AppEvent.UndoTapped))
    }

    /** AN7: resuming a saved game, whatever its state, announces nothing. */
    @Test
    fun `AN7 resume announces nothing`() {
        val saves = listOf(
            SaveFixtures.MID_TURN,
            SaveFixtures.WAITING_NORMAL,
            SaveFixtures.WAITING_BUST,
            SaveFixtures.WAITING_CHECKOUT,
        )
        saves.forEach { saved ->
            val home = AppState(screen = Screen.Home(resumableGame = saved))
            assertEquals(emptyList<X01Cue>(), cuesOf(home, AppEvent.ResumeTapped))
        }
    }

    /** AN7: other events of the game screen announce nothing. */
    @Test
    fun `AN7 other events announce nothing`() {
        val waiting = reduceAll(inGame(listOf(Dart.single(1), Dart.single(1))), AppEvent.NumberTapped(1))
        assertEquals(emptyList<X01Cue>(), cuesOf(waiting, AppEvent.NumberTapped(20))) // keypad disabled
        assertEquals(emptyList<X01Cue>(), cuesOf(waiting, AppEvent.Back))
        assertEquals(emptyList<X01Cue>(), cuesOf(waiting, AppEvent.Back, AppEvent.AbandonDismissed))
        assertEquals(emptyList<X01Cue>(), cuesOf(waiting, AppEvent.Back, AppEvent.AbandonConfirmed))
        assertEquals(emptyList<X01Cue>(), cuesOf(inGame(), AppEvent.NextPlayerTapped)) // nothing waits
        assertEquals(emptyList<X01Cue>(), cuesOf(inGame(), AppEvent.FinishGameTapped)) // no dialog
        assertEquals(emptyList<X01Cue>(), cuesOf(inGame(), AppEvent.MultiplierTapped(Multiplier.DOUBLE)))
    }

    /** AN7: the screens without a game announce nothing. */
    @Test
    fun `AN7 outside the game nothing is announced`() {
        assertEquals(emptyList<X01Cue>(), cuesOf(AppState(), AppEvent.NewGame))
        assertEquals(emptyList<X01Cue>(), cuesOf(AppState(), AppEvent.SettingsTapped, AppEvent.Back))
    }

    private fun numberEvent(dart: Dart): AppEvent = when (dart) {
        Dart.BULL -> AppEvent.BullTapped
        Dart.OUTER_BULL -> AppEvent.OuterBullTapped
        Dart.MISS -> AppEvent.MissTapped
        else -> AppEvent.NumberTapped(dart.segment)
    }
}
