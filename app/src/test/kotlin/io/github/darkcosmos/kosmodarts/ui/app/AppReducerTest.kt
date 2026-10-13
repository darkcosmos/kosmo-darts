package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AppReducerTest {

    private val reducer = AppReducer(Random(SEED))

    private fun AppState.after(vararg events: AppEvent): AppState = events.fold(this, reducer::reduce)

    private val singleOut301 = X01Config(listOf("Ann", "Bob", "Cid"), 301, CheckoutRule.SINGLE_OUT)

    /** Ann wins 301 single out in her second turn; Bob and Cid score 60 each. */
    private val winningDarts = List(3) { Dart.triple(20) } +
        List(6) { Dart.single(20) } +
        listOf(Dart.triple(20), Dart.triple(20))

    private val lastWinningEvent = AppEvent.NumberTapped(1)

    private fun inGame(config: X01Config, darts: List<Dart> = emptyList()) =
        AppState(screen = Screen.Game(GameScreenState(X01Game(config, darts))))

    @Test
    fun theAppStartsOnHome() {
        assertEquals(Screen.Home, AppState().screen)
    }

    @Test
    fun newGameFromHomeOpensTheDefaultSetup() {
        assertEquals(Screen.Setup(SetupState()), AppState().after(AppEvent.NewGame).screen)
    }

    @Test
    fun backFromSetupReturnsHome() {
        assertEquals(Screen.Home, AppState().after(AppEvent.NewGame, AppEvent.Back).screen)
    }

    @Test
    fun setupEventsEditTheSetup() {
        val state = AppState().after(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.CheckoutRuleSelected(CheckoutRule.SINGLE_OUT),
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            AppEvent.PlayerRemoved,
            AppEvent.NameChanged(0, "Ann"),
            AppEvent.NameChanged(1, "Bob"),
            AppEvent.NameChanged(2, "Cid"),
        )

        val expected = SetupState(301, CheckoutRule.SINGLE_OUT, listOf("Ann", "Bob", "Cid"))
        assertEquals(Screen.Setup(expected), state.screen)
    }

    @Test
    fun shuffleUsesTheReducerRandomness() {
        val names = listOf("Ann", "Bob", "Cid", "Dan")
        val setup = AppState(screen = Screen.Setup(SetupState(names = names)))

        val shuffled = setup.after(AppEvent.PlayersShuffled).screen as Screen.Setup

        assertEquals(names.shuffled(Random(SEED)), shuffled.setup.names)
    }

    @Test
    fun invalidStartStaysOnSetupWithErrors() {
        val state = AppState().after(AppEvent.NewGame, AppEvent.StartRequested)

        val setup = (state.screen as Screen.Setup).setup
        assertEquals(listOf(X01ConfigError.BLANK_NAME), setup.errorsFor(0))
    }

    @Test
    fun validStartOpensTheGameAndRemembersTheNames() {
        val state = AppState().after(
            AppEvent.NewGame,
            AppEvent.NameChanged(0, " Ann"),
            AppEvent.NameChanged(1, "Bob"),
            AppEvent.StartRequested,
        )

        assertEquals(Screen.Game(GameScreenState(X01Game(X01Config(listOf("Ann", "Bob"))))), state.screen)
        assertEquals(listOf("Ann", "Bob"), state.lastNames)
    }

    @Test
    fun aNewSetupIsPrefilledWithTheLastNames() {
        val state = AppState(lastNames = listOf("Ann", "Bob", "Cid")).after(AppEvent.NewGame)

        assertEquals(Screen.Setup(SetupState.prefilled(listOf("Ann", "Bob", "Cid"))), state.screen)
    }

    @Test
    fun gameEventsReachTheGame() {
        val state = inGame(singleOut301).after(
            AppEvent.MultiplierTapped(Multiplier.DOUBLE),
            AppEvent.NumberTapped(20),
            AppEvent.OuterBullTapped,
            AppEvent.BullTapped,
            AppEvent.MissTapped,
            AppEvent.UndoTapped,
        )

        val game = (state.screen as Screen.Game).game.game
        assertEquals(listOf(Dart.double(20), Dart.OUTER_BULL, Dart.BULL), game.darts)
    }

    @Test
    fun backDuringAGameAsksForConfirmation() {
        val state = inGame(singleOut301).after(AppEvent.NumberTapped(20), AppEvent.Back)

        val game = (state.screen as Screen.Game).game
        assertTrue(game.isAbandonDialogShown)
        assertEquals(1, game.game.darts.size)
    }

    @Test
    fun dismissingTheConfirmationKeepsTheGame() {
        val state = inGame(singleOut301).after(AppEvent.NumberTapped(20), AppEvent.Back, AppEvent.AbandonDismissed)

        val game = (state.screen as Screen.Game).game
        assertEquals(GameScreenState(X01Game(singleOut301, listOf(Dart.single(20)))), game)
    }

    @Test
    fun confirmingTheConfirmationReturnsHome() {
        val state = inGame(singleOut301).after(AppEvent.Back, AppEvent.AbandonConfirmed)

        assertEquals(Screen.Home, state.screen)
    }

    @Test
    fun theWinningDartOpensTheEndScreen() {
        val state = inGame(singleOut301, winningDarts).after(lastWinningEvent)

        assertEquals(Screen.End(X01Game(singleOut301, winningDarts + Dart.single(1))), state.screen)
    }

    @Test
    fun undoFromTheEndScreenReturnsToTheGameWithoutTheWinningDart() {
        val state = inGame(singleOut301, winningDarts).after(lastWinningEvent, AppEvent.UndoTapped)

        assertEquals(Screen.Game(GameScreenState(X01Game(singleOut301, winningDarts))), state.screen)
    }

    @Test
    fun rematchKeepsTheSettingsAndRotatesTheFirstPlayer() {
        val state = inGame(singleOut301, winningDarts).after(lastWinningEvent, AppEvent.Rematch)

        val rotated = X01Config(listOf("Bob", "Cid", "Ann"), 301, CheckoutRule.SINGLE_OUT)
        assertEquals(Screen.Game(GameScreenState(X01Game(rotated))), state.screen)
    }

    @Test
    fun newGameFromTheEndScreenOpensThePrefilledSetup() {
        val names = listOf("Ann", "Bob", "Cid")
        val state = AppState(lastNames = names, screen = inGame(singleOut301, winningDarts).screen)
            .after(lastWinningEvent, AppEvent.NewGame)

        assertEquals(Screen.Setup(SetupState.prefilled(listOf("Ann", "Bob", "Cid"))), state.screen)
    }

    @Test
    fun backFromTheEndScreenReturnsHome() {
        val state = inGame(singleOut301, winningDarts).after(lastWinningEvent, AppEvent.Back)

        assertEquals(Screen.Home, state.screen)
    }

    @Test
    fun eventsOfAnotherScreenAreIgnored() {
        val home = AppState()
        val setup = AppState(screen = Screen.Setup(SetupState()))

        assertEquals(home, home.after(AppEvent.NumberTapped(20), AppEvent.StartRequested, AppEvent.Rematch))
        assertEquals(setup, setup.after(AppEvent.UndoTapped, AppEvent.AbandonConfirmed, AppEvent.NewGame))
    }

    private companion object {
        const val SEED = 7
    }
}
