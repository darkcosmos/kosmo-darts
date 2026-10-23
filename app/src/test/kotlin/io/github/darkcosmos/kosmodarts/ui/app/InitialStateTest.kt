package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.CricketSettings
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.InvalidSaveReason
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SharedRoster
import io.github.darkcosmos.kosmodarts.persistence.toLastSetups
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
        listOf(
            null,
            SaveFixtures.setup(players = 3, startScore = 301, rule = CheckoutRule.SINGLE_OUT).toLastSetups(),
        )

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

                    val expected = Screen.Setup(SetupState.prefilled(lastSetup?.x01Setup), isRestoreFailedShown = true)
                    assertEquals(AppState(expected, lastSetup), state)
                }
            }
        }
    }

    /**
     * R22, R25: a last setup without X01 settings (only Cricket games since it was written) prefills the X01 setup
     * with the shared roster and the X01 defaults, and the whole last setup is kept for the next start.
     */
    @Test
    fun `R22 a last setup without X01 settings prefills the shared roster with the X01 defaults`() {
        val roster = SharedRoster(listOf("Cid", "Ann"), listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1))
        val cricketOnly = LastSetups(roster, cricket = CricketSettings(CricketVariant.CUT_THROAT))

        val state = initialState(LoadResult(GameLoad.Invalid(InvalidSaveReason.MALFORMED), cricketOnly), false)

        val expected = X01MatchConfig(X01Config(listOf("Cid", "Ann")), colorSlots = roster.colorSlots)
        assertEquals(Screen.Setup(SetupState.prefilled(expected), isRestoreFailedShown = true), state.screen)
        assertEquals(cricketOnly, state.lastSetup)
    }
}
