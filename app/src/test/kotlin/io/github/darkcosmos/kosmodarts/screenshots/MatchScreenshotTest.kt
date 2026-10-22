package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the match screens (docs/rules/match.md, M13 to M18; U1 to U8 of the design review), on a Pixel 7.
 * Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MatchScreenshotTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    @After
    fun tearDown() = process.close()

    /** Whole screen, dialog windows included. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        captureScreenRoboImage("$directory/$name.png")
    }

    /** A fresh launch, then [events] sent to the ViewModel, then the capture. */
    private fun captureAfter(name: String, events: List<AppEvent>) {
        process.launchFresh()
        val viewModel = ViewModelProvider(process.activity)[KosmoDartsViewModel::class.java]
        process.activity.runOnUiThread { events.forEach(viewModel::onEvent) }
        capture(name)
    }

    private fun captureHome(name: String, saved: SavedGame) {
        process.writeGame(saved)
        process.launchFresh()
        capture(name)
    }

    // Setup (M13, U4).

    @Test
    @Config(qualifiers = "+en-port")
    fun setupSingleGame() = captureAfter("match_setup_1x1_portrait_en", listOf(AppEvent.NewGame))

    @Test
    @Config(qualifiers = "+en-port")
    fun setupTwoSetsOfThree() = captureAfter("match_setup_3x2_portrait_en", SETUP_3X2)

    @Test
    @Config(qualifiers = "+fr-port")
    fun setupTwoSetsOfThreeFrench() = captureAfter("match_setup_3x2_portrait_fr", SETUP_3X2)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun setupTwoSetsOfThreeFrenchLargestFont() = captureAfter("match_setup_3x2_portrait_fr_font200", SETUP_3X2)

    // Game screen indicators (M14, U1 to U3).

    @Test
    @Config(qualifiers = "+en-port")
    fun twoPlayersPortrait() = captureAfter("match_cards_2p_portrait_en", twoPlayers())

    @Test
    @Config(qualifiers = "+en-land")
    fun twoPlayersLandscape() = captureAfter("match_cards_2p_landscape_en", twoPlayers())

    @Test
    @Config(qualifiers = "+en-port")
    fun fourPlayersPortrait() = captureAfter("match_cards_4p_portrait_en", fourPlayers())

    @Test
    @Config(qualifiers = "+en-land")
    fun fourPlayersLandscape() = captureAfter("match_cards_4p_landscape_en", fourPlayers())

    /** U1: 4 long names at 200 %, the last player highlighted. */
    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun fourLongNamesLastPlayerLargestFont() =
        captureAfter("match_cards_4p_long_names_last_player_portrait_en_font200", fourLongNamesLastPlayer())

    /** U3: a group of 5 legs or more shows one mark and "×N". */
    @Test
    @Config(qualifiers = "+en-port")
    fun overflowCount() = captureAfter(
        "match_cards_overflow_portrait_en",
        MatchScript().start("Ann", "Bob", legsPerSet = 9, sets = 2).legsWonBy(0, 0, 0, 0, 0).events,
    )

    // Checkout dialog (M15).

    @Test
    @Config(qualifiers = "+en-port")
    fun dialogLeg() = captureAfter("match_dialog_leg_portrait_en", script().playLegWonBy(0).events)

    @Test
    @Config(qualifiers = "+en-port")
    fun dialogSet() = captureAfter("match_dialog_set_portrait_en", script().legsWonBy(0).playLegWonBy(0).events)

    @Test
    @Config(qualifiers = "+en-port")
    fun dialogMatch() =
        captureAfter("match_dialog_match_portrait_en", script().legsWonBy(0, 0, 0).playLegWonBy(0).events)

    // Between legs (M16, U7, T5).

    @Test
    @Config(qualifiers = "+en-port")
    fun betweenLegsPortrait() = captureAfter("match_between_legs_portrait_en", betweenLegs())

    @Test
    @Config(qualifiers = "+en-land")
    fun betweenLegsLandscape() = captureAfter("match_between_legs_landscape_en", betweenLegs())

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun betweenLegsFrenchLargestFont() = captureAfter("match_between_legs_portrait_fr_font200", betweenLegs())

    // End screen (M17, U8).

    @Test
    @Config(qualifiers = "+en-port")
    fun endFourLongNames() = captureAfter("match_end_4p_long_names_portrait_en", endFourLongNames4p())

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun endFourLongNamesLargestFont() =
        captureAfter("match_end_4p_long_names_portrait_en_font200", endFourLongNames4p())

    // Home summary (M18, T5).

    @Test
    @Config(qualifiers = "+en-port")
    fun homeSummary() = captureHome("match_home_summary_portrait_en", SAVED_MATCH)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun homeSummaryFrenchLargestFont() = captureHome("match_home_summary_portrait_fr_font200", SAVED_MATCH)

    private fun script() = MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 2)

    private fun twoPlayers() = MatchScript().start("Ann", "Bob", legsPerSet = 3, sets = 3)
        .legsWonBy(0, 0, 0, 1, 1)
        .then(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(20))
        .events

    private fun fourPlayers() = MatchScript().start("Ann", "Bob", "Cid", "Dan", legsPerSet = 3, sets = 3)
        .legsWonBy(0, 1, 1, 1, 2)
        .events

    private fun fourLongNamesLastPlayer(): List<AppEvent> {
        val script = MatchScript().start(*LONG_NAMES, legsPerSet = 3, sets = 3).legsWonBy(0, 1, 1, 1, 2)
        // Leg 6 starts with the second player: two turns of misses bring the turn to the last player.
        val missedTurn = Array<AppEvent>(3) { AppEvent.MissTapped } + AppEvent.NextPlayerTapped
        repeat(2) { script.then(*missedTurn) }
        return script.events
    }

    private fun betweenLegs() = MatchScript().start("Ann", "Bob", "Clémentine Dubois", legsPerSet = 2, sets = 3)
        .legsWonBy(0, 0, 2, 1)
        .playLegWonBy(2)
        .finish()
        .events

    private fun endFourLongNames4p() = MatchScript().start(*LONG_NAMES, legsPerSet = 2, sets = 2)
        .legsWonBy(1, 1, 2, 0, 0, 3, 0, 0)
        .events

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f

        val LONG_NAMES =
            arrayOf("Alexandrine Martinez", "Bartholomew Jenkins", "Clementine Dubois", "Dimitri Kowalczyk")

        val SETUP_3X2: List<AppEvent> =
            listOf(AppEvent.NewGame) + List(2) { AppEvent.LegsPerSetChanged(1) } + AppEvent.SetsChanged(1)

        val SAVED_MATCH = SavedGame(
            match("Ann", "Bob", "Clementine Dubois", legsPerSet = 3, sets = 2)
                .confirmLegsWonBy(0, 0, 0, 2)
                .throwing(Dart.triple(20)),
            isAwaitingNextPlayer = false,
        )
    }
}
