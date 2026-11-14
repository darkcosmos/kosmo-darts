package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.history.HistoryTags
import io.github.darkcosmos.kosmodarts.ui.history.LOADING_INDICATOR_DELAY_MILLIS
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** PS2 to PS6, PS8, ST6, ST9, ST10, HS7: the Profile screen alone, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class ProfileScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()
    private val both = listOf(ProfileTestData.x01Raw(), ProfileTestData.cricketRaw())

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showState(state: ProfileState) = show { ProfileScreen(state, events::add) }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun loaded(
        raws: List<RawStatsMatch> = both,
        resetAt: Long = ProfileTestData.CREATED_AT,
        section: ProfileSection = ProfileSection.X01,
        x01Filter: X01Filter = X01Filter(),
        cricketFilter: CricketFilter = CricketFilter(),
        x01Period: Period = Period.SINCE_ALWAYS,
    ): ProfileState {
        val data = ProfileTestData.data(raws, resetAt)
        val base = ProfileState(
            section = section,
            x01Filter = x01Filter,
            cricketFilter = cricketFilter,
            x01Period = x01Period,
            now = NOW,
        )
        return base.copy(content = ProfileContent.Loaded(data, data.figures(base.query)))
    }

    private fun option(group: String, text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(group)))

    /** PS4 */
    @Test
    fun x01SectionShowsItsFigures() {
        showState(loaded())
        node(ProfileTags.NAME).assertTextEquals("Kevin")
        node(ProfileTags.figure("average")).assertIsDisplayed()
        node(ProfileTags.figure("best_checkout")).assertIsDisplayed()
        node(ProfileTags.figure("matches_won")).assertIsDisplayed()
    }

    /** ST6, PS4 */
    @Test
    fun aFigureThatCannotBeComputedShowsADash() {
        showState(loaded(section = ProfileSection.CRICKET))
        // The only Cricket match is Standard: nothing was given in Cut-throat.
        composeRule.onNode(hasTestTag(ProfileTags.figure("points_given")), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(
            hasText("–") and hasAnyAncestor(hasTestTag(ProfileTags.figure("points_given"))),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /** PS3 */
    @Test
    fun tabsSwitchSections() {
        showState(loaded())
        node(ProfileTags.tab(ProfileSection.X01)).assertIsSelected()
        node(ProfileTags.tab(ProfileSection.CRICKET)).performClick()
        node(ProfileTags.tab(ProfileSection.HEATMAP)).performClick()
        assertEquals(
            listOf(
                ProfileEvent.SectionSelected(ProfileSection.CRICKET),
                ProfileEvent.SectionSelected(ProfileSection.HEATMAP),
            ),
            events,
        )
    }

    /** PS3, PS4 */
    @Test
    fun cricketSectionWithAllVariantsShowsTwoResultBlocks() {
        showState(loaded(section = ProfileSection.CRICKET))
        node(ProfileTags.block("results_standard")).assertExists()
        node(ProfileTags.block("results_cut_throat")).assertExists()
    }

    /** PS3, PS4 */
    @Test
    fun cricketSectionWithOneVariantShowsOneResultBlock() {
        showState(
            loaded(section = ProfileSection.CRICKET, cricketFilter = CricketFilter(CricketVariant.STANDARD)),
        )
        node(ProfileTags.block("results_standard")).assertExists()
        node(ProfileTags.block("results_cut_throat")).assertDoesNotExist()
    }

    /** PS3 */
    @Test
    fun heatmapTabShowsItsModeFiltersAndItsSection() {
        showState(loaded(section = ProfileSection.HEATMAP))
        node(HeatmapTags.MODE_FILTER).assertIsDisplayed()
        node(ProfileTags.PERIOD_FILTER).assertIsDisplayed()
        node(HeatmapTags.TOP_LIST).assertExists()
    }

    /** ST3, PS3 */
    @Test
    fun x01FiltersAndPeriodSelect() {
        showState(loaded(x01Filter = X01Filter(startScore = 301)))
        option(ProfileTags.START_SCORE_FILTER, "301").assertIsSelected()
        option(ProfileTags.START_SCORE_FILTER, "501").performClick()
        option(ProfileTags.OUT_RULE_FILTER, "SINGLE OUT").performClick()
        option(ProfileTags.PERIOD_FILTER, "Last 30 days").performClick()
        assertEquals(
            listOf(
                ProfileEvent.X01FilterSelected(X01Filter(startScore = 501)),
                ProfileEvent.X01FilterSelected(X01Filter(startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)),
                ProfileEvent.X01PeriodSelected(Period.LAST_30_DAYS),
            ),
            events,
        )
    }

    /** ST3, PS3 */
    @Test
    fun cricketFilterAndPeriodSelect() {
        showState(loaded(section = ProfileSection.CRICKET))
        option(ProfileTags.VARIANT_FILTER, "CUT-THROAT").performClick()
        option(ProfileTags.PERIOD_FILTER, "Last 10 matches").performClick()
        assertEquals(
            listOf(
                ProfileEvent.CricketFilterSelected(CricketFilter(CricketVariant.CUT_THROAT)),
                ProfileEvent.CricketPeriodSelected(Period.LAST_10_MATCHES),
            ),
            events,
        )
    }

    /** PS6 */
    @Test
    fun noMatchAtAllShowsTheEmptyState() {
        showState(loaded(raws = emptyList()))
        node(ProfileTags.EMPTY).assertIsDisplayed()
        node(ProfileTags.NAME).assertIsDisplayed()
    }

    /** PS6 */
    @Test
    fun noMatchForTheFiltersKeepsTheFiltersShown() {
        showState(loaded(x01Filter = X01Filter(startScore = 501)))
        node(ProfileTags.NO_MATCH_FILTERS).assertIsDisplayed()
        node(ProfileTags.START_SCORE_FILTER).assertIsDisplayed()
        node(ProfileTags.PERIOD_FILTER).assertIsDisplayed()
        node(ProfileTags.tab(ProfileSection.CRICKET)).assertIsDisplayed()
    }

    /** HS7 */
    @Test
    fun failedReadShowsAMessageAndRetry() {
        showState(ProfileState(content = ProfileContent.Failed))
        node(ProfileTags.FAILED).assertIsDisplayed()
        node(ProfileTags.RETRY).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.RetryTapped), events)
    }

    /** HS7 */
    @Test
    fun loadingShowsAnIndicatorAfterTheDelay() {
        composeRule.mainClock.autoAdvance = false
        showState(ProfileState())
        node(HistoryTags.LOADING).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MILLIS + FRAME)
        node(HistoryTags.LOADING).assertIsDisplayed()
    }

    /** PS8 */
    @Test
    fun backHasADescriptionAndRaisesBack() {
        showState(loaded())
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf<AppEvent>(AppEvent.Back), events)
    }

    /** PS8 */
    @Test
    fun tabsAndChipsAreAtLeast48DpHigh() {
        showState(loaded())
        ProfileSection.entries.forEach { node(ProfileTags.tab(it)).assertHeightIsAtLeast(MIN_TARGET) }
        composeRule.onAllNodes(isSelectable() and hasAnyAncestor(hasTestTag(ProfileTags.START_SCORE_FILTER)))
            .assertCountEquals(START_SCORE_OPTIONS)
        option(ProfileTags.START_SCORE_FILTER, "501").assertHeightIsAtLeast(MIN_TARGET)
        option(ProfileTags.PERIOD_FILTER, "Last 30 days").assertHeightIsAtLeast(MIN_TARGET)
    }

    /** ST10 */
    @Test
    fun ignoredMatchesNoticeShowsOnlyWhenThereAreSome() {
        showState(loaded(raws = both + ProfileTestData.corruptRaw("bad")))
        node(ProfileTags.IGNORED).assertTextEquals("1 match ignored (unreadable data)")
    }

    /** ST10 */
    @Test
    fun noIgnoredMatchNoNotice() {
        showState(loaded())
        node(ProfileTags.IGNORED).assertDoesNotExist()
    }

    /** ST9 */
    @Test
    fun sinceAlwaysUntilAReallyResetThenADate() {
        showState(loaded())
        node(ProfileTags.SINCE).assertTextEquals("Since always")
        showState(loaded(resetAt = ProfileTestData.CREATED_AT + 1))
        node(ProfileTags.SINCE).assertIsDisplayed()
        composeRule.onNode(hasTestTag(ProfileTags.SINCE) and hasText("Since always")).assertDoesNotExist()
    }

    /** PS3, D8: at a large font the content scrolls; the tabs stay, the header goes. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT, fontScale = 2.0f)
    fun tabsStayPinnedWhileTheContentScrolls() {
        showState(loaded())
        node(ProfileTags.NAME).assertIsDisplayed()
        node(ProfileTags.CONTENT).performScrollToNode(hasTestTag(ProfileTags.block("trend")))
        node(ProfileTags.block("trend")).assertIsDisplayed()
        node(ProfileTags.tab(ProfileSection.CRICKET)).assertIsDisplayed()
        // The header scrolled away: it is no longer composed, or at least not displayed.
        if (composeRule.onAllNodesWithTag(ProfileTags.NAME).fetchSemanticsNodes().isNotEmpty()) {
            node(ProfileTags.NAME).assertIsNotDisplayed()
        }
    }

    /** PS8, U11: at 200 % the options are stacked, 48 dp high, and still select. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT, fontScale = 2.0f)
    fun filtersStayUsableAtLargestFont() {
        showState(loaded())
        node(ProfileTags.tab(ProfileSection.HEATMAP)).assertHeightIsAtLeast(MIN_TARGET)
        option(ProfileTags.START_SCORE_FILTER, "501").assertHeightIsAtLeast(MIN_TARGET).performClick()
        assertEquals(listOf<AppEvent>(ProfileEvent.X01FilterSelected(X01Filter(startScore = 501))), events)
    }

    /** PS3: in landscape the tabs and filters are at the left, the figures at the right. */
    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE)
    fun landscapeShowsTabsFiltersAndFigures() {
        showState(loaded())
        node(ProfileTags.tab(ProfileSection.X01)).assertIsDisplayed()
        node(ProfileTags.START_SCORE_FILTER).assertIsDisplayed()
        node(ProfileTags.figure("average")).assertIsDisplayed()
        node(ProfileTags.block("results")).assertIsDisplayed()
    }

    /** PS3: the section of the state is the selected tab. */
    @Test
    fun theSelectedSectionIsSelected() {
        showState(loaded(section = ProfileSection.CRICKET))
        node(ProfileTags.tab(ProfileSection.CRICKET)).assertIsSelected()
        composeRule.onNodeWithText("Marks per round").assertIsDisplayed()
    }

    private companion object {
        const val NOW = 10_000L
        const val FRAME = 16L
        val MIN_TARGET = 48.dp
        const val START_SCORE_OPTIONS = 3
    }
}
