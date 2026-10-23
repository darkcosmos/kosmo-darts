package io.github.darkcosmos.kosmodarts.screenshots

import android.animation.ValueAnimator
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures of the redesigned screens (issue #49; docs/design/mockups), on a Pixel 7 unless the name says otherwise,
 * with animations off (the zone color is captured without its fade). French unless the name ends in "_en". Record
 * only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    /** "Remove animations" on: ValueAnimator.areAnimatorsEnabled() is false, so the zone color snaps (section 7). */
    @Before
    fun noAnimations() {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 0f)
    }

    @After
    fun restoreAnimations() {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
    }

    @After
    fun tearDown() = process.close()

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        captureScreenRoboImage("$directory/ds_$name.png")
    }

    private fun captureAfter(name: String, events: List<AppEvent>) {
        process.launchFresh()
        val viewModel = ViewModelProvider(process.activity)[KosmoDartsViewModel::class.java]
        process.activity.runOnUiThread { events.forEach(viewModel::onEvent) }
        capture(name)
    }

    // Game (mockups 01 to 05).

    @Test
    @Config(qualifiers = "+fr-port")
    fun game3Players() = captureAfter("game_3p_sets_portrait_fr", threePlayers())

    @Test
    @Config(qualifiers = "+en-port")
    fun game3PlayersEnglish() = captureAfter("game_3p_sets_portrait_en", threePlayers())

    @Test
    @Config(qualifiers = "+fr-port")
    fun game4PlayersLongNames() = captureAfter("game_4p_long_names_portrait_fr", fourLongNames())

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST)
    fun game4PlayersLongNamesLargestFont() = captureAfter("game_4p_long_names_portrait_fr_font200", fourLongNames())

    @Test
    @Config(qualifiers = "+fr-land")
    fun gameLandscape3Players() = captureAfter("game_3p_sets_landscape_fr", threePlayers())

    @Test
    @Config(qualifiers = "+fr-land")
    fun gameLandscape4Players() = captureAfter("game_4p_long_names_landscape_fr", fourLongNames())

    @Test
    @Config(qualifiers = "+fr-port")
    fun bust() = captureAfter("game_bust_portrait_fr", bustEvents())

    @Test
    @Config(qualifiers = "+fr-land")
    fun bustLandscape() = captureAfter("game_bust_landscape_fr", bustEvents())

    @Test
    @Config(qualifiers = "+fr-port")
    fun checkoutLeg() = captureAfter("game_checkout_leg_portrait_fr", checkoutLeg3Players())

    @Test
    @Config(qualifiers = "+fr-land")
    fun checkoutLegLandscape() = captureAfter("game_checkout_leg_landscape_fr", checkoutLeg3Players())

    @Test
    @Config(qualifiers = "+fr-port")
    fun checkoutSingleGame() = captureAfter("game_checkout_match_portrait_fr", singleGameCheckout())

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST)
    fun checkoutSingleGameLargestFont() = captureAfter("game_checkout_match_portrait_fr_font200", singleGameCheckout())

    @Test
    @Config(qualifiers = "+fr-port")
    fun headerLegsOnly() = captureAfter(
        "game_header_legs_only_portrait_fr",
        MatchScript().start("Ann", "Bob", legsPerSet = 3).legsWonBy(1).events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun headerSingleGame() = captureAfter(
        "game_header_single_game_portrait_fr",
        MatchScript().start("Ann", "Bob").then(*triple(20)).events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun doubleActive() = captureAfter(
        "game_double_active_portrait_fr",
        MatchScript().start("Ann", "Bob").then(AppEvent.MultiplierTapped(Multiplier.DOUBLE)).events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun abandonDialog() = captureAfter(
        "game_abandon_dialog_portrait_fr",
        MatchScript().start("Ann", "Bob").then(AppEvent.Back).events,
    )

    // Small screens (U1).

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
    fun smallPortrait() = captureAfter("game_4p_small_360x640_fr", fourLongNames())

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST)
    fun smallPortraitLargestFont() = captureAfter("game_4p_small_360x640_fr_font200", fourLongNames())

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi")
    fun smallLandscape() = captureAfter("game_4p_small_640x360_fr", fourLongNames())

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = LARGEST)
    fun smallLandscapeLargestFont() = captureAfter("game_4p_small_640x360_fr_font200", fourLongNames())

    // A Pixel 7 in landscape inside its bars and cutout (owner review of PR #57).

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun ownerLandscape4Players() = captureAfter("game_4p_route_800x360_fr", fourPlayersRoute())

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun ownerLandscapeBust() = captureAfter("game_bust_800x360_fr", bustEvents())

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun ownerLandscapeCheckout() = captureAfter("game_checkout_leg_800x360_fr", checkoutLeg3Players())

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun ownerLandscapeNextLeg() = captureAfter(
        "next_leg_sets_800x360_fr",
        MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 3, sets = 2).legsWonBy(1, 1, 1, 2).playLegWonBy(1)
            .finish().events,
    )

    // Setup (mockup 07).

    @Test
    @Config(qualifiers = "+fr-port")
    fun setup4Players() = captureAfter("setup_4p_portrait_fr", setupEvents())

    @Test
    @Config(qualifiers = "+en-port")
    fun setup4PlayersEnglish() = captureAfter("setup_4p_portrait_en", setupEvents())

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST)
    fun setup4PlayersLargestFont() = captureAfter("setup_4p_portrait_fr_font200", setupEvents())

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST)
    fun setupFocusedLastField() {
        process.launchFresh()
        val viewModel = ViewModelProvider(process.activity)[KosmoDartsViewModel::class.java]
        process.activity.runOnUiThread { setupEvents().forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SetupTags.nameField(3)).performScrollTo().performClick()
        capture("setup_4p_focused_small_360x640_fr_font200")
    }

    // Between legs (mockup 08) and end (mockup 09).

    @Test
    @Config(qualifiers = "+fr-port")
    fun nextLegSets() = captureAfter(
        "next_leg_sets_portrait_fr",
        MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 3, sets = 2).legsWonBy(1, 1, 1, 2).playLegWonBy(1)
            .finish().events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun nextLegLegsOnly() = captureAfter(
        "next_leg_legs_only_portrait_fr",
        MatchScript().start("Ann", "Bob", legsPerSet = 3).playLegWonBy(0).finish().events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun endSets() = captureAfter(
        "end_sets_portrait_fr",
        MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2).legsWonBy(1, 1, 2, 1, 1).events,
    )

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST)
    fun endSetsLargestFont() = captureAfter(
        "end_sets_portrait_fr_font200",
        MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2).legsWonBy(1, 1, 2, 1, 1).events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun endLegsOnly() = captureAfter(
        "end_legs_only_portrait_fr",
        MatchScript().start("Ann", "Bob", legsPerSet = 2).legsWonBy(0, 1, 0).events,
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun endSingleGame() = captureAfter(
        "end_single_game_portrait_fr",
        MatchScript().start("Ann", "Bob").legsWonBy(1).events,
    )

    // Settings (mockup 10). The home screen: PersistenceScreenshotTest and MatchScreenshotTest.

    @Test
    @Config(qualifiers = "+fr-port")
    fun settings() = captureAfter("settings_portrait_fr", listOf(AppEvent.SettingsTapped))

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST)
    fun settingsLargestFont() = captureAfter("settings_portrait_fr_font200", listOf(AppEvent.SettingsTapped))

    private fun threePlayers() = MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 3, sets = 2, startScore = 501)
        .legsWonBy(0, 0, 0, 1)
        .then(*triple(20), AppEvent.NumberTapped(20), AppEvent.NumberTapped(20), AppEvent.NextPlayerTapped)
        .then(*triple(19))
        .events

    private fun fourLongNames() = MatchScript()
        .start("Anne-Sophie", "Maximilien-Alexandre", "Bob", "Clémentine", legsPerSet = 3, sets = 2, startScore = 501)
        .legsWonBy(0)
        .then(*triple(20), AppEvent.NumberTapped(20), AppEvent.NumberTapped(20), AppEvent.NextPlayerTapped)
        .events

    /** 4 players in a match: Ann leaves 121 and throws next, with the route on screen. */
    private fun fourPlayersRoute() = MatchScript()
        .start("Ann", "Bob", "Cid", "Dan", legsPerSet = 3, sets = 2, singleOut = false)
        .then(*triple(20), *triple(20), *triple(20), AppEvent.NextPlayerTapped)
        .then(*MISSED_TURN, *MISSED_TURN, *MISSED_TURN)
        .events

    /** 301 double out, Ann and Bob: Ann leaves 121, Bob 3, then Ann's T20 T20 leaves 1, a bust. */
    private fun bustEvents() = MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 3, sets = 2, singleOut = false)
        .then(*triple(20), *triple(20), *triple(20), AppEvent.NextPlayerTapped)
        .then(AppEvent.NumberTapped(1), AppEvent.NumberTapped(1), AppEvent.NumberTapped(1), AppEvent.NextPlayerTapped)
        .then(AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped)
        .then(*triple(20), *triple(20))
        .events

    private fun checkoutLeg3Players() = MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 3, sets = 2)
        .playLegWonBy(1)
        .events

    private fun singleGameCheckout() = MatchScript().start("Ann", "Bob").playLegWonBy(0).events

    private fun setupEvents() = listOf(
        AppEvent.NewGame,
        AppEvent.PlayerAdded,
        AppEvent.PlayerAdded,
        AppEvent.NameChanged(0, "Anne-Sophie"),
        AppEvent.NameChanged(1, "Bob"),
        AppEvent.NameChanged(2, "Clémentine"),
        AppEvent.LegsPerSetChanged(1),
        AppEvent.LegsPerSetChanged(1),
        AppEvent.SetsChanged(1),
    )

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST = 2.0f

        val MISSED_TURN: Array<AppEvent> =
            arrayOf(AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped)

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))
    }
}
