package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.home.HomeScreen
import io.github.darkcosmos.kosmodarts.ui.setup.SetupResult
import io.github.darkcosmos.kosmodarts.ui.setup.SetupScreen
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Cricket setup and of the home screen with a Cricket game in progress (docs/rules/cricket.md,
 * CR20, CR21) for visual review, on a Pixel 7, French unless the name ends in "_en". The state is given directly.
 * Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CricketSetupScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/cricket_$name.png")
    }

    private fun setup(name: String, state: SetupState) =
        captureScreen(name) { SetupScreen(state, onEvent = {}) }

    private fun home(name: String, saved: StoredGame) =
        captureScreen(name) { HomeScreen(Screen.Home(resumableGame = saved), onEvent = {}) }

    private val cricketSetup = SetupState(names = listOf("Anna", "Bob")).copy(mode = GameMode.CRICKET)

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupDefaults() = setup("setup_defaults_fr", cricketSetup)

    @Test
    @Config(qualifiers = "+en-port")
    fun setupDefaultsEnglish() = setup("setup_defaults_en", cricketSetup)

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupCutThroatWithoutBull() = setup(
        "setup_cut_throat_no_bull_fr",
        cricketSetup.withCricketVariant(CricketVariant.CUT_THROAT).withBullInPlay(false),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupFourPlayersMatch() = setup(
        "setup_4p_match_fr",
        SetupState(names = listOf("Anna", "Bob", "Cid", "Dan")).copy(mode = GameMode.CRICKET)
            .withLegsPerSet(3)
            .withSets(2),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupNameError() {
        val invalid = SetupState(names = listOf("Anna", "anna", "")).copy(mode = GameMode.CRICKET).start()
        setup("setup_name_error_fr", (invalid as SetupResult.Invalid).setup)
    }

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun setupLargestFont() = setup(
        "setup_font200_fr",
        cricketSetup.withCricketVariant(CricketVariant.CUT_THROAT),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeWithCricketGame() = home("home_game_fr", CricketSaveFixtures.saved(standardGame()))

    @Test
    @Config(qualifiers = "+en-port")
    fun homeWithCricketGameEnglish() = home("home_game_en", CricketSaveFixtures.saved(standardGame()))

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeWithSoloGame() = home(
        "home_solo_fr",
        CricketSaveFixtures.saved(soloGame()),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeWithCutThroatMatch() = home(
        "home_match_cut_throat_fr",
        CricketSaveFixtures.saved(
            CricketSaveFixtures.match(
                "Anna",
                "Bob",
                "Cid",
                variant = CricketVariant.CUT_THROAT,
                isBullInPlay = false,
                legsPerSet = 2,
                sets = 2,
            ).confirmLegsWonBy(0, 0, 1),
        ),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeWithX01Game() = home("home_x01_game_fr", SaveFixtures.MID_TURN)

    private fun soloGame() = CricketSaveFixtures.match("Anna").throwing(*CricketSaveFixtures.darts("T20 T19"))

    private fun standardGame() = CricketSaveFixtures.match("Anna", "Bob")
        .throwing(*CricketSaveFixtures.darts("T20 T20 19", "T19"))

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
