package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HS5, HS7, U8: the match detail screen alone, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class MatchDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showState(state: MatchDetailState) = show { MatchDetailScreen(state, events::add) }

    private fun loaded(detail: io.github.darkcosmos.kosmodarts.history.MatchDetail = x01Detail()) =
        MatchDetailState(detail.sessionId).loaded(1, DbResult.Ok(detail))

    @Test
    fun `HS5 the summary shows the settings, the duration and the ranking`() {
        showState(loaded())
        node(HistoryTags.DETAIL_SUMMARY).assertIsDisplayed()
        composeRule.onNode(hasText("X01 · 501 · DOUBLE OUT")).assertIsDisplayed()
        composeRule.onNode(hasText("FIRST TO 2 SETS · FIRST TO 3 LEGS")).assertIsDisplayed()
        composeRule.onNode(hasText("Duration: 47 minutes")).assertIsDisplayed()
        composeRule.onNode(hasText("1. Ana")).assertIsDisplayed()
        composeRule.onNode(hasText("2. Bob")).assertIsDisplayed()
    }

    @Test
    fun `HS5 each leg has a heading and its visits, with the total, BUST and the darts as text`() {
        showState(loaded())
        node(HistoryTags.legHeading(0)).assertIsDisplayed()
        composeRule.onNode(hasText("SET 1 · LEG 1")).assertIsDisplayed()
        // A visit row has one description for TalkBack: its texts are in the unmerged tree.
        assertTextShown("T20 T20 T20")
        assertTextShown("180")
        assertTextShown("BUST")
        assertTextShown("BULL MISS 19")
    }

    private fun assertTextShown(text: String) {
        composeRule.onAllNodes(hasText(text), useUnmergedTree = true).onFirst().assertExists()
    }

    @Test
    fun `HS5 the last leg is reachable by scrolling and is named SET 2 LEG 3`() {
        showState(loaded())
        node(HistoryTags.DETAIL_LIST).performScrollToNode(hasText("SET 2 · LEG 3"))
        composeRule.onNode(hasText("SET 2 · LEG 3")).assertIsDisplayed()
    }

    @Test
    fun `U8 a visit is read as one sentence by TalkBack`() {
        showState(loaded())
        val visit = node(HistoryTags.visit(0, 0))
        visit.assertContentDescriptionEquals("Ana: Triple 20, Triple 20, Triple 20. Total 180")
    }

    @Test
    fun `HS5 the screen is read-only, with no delete and no popup`() {
        showState(loaded())
        composeRule.onNode(hasText("Delete", substring = true, ignoreCase = true)).assertDoesNotExist()
        assertFalse(
            composeRule.onAllNodes(androidx.compose.ui.test.isDialog()).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `HS5 Cricket shows the marks of a visit`() {
        showState(loaded(cricketDetail(isBullInPlay = false)))
        assertTextShown("5 marks")
    }

    @Test
    fun `HS7 a failed read shows a message and Retry, Back stays available`() {
        showState(MatchDetailState(7).loaded(1, DbResult.Failed))
        node(HistoryTags.DETAIL_FAILED).assertIsDisplayed()
        node(HistoryTags.DETAIL_RETRY).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).assertIsDisplayed()
        assertEquals(listOf<AppEvent>(AppEvent.MatchDetailRetryTapped), events)
    }

    @Test
    fun `HS7 the loading indicator waits 300 ms, then shows with a description`() {
        composeRule.mainClock.autoAdvance = false
        showState(MatchDetailState(7))
        node(HistoryTags.LOADING).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MILLIS - 1)
        node(HistoryTags.LOADING).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(2)
        node(HistoryTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.detail_loading)).assertIsDisplayed()
    }

    @Test
    fun `HS5 Back sends the Back event`() {
        showState(loaded())
        node(HistoryTags.DETAIL_BACK).performClick()
        assertEquals(listOf<AppEvent>(AppEvent.Back), events)
    }
}
