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
 * Screenshots of the checkout suggestion line (docs/rules/checkout-suggestions.md) for visual review, on a Pixel 7:
 * with and without a suggestion, in portrait and landscape, English and French, 4 players, and at the largest font
 * scale. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CheckoutSuggestionScreenshotTest {

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
    fun withSuggestionPortrait() = capture("checkout_suggestion_170_portrait_en", *AT_170)

    @Test
    @Config(qualifiers = "+en-land")
    fun withSuggestionLandscape() = capture("checkout_suggestion_170_landscape_en", *AT_170)

    @Test
    @Config(qualifiers = "+fr-port")
    fun withSuggestionPortraitFrench() = capture("checkout_suggestion_170_portrait_fr", *AT_170)

    @Test
    @Config(qualifiers = "+fr-land")
    fun withSuggestionLandscapeFrench() = capture("checkout_suggestion_170_landscape_fr", *AT_170)

    @Test
    @Config(qualifiers = "+en-port")
    fun withoutSuggestionPortrait() = capture("checkout_suggestion_none_portrait_en", *START)

    @Test
    @Config(qualifiers = "+en-land")
    fun withoutSuggestionLandscape() = capture("checkout_suggestion_none_landscape_en", *START)

    @Test
    @Config(qualifiers = "+fr-port")
    fun withoutSuggestionPortraitFrench() = capture("checkout_suggestion_none_portrait_fr", *START)

    @Test
    @Config(qualifiers = "+fr-land")
    fun withoutSuggestionLandscapeFrench() = capture("checkout_suggestion_none_landscape_fr", *START)

    @Test
    @Config(qualifiers = "+en-port")
    fun singleWithoutSPortrait() = capture("checkout_suggestion_60_portrait_en", *AT_60)

    @Test
    @Config(qualifiers = "+fr-port")
    fun fourPlayersPortraitFrench() = capture("checkout_suggestion_4p_170_portrait_fr", *FOUR_PLAYERS_AT_170)

    @Test
    @Config(qualifiers = "+fr-land")
    fun fourPlayersLandscapeFrench() = capture("checkout_suggestion_4p_170_landscape_fr", *FOUR_PLAYERS_AT_170)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun withSuggestionPortraitFrenchLargestFont() =
        capture("checkout_suggestion_170_portrait_fr_font200", *AT_170)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun withoutSuggestionPortraitFrenchLargestFont() =
        capture("checkout_suggestion_none_portrait_fr_font200", *START)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun withSuggestionLandscapeFrenchLargestFont() =
        capture("checkout_suggestion_170_landscape_fr_font200", *AT_170)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** One player, Ann, 301 double out, at the start: no suggestion. */
        val START: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.PlayerRemoved,
            AppEvent.NameChanged(0, "Ann"),
            AppEvent.StartRequested,
        )

        /** T20, T20, 11 then "Next player": 170 left, "T20 · T20 · Bull". */
        val AT_170: Array<AppEvent> =
            arrayOf(*START, *triple(20), *triple(20), AppEvent.NumberTapped(11), AppEvent.NextPlayerTapped)

        /** From 170: T20, 20, T10 then "Next player": 60 left, "20 · D20". */
        val AT_60: Array<AppEvent> =
            arrayOf(*AT_170, *triple(20), AppEvent.NumberTapped(20), *triple(10), AppEvent.NextPlayerTapped)

        /** Four players, 301: Ann leaves 170, the others miss, Ann is back with 170 left. */
        val FOUR_PLAYERS_AT_170: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            AppEvent.NameChanged(0, "Ann"),
            AppEvent.NameChanged(1, "Bob"),
            AppEvent.NameChanged(2, "Cid"),
            AppEvent.NameChanged(3, "Dan"),
            AppEvent.StartRequested,
            *triple(20), *triple(20), AppEvent.NumberTapped(11), AppEvent.NextPlayerTapped,
            AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped,
            AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped,
            AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped,
        )
    }
}
