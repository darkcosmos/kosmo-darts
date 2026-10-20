package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the visit total boxes (docs/rules/visit-total.md) for visual review, on a Pixel 7, in portrait and
 * landscape. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VisitTotalScreenshotTest {

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
    fun startPortrait() = capture("visit_total_start_2p_portrait_en", *START)

    @Test
    @Config(qualifiers = "+en-land")
    fun startLandscape() = capture("visit_total_start_2p_landscape_en", *START)

    @Test
    @Config(qualifiers = "+en-port")
    fun twoPlayersPortrait() = capture("visit_total_2p_mid_turn_portrait_en", *TWO_PLAYERS)

    @Test
    @Config(qualifiers = "+en-land")
    fun twoPlayersLandscape() = capture("visit_total_2p_mid_turn_landscape_en", *TWO_PLAYERS)

    @Test
    @Config(qualifiers = "+fr-port")
    fun twoPlayersPortraitFrench() = capture("visit_total_2p_mid_turn_portrait_fr", *TWO_PLAYERS)

    @Test
    @Config(qualifiers = "+en-port")
    fun fourPlayersPortrait() = capture("visit_total_4p_mid_turn_portrait_en", *FOUR_PLAYERS)

    @Test
    @Config(qualifiers = "+en-land")
    fun fourPlayersLandscape() = capture("visit_total_4p_mid_turn_landscape_en", *FOUR_PLAYERS)

    @Test
    @Config(qualifiers = "+fr-port")
    fun fourPlayersPortraitFrench() = capture("visit_total_4p_mid_turn_portrait_fr", *FOUR_PLAYERS)

    @Test
    @Config(qualifiers = "+en-port")
    fun bustPortrait() = capture("visit_total_bust_portrait_en", *BUST)

    @Test
    @Config(qualifiers = "+en-land")
    fun bustLandscape() = capture("visit_total_bust_landscape_en", *BUST)

    @Test
    @Config(qualifiers = "+en-port")
    fun oneEightyPortrait() = capture("visit_total_180_portrait_en", *ONE_EIGHTY)

    @Test
    @Config(qualifiers = "+en-land")
    fun oneEightyLandscape() = capture("visit_total_180_landscape_en", *ONE_EIGHTY)

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun longNamesPortraitLargestFont() = capture("visit_total_4p_long_names_portrait_en_font200", *LONG_NAMES)

    @Test
    @Config(qualifiers = "+en-land", fontScale = LARGEST_FONT_SCALE)
    fun longNamesLandscapeLargestFont() = capture("visit_total_4p_long_names_landscape_en_font200", *LONG_NAMES)

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun longNamesBustPortraitLargestFont() =
        capture("visit_total_4p_waiting_bust_portrait_en_font200", *LONG_NAMES_WAITING_BUST)

    @Test
    @Config(qualifiers = "+en-land", fontScale = LARGEST_FONT_SCALE)
    fun longNamesBustLandscapeLargestFont() =
        capture("visit_total_4p_waiting_bust_landscape_en_font200", *LONG_NAMES_WAITING_BUST)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f

        fun names(vararg names: String): Array<AppEvent> =
            names.mapIndexed { index, name -> AppEvent.NameChanged(index, name) }.toTypedArray()

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        fun singles(vararg numbers: Int): Array<AppEvent> = numbers.map { AppEvent.NumberTapped(it) }.toTypedArray()

        val NEXT = AppEvent.NextPlayerTapped

        /** 501, Ann and Bob: every box at 0. */
        val START: Array<AppEvent> = arrayOf(AppEvent.NewGame, *names("Ann", "Bob"), AppEvent.StartRequested)

        /** Ann scored 50; Bob has thrown T20. */
        val TWO_PLAYERS: Array<AppEvent> = arrayOf(*START, *singles(20, 15, 15), NEXT, *triple(20))

        /** 501, four players: Ann 50, Bob 180, Cid 15, Dan has thrown T19. */
        val FOUR_PLAYERS: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            *names("Ann", "Bob", "Cid", "Dan"),
            AppEvent.StartRequested,
            *singles(20, 15, 15), NEXT,
            *triple(20), *triple(20), *triple(20), NEXT,
            *singles(5, 5, 5), NEXT,
            *triple(19),
        )

        /** 301 double out: Ann leaves 121, Bob scores 3, then Ann's T20 T20 leaves 1, a bust waiting. */
        val BUST: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            *names("Ann", "Bob"),
            AppEvent.StartRequested,
            *triple(20), *triple(20), *triple(20), NEXT,
            *singles(1, 1, 1), NEXT,
            *triple(20), *triple(20),
        )

        /** Ann's 180 waits for "Next player". */
        val ONE_EIGHTY: Array<AppEvent> = arrayOf(*START, *triple(20), *triple(20), *triple(20))

        private val LONG_NAMES_SETUP: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            *names("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Jean-Baptiste Pierre"),
            AppEvent.StartRequested,
            *triple(20), *triple(20), *triple(20), NEXT,
            *singles(1, 1, 1), NEXT,
            *triple(20), *triple(20), *triple(20), NEXT,
            AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, NEXT,
        )

        /** 301, four 20-character names: Ann 180, Bob 3, Cid 180, Dan 0; Ann has thrown T20. */
        val LONG_NAMES: Array<AppEvent> = arrayOf(*LONG_NAMES_SETUP, *triple(20))

        /** The same, with Ann's T20 T20 leaving 1: a bust waiting for "Next player". */
        val LONG_NAMES_WAITING_BUST: Array<AppEvent> = arrayOf(*LONG_NAMES_SETUP, *triple(20), *triple(20))
    }
}
