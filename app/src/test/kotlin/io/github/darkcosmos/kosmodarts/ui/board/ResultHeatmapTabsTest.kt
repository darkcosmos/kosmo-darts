package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndTab
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsTags
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tabs "Result" and "Leg heatmap" of the between-legs and end screens, X01 and Cricket, on a Pixel 7 (HM9, HM10,
 * review U8, U9, U10): switching, the main actions in both tabs and both orientations, the tab row at a large font and
 * the Result tab reaching its last row and its main action.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class ResultHeatmapTabsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()

    private fun show(screen: EndHeatmapScreen) = showEndScreen(composeRule, screen, events)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    /** The main action tags of [screen] and the event each sends. */
    private fun actionsOf(screen: EndHeatmapScreen): List<Pair<String, AppEvent>> = when (screen) {
        is Screen.BetweenLegs, is Screen.CricketBetweenLegs ->
            listOf(BetweenLegsTags.NEXT_LEG to AppEvent.NextLegTapped)
        else -> listOf(
            EndTags.REMATCH to AppEvent.Rematch,
            EndTags.NEW_GAME to screen.newGameEvent(),
        )
    }

    private fun EndHeatmapScreen.newGameEvent(): AppEvent =
        AppEvent.NewGame(if (this is Screen.CricketEnd) GameMode.CRICKET else GameMode.X01)

    private fun assertInsideScreen(tag: String) {
        val rect = node(tag).fetchSemanticsNode().boundsInRoot
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$tag $rect inside $screen",
            rect.left >= screen.left - 1f && rect.right <= screen.right + 1f &&
                rect.top >= screen.top - 1f && rect.bottom <= screen.bottom + 1f,
        )
    }

    /** HM10: in each tab, every main action of every screen is displayed and sends its event. */
    private fun checkActionsInBothTabs() {
        allEndScreens(FOUR_NAMES.take(2)).forEach { initial ->
            EndTab.entries.forEach { tab ->
                events.clear()
                show(initial.withHeatmap(tab, initial.heatmapPlayer, null) as EndHeatmapScreen)
                actionsOf(initial).forEach { (tag, event) ->
                    node(tag).reach().assertIsDisplayed()
                    assertInsideScreen(tag)
                    node(tag).performClick()
                    assertTrue("$tab ${initial::class.simpleName} $tag sends $event", event in events)
                }
            }
        }
    }

    /** HM9: the screen opens on Result; the heatmap tab shows the board, and Result brings today's content back. */
    @Test
    fun `HM9 the tabs switch between the Result content and the leg heatmap`() {
        events.clear()
        show(x01BetweenLegs(FOUR_NAMES.take(2)))
        node(EndTabTags.tab(EndTab.RESULT)).assertIsSelected()
        node(BetweenLegsTags.SCOREBOARD).assertIsDisplayed()
        node(DartboardTags.BOARD).assertDoesNotExist()

        node(EndTabTags.tab(EndTab.LEG_HEATMAP)).performClick()
        node(EndTabTags.tab(EndTab.LEG_HEATMAP)).assertIsSelected()
        node(DartboardTags.BOARD).assertIsDisplayed()
        node(LegHeatmapTags.TITLE).assertIsDisplayed()

        node(EndTabTags.tab(EndTab.RESULT)).performClick()
        node(BetweenLegsTags.SCOREBOARD).assertIsDisplayed()
        assertEquals(
            listOf(AppEvent.EndTabSelected(EndTab.LEG_HEATMAP), AppEvent.EndTabSelected(EndTab.RESULT)),
            events,
        )
    }

    @Test
    fun `HM10 the main actions are displayed and clickable in both tabs in portrait`() = checkActionsInBothTabs()

    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE)
    fun `HM10 the main actions are displayed and clickable in both tabs in landscape`() = checkActionsInBothTabs()

    /** HM10, owner decision U3: NEXT LEG is pinned, displayed and clickable without scrolling, 4 players, 800x360. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE_INSIDE_BARS)
    fun `HM10 the Result tab in landscape with four players shows its main action without scrolling`() {
        events.clear()
        show(x01BetweenLegs(FOUR_NAMES))

        node(BetweenLegsTags.NEXT_LEG).assertIsDisplayed()
        assertInsideScreen(BetweenLegsTags.NEXT_LEG)
        node(BetweenLegsTags.NEXT_LEG).performClick()
        assertTrue(AppEvent.NextLegTapped in events)
    }

    /** HM9: in landscape the tab row has its planned height of 42 dp, with no top margin. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE_INSIDE_BARS)
    fun `HM9 the tab row takes 42 dp in landscape`() {
        show(x01BetweenLegs(FOUR_NAMES))

        val bounds = node(EndTabTags.ROW).fetchSemanticsNode().boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        assertEquals(42f, bounds.height / density, 1f)
    }

    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT, fontScale = 2.0f)
    fun `HM10 the main actions are reachable in both tabs at font scale 2 in portrait`() {
        allEndScreens(FOUR_NAMES).forEach { initial ->
            EndTab.entries.forEach { tab ->
                show(initial.withHeatmap(tab, initial.heatmapPlayer, null) as EndHeatmapScreen)
                actionsOf(initial).forEach { (tag, _) ->
                    node(tag).reach()
                    assertInsideScreen(tag)
                }
            }
        }
    }

    /** Review U10: the labels are fully shown and the tab grows beyond 48 dp at font scale 2. */
    @Test
    @Config(qualifiers = TestDevices.FR_PIXEL7_PORTRAIT, fontScale = 2.0f)
    fun `HM9 the tab labels are not cut at font scale 2 and the tab grows`() {
        show(x01End(FOUR_NAMES))
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        EndTab.entries.forEach { tab ->
            val tag = EndTabTags.tab(tab)
            val height = node(tag).fetchSemanticsNode().boundsInRoot.height
            assertTrue("$tag height $height at least 48 dp", height >= minimum - 1f)
            val text = composeRule.activity.getString(
                if (tab == EndTab.RESULT) R.string.end_tab_result else R.string.end_tab_heatmap,
            )
            val label = composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            val layout = layoutOf(label)
            assertFalse("$tag label overflows its height", layout.didOverflowHeight)
            val bounds = label.fetchSemanticsNode().boundsInRoot
            val tabBounds = node(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag label $bounds inside its tab $tabBounds",
                bounds.left >= tabBounds.left - 1f && bounds.right <= tabBounds.right + 1f,
            )
            if (tab == EndTab.RESULT) assertEquals("one-word label is not broken", 1, layout.lineCount)
        }
        val heatmapTab = node(EndTabTags.tab(EndTab.LEG_HEATMAP)).fetchSemanticsNode().boundsInRoot.height
        assertTrue("the two-word label wraps, so its tab is taller than 48 dp", heatmapTab > minimum + 1f)
    }

    private fun layoutOf(label: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = label.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(checkNotNull(action)(results))
        return results.first()
    }

    /** Review U9: Result in landscape with 4 players at font scale 2 reaches its last row and its main action. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE, fontScale = 2.0f)
    fun `HM10 the Result tab in landscape with four players reaches its last row and its main action`() {
        show(x01BetweenLegs(FOUR_NAMES))
        node(BetweenLegsTags.legs(3)).reach()
        assertInsideScreen(BetweenLegsTags.legs(3))
        node(BetweenLegsTags.NEXT_LEG).reach()
        assertInsideScreen(BetweenLegsTags.NEXT_LEG)
        node(EndTabTags.ROW).assertIsDisplayed()

        show(x01End(FOUR_NAMES))
        node(EndTags.row(3)).reach()
        assertInsideScreen(EndTags.row(3))
        node(EndTags.NEW_GAME).reach()
        assertInsideScreen(EndTags.NEW_GAME)
    }
}
