package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Home with a saved game, the new-game confirmation and the restore-failed message (rules R2, R3, R6, R7, R9). */
class AppReducerResumeTest {

    private val reducer = AppReducer(Random(0))

    private fun AppState.after(vararg events: AppEvent): AppState = events.fold(this, reducer::reduce)

    private val lastSetup = X01Config(listOf("Cid", "Ann"), 301, CheckoutRule.SINGLE_OUT)
    private val saved = SaveFixtures.WAITING_BUST
    private val home = AppState(screen = Screen.Home(resumableGame = saved), lastSetup = lastSetup)

    @Test
    fun `R2 Resume game opens the saved game exactly as it was`() {
        val state = home.after(AppEvent.ResumeTapped)

        assertEquals(Screen.Game(saved.toGameScreenState()), state.screen)
        assertEquals(lastSetup, state.lastSetup)
    }

    @Test
    fun `R2 Resume game does nothing without a saved game`() {
        val empty = AppState()

        assertEquals(empty, empty.after(AppEvent.ResumeTapped))
    }

    @Test
    fun `R3 New game with a saved game asks first`() {
        val state = home.after(AppEvent.NewGame)

        assertEquals(Screen.Home(resumableGame = saved, isNewGameDialogShown = true), state.screen)
    }

    @Test
    fun `R3 Cancel and back keep the saved game`() {
        assertEquals(home, home.after(AppEvent.NewGame, AppEvent.NewGameDismissed))
        assertEquals(home, home.after(AppEvent.NewGame, AppEvent.Back))
    }

    @Test
    fun `R3 the dialog ignores other events`() {
        val dialog = home.after(AppEvent.NewGame)

        assertEquals(dialog, dialog.after(AppEvent.ResumeTapped, AppEvent.NewGame))
    }

    @Test
    fun `R3 Delete opens the prefilled setup, and back from it leaves nothing to resume`() {
        val setup = home.after(AppEvent.NewGame, AppEvent.NewGameConfirmed)

        assertEquals(Screen.Setup(SetupState(301, CheckoutRule.SINGLE_OUT, listOf("Cid", "Ann"))), setup.screen)
        assertEquals(Screen.Home(), setup.after(AppEvent.Back).screen)
    }

    @Test
    fun `R3 New game without a saved game opens the setup directly`() {
        val state = AppState(lastSetup = lastSetup).after(AppEvent.NewGame)

        assertEquals(Screen.Setup(SetupState.prefilled(lastSetup)), state.screen)
    }

    @Test
    fun `R6 Abandon leaves nothing to resume`() {
        val state = home.after(AppEvent.ResumeTapped, AppEvent.Back, AppEvent.AbandonConfirmed)

        assertEquals(Screen.Home(), state.screen)
    }

    @Test
    fun `R6 Finish game opens the end screen`() {
        val checkout = AppState(screen = Screen.Home(resumableGame = SaveFixtures.WAITING_CHECKOUT))

        val state = checkout.after(AppEvent.ResumeTapped, AppEvent.FinishGameTapped)

        assertEquals(Screen.End(SaveFixtures.WAITING_CHECKOUT.game), state.screen)
    }

    @Test
    fun `R7 a rematch becomes the last setup, and the next setup is prefilled with it`() {
        val checkout = AppState(screen = Screen.Home(resumableGame = SaveFixtures.WAITING_CHECKOUT))
        val ended = checkout.after(AppEvent.ResumeTapped, AppEvent.FinishGameTapped)
        val rematch = ended.after(AppEvent.Rematch)
        val config = SaveFixtures.WAITING_CHECKOUT.game.config
        val rotated = X01Config(config.players.drop(1) + config.players.first(), config.startScore, config.checkoutRule)

        assertEquals(rotated, rematch.lastSetup)
        val next = rematch.after(AppEvent.Back, AppEvent.AbandonConfirmed, AppEvent.NewGame)
        assertEquals(Screen.Setup(SetupState.prefilled(rotated)), next.screen)
    }

    /** R4, Q5: a resumed game has no active modifier, even if one was active when it was saved. */
    @Test
    fun `R4 a resumed game has no active modifier`() {
        val withDouble =
            home.after(AppEvent.ResumeTapped, AppEvent.UndoTapped, AppEvent.MultiplierTapped(Multiplier.DOUBLE))
        val stored = savedGameOf(withDouble)
        val resumed = AppState(screen = Screen.Home(resumableGame = stored)).after(AppEvent.ResumeTapped)

        assertEquals(Multiplier.SINGLE, (resumed.screen as Screen.Game).game.multiplier)
    }

    @Test
    fun `R9 OK and back close the message and stay on the setup`() {
        val message = AppState(screen = Screen.Setup(SetupState(), isRestoreFailedShown = true))

        assertEquals(Screen.Setup(SetupState()), message.after(AppEvent.RestoreFailedDismissed).screen)
        assertEquals(Screen.Setup(SetupState()), message.after(AppEvent.Back).screen)
    }

    @Test
    fun `R9 the setup ignores other events while the message is open`() {
        val message = AppState(screen = Screen.Setup(SetupState(), isRestoreFailedShown = true))

        assertEquals(message, message.after(AppEvent.StartRequested, AppEvent.PlayerAdded))
    }
}
