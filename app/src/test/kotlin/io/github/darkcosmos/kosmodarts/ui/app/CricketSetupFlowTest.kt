package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.toLastSetups
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * From the home screen to a started Cricket game, and back to a resumed one (docs/rules/cricket.md, CR20, CR21;
 * docs/rules/persistence.md, R20 to R22).
 */
class CricketSetupFlowTest {

    private val reducer = AppReducer(Random(0))

    private fun AppState.after(vararg events: AppEvent): AppState = events.fold(this, reducer::reduce)

    private fun AppState.setup(): SetupState = (screen as Screen.Setup).setup

    private val x01Setup = X01MatchConfig(X01Config(listOf("Cid", "Ann"), 301, CheckoutRule.SINGLE_OUT))

    /** CR21, R22: the Cricket setup has the roster of the last game and the Cricket defaults. */
    @Test
    fun `CR21 R22 the Cricket setup takes the last roster and the Cricket defaults`() {
        val setup = AppState(lastSetup = x01Setup.toLastSetups()).after(AppEvent.NewGame(GameMode.CRICKET)).setup()

        assertEquals(GameMode.CRICKET, setup.mode)
        assertEquals(listOf("Cid", "Ann"), setup.names)
        assertEquals(CricketVariant.STANDARD, setup.cricketVariant)
        assertTrue(setup.isBullInPlay)
    }

    /** CR21: Start with valid names opens the Cricket game with the chosen settings, and they become the last ones. */
    @Test
    fun `CR21 Start opens the Cricket game with the chosen settings`() {
        val state = AppState(lastSetup = x01Setup.toLastSetups()).after(
            AppEvent.NewGame(GameMode.CRICKET),
            AppEvent.CricketVariantSelected(CricketVariant.CUT_THROAT),
            AppEvent.BullInPlayToggled(false),
            AppEvent.LegsPerSetChanged(1),
            AppEvent.StartRequested,
        )

        val screen = state.screen as Screen.CricketGame
        assertTrue(screen.isNewGame)
        assertEquals(listOf("Cid", "Ann"), screen.game.game.config.players)
        assertEquals(CricketVariant.CUT_THROAT, screen.game.game.config.variant)
        assertEquals(false, screen.game.game.config.isBullInPlay)
        assertEquals(MatchFormat(2, 1), screen.game.match.format)
        val cricket = checkNotNull(state.lastSetup).cricketSetup
        assertEquals(CricketVariant.CUT_THROAT to false, cricket.config.variant to cricket.config.isBullInPlay)
        assertEquals(301, state.lastSetup?.x01Setup?.config?.startScore)
    }

    /** R22: the Cricket settings come back at the next Cricket setup, and the X01 setup is not changed by them. */
    @Test
    fun `R22 the two modes keep their own settings`() {
        val afterCricket = AppState(lastSetup = x01Setup.toLastSetups()).after(
            AppEvent.NewGame(GameMode.CRICKET),
            AppEvent.CricketVariantSelected(CricketVariant.CUT_THROAT),
            AppEvent.StartRequested,
            AppEvent.AbandonConfirmed,
        )

        val cricketAgain = afterCricket.after(AppEvent.NewGame(GameMode.CRICKET)).setup()
        val x01 = afterCricket.after(AppEvent.NewGame(GameMode.X01)).setup()

        assertEquals(CricketVariant.CUT_THROAT, cricketAgain.cricketVariant)
        assertEquals(301, x01.startScore)
        assertEquals(CheckoutRule.SINGLE_OUT, x01.checkoutRule)
    }

    /** CR1: blank names stay on the Cricket setup with the error under the field. */
    @Test
    fun `CR1 a Cricket Start with a blank name stays on the setup`() {
        val setup = AppState().after(AppEvent.NewGame(GameMode.CRICKET), AppEvent.StartRequested).setup()

        assertEquals(GameMode.CRICKET, setup.mode)
        assertEquals(listOf(X01ConfigError.BLANK_NAME), setup.errorsFor(0))
    }

    /** R20, CR20: a saved Cricket game on the home screen is resumed, between legs included. */
    @Test
    fun `R20 Resume opens the saved Cricket game on its screen`() {
        val playing = AppState(screen = Screen.Home(resumableGame = CricketSaveFixtures.MID_TURN))
            .after(AppEvent.ResumeTapped)
        val between = CricketSaveFixtures.saved(
            CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0),
            isBetweenLegs = true,
        )

        assertTrue(playing.screen is Screen.CricketGame)
        val resumed = AppState(screen = Screen.Home(resumableGame = between)).after(AppEvent.ResumeTapped)
        assertTrue(resumed.screen is Screen.CricketBetweenLegs)
    }

    /** R3, R20: any mode asks before replacing the game in progress of the other mode, then deletes it. */
    @Test
    fun `R20 starting X01 over a Cricket game asks and replaces it`() {
        val home = AppState(screen = Screen.Home(resumableGame = CricketSaveFixtures.MID_TURN))

        val asked = home.after(AppEvent.NewGame(GameMode.X01))
        val setup = asked.after(AppEvent.NewGameConfirmed)

        assertEquals(GameMode.X01, (asked.screen as Screen.Home).newGameDialogMode)
        assertEquals(GameMode.X01, setup.setup().mode)
        assertEquals(listOf(PersistenceEffect.DeleteGame), persistenceEffects(asked, setup))
    }

    /** R20, R21: after a kill, the saved Cricket game is offered on the home screen, or its screen comes back. */
    @Test
    fun `R20 R21 a saved Cricket game is on the home screen or on its screen after a kill`() {
        val load = LoadResult(GameLoad.Valid(CricketSaveFixtures.MID_TURN), lastSetup = null)

        assertEquals(
            Screen.Home(resumableGame = CricketSaveFixtures.MID_TURN),
            initialState(load, wasOnGameScreen = false).screen,
        )
        assertTrue(initialState(load, wasOnGameScreen = true).screen is Screen.CricketGame)
    }

    /** R20: a game that stays on the home screen stays saved (no write, no deletion), and an X01 one too. */
    @Test
    fun `R20 the home screen keeps the saved game of either mode`() {
        val cricket = AppState(screen = Screen.Home(resumableGame = CricketSaveFixtures.MID_TURN))
        val x01 = AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN))

        assertEquals(CricketSaveFixtures.MID_TURN, savedGameOf(cricket))
        assertEquals(SaveFixtures.MID_TURN, savedGameOf(x01))
        val settings = AppState(screen = Screen.Settings(cricket.screen as Screen.Home))
        assertEquals(CricketSaveFixtures.MID_TURN, savedGameOf(settings))
    }
}
