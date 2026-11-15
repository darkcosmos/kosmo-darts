package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.players.playerSummary
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileContent
import io.github.darkcosmos.kosmodarts.ui.profile.ProfilePanel
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRoster
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileScreen
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData
import io.github.darkcosmos.kosmodarts.ui.profile.RenameError
import io.github.darkcosmos.kosmodarts.ui.profile.figures
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the actions of the Profile (PS7, PM11) for visual review, on a Pixel 7: "Manage player" folded and
 * unfolded, the rename panel with a name already taken, and the confirmations of a merge and of a deletion, in
 * portrait, landscape and at the largest font. The state is given directly. Record only: see
 * [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ProfileActionsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val kevin = playerSummary(1, "Kevin", matches = 2)
    private val lea = playerSummary(2, "Léa", matches = 1)

    private fun stateOf(isManageOpen: Boolean, panel: ProfilePanel? = null): ProfileState {
        val data = ProfileTestData.data(listOf(ProfileTestData.x01Raw(), ProfileTestData.cricketRaw()))
        val base = ProfileState(
            now = NOW,
            roster = ProfileRoster.Loaded(kevin, listOf(lea)),
            isManageOpen = isManageOpen,
            panel = panel,
        )
        return base.copy(content = ProfileContent.Loaded(data, data.figures(base.query)))
    }

    private fun capture(name: String, state: ProfileState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { ProfileScreen(state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/profile_actions_$name.png")
    }

    private val folded get() = stateOf(isManageOpen = false)
    private val actions get() = stateOf(isManageOpen = true)
    private val renameClash
        get() = stateOf(true, ProfilePanel.Rename("léa", RenameError.Taken(lea)))
    private val mergeConfirm get() = stateOf(true, ProfilePanel.MergeConfirm(lea))
    private val deleteConfirm get() = stateOf(true, ProfilePanel.DeleteConfirm(3))

    @Test
    @Config(qualifiers = "+en-port")
    fun foldedPortrait() = capture("folded_portrait_en", folded)

    @Test
    @Config(qualifiers = "+en-port")
    fun actionsPortrait() = capture("actions_portrait_en", actions)

    @Test
    @Config(qualifiers = "+fr-land")
    fun actionsLandscape() = capture("actions_landscape_fr", actions)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun actionsPortraitLargestFont() = capture("actions_portrait_fr_font200", actions)

    @Test
    @Config(qualifiers = "+fr-port")
    fun renameClashPortrait() = capture("rename_clash_portrait_fr", renameClash)

    @Test
    @Config(qualifiers = "+en-land")
    fun renameClashLandscape() = capture("rename_clash_landscape_en", renameClash)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun renameClashPortraitLargestFont() = capture("rename_clash_portrait_fr_font200", renameClash)

    @Test
    @Config(qualifiers = "+en-port")
    fun mergeConfirmPortrait() = capture("merge_confirm_portrait_en", mergeConfirm)

    @Test
    @Config(qualifiers = "+fr-land")
    fun mergeConfirmLandscape() = capture("merge_confirm_landscape_fr", mergeConfirm)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun mergeConfirmPortraitLargestFont() = capture("merge_confirm_portrait_fr_font200", mergeConfirm)

    @Test
    @Config(qualifiers = "+fr-port")
    fun deleteConfirmPortrait() = capture("delete_confirm_portrait_fr", deleteConfirm)

    @Test
    @Config(qualifiers = "+en-land")
    fun deleteConfirmLandscape() = capture("delete_confirm_landscape_en", deleteConfirm)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun deleteConfirmLandscapeLargestFont() = capture("delete_confirm_landscape_fr_font200", deleteConfirm)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
        const val NOW = 10_000L
    }
}
