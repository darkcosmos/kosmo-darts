package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import io.github.darkcosmos.kosmodarts.engine.stats.Heatmap
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.board.DartboardTags
import io.github.darkcosmos.kosmodarts.ui.board.HeatmapLegendTags
import io.github.darkcosmos.kosmodarts.ui.board.ZoneBandTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HM2 to HM8, PS8: the Heatmap section of the Profile screen, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class HeatmapSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()
    private val t20 = BoardZone(20, Ring.TRIPLE)
    private val resources get() = composeRule.activity.resources

    private fun stateOf(selections: HeatmapSelections = HeatmapSelections(), empty: Boolean = false): ProfileState {
        val data = ProfileTestData.data(listOf(ProfileTestData.x01Raw(), ProfileTestData.cricketRaw()))
        val base = ProfileState(section = ProfileSection.HEATMAP, heatmapSelections = selections, now = NOW)
        val figures = data.figures(base.query)
        val shown = if (empty) figures.copy(heatmap = HeatmapFigures(1, Heatmap.EMPTY)) else figures
        return base.copy(content = ProfileContent.Loaded(data, shown))
    }

    private fun show(state: ProfileState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { ProfileScreenForTest(state) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun ProfileScreenForTest(state: ProfileState) = ProfileScreen(state, events::add)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun texts(tag: String): List<String> =
        node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            .orEmpty().map { it.text }

    private fun mergedText(tag: String): String = texts(tag).joinToString(" ")

    /** HM6: before any tap the band says to touch a zone. */
    @Test
    fun bandAsksToTouchAZoneBeforeAnyTap() {
        show(stateOf())
        node(ZoneBandTags.BAND).assertExists()
        assertEquals(listOf("Touch a zone"), texts(ZoneBandTags.BAND))
    }


    /** HM6: the band shows the name, the darts and the percentage of the selected zone. */
    @Test
    fun selectedZoneShowsItsNameDartsAndPercentInTheBand() {
        show(stateOf(HeatmapSelections(selectedZone = t20)))
        assertEquals(listOf("Triple 20", "5 darts · 35.7 %"), texts(ZoneBandTags.BAND))
    }

    /** HM6, PS8: a zone action of the board (TalkBack) reports the tap and reads name, darts and percentage. */
    @Test
    fun zoneActionOfTheBoardReportsTheTapAndReadsTheFigures() {
        show(stateOf())
        val actions = node(DartboardTags.BOARD).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty()
        val action = actions.first { it.label == "Triple 20, 5 darts, 35.7 %" }
        composeRule.runOnUiThread { action.action.invoke() }
        assertEquals(ProfileEvent.HeatmapZoneTapped(t20), events.last())
        assertNotNull(actions.firstOrNull { it.label == "Single 5, 0 darts, 0 %" })
    }

    /** HM6: the 5 most hit zones are readable text, one element per row for TalkBack. */
    @Test
    fun topFiveListShowsTheMostHitZonesAsText() {
        show(stateOf())
        assertEquals("Triple 20 5 darts · 35.7 %", mergedText(HeatmapTags.topRow(0)))
        (0..4).forEach { node(HeatmapTags.topRow(it)).assertExists() }
        composeRule.onNodeWithTag(HeatmapTags.topRow(5)).assertDoesNotExist()
    }

    /** HM2, HM4: the misses line carries the combined label as soon as Cricket darts count, and the count and %. */
    @Test
    fun missesLineShowsTheCombinedLabelWithCountAndPercent() {
        val state = stateOf()
        show(state)
        val heatmap = checkNotNull(state.heatmap).heatmap
        val text = mergedText(ProfileTags.figure("heatmap_misses"))
        assert(text.startsWith("Miss or outside Cricket numbers")) { text }
        assert(text.contains("${heatmap.misses} dart")) { text }
        assert(text.endsWith(" %")) { text }
    }

    /** HM4: X01 only counts "Miss". */
    @Test
    fun x01ModeShowsTheShortMissLabelAndItsFilters() {
        show(stateOf(HeatmapSelections(mode = HeatmapMode.X01)))
        assert(mergedText(ProfileTags.figure("heatmap_misses")).startsWith("Miss ")) { "label" }
        node(ProfileTags.START_SCORE_FILTER).assertExists()
        node(ProfileTags.OUT_RULE_FILTER).assertExists()
    }

    /** HM3: All modes has no mode filter; the mode chips send their event and are at least 48 dp tall. */
    @Test
    fun modeChipsSendTheirEventAndAllModesHasNoFilterOfItsOwn() {
        show(stateOf())
        composeRule.onAllNodes(hasTestTag(ProfileTags.START_SCORE_FILTER)).assertCountEquals(0)
        val chip = composeRule.onNode(hasText("Cricket") and hasAnyAncestor(hasTestTag(HeatmapTags.MODE_FILTER)))
        chip.assertHeightIsAtLeast(48.dp)
        chip.performClick()
        assertEquals(ProfileEvent.HeatmapModeSelected(HeatmapMode.CRICKET), events.last())
    }

    /** HM5: the legend shows with the board. */
    @Test
    fun legendShowsTheMaxShareOfTheBoard() {
        show(stateOf())
        val max = composeRule.onNodeWithTag(HeatmapLegendTags.MAX, useUnmergedTree = true)
        max.assertExists()
        assertEquals(listOf("max = 35.7 %"), max.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
    }

    /** HM7: no dart in the selection gives the explicit empty state, without a board. */
    @Test
    fun noDartShowsTheExplicitEmptyState() {
        show(stateOf(empty = true))
        node(HeatmapTags.EMPTY).assertExists()
        assertEquals(listOf("No dart in this selection yet."), texts(HeatmapTags.EMPTY))
        composeRule.onAllNodes(hasTestTag(DartboardTags.BOARD)).assertCountEquals(0)
    }
}

private const val NOW = 10_000L
