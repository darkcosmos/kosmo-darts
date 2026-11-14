package io.github.darkcosmos.kosmodarts.ui.headtohead

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HeadToHeadData
import io.github.darkcosmos.kosmodarts.history.PickerProfile
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.HeadToHeadEvent
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadTestData.A
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadTestData.B
import io.github.darkcosmos.kosmodarts.ui.history.HistoryTags
import io.github.darkcosmos.kosmodarts.ui.history.LOADING_INDICATOR_DELAY_MILLIS
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTags
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HH1 to HH9, HS7, PS8, ST6, ST10: the Head-to-head screen alone, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class HeadToHeadScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()

    private val raws = listOf(
        HeadToHeadTestData.x01("1", startedAt = 2_000),
        HeadToHeadTestData.cricket("2", startedAt = 4_000),
        HeadToHeadTestData.x01("3", startedAt = 6_000, rankA = 2, rankB = 1, legsA = 0, legsB = 1),
        HeadToHeadTestData.x01("4", startedAt = 8_000, rankA = 1, rankB = 1, legsA = 1, legsB = 1),
    )
    private val data = HeadToHeadTestData.pair(raws)

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showState(state: HeadToHeadState, partner: Long? = B) =
        show { HeadToHeadScreen(partner, state, events::add) }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun SemanticsNodeInteraction.assertFits() {
        assertTrue(getBoundsInRoot().let { it.right - it.left } <= HALF_COLUMN)
    }

    private fun option(group: String, text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(group)))

    private fun content(
        selections: HeadToHeadSelections = HeadToHeadSelections(),
        pair: HeadToHeadData = data,
    ): HeadToHeadState = HeadToHeadTestData.shown(pair, selections)

    private val others = listOf(PickerProfile(B, "Zoe", 3), PickerProfile(3L, "Abe", 0))

    /** HH1 */
    @Test
    fun pickerListsTheOtherProfilesWithTheirCommonCountsAndAPickOpensTheComparison() {
        showState(HeadToHeadTestData.pickerState(others), partner = null)
        node(HeadToHeadTags.PICKER_TITLE).assertTextEquals("Compare Kevin with:")
        composeRule.onNode(hasText("3 matches in common")).assertIsDisplayed()
        composeRule.onNode(hasText("0 matches in common")).assertIsDisplayed()
        node(HeadToHeadTags.pickerRow(B)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<AppEvent>(HeadToHeadEvent.PartnerPicked(B)), events)
    }

    /** HH1 */
    @Test
    fun pickerWithoutAnotherProfileShowsAnEmptyMessage() {
        showState(HeadToHeadTestData.pickerState(emptyList()), partner = null)
        node(HeadToHeadTags.PICKER_EMPTY).assertIsDisplayed()
    }

    /** HS7 */
    @Test
    fun failedPickerShowsAMessageAndRetry() {
        val failed = HeadToHeadState().pickerLoaded(1, DbResult.Failed)
        showState(failed, partner = null)
        node(HeadToHeadTags.FAILED).assertIsDisplayed()
        node(HeadToHeadTags.RETRY).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<AppEvent>(HeadToHeadEvent.RetryTapped), events)
    }

    /** HS7 */
    @Test
    fun loadingShowsAnIndicatorAfterTheDelay() {
        composeRule.mainClock.autoAdvance = false
        showState(HeadToHeadState().partnerPicked(1L))
        node(HistoryTags.LOADING).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MILLIS + FRAME)
        node(HistoryTags.LOADING).assertIsDisplayed()
    }

    /** HS7 */
    @Test
    fun failedPairShowsAMessageRetryAndChange() {
        val failed = HeadToHeadState().partnerPicked(1L).let { it.pairLoaded(it.pairLoadId, DbResult.Failed) }
        showState(failed)
        node(HeadToHeadTags.FAILED).assertIsDisplayed()
        node(HeadToHeadTags.RETRY).performClick()
        node(HeadToHeadTags.CHANGE).performClick()
        assertEquals(listOf(HeadToHeadEvent.RetryTapped, HeadToHeadEvent.ChangePartnerTapped), events)
    }

    /** HH3, HH4: wins of each, the draws apart, the legs won by each. */
    @Test
    fun countersShowWinsDrawsAndLegs() {
        showState(content())
        node(HeadToHeadTags.TITLE).assertTextEquals("Kevin vs Zoe")
        node(HeadToHeadTags.figure("wins")).assertContentDescriptionEquals("Wins: Kevin 2, Zoe 1")
        node(HeadToHeadTags.figure("legs")).assertIsDisplayed()
        composeRule.onNode(
            hasTestTag(ProfileTags.figure("h2h_draws")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("1") and hasAnyAncestor(hasTestTag(ProfileTags.figure("h2h_draws"))),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("4") and hasAnyAncestor(hasTestTag(ProfileTags.figure("h2h_common"))),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /** HH5, HH6: All modes shows the X01 figures and the Cricket MPR apart; a mode shows its own. */
    @Test
    fun modesShowTheirBlocks() {
        showState(content())
        node(HeadToHeadTags.block("x01")).assertExists()
        node(HeadToHeadTags.block("cricket")).assertExists()
        showState(content(HeadToHeadSelections(mode = HeadToHeadMode.X01)))
        node(HeadToHeadTags.block("x01")).assertExists()
        node(HeadToHeadTags.block("cricket")).assertDoesNotExist()
        showState(content(HeadToHeadSelections(mode = HeadToHeadMode.CRICKET)))
        node(HeadToHeadTags.block("x01")).assertDoesNotExist()
        node(HeadToHeadTags.block("cricket")).assertExists()
    }

    /** ST6: a figure that cannot be computed shows a dash. */
    @Test
    fun aFigureWithoutAMatchOfItsModeShowsADash() {
        val x01Only = HeadToHeadTestData.pair(listOf(HeadToHeadTestData.x01("1")))
        showState(content(pair = x01Only))
        val mpr = node(HeadToHeadTags.figure("mpr"))
        mpr.assertContentDescriptionEquals("Marks per round: Kevin –, Zoe –")
    }

    /** HH6 */
    @Test
    fun modeFilterAndPeriodRaiseTheirEvents() {
        showState(content())
        option(HeadToHeadTags.MODE_FILTER, "All modes").assertIsSelected()
        option(HeadToHeadTags.MODE_FILTER, "Cricket").performClick()
        option(ProfileTags.PERIOD_FILTER, "Last 30 days").performClick()
        assertEquals(
            listOf(
                HeadToHeadEvent.ModeSelected(HeadToHeadMode.CRICKET),
                HeadToHeadEvent.PeriodSelected(Period.LAST_30_DAYS),
            ),
            events,
        )
    }

    /** HH6, ST3: the filters of the chosen mode show and raise their events. */
    @Test
    fun x01AndCricketFiltersShowForTheirModeOnly() {
        showState(content(HeadToHeadSelections(mode = HeadToHeadMode.X01, x01Filter = X01Filter(startScore = 301))))
        option(ProfileTags.START_SCORE_FILTER, "301").assertIsSelected()
        option(ProfileTags.START_SCORE_FILTER, "501").performClick()
        node(ProfileTags.VARIANT_FILTER).assertDoesNotExist()
        showState(content(HeadToHeadSelections(mode = HeadToHeadMode.CRICKET)))
        option(ProfileTags.VARIANT_FILTER, "CUT-THROAT").performClick()
        node(ProfileTags.START_SCORE_FILTER).assertDoesNotExist()
        assertEquals(
            listOf(
                HeadToHeadEvent.X01FilterSelected(X01Filter(startScore = 501)),
                HeadToHeadEvent.CricketFilterSelected(CricketFilter(CricketVariant.CUT_THROAT)),
            ),
            events,
        )
    }

    /** HH8 */
    @Test
    fun dateLineSaysSinceAlwaysOrSinceTheLatestRealReset() {
        showState(content())
        node(HeadToHeadTags.SINCE).assertTextEquals("Since always")
        showState(content(pair = HeadToHeadTestData.pair(raws, resetA = 1_500)))
        node(HeadToHeadTags.SINCE).assertExists()
    }

    /** ST10 */
    @Test
    fun ignoredNoticeShowsOnlyWhenAMatchIsIgnored() {
        showState(content())
        node(HeadToHeadTags.IGNORED).assertDoesNotExist()
        val withBad = HeadToHeadTestData.pair(
            listOf(HeadToHeadTestData.x01("1"), ProfileTestData.corruptRaw("bad")),
        )
        showState(content(pair = withBad))
        node(HeadToHeadTags.IGNORED).assertTextEquals("1 match ignored (unreadable data)")
    }

    /** HH7, HS9 */
    @Test
    fun aCommonMatchOpensItsDetail() {
        showState(content())
        node(HeadToHeadTags.CONTENT).performScrollToNode(hasTestTag(HeadToHeadTags.matchRow(3)))
        node(HeadToHeadTags.matchRow(3)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<AppEvent>(HeadToHeadEvent.MatchTapped(3)), events)
    }

    /** HH3, HH7: each row tells who won, or the draw. */
    @Test
    fun matchRowsTellTheWinnerOrTheDraw() {
        showState(content())
        node(HeadToHeadTags.CONTENT).performScrollToNode(hasTestTag(HeadToHeadTags.matchRow(1)))
        composeRule.onNode(hasText("Kevin won") and hasAnyAncestor(hasTestTag(HeadToHeadTags.matchRow(1))), true)
            .assertIsDisplayed()
        node(HeadToHeadTags.CONTENT).performScrollToNode(hasTestTag(HeadToHeadTags.matchRow(4)))
        composeRule.onNode(hasText("Draw") and hasAnyAncestor(hasTestTag(HeadToHeadTags.matchRow(4))), true)
            .assertIsDisplayed()
    }

    /** HH9 */
    @Test
    fun noCommonMatchShowsAnExplicitEmptyState() {
        showState(content(pair = HeadToHeadTestData.pair(emptyList())))
        node(HeadToHeadTags.NO_COMMON_MATCH).assertIsDisplayed()
        node(HeadToHeadTags.MODE_FILTER).assertDoesNotExist()
        node(HeadToHeadTags.CHANGE).performClick()
        assertEquals(listOf<AppEvent>(HeadToHeadEvent.ChangePartnerTapped), events)
    }

    /** PS8, HH9: common matches exist, none for the selection; the filters stay. */
    @Test
    fun noMatchForTheSelectionKeepsTheFilters() {
        showState(content(HeadToHeadSelections(mode = HeadToHeadMode.X01, x01Filter = X01Filter(startScore = 501))))
        node(HeadToHeadTags.CONTENT).performScrollToNode(hasTestTag(ProfileTags.NO_MATCH_FILTERS))
        node(ProfileTags.NO_MATCH_FILTERS).assertIsDisplayed()
        node(HeadToHeadTags.MODE_FILTER).assertIsDisplayed()
        node(HeadToHeadTags.block("results")).assertDoesNotExist()
    }

    /** HH5: two 20 code point names fit their two columns. */
    @Test
    fun longNamesFitTheirTwoColumns() {
        val longA = "ABCDEFGHIJKLMNOPQRST"
        val longB = "UVWXYZABCDEFGHIJKLMN"
        showState(content(pair = HeadToHeadTestData.pair(raws, nameA = longA, nameB = longB)))
        node(HeadToHeadTags.name("results", "a")).assertIsDisplayed().assertFits()
        node(HeadToHeadTags.name("results", "b")).assertIsDisplayed().assertFits()
    }

    /** PS8 */
    @Test
    fun backHasADescriptionAndRaisesBack() {
        showState(content())
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf<AppEvent>(AppEvent.Back), events)
    }

    private companion object {
        const val FRAME = 16L
        val HALF_COLUMN = 205.dp
    }
}
