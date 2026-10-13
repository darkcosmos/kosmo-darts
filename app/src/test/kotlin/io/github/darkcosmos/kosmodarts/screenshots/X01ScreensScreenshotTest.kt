package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the X01 screens for visual review, on a Pixel 7 (JVM only, Robolectric + Roborazzi).
 *
 * Each state is reached by sending user events to the activity ViewModel, then captured in portrait English,
 * landscape English and portrait French. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class X01ScreensScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/$name.png")
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun setupDefaultPortraitEnglish() = capture("setup_default_portrait_en", *SETUP_DEFAULT)

    @Test
    @Config(qualifiers = "+en-land")
    fun setupDefaultLandscapeEnglish() = capture("setup_default_landscape_en", *SETUP_DEFAULT)

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupDefaultPortraitFrench() = capture("setup_default_portrait_fr", *SETUP_DEFAULT)

    @Test
    @Config(qualifiers = "+en-port")
    fun setupErrorsPortraitEnglish() = capture("setup_errors_portrait_en", *SETUP_ERRORS)

    @Test
    @Config(qualifiers = "+en-land")
    fun setupErrorsLandscapeEnglish() = capture("setup_errors_landscape_en", *SETUP_ERRORS)

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupErrorsPortraitFrench() = capture("setup_errors_portrait_fr", *SETUP_ERRORS)

    @Test
    @Config(qualifiers = "+en-port")
    fun gameStartPortraitEnglish() = capture("game_start_501_2p_portrait_en", *GAME_START)

    @Test
    @Config(qualifiers = "+en-land")
    fun gameStartLandscapeEnglish() = capture("game_start_501_2p_landscape_en", *GAME_START)

    @Test
    @Config(qualifiers = "+fr-port")
    fun gameStartPortraitFrench() = capture("game_start_501_2p_portrait_fr", *GAME_START)

    @Test
    @Config(qualifiers = "+en-port")
    fun gameDoubleActivePortraitEnglish() = capture("game_4p_double_active_portrait_en", *GAME_DOUBLE_ACTIVE)

    @Test
    @Config(qualifiers = "+en-land")
    fun gameDoubleActiveLandscapeEnglish() = capture("game_4p_double_active_landscape_en", *GAME_DOUBLE_ACTIVE)

    @Test
    @Config(qualifiers = "+fr-port")
    fun gameDoubleActivePortraitFrench() = capture("game_4p_double_active_portrait_fr", *GAME_DOUBLE_ACTIVE)

    @Test
    @Config(qualifiers = "+en-port")
    fun gameBustPortraitEnglish() = capture("game_bust_portrait_en", *GAME_BUST)

    @Test
    @Config(qualifiers = "+en-land")
    fun gameBustLandscapeEnglish() = capture("game_bust_landscape_en", *GAME_BUST)

    @Test
    @Config(qualifiers = "+fr-port")
    fun gameBustPortraitFrench() = capture("game_bust_portrait_fr", *GAME_BUST)

    @Test
    @Config(qualifiers = "+en-port")
    fun endTiePortraitEnglish() = capture("end_tie_portrait_en", *END_TIE)

    @Test
    @Config(qualifiers = "+en-land")
    fun endTieLandscapeEnglish() = capture("end_tie_landscape_en", *END_TIE)

    @Test
    @Config(qualifiers = "+fr-port")
    fun endTiePortraitFrench() = capture("end_tie_portrait_fr", *END_TIE)

    /** 2.0 is the largest font size offered by Android 14 and later. */
    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun gameDoubleActivePortraitEnglishLargestFont() =
        capture("game_4p_double_active_portrait_en_font200", *GAME_DOUBLE_ACTIVE)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f

        fun names(vararg names: String): Array<AppEvent> =
            names.mapIndexed { index, name -> AppEvent.NameChanged(index, name) }.toTypedArray()

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        fun singles(vararg numbers: Int): Array<AppEvent> = numbers.map { AppEvent.NumberTapped(it) }.toTypedArray()

        val SETUP_DEFAULT: Array<AppEvent> = arrayOf(AppEvent.NewGame)

        /** Second name blank, first and third the same ignoring case. */
        val SETUP_ERRORS: Array<AppEvent> =
            arrayOf(AppEvent.NewGame, AppEvent.PlayerAdded, *names("Ann", "", "ann"), AppEvent.StartRequested)

        val GAME_START: Array<AppEvent> = arrayOf(AppEvent.NewGame, *names("Ann", "Bob"), AppEvent.StartRequested)

        /** 501, four players: Ann and Bob have thrown, Cid has thrown one dart and pressed Double. */
        val GAME_DOUBLE_ACTIVE: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            *names("Ann", "Bob", "Cid", "Dan"),
            AppEvent.StartRequested,
            *triple(20),
            *triple(20),
            *singles(20),
            *singles(20, 5, 1),
            *triple(19),
            AppEvent.MultiplierTapped(Multiplier.DOUBLE),
        )

        /** 301 double out: Ann and Bob leave 121, then Ann's T20 T20 leaves 1, a bust. */
        val GAME_BUST: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            *names("Ann", "Bob"),
            AppEvent.StartRequested,
            *triple(20), *triple(20), *triple(20),
            *triple(20), *triple(20), *triple(20),
            *triple(20), *triple(20),
        )

        /** 301 single out: Bob and Cid both leave 241, Ann wins in two turns. */
        val END_TIE: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.CheckoutRuleSelected(CheckoutRule.SINGLE_OUT),
            AppEvent.PlayerAdded,
            *names("Ann", "Bob", "Cid"),
            AppEvent.StartRequested,
            *triple(20), *triple(20), *triple(20),
            *singles(20, 20, 20),
            *singles(20, 20, 20),
            *triple(20), *triple(20), *singles(1),
        )
    }
}
