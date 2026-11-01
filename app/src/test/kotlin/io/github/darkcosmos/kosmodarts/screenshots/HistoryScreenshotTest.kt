package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScreen
import io.github.darkcosmos.kosmodarts.ui.history.HistoryState
import io.github.darkcosmos.kosmodarts.ui.history.LOADING_INDICATOR_DELAY_MILLIS
import io.github.darkcosmos.kosmodarts.ui.history.cricketHistoryMatch
import io.github.darkcosmos.kosmodarts.ui.history.okList
import io.github.darkcosmos.kosmodarts.ui.history.profilesOf
import io.github.darkcosmos.kosmodarts.ui.history.x01HistoryMatch
import io.github.darkcosmos.kosmodarts.ui.home.HomeScreen
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the History screen (HS2 to HS4, HS7) for visual review, on a Pixel 7: loading, empty archive, read
 * failure, no match and a filled list (X01 and Cricket, one to four players, long names), in portrait and landscape,
 * French or English, and at the largest font. The state is given directly. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HistoryScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val profiles = profilesOf("Anna", "Bob", "Cleo", "Dan")
    private val longName = "Maximilien-Alexandre de la Tour d'Auvergne"

    private val filledState: HistoryState = HistoryState().loaded(
        1,
        okList(
            listOf(
                cricketHistoryMatch(5, "Anna", "Bob", "Cleo"),
                x01HistoryMatch(
                    4, longName, "Bob", "Cleo", "Dan",
                    format = MatchFormat(legsPerSet = 3, sets = 2),
                    legs = listOf(6, 5, 2, 1),
                    sets = listOf(2, 1, 0, 0),
                ),
                x01HistoryMatch(3, "Anna", "Bob", format = MatchFormat(legsPerSet = 2), legs = listOf(2, 1)),
                x01HistoryMatch(2, "Cleo"),
                x01HistoryMatch(1, "Dan", "Anna"),
            ),
            profiles,
        ),
    )
    private val emptyState = HistoryState().loaded(1, okList(emptyList()))
    private val failedState = HistoryState().loaded(1, DbResult.Failed)
    private val noMatchState = HistoryState(filters = HistoryFilters(ArchivedMode.CRICKET, profileId = 2))
        .loaded(1, okList(emptyList(), profiles))

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/history_$name.png")
    }

    private fun history(name: String, state: HistoryState) = captureScreen(name) { HistoryScreen(state, onEvent = {}) }

    @Test
    @Config(qualifiers = "+fr-port")
    fun filledPortraitFrench() = history("filled_portrait_fr", filledState)

    @Test
    @Config(qualifiers = "+en-port")
    fun filledPortraitEnglish() = history("filled_portrait_en", filledState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun filledLandscapeFrench() = history("filled_landscape_fr", filledState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun filledPortraitLargestFont() = history("filled_portrait_fr_font200", filledState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun filledLandscapeLargestFont() = history("filled_landscape_fr_font200", filledState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun emptyPortraitFrench() = history("empty_portrait_fr", emptyState)

    @Test
    @Config(qualifiers = "+en-port")
    fun emptyPortraitEnglish() = history("empty_portrait_en", emptyState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun emptyLandscapeFrench() = history("empty_landscape_fr", emptyState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun emptyPortraitLargestFont() = history("empty_portrait_fr_font200", emptyState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun failedPortraitFrench() = history("failed_portrait_fr", failedState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun failedLandscapeLargestFont() = history("failed_landscape_fr_font200", failedState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun noMatchPortraitFrench() = history("no_match_portrait_fr", noMatchState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun noMatchPortraitLargestFont() = history("no_match_portrait_fr_font200", noMatchState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun loadingPortraitFrench() {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { HistoryScreen(HistoryState(), onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MILLIS + 50)
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/history_loading_portrait_fr.png")
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeWithTheHistoryButton() =
        captureScreen("home_button_portrait_fr") { HomeScreen(Screen.Home(), onEvent = {}) }

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
