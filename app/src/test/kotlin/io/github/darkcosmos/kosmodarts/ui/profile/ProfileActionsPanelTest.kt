package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
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

/** PM3 to PM6, PM8, PM10: the actions panel of the Profile alone, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class ProfileActionsPanelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()
    private val kevin = playerSummary(1, "Kevin", matches = 3)
    private val lea = playerSummary(2, "Lea", matches = 1)
    private val ana = playerSummary(3, "Ana", matches = 0)

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** A node inside a merged parent (the text of a card header, the supporting text of a field). */
    private fun unmerged(tag: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showState(state: ProfileState) = show {
        Column {
            ProfileActionsPanelContent(kevin, state, events::add)
            state.message?.let { ProfileMessageText(it) }
        }
    }

    private fun stateOf(panel: ProfilePanel? = null, message: ProfileMessage? = null) = ProfileState(
        roster = ProfileRoster.Loaded(kevin, listOf(lea, ana)),
        panel = panel,
        message = message,
    )

    private fun open(panel: ProfilePanel? = null, message: ProfileMessage? = null) =
        showState(stateOf(panel, message))

    private fun assertLiveRegion(tag: String) =
        unmerged(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

    @Test
    fun `PM1 the open card shows the four actions of at least 48dp, Delete under the others`() {
        open()
        listOf(PlayersTags.RENAME, PlayersTags.MERGE, PlayersTags.RESET, PlayersTags.DELETE).forEach {
            node(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        val reset = node(PlayersTags.RESET).fetchSemanticsNode().boundsInRoot
        val delete = node(PlayersTags.DELETE).fetchSemanticsNode().boundsInRoot
        assert(delete.top - reset.bottom >= 16f) { "Delete is not apart from Reset: $reset $delete" }
    }

    @Test
    fun `the four actions send their event`() {
        open()
        node(PlayersTags.RENAME).performClick()
        node(PlayersTags.MERGE).performClick()
        node(PlayersTags.RESET).performClick()
        node(PlayersTags.DELETE).performClick()
        assertEquals(
            listOf(
                ProfileEvent.RenameTapped,
                ProfileEvent.MergeTapped,
                ProfileEvent.ResetTapped,
                ProfileEvent.DeleteTapped,
            ),
            events,
        )
    }

    @Test
    fun `PM3 the rename panel shows the name, saves with its button and with the IME action`() {
        open(ProfilePanel.Rename("Kevin"))
        node(PlayersTags.RENAME_FIELD).assertIsDisplayed()
        node(PlayersTags.RENAME_FIELD).performTextReplacement("Kev")
        node(PlayersTags.RENAME_SAVE).performClick()
        node(PlayersTags.RENAME_FIELD).performImeAction()
        node(PlayersTags.CANCEL).performClick()
        assertEquals(
            listOf(
                ProfileEvent.RenameTextChanged("Kev"),
                ProfileEvent.RenameSaved,
                ProfileEvent.RenameSaved,
                ProfileEvent.Cancelled,
            ),
            events,
        )
    }

    @Test
    fun `PM3 a blank name shows its error under the field, announced`() {
        open(ProfilePanel.Rename("", RenameError.Invalid(PlayerListError.BLANK_NAME)))
        unmerged(PlayersTags.RENAME_ERROR).assertTextEquals(string(R.string.setup_error_blank_name))
        assertLiveRegion(PlayersTags.RENAME_ERROR)
    }

    @Test
    fun `PM3 Q6 a name taken by Lea offers to merge into Lea`() {
        open(ProfilePanel.Rename("lea", RenameError.Taken(lea)))
        unmerged(PlayersTags.RENAME_ERROR).assertTextEquals(string(R.string.players_error_name_taken, "Lea"))
        node(PlayersTags.MERGE_INTO_TAKEN).assertIsDisplayed()
            .assertTextEquals(string(R.string.players_merge_into, "Lea"))
        node(PlayersTags.MERGE_INTO_TAKEN).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.MergeIntoTakenTapped), events)
    }

    @Test
    fun `PM4 the merge list shows the other profiles and sends the one picked`() {
        open(ProfilePanel.MergePick)
        node(PlayersTags.MERGE_PICK).assertTextEquals(string(R.string.players_merge_pick, "Kevin"))
        node(PlayersTags.mergeTarget(1)).assertDoesNotExist()
        node(PlayersTags.mergeTarget(2)).assertHeightIsAtLeast(48.dp).performClick()
        node(PlayersTags.mergeTarget(3)).assertIsDisplayed()
        assertEquals(listOf<AppEvent>(ProfileEvent.MergeTargetPicked(2)), events)
    }

    @Test
    fun `PM4 the merge confirmation names both profiles and is announced`() {
        open(ProfilePanel.MergeConfirm(lea))
        node(PlayersTags.CONFIRM_TEXT).assertTextEquals(string(R.string.players_merge_confirm_text, "Kevin", "Lea"))
        assertLiveRegion(PlayersTags.CONFIRM_TEXT)
        node(PlayersTags.CONFIRM).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.Confirmed), events)
    }

    @Test
    fun `PM5 the reset confirmation says that no match is erased`() {
        open(ProfilePanel.ResetConfirm)
        node(PlayersTags.CONFIRM_TEXT).assertTextEquals(string(R.string.players_reset_confirm_text, "Kevin"))
        node(PlayersTags.CANCEL).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.Cancelled), events)
    }

    @Test
    fun `PM6 the delete confirmation gives the number of matches erased, none, one or several`() {
        val texts = listOf(0, 1, 5).map { count ->
            open(ProfilePanel.DeleteConfirm(count))
            val shown = node(PlayersTags.CONFIRM_TEXT).fetchSemanticsNode().config[SemanticsProperties.Text]
            shown.joinToString { it.text }
        }
        assert(string(R.string.players_delete_erased_none) in texts[0]) { texts[0] }
        assert("1 match will be erased." in texts[1]) { texts[1] }
        assert("5 matches will be erased." in texts[2]) { texts[2] }
        assert(texts.all { "Kevin will be deleted." in it }) { texts.toString() }
        assertLiveRegion(PlayersTags.CONFIRM_TEXT)
    }

    @Test
    fun `PM8 the refusal names the player of the game in progress, announced`() {
        open(message = ProfileMessage.InProgress("Kevin"))
        node(PlayersTags.MESSAGE).assertTextEquals(string(R.string.players_in_progress, "Kevin"))
        node(PlayersTags.MESSAGE).assert(
            SemanticsMatcher("names the player") { node ->
                node.config[SemanticsProperties.Text].joinToString { it.text } ==
                    "Kevin is in the game in progress. Finish or abandon it first."
            },
        )
        assertLiveRegion(PlayersTags.MESSAGE)
    }

    @Test
    fun `PM4 PM10 the shared match and the database error are shown and announced in the open panel`() {
        open(ProfilePanel.MergeConfirm(lea), ProfileMessage.SharedMatch)
        node(PlayersTags.MESSAGE).assertTextEquals(string(R.string.players_shared_match))
        assertLiveRegion(PlayersTags.MESSAGE)

        open(ProfilePanel.ResetConfirm, ProfileMessage.DatabaseError)
        node(PlayersTags.MESSAGE).assertTextEquals(string(R.string.players_database_error))
        assertLiveRegion(PlayersTags.MESSAGE)
        node(PlayersTags.CONFIRM).assertIsDisplayed()
    }

    @Test
    fun `PM10 while an action waits for the database the buttons are disabled`() {
        showState(stateOf(ProfilePanel.ResetConfirm).copy(request = ProfileRequest.Reset(kevin, 1L)))
        node(PlayersTags.CONFIRM).assertIsNotEnabled()
    }
}
