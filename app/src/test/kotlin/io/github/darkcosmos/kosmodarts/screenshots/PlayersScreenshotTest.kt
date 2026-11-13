package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.players.PlayersMessage
import io.github.darkcosmos.kosmodarts.ui.players.PlayersPanel
import io.github.darkcosmos.kosmodarts.ui.players.PlayersScreen
import io.github.darkcosmos.kosmodarts.ui.players.PlayersState
import io.github.darkcosmos.kosmodarts.ui.players.RenameError
import io.github.darkcosmos.kosmodarts.ui.players.loadedPlayers
import io.github.darkcosmos.kosmodarts.ui.players.playerSummary
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Players screen (PM2 to PM11) for visual review, on a Pixel 7: the list, the empty state, the open
 * card with its actions, the rename clash, the merge and delete confirmations and a refusal, in portrait, landscape
 * and at the largest font. The state is given directly. Record only: see [HomeScreenScreenshotTest].
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
    private val expandedState = loadedPlayers(kevin, lea, ana, max, expanded = 1)
    private val clashState = loadedPlayers(
        kevin, lea, ana, max,
        expanded = 1,
        panel = PlayersPanel.Rename("léa", RenameError.Taken(lea)),
    )
    private val mergeConfirmState = loadedPlayers(
        kevin, lea, ana, max,
        expanded = 1,
        panel = PlayersPanel.MergeConfirm(lea),
    )
    private val deleteConfirmState = loadedPlayers(
        kevin, lea, ana, max,
        expanded = 1,
        panel = PlayersPanel.DeleteConfirm(12),
    )
    private val refusedState = loadedPlayers(
        kevin, lea, ana, max,
        expanded = 1,
        message = PlayersMessage.InProgress("Kevin"),
    )

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

    @Test
    @Config(qualifiers = "+fr-port")
    fun expandedPortrait() = players("expanded_portrait_fr", expandedState)

    @Test
    @Config(qualifiers = "+en-land")
    fun expandedLandscape() = players("expanded_landscape_en", expandedState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun expandedPortraitLargestFont() = players("expanded_portrait_fr_font200", expandedState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun expandedLandscapeLargestFont() = players("expanded_landscape_fr_font200", expandedState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun renameClashPortrait() = players("rename_clash_portrait_fr", clashState)

    @Test
    @Config(qualifiers = "+en-land")
    fun renameClashLandscape() = players("rename_clash_landscape_en", clashState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun renameClashPortraitLargestFont() = players("rename_clash_portrait_fr_font200", clashState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun mergeConfirmPortrait() = players("merge_confirm_portrait_fr", mergeConfirmState)

    @Test
    @Config(qualifiers = "+en-land")
    fun mergeConfirmLandscape() = players("merge_confirm_landscape_en", mergeConfirmState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun mergeConfirmPortraitLargestFont() = players("merge_confirm_portrait_fr_font200", mergeConfirmState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun deleteConfirmPortrait() = players("delete_confirm_portrait_fr", deleteConfirmState)

    @Test
    @Config(qualifiers = "+en-land")
    fun deleteConfirmLandscape() = players("delete_confirm_landscape_en", deleteConfirmState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun deleteConfirmPortraitLargestFont() = players("delete_confirm_portrait_fr_font200", deleteConfirmState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun deleteConfirmLandscapeLargestFont() = players("delete_confirm_landscape_fr_font200", deleteConfirmState)

    @Test
    @Config(qualifiers = "+fr-port")
    fun refusedPortrait() = players("refused_portrait_fr", refusedState)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
