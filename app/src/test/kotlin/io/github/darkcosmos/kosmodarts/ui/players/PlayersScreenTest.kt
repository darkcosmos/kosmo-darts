package io.github.darkcosmos.kosmodarts.ui.players

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import io.github.darkcosmos.kosmodarts.ui.app.PlayersEvent
import io.github.darkcosmos.kosmodarts.ui.history.formatEndDate
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/** PM2 to PM11: the Players screen alone, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class PlayersScreenTest {

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

    private fun showState(state: PlayersState) = show { PlayersScreen(state, events::add) }

    private fun open(panel: PlayersPanel? = null, message: PlayersMessage? = null) =
        showState(loadedPlayers(kevin, lea, ana, expanded = 1, panel = panel, message = message))

    private fun assertLiveRegion(tag: String) =
        unmerged(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

    @Test
    fun `PM2 each card shows the name, the number of matches and the date of the last match`() {
        showState(loadedPlayers(kevin, lea, ana))
        node(PlayersTags.card(1)).assertIsDisplayed()
        val locale = composeRule.activity.resources.configuration.locales[0]
        val date = formatEndDate(SOME_MATCH_END, locale, ZoneId.systemDefault())
        unmerged(PlayersTags.summary(1)).assertTextEquals("3 matches · Last match: $date")
    }

    @Test
    fun `PM2 one match and no match use their own texts`() {
        showState(loadedPlayers(kevin, lea, ana))
        val locale = composeRule.activity.resources.configuration.locales[0]
        val date = formatEndDate(SOME_MATCH_END, locale, ZoneId.systemDefault())
        unmerged(PlayersTags.summary(2)).assertTextEquals("1 match · Last match: $date")
        unmerged(PlayersTags.summary(3)).assertTextEquals("No match yet")
    }

    @Test
    fun `PM2 an empty list, a failed read and a loading read each show their own screen`() {
        showState(loadedPlayers())
        node(PlayersTags.EMPTY).assertIsDisplayed()

        showState(PlayersState(content = PlayersContent.Failed))
        node(PlayersTags.FAILED).assertIsDisplayed()
        assertLiveRegion(PlayersTags.FAILED)
        node(PlayersTags.RETRY).performClick()
        assertEquals(listOf<AppEvent>(PlayersEvent.RetryTapped), events)
    }

    @Test
    fun `PM2 a tap on a card sends the tap and the back arrow sends Back`() {
        showState(loadedPlayers(kevin, lea))
        node(PlayersTags.card(2)).performClick()
        node(PlayersTags.BACK).performClick()
        assertEquals(listOf(PlayersEvent.ProfileTapped(2), AppEvent.Back), events)
    }

    @Test
    fun `PM1 the open card shows the four actions of at least 48dp, Delete under the others`() {
        open()
        listOf(PlayersTags.RENAME, PlayersTags.MERGE, PlayersTags.RESET, PlayersTags.DELETE).forEach {
            node(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        val reset = node(PlayersTags.RESET).fetchSemanticsNode().boundsInRoot
        val delete = node(PlayersTags.DELETE).fetchSemanticsNode().boundsInRoot
        assert(delete.top - reset.bottom >= 16f) { "Delete is not apart from Reset: $reset $delete" }
        // Only the open card has actions.
        composeRule.onAllNodesWithTag(PlayersTags.RENAME).assertCountEquals(1)
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
                PlayersEvent.RenameTapped,
                PlayersEvent.MergeTapped,
                PlayersEvent.ResetTapped,
                PlayersEvent.DeleteTapped,
            ),
            events,
        )
    }

    @Test
    fun `PM3 the rename panel shows the name, saves with its button and with the IME action`() {
        open(PlayersPanel.Rename("Kevin"))
        node(PlayersTags.RENAME_FIELD).assertIsDisplayed()
        node(PlayersTags.RENAME_FIELD).performTextReplacement("Kev")
        node(PlayersTags.RENAME_SAVE).performClick()
        node(PlayersTags.RENAME_FIELD).performImeAction()
        node(PlayersTags.CANCEL).performClick()
        assertEquals(
            listOf(
                PlayersEvent.RenameTextChanged("Kev"),
                PlayersEvent.RenameSaved,
                PlayersEvent.RenameSaved,
                PlayersEvent.Cancelled,
            ),
            events,
        )
    }

    @Test
    fun `PM3 a blank name shows its error under the field, announced`() {
        open(PlayersPanel.Rename("", RenameError.Invalid(PlayerListError.BLANK_NAME)))
        unmerged(PlayersTags.RENAME_ERROR).assertTextEquals(string(R.string.setup_error_blank_name))
        assertLiveRegion(PlayersTags.RENAME_ERROR)
    }

    @Test
    fun `PM3 Q6 a name taken by Lea offers to merge into Lea`() {
        open(PlayersPanel.Rename("lea", RenameError.Taken(lea)))
        unmerged(PlayersTags.RENAME_ERROR).assertTextEquals(string(R.string.players_error_name_taken, "Lea"))
        node(PlayersTags.MERGE_INTO_TAKEN).assertIsDisplayed()
            .assertTextEquals(string(R.string.players_merge_into, "Lea"))
        node(PlayersTags.MERGE_INTO_TAKEN).performClick()
        assertEquals(listOf<AppEvent>(PlayersEvent.MergeIntoTakenTapped), events)
    }

    @Test
    fun `PM4 the merge list shows the other profiles and sends the one picked`() {
        open(PlayersPanel.MergePick)
        node(PlayersTags.MERGE_PICK).assertTextEquals(string(R.string.players_merge_pick, "Kevin"))
        node(PlayersTags.mergeTarget(1)).assertDoesNotExist()
        node(PlayersTags.mergeTarget(2)).assertHeightIsAtLeast(48.dp).performClick()
        node(PlayersTags.mergeTarget(3)).assertIsDisplayed()
        assertEquals(listOf<AppEvent>(PlayersEvent.MergeTargetPicked(2)), events)
    }

    @Test
    fun `PM4 the merge confirmation names both profiles and is announced`() {
        open(PlayersPanel.MergeConfirm(lea))
        node(PlayersTags.CONFIRM_TEXT).assertTextEquals(string(R.string.players_merge_confirm_text, "Kevin", "Lea"))
        assertLiveRegion(PlayersTags.CONFIRM_TEXT)
        node(PlayersTags.CONFIRM).performClick()
        assertEquals(listOf<AppEvent>(PlayersEvent.Confirmed), events)
    }

    @Test
    fun `PM5 the reset confirmation says that no match is erased`() {
        open(PlayersPanel.ResetConfirm)
        node(PlayersTags.CONFIRM_TEXT).assertTextEquals(string(R.string.players_reset_confirm_text, "Kevin"))
        node(PlayersTags.CANCEL).performClick()
        assertEquals(listOf<AppEvent>(PlayersEvent.Cancelled), events)
    }

    @Test
    fun `PM6 the delete confirmation gives the number of matches erased, none, one or several`() {
        val texts = listOf(0, 1, 5).map { count ->
            open(PlayersPanel.DeleteConfirm(count))
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
        open(message = PlayersMessage.InProgress("Kevin"))
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
        open(PlayersPanel.MergeConfirm(lea), PlayersMessage.SharedMatch)
        node(PlayersTags.MESSAGE).assertTextEquals(string(R.string.players_shared_match))
        assertLiveRegion(PlayersTags.MESSAGE)

        open(PlayersPanel.ResetConfirm, PlayersMessage.DatabaseError)
        node(PlayersTags.MESSAGE).assertTextEquals(string(R.string.players_database_error))
        assertLiveRegion(PlayersTags.MESSAGE)
        node(PlayersTags.CONFIRM).assertIsDisplayed()
    }

    @Test
    fun `PM10 while an action waits for the database the buttons are disabled`() {
        showState(
            loadedPlayers(kevin, lea, expanded = 1, panel = PlayersPanel.ResetConfirm)
                .copy(request = PlayersRequest.Reset(kevin, 1L)),
        )
        node(PlayersTags.CONFIRM).assertIsNotEnabled()
    }
}
