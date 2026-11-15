package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.players.PlayersScreen
import io.github.darkcosmos.kosmodarts.ui.players.PlayersState
import io.github.darkcosmos.kosmodarts.ui.players.loadedPlayers
import io.github.darkcosmos.kosmodarts.ui.players.playerSummary
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Players screen (PM2) for visual review, on a Pixel 7: the list and the empty state, in portrait,
 * landscape and at the largest font (the actions are on the Profile, PS7). The state is given directly. Record only:
 * see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlayersScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val longName = "Maximilien-Alexandre"
    private val kevin = playerSummary(1, "Kevin", matches = 12)
    private val lea = playerSummary(2, "Léa", matches = 1)
    private val ana = playerSummary(3, "Ana", matches = 0)
    private val max = playerSummary(4, longName, matches = 27)

    private val listState = loadedPlayers(kevin, lea, ana, max)
    private val emptyState = loadedPlayers()

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/players_$name.png")
    }

    private fun players(name: String, state: PlayersState) =
        captureScreen(name) { PlayersScreen(state, onEvent = {}) }

    @Test
    @Config(qualifiers = "+fr-port")
    fun listPortrait() = players("list_portrait_fr", listState)

    @Test
    @Config(qualifiers = "+en-land")
    fun listLandscape() = players("list_landscape_en", listState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun listPortraitLargestFont() = players("list_portrait_fr_font200", listState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun emptyPortrait() = players("empty_portrait_fr", emptyState)

    @Test
    @Config(qualifiers = "+en-land", fontScale = LARGEST_FONT_SCALE)
    fun emptyLandscapeLargestFont() = players("empty_landscape_en_font200", emptyState)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
