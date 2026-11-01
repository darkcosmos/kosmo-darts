package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HS1 to HS4, HS7: the History screen alone, with a state given directly. */
@RunWith(AndroidJUnit4::class)
// A tall screen: a lazy list only composes the rows it shows, and these tests look at several of them.
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class HistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()
    private val ana = x01HistoryMatch(1, "Ana", "Bob", format = MatchFormat(legsPerSet = 2), legs = listOf(2, 1))
    private val cricket = cricketHistoryMatch(2, "Cleo")
    private val profiles = profilesOf("Ana", "Bob", "Cleo")

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

    private fun showState(state: HistoryState) = show { HistoryScreen(state, events::add) }

    private fun filled(filters: HistoryFilters = HistoryFilters()) = HistoryState(filters = filters)
        .loaded(1, okList(listOf(cricket, ana), profiles))

    @Test
    fun `HS2 a row opens its match by a tap and is a button of at least 48 dp`() {
        showState(filled())
        node(HistoryTags.row(1)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<AppEvent>(AppEvent.HistoryMatchTapped(1)), events)
    }

    @Test
    fun `HS2 rows come in the order of the state, newest first`() {
        showState(filled())
        val newest = node(HistoryTags.row(2)).fetchSemanticsNode().boundsInRoot.top
        val older = node(HistoryTags.row(1)).fetchSemanticsNode().boundsInRoot.top
        assertTrue(newest < older)
    }

    @Test
    fun `HS2 a row has one merged description with date, mode, players, winner and score`() {
        showState(filled())
        val row = node(HistoryTags.row(1))
        listOf("X01 · 501 · DOUBLE OUT", "Players: Ana, Bob", "Winner: Ana", "Legs 2–1").forEach {
            row.assertContentDescriptionContains(it, substring = true)
        }
        node(HistoryTags.row(2)).assertContentDescriptionContains("CRICKET · CUT-THROAT", substring = true)
    }

    @Test
    fun `HS2 the winner is written as text and a single game shows no score`() {
        showState(filled())
        composeRule.onNodeWithText("Winner: Ana", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Winner: Cleo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Legs 2–1", useUnmergedTree = true).assertIsDisplayed()
        val legsNodes = composeRule.onAllNodes(hasText("Legs", substring = true), useUnmergedTree = true)
        assertEquals(1, legsNodes.fetchSemanticsNodes().size)
    }

    @Test
    fun `HS2 four players with long names stay readable, wrapped then cut`() {
        val long = "A name that is much too long to fit on a single line of the row even on a wide phone screen"
        val match = x01HistoryMatch(7, long, "Bob", "Cleo", "Dan")
        showState(HistoryState().loaded(1, okList(listOf(match), profilesOf("Bob"))))
        node(HistoryTags.row(7)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `HS3 the selected filters are exposed as selected and the others as not selected`() {
        showState(filled(HistoryFilters(ArchivedMode.CRICKET, profileId = 2)))
        composeRule.onNode(hasText("CRICKET") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("X01") and isSelectable()).assertIsNotSelected()
        node(HistoryTags.playerChip(2)).assertIsSelected()
        node(HistoryTags.playerChip(1)).assertIsNotSelected()
        node(HistoryTags.PLAYER_ALL).assertIsNotSelected()
    }

    @Test
    fun `HS3 the chips are at least 48 dp`() {
        showState(filled())
        listOf(HistoryTags.PLAYER_ALL, HistoryTags.playerChip(1), HistoryTags.playerChip(3)).forEach {
            node(it).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `HS3 tapping a filter sends it`() {
        showState(filled())
        composeRule.onNode(hasText("CRICKET") and isSelectable()).performClick()
        node(HistoryTags.playerChip(3)).performClick()
        node(HistoryTags.PLAYER_ALL).performClick()
        assertEquals(
            listOf<AppEvent>(
                AppEvent.HistoryModeSelected(ArchivedMode.CRICKET),
                AppEvent.HistoryProfileSelected(3),
                AppEvent.HistoryProfileSelected(null),
            ),
            events,
        )
    }

    @Test
    fun `HS3 the selected chip is scrolled into view when there are many profiles`() {
        val many = profilesOf(*Array(30) { "Player number $it" })
        val state = HistoryState(filters = HistoryFilters(profileId = 30)).loaded(1, okList(listOf(ana), many))
        showState(state)
        node(HistoryTags.playerChip(30)).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `HS4 the empty archive says so and offers nothing to reset`() {
        showState(HistoryState().loaded(1, okList(emptyList())))
        node(HistoryTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.history_empty_title)).assertIsDisplayed()
        node(HistoryTags.SHOW_ALL).assertDoesNotExist()
    }

    @Test
    fun `HS4 no match keeps the filters and Show all matches resets them`() {
        val filtered = HistoryState(filters = HistoryFilters(ArchivedMode.CRICKET, 1))
        val state = filtered.loaded(1, okList(emptyList(), profiles))
        showState(state)
        node(HistoryTags.NO_MATCH).assertIsDisplayed()
        node(HistoryTags.playerChip(1)).assertIsSelected()
        node(HistoryTags.SHOW_ALL).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<AppEvent>(AppEvent.HistoryShowAllTapped), events)
    }

    @Test
    fun `HS7 a failed read shows a message and Retry, Back stays available`() {
        showState(HistoryState().loaded(1, DbResult.Failed))
        node(HistoryTags.FAILED).assertIsDisplayed()
        node(HistoryTags.RETRY).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).assertIsDisplayed()
        assertEquals(listOf<AppEvent>(AppEvent.HistoryRetryTapped), events)
    }

    @Test
    fun `HS7 the loading indicator waits 300 ms, then shows with a description`() {
        composeRule.mainClock.autoAdvance = false
        showState(HistoryState())
        node(HistoryTags.LOADING).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MILLIS - 1)
        node(HistoryTags.LOADING).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(2)
        node(HistoryTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.history_loading)).assertIsDisplayed()
    }

    @Test
    fun `HS6 the list reports where it stopped and starts where the state says`() {
        val many = List(60) { x01HistoryMatch(it + 1L, "Ana", "Bob") }
        showState(HistoryState().loaded(1, okList(many, profiles)))
        node(HistoryTags.LIST).performTouchInput { swipeUp(); swipeUp() }
        composeRule.waitForIdle()
        val scrolled = events.filterIsInstance<AppEvent.HistoryScrolled>().last()
        assertTrue("index ${scrolled.index}", scrolled.index > 1)

        events.clear()
        val restored = HistoryState(scroll = HistoryScroll(scrolled.index, scrolled.offset))
        showState(restored.loaded(1, okList(many, profiles)))
        node(HistoryTags.row(many[scrolled.index - 1].sessionId)).assertIsDisplayed()
    }

    @Test
    fun `AccessibilityBack the Back action is described and sends Back`() {
        showState(filled())
        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).performClick()
        assertEquals(listOf<AppEvent>(AppEvent.Back), events)
    }
}
