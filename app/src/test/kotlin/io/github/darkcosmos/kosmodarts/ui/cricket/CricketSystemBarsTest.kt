package io.github.darkcosmos.kosmodarts.ui.cricket

import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonByScoring20
import io.github.darkcosmos.kosmodarts.ui.KosmoDartsApp
import io.github.darkcosmos.kosmodarts.ui.app.AppState
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsUiState
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The system bars around the Cricket game (docs/rules/cricket.md CR22, Kosmo's Q5 B, review U1 and A3): hidden while
 * the landscape game is shown, shown again on every way out of it. The app is shown with a fake controller; the
 * decision itself is [SystemBarsPolicy] (`SystemBarsPolicyTest`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr-w914dp-h411dp-land-420dpi")
class CricketSystemBarsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val bars = FakeSystemBars()

    private val match = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 3)

    private var screen: Screen by mutableStateOf(Screen.CricketGame(CricketGameScreenState(match)))

    /** Whether the app gets a portrait space (the phone turned back), instead of the whole landscape screen. */
    private var isPortrait by mutableStateOf(false)

    private val settings = SettingsUiState(
        voiceEnabled = true,
        soundsEnabled = true,
        callEachDart = false,
        voiceStatus = VoiceStatus.CHECKING,
        canInstall = false,
    )

    private fun show() {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent {
                KosmoDartsTheme {
                    CompositionLocalProvider(LocalSystemBarsController provides bars) {
                        val space = if (isPortrait) {
                            Modifier.requiredSize(PORTRAIT_WIDTH.dp, PORTRAIT_HEIGHT.dp)
                        } else {
                            Modifier.fillMaxSize()
                        }
                        Box(space) {
                            KosmoDartsApp(AppState(screen), settings, onEvent = {}, onSettingsEvent = {})
                        }
                    }
                }
            }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showHiddenGame() {
        show()
        assertTrue("the landscape game hides the bars", bars.isHidden)
    }

    private fun leaveTo(next: Screen) {
        composeRule.runOnUiThread { screen = next }
        composeRule.waitForIdle()
    }

    @Test
    fun `CR22 Q5 B the landscape game hides the bars once`() {
        showHiddenGame()

        assertEquals(listOf("hide"), bars.calls)
    }

    @Test
    fun `CR22 U1 the window controller hides the bars so that a swipe shows them for a moment`() {
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val controller = checkNotNull(decor.windowInsetsController)

            WindowSystemBarsController(decor).hide()
            assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, controller.systemBarsBehavior)

            WindowSystemBarsController(decor).show()
        }
    }

    @Test
    fun `CR22 turning the phone back to portrait shows the bars again, and they stay shown`() {
        showHiddenGame()

        composeRule.runOnUiThread { isPortrait = true }
        composeRule.waitForIdle()

        assertFalse(bars.isHidden)
        assertEquals("hide", bars.calls.first())
        assertFalse("hidden again in portrait", bars.calls.drop(1).contains("hide"))
    }

    @Test
    fun `CR22 a game started in portrait never hides the bars`() {
        isPortrait = true
        show()

        assertFalse(bars.isHidden)
        assertFalse(bars.calls.contains("hide"))
    }

    @Test
    fun `CR22 between legs shows the bars again`() {
        showHiddenGame()

        leaveTo(Screen.CricketBetweenLegs(match.playLegWonBy(0).confirmLeg()))

        assertFalse(bars.isHidden)
    }

    @Test
    fun `CR22 the end of the match shows the bars again`() {
        showHiddenGame()

        leaveTo(Screen.CricketEnd(CricketSaveFixtures.match("Ann", "Bob").playLegWonByScoring20(0)))

        assertFalse(bars.isHidden)
    }

    @Test
    fun `CR22 abandoning the game shows the bars again on the home screen`() {
        showHiddenGame()
        leaveTo(Screen.CricketGame(CricketGameScreenState(match, isAbandonDialogShown = true)))
        assertTrue("still hidden behind the abandon dialog", bars.isHidden)

        leaveTo(Screen.Home())

        assertFalse(bars.isHidden)
    }

    @Test
    fun `CR22 the screen leaving the window (activity destroyed) shows the bars again`() {
        showHiddenGame()

        composeRule.runOnUiThread { composeRule.activity.setContentView(View(composeRule.activity)) }
        composeRule.waitForIdle()

        assertFalse(bars.isHidden)
    }

    /** Records the calls; [isHidden] is the state the last call left. */
    private class FakeSystemBars : SystemBarsController {
        val calls = mutableListOf<String>()

        val isHidden: Boolean
            get() = calls.lastOrNull() == "hide"

        override fun hide() {
            calls += "hide"
        }

        override fun show() {
            calls += "show"
        }
    }

    private companion object {
        const val PORTRAIT_WIDTH = 400
        const val PORTRAIT_HEIGHT = 411
    }
}
