package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.InvalidSaveReason
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The first screen from the files found at launch (rules R1, R2, R8, R9, R10). */
class InitialStateTest {

    private val savedGames = listOf(
        SaveFixtures.EMPTY,
        SaveFixtures.MID_TURN,
        SaveFixtures.WAITING_NORMAL,
        SaveFixtures.WAITING_BUST,
        SaveFixtures.WAITING_CHECKOUT,
    )
    private val lastSetups =
        listOf(null, SaveFixtures.config(players = 3, startScore = 301, rule = CheckoutRule.SINGLE_OUT))

    @Test
    fun `R1 back on the game screen after process death`() {
        for (saved in savedGames) {
            for (lastSetup in lastSetups) {
                val state = initialState(LoadResult(GameLoad.Valid(saved), lastSetup), wasOnGameScreen = true)

                assertEquals(AppState(Screen.Game(saved.toGameScreenState()), lastSetup), state)
            }
        }
    }

    @Test
    fun `R2 a fresh launch, or a return to another screen, shows home with the summary`() {
        for (saved in savedGames) {
            for (lastSetup in lastSetups) {
                val state = initialState(LoadResult(GameLoad.Valid(saved), lastSetup), wasOnGameScreen = false)

                assertEquals(AppState(Screen.Home(resumableGame = saved), lastSetup), state)
            }
        }
    }

    @Test
    fun `R2 without a saved game, home is unchanged`() {
        for (wasOnGameScreen in listOf(false, true)) {
            for (lastSetup in lastSetups) {
                val state = initialState(LoadResult(GameLoad.Absent, lastSetup), wasOnGameScreen)

                assertEquals(AppState(Screen.Home(), lastSetup), state)
            }
        }
    }

    /** R9, R8: the message opens over the setup, which keeps the last setup (or the defaults, R10). */
    @Test
    fun `R9 an unreadable saved game opens the message over the prefilled setup`() {
        for (reason in InvalidSaveReason.entries) {
            for (wasOnGameScreen in listOf(false, true)) {
                for (lastSetup in lastSetups) {
                    val state = initialState(LoadResult(GameLoad.Invalid(reason), lastSetup), wasOnGameScreen)

                    val expected = Screen.Setup(SetupState.prefilled(lastSetup), isRestoreFailedShown = true)
                    assertEquals(AppState(expected, lastSetup), state)
                }
            }
        }
    }
}
