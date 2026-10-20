package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The Settings screen in the app's navigation (docs/rules/announcements.md, AN13). */
class SettingsNavigationTest {

    private val reducer = AppReducer(Random(0))

    @Test
    fun `AN13 the gear opens Settings from home, and back returns to the same home`() {
        listOf(Screen.Home(), Screen.Home(resumableGame = SaveFixtures.WAITING_BUST)).forEach { home ->
            val settings = reducer.reduce(AppState(screen = home), AppEvent.SettingsTapped)
            assertEquals(Screen.Settings(home), settings.screen)
            assertEquals(home, reducer.reduce(settings, AppEvent.Back).screen)
        }
    }

    @Test
    fun `AN13 the gear does nothing while the new game dialog is open`() {
        val home = AppState(screen = Screen.Home(SaveFixtures.MID_TURN, isNewGameDialogShown = true))
        assertEquals(home, reducer.reduce(home, AppEvent.SettingsTapped))
    }

    @Test
    fun `AN13 Settings cannot be opened from the setup, game or end screens`() {
        listOf(
            Screen.Setup(SetupState.prefilled(null)),
            Screen.Game(SaveFixtures.MID_TURN.game.let { io.github.darkcosmos.kosmodarts.ui.game.GameScreenState(it) }),
            Screen.End(SaveFixtures.WAITING_CHECKOUT.game),
        ).forEach { screen ->
            val state = AppState(screen = screen)
            assertEquals(state, reducer.reduce(state, AppEvent.SettingsTapped))
        }
    }

    @Test
    fun `AN13 R2 opening and leaving Settings neither writes nor deletes the saved game`() {
        val home = AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN))
        val settings = reducer.reduce(home, AppEvent.SettingsTapped)
        val back = reducer.reduce(settings, AppEvent.Back)

        assertEquals(SaveFixtures.MID_TURN, savedGameOf(settings))
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(home, settings))
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(settings, back))
    }

    @Test
    fun `AN13 other events on Settings change nothing`() {
        val settings = AppState(screen = Screen.Settings(Screen.Home()))
        listOf(AppEvent.NewGame, AppEvent.ResumeTapped, AppEvent.NumberTapped(20), AppEvent.SettingsTapped).forEach {
            assertEquals(settings, reducer.reduce(settings, it))
        }
    }
}
