package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.VisitBoxText
import io.github.darkcosmos.kosmodarts.ui.game.visitBoxTexts
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** The winning dart, then "Finish game" in the checkout dialog. */
    private val finishEvents = arrayOf(lastWinningEvent, AppEvent.FinishGameTapped)

    private fun inGame(config: X01Config, darts: List<Dart> = emptyList()) =
        AppState(screen = Screen.Game(GameScreenState(X01Game(config, darts))))

    @Test
    fun theAppStartsOnHome() {
        assertEquals(Screen.Home(), AppState().screen)
    }

    @Test
    fun newGameFromHomeOpensTheDefaultSetup() {
        assertEquals(Screen.Setup(SetupState()), AppState().after(AppEvent.NewGame).screen)
    }

    @Test
    fun backFromSetupReturnsHome() {
        assertEquals(Screen.Home(), AppState().after(AppEvent.NewGame, AppEvent.Back).screen)
    }

    @Test
    fun setupEventsEditTheSetup() {
        val state = AppState().after(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.CheckoutRuleSelected(CheckoutRule.SINGLE_OUT),
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            AppEvent.PlayerRemoved(3),
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
    /** R7: the last setup is retained when a game starts. */
    fun validStartOpensTheGameAndRemembersTheSetup() {
        val state = AppState().after(
            AppEvent.NewGame,
            AppEvent.NameChanged(0, " Ann"),
            AppEvent.NameChanged(1, "Bob"),
            AppEvent.StartRequested,
        )

        val expected = GameScreenState(X01Game(X01Config(listOf("Ann", "Bob"))))
        assertEquals(Screen.Game(expected, isNewGame = true), state.screen)
        assertEquals(X01MatchConfig(X01Config(listOf("Ann", "Bob"))), state.lastSetup)
    }

    @Test
    /** R7: names in order, start score and checkout rule. */
    fun aNewSetupIsPrefilledWithTheLastSetup() {
        val state = AppState(lastSetup = X01MatchConfig(singleOut301)).after(AppEvent.NewGame)

        assertEquals(Screen.Setup(SetupState(301, CheckoutRule.SINGLE_OUT, listOf("Ann", "Bob", "Cid"))), state.screen)
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
        // The third dart completes the turn: Miss is ignored while it waits, Undo removes the bull.
        assertEquals(listOf(Dart.double(20), Dart.OUTER_BULL), game.darts)
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

        assertEquals(Screen.Home(), state.screen)
    }

    @Test
    fun theWinningDartWaitsForConfirmation() {
        val state = inGame(singleOut301, winningDarts).after(lastWinningEvent)

        val game = (state.screen as Screen.Game).game
        assertTrue(game.isAwaitingConfirmation)
        assertTrue(game.game.state.isFinished)
    }

    /** M15, owner O1: "Next player" is ignored while a checkout waits; back opens the abandon dialog, and dismissing it
     * keeps the checkout pending. */
    @Test
    fun `M15 O1 back on a pending checkout opens the abandon dialog`() {
        val pending = inGame(singleOut301, winningDarts).after(lastWinningEvent)
        assertEquals(pending, pending.after(AppEvent.NextPlayerTapped))

        val dialog = pending.after(AppEvent.Back)
        val game = (dialog.screen as Screen.Game).game
        assertTrue(game.isAbandonDialogShown)
        assertTrue(game.isCheckoutPending)

        assertEquals(pending, dialog.after(AppEvent.AbandonDismissed))
        assertEquals(Screen.Home(), dialog.after(AppEvent.AbandonConfirmed).screen)
    }

    @Test
    fun finishGameIsIgnoredWithoutCheckout() {
        val waiting = inGame(singleOut301).after(*Array(3) { AppEvent.NumberTapped(1) })

        assertEquals(waiting, waiting.after(AppEvent.FinishGameTapped))
    }

    @Test
    fun undoInTheCheckoutDialogReopensTheTurn() {
        val state = inGame(singleOut301, winningDarts).after(lastWinningEvent, AppEvent.UndoTapped)

        assertEquals(Screen.Game(GameScreenState(X01Game(singleOut301, winningDarts))), state.screen)
    }

    @Test
    fun nextPlayerMovesPlayOnAfterACompletedTurn() {
        val state = inGame(singleOut301).after(*Array(3) { AppEvent.NumberTapped(1) }, AppEvent.NextPlayerTapped)

        val game = (state.screen as Screen.Game).game
        assertFalse(game.isAwaitingConfirmation)
        assertEquals(1, game.game.state.currentPlayerIndex)
    }

    @Test
    fun finishGameOpensTheEndScreen() {
        val state = inGame(singleOut301, winningDarts).after(*finishEvents)

        assertEquals(Screen.End(X01Game(singleOut301, winningDarts + Dart.single(1))), state.screen)
    }

    @Test
    fun undoIsIgnoredOnTheEndScreen() {
        val end = inGame(singleOut301, winningDarts).after(*finishEvents)

        assertEquals(end, end.after(AppEvent.UndoTapped))
    }

    @Test
    fun rematchKeepsTheSettingsAndRotatesTheFirstPlayer() {
        val state = inGame(singleOut301, winningDarts).after(*finishEvents, AppEvent.Rematch)

        val rotated = X01Config(listOf("Bob", "Cid", "Ann"), 301, CheckoutRule.SINGLE_OUT)
        // PC7: each player keeps his color slot through the rotation.
        val slots = listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_3, ColorSlot.SLOT_1)
        val expected = GameScreenState(X01Match.start(X01MatchConfig(rotated, MatchFormat(), slots)))
        assertEquals(Screen.Game(expected, isNewGame = true), state.screen)
    }

    @Test
    fun newGameFromTheEndScreenOpensThePrefilledSetup() {
        val state = AppState(
            lastSetup = X01MatchConfig(singleOut301),
            screen = inGame(singleOut301, winningDarts).screen,
        )
            .after(*finishEvents, AppEvent.NewGame)

        assertEquals(Screen.Setup(SetupState.prefilled(X01MatchConfig(singleOut301))), state.screen)
    }

    /** VT2, VT5 (T1 of the #36 design review): a rematch starts with every box at 0, through the existing reducer. */
    @Test
    fun rematchStartsWithFreshVisitTotals() {
        val state = inGame(singleOut301, winningDarts).after(*finishEvents, AppEvent.Rematch)

        val screen = (state.screen as Screen.Game).game
        assertEquals(listOf(inProgressZero, notPlayed, notPlayed), visitBoxTexts(screen))
    }

    /** VT2, VT5: "New game" from the end screen, then Start: every box at 0. */
    @Test
    fun newGameAfterAGameStartsWithFreshVisitTotals() {
        val state = AppState(
            lastSetup = X01MatchConfig(singleOut301),
            screen = inGame(singleOut301, winningDarts).screen,
        )
            .after(*finishEvents, AppEvent.NewGame, AppEvent.StartRequested)

        val screen = (state.screen as Screen.Game).game
        assertEquals(listOf(inProgressZero, notPlayed, notPlayed), visitBoxTexts(screen))
    }

    @Test
    fun backFromTheEndScreenReturnsHome() {
        val state = inGame(singleOut301, winningDarts).after(*finishEvents, AppEvent.Back)

        assertEquals(Screen.Home(), state.screen)
    }

    @Test
    fun eventsOfAnotherScreenAreIgnored() {
        val home = AppState()
        val setup = AppState(screen = Screen.Setup(SetupState()))

        assertEquals(home, home.after(AppEvent.NumberTapped(20), AppEvent.StartRequested, AppEvent.Rematch))
        assertEquals(setup, setup.after(AppEvent.UndoTapped, AppEvent.AbandonConfirmed, AppEvent.NewGame))
    }

    private val inProgressZero =
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_in_progress_description, listOf(0))

    private val notPlayed =
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_not_played_description, listOf(0))

    private companion object {
        const val SEED = 7
    }
}
