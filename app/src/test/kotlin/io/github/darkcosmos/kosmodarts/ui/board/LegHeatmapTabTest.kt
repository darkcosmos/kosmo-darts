package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndTab
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsTags
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapTags
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Leg heatmap" tab (HM4 to HM10, D11): the title, the player chips, the board, the band, the misses line and the
 * top 5, in portrait and in landscape; the band is left out and the taps ignored when the zone-tap switch is off.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class LegHeatmapTabTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()

    private fun showHeatmap(screen: EndHeatmapScreen, isZoneTapEnabled: Boolean = END_SCREEN_ZONE_TAP_ENABLED) {
        showEndScreen(
            composeRule,
            screen.withHeatmap(EndTab.LEG_HEATMAP, screen.heatmapPlayer, null) as EndHeatmapScreen,
            events,
            isZoneTapEnabled,
        )
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    /** The tag of the last row of the top list: the made-up journal may hit fewer than 5 zones. */
    private fun lastTopRow(): String = (4 downTo 0).map { HeatmapTags.topRow(it) }.first {
        composeRule.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun dp(px: Float): Float = px / composeRule.density.density

    /** HM9, M19: the title is the position of the leg, as in "SET 1 · LEG 1 WON" without the result. */
    @Test
    fun `HM9 the heatmap tab shows the LEG n title`() {
        showHeatmap(x01BetweenLegs(FOUR_NAMES.take(2)))
        node(LegHeatmapTags.TITLE).assertIsDisplayed().assertTextContains("LEG 1")
    }

    /** HM4: X01 shows the plain miss label, Cricket the combined one. */
    @Test
    fun `HM4 the misses line carries the X01 or the Cricket label`() {
        showHeatmap(x01End(FOUR_NAMES.take(2)))
        assertMissLabel(R.string.heatmap_miss)

        showHeatmap(cricketEnd(FOUR_NAMES.take(3)))
        assertMissLabel(R.string.heatmap_miss_cricket)
    }

    private fun assertMissLabel(res: Int) {
        val label = composeRule.activity.getString(res)
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(ProfileTags.figure(HeatmapTags.MISSES))), true)
            .assertIsDisplayed()
    }

    /** HM9: one chip per player, the first player is the winner; a chip switches the player. */
    @Test
    fun `HM9 the chips switch the player`() {
        showHeatmap(x01BetweenLegs(FOUR_NAMES.take(3)))
        node(LegHeatmapTags.chip(0)).assertIsSelected()
        node(LegHeatmapTags.chip(2)).performClick()
        node(LegHeatmapTags.chip(2)).assertIsSelected()
        assertEquals(listOf<AppEvent>(AppEvent.HeatmapPlayerSelected(2)), events)
        // The made-up journal may leave a player without a dart: the list or the empty text shows, never nothing.
        composeRule.onAllNodes(hasTestTag(HeatmapTags.TOP_LIST) or hasTestTag(HeatmapTags.EMPTY), true)
            .assertCountEquals(1)
    }

    /** HM9: no chips in a solo game. */
    @Test
    fun `HM9 a solo game has no chips`() {
        showHeatmap(x01End(listOf("Ann")))
        node(LegHeatmapTags.TITLE).assertIsDisplayed()
        node(LegHeatmapTags.CHIPS).assertDoesNotExist()
        node(DartboardTags.BOARD).assertIsDisplayed()
    }

    /** HM6, D11 A: a tap on the board selects the zone and fills the band. */
    @Test
    fun `HM6 a tap on a zone shows its band`() {
        showHeatmap(x01End(FOUR_NAMES.take(2)))
        node(ZoneBandTags.TITLE).assertTextContains(composeRule.activity.getString(R.string.heatmap_band_none))

        node(DartboardTags.BOARD).performTouchInput { click(center) }

        val zone = BoardZone(Dart.BULL_SEGMENT, BoardZone.Ring.BULL)
        assertEquals(listOf<AppEvent>(AppEvent.HeatmapZoneTapped(zone)), events)
        node(ZoneBandTags.TITLE).assertTextContains(composeRule.activity.getString(R.string.heatmap_zone_bull))
    }

    /** D11 B (switch off through the parameter): no band, a tap does nothing. */
    @Test
    fun `D11 with the zone taps off there is no band and a tap does nothing`() {
        showHeatmap(x01End(FOUR_NAMES.take(2)), isZoneTapEnabled = false)
        node(DartboardTags.BOARD).assertIsDisplayed()
        node(ZoneBandTags.BAND).assertDoesNotExist()

        node(DartboardTags.BOARD).performTouchInput { click(center) }

        assertTrue("no zone event", events.none { it is AppEvent.HeatmapZoneTapped })
        node(ZoneBandTags.BAND).assertDoesNotExist()
        node(HeatmapTags.TOP_LIST).assertIsDisplayed()
    }

    /** HM9, HM10 in portrait: the last row of the top 5 and the main action are reachable by scrolling. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT, fontScale = 2.0f)
    fun `HM9 portrait at font scale 2 reaches the last top row and the main action`() {
        showHeatmap(x01End(FOUR_NAMES))
        node(lastTopRow()).reach().assertIsDisplayed()
        node(EndTags.NEW_GAME).reach().assertIsDisplayed()
    }

    /** HM9 in landscape: the board is at least 200 dp, at the left, and the right column scrolls on its own. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE, fontScale = 2.0f)
    fun `HM9 landscape keeps the board and scrolls the right column alone`() {
        showHeatmap(x01End(FOUR_NAMES))
        val board = node(DartboardTags.BOARD).fetchSemanticsNode().boundsInRoot
        assertTrue("board ${dp(board.width)} dp at least 200", dp(board.width) >= 199f)
        val column = node(LegHeatmapTags.RIGHT_COLUMN).fetchSemanticsNode().boundsInRoot
        assertTrue("board at the left of the right column", board.right <= column.left + 1f)

        node(lastTopRow()).reach().assertIsDisplayed()
        assertEquals(board, node(DartboardTags.BOARD).fetchSemanticsNode().boundsInRoot)
        node(EndTags.NEW_GAME).reach().assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** HM10, D10: between legs in landscape "Next leg" stays pinned at the bottom of the right column. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE, fontScale = 2.0f)
    fun `HM10 landscape between legs pins Next leg while the right column scrolls`() {
        showHeatmap(x01BetweenLegs(FOUR_NAMES))
        val before = node(BetweenLegsTags.NEXT_LEG).fetchSemanticsNode().boundsInRoot
        node(lastTopRow()).reach().assertIsDisplayed()
        assertEquals(before, node(BetweenLegsTags.NEXT_LEG).fetchSemanticsNode().boundsInRoot)
        node(BetweenLegsTags.NEXT_LEG).performClick()
        assertTrue(AppEvent.NextLegTapped in events)
    }

    /** HM9: long names in the chips do not push the chips out of the screen (they scroll sideways). */
    @Test
    fun `HM9 long names keep the chips within the screen width`() {
        showHeatmap(x01End(LONG_NAMES))
        val chips = node(LegHeatmapTags.CHIPS).fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(chips.right <= root.right + 1f)
        assertTrue(48.dp.let { with(composeRule.density) { it.toPx() } } <= chips.height + 1f)
    }
}
