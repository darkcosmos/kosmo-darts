package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.players.PlayersTags
import io.github.darkcosmos.kosmodarts.ui.players.playerSummary
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** PS2, PS7, PM11: the "Manage player" button of the Profile and the panels it unfolds, inside the screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class ProfileManageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()
    private val kevin = playerSummary(1, "Kevin", matches = 3)
    private val lea = playerSummary(2, "Lea", matches = 1)

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun profile(
        roster: ProfileRoster = ProfileRoster.Loaded(kevin, listOf(lea)),
        isManageOpen: Boolean = false,
        panel: ProfilePanel? = null,
        message: ProfileMessage? = null,
        request: ProfileRequest? = null,
        raws: Boolean = true,
    ): ProfileState {
        val data = ProfileTestData.data(if (raws) listOf(ProfileTestData.x01Raw()) else emptyList())
        val base = ProfileState(
            now = NOW,
            roster = roster,
            isManageOpen = isManageOpen,
            panel = panel,
            message = message,
            request = request,
        )
        return base.copy(content = ProfileContent.Loaded(data, data.figures(base.query)))
    }

    private fun showState(state: ProfileState) = show { ProfileScreen(state, events::add) }

    /** P1 A: the button is under the name, at least 48 dp high, and the actions are folded. */
    @Test
    fun theManageButtonIsShownFoldedAndSendsTheToggle() {
        showState(profile())
        node(ProfileTags.MANAGE).assertIsDisplayed().assertHeightIsAtLeast(MIN_TARGET).assertTextEquals(
            string(R.string.profile_manage),
        )
        node(PlayersTags.RENAME).assertDoesNotExist()
        node(ProfileTags.MANAGE).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.ManageToggled), events)
    }

    /** PS7: nothing to manage before the profile is known in the roster. */
    @Test
    fun noButtonWhileTheRosterIsNotRead() {
        showState(profile(roster = ProfileRoster.Loading))
        node(ProfileTags.NAME).assertIsDisplayed()
        node(ProfileTags.MANAGE).assertDoesNotExist()
    }

    /** PM11: unfolded, the four actions are in the screen, each at least 48 dp. */
    @Test
    fun theOpenPanelShowsTheFourActionsInsideTheScreen() {
        showState(profile(isManageOpen = true))
        node(ProfileTags.ACTIONS_PANEL).assertIsDisplayed()
        listOf(PlayersTags.RENAME, PlayersTags.MERGE, PlayersTags.RESET, PlayersTags.DELETE).forEach {
            node(it).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(MIN_TARGET)
        }
        node(PlayersTags.DELETE).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.DeleteTapped), events)
    }

    /** PM11: a confirmation is in the Profile, with its text and Cancel; no pop-up. */
    @Test
    fun aConfirmationIsShownInTheProfile() {
        showState(profile(isManageOpen = true, panel = ProfilePanel.MergeConfirm(lea)))
        node(PlayersTags.CONFIRM_TEXT).performScrollTo()
            .assertTextEquals(string(R.string.players_merge_confirm_text, "Kevin", "Lea"))
        node(PlayersTags.CONFIRM).assertIsDisplayed()
        node(PlayersTags.CANCEL).assertIsDisplayed()
    }

    /** PM4, PM8, PM10: a refusal is told under the button. */
    @Test
    fun aRefusalIsShown() {
        showState(profile(message = ProfileMessage.InProgress("Kevin")))
        node(PlayersTags.MESSAGE).assertTextEquals(string(R.string.players_in_progress, "Kevin"))
    }

    /** PM3 to PM6: while a request waits for the database, the button and the actions are disabled. */
    @Test
    fun theButtonIsDisabledWhileARequestIsPending() {
        showState(profile(request = ProfileRequest.Reset(kevin, 1L)))
        node(ProfileTags.MANAGE).assertIsNotEnabled()
    }

    /** The actions are available even when the player has no finished match (PS6). */
    @Test
    fun theButtonIsThereForAPlayerWithoutMatch() {
        showState(profile(raws = false))
        node(ProfileTags.EMPTY).assertIsDisplayed()
        node(ProfileTags.MANAGE).assertIsDisplayed()
    }

    /** PS7: in landscape the panel is in the left column and still reachable. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE)
    fun landscapeShowsThePanel() {
        showState(profile(isManageOpen = true))
        node(PlayersTags.DELETE).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val NOW = 10_000L
        val MIN_TARGET = 48.dp
    }
}
