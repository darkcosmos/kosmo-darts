package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.stats.TrendPoint
import io.github.darkcosmos.kosmodarts.engine.stats.summarize
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** PS5, PS8: the trend curve component (0, 1, 2 and 1,000 points, flat line) and the texts around it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class TrendCurveTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showCurve(points: List<TrendPoint>, description: String = "curve text") =
        show { TrendCurve(TrendDisplay(points, summarize(points), description)) }

    private fun curveCount() = composeRule.onAllNodesWithTag(ProfileTags.TREND_CURVE).fetchSemanticsNodes().size

    @Test
    fun noPointAndOnePointDrawNoCurve() {
        showCurve(emptyList())
        assertEquals(0, curveCount())
        showCurve(TrendTestData.points(listOf(50.0)))
        assertEquals(0, curveCount())
    }

    @Test
    fun twoPointsDrawTheCurveWithItsDescription() {
        showCurve(TrendTestData.points(listOf(40.0, 60.0)))
        composeRule.onNodeWithTag(ProfileTags.TREND_CURVE).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("curve text")))
    }

    @Test
    fun aThousandPointsAreDrawn() {
        showCurve(TrendTestData.points(TrendTestData.wave(1_000)))
        composeRule.onNodeWithTag(ProfileTags.TREND_CURVE).assertIsDisplayed()
    }

    @Test
    fun equalValuesDrawAFlatLineWithoutDividingByZero() {
        showCurve(TrendTestData.points(List(5) { 50.0 }))
        composeRule.onNodeWithTag(ProfileTags.TREND_CURVE).assertIsDisplayed()
    }

    private fun showState(state: ProfileState) = show { ProfileScreen(state, onEvent = {}) }

    private fun loaded(section: ProfileSection = ProfileSection.X01): ProfileState {
        val data = ProfileTestData.data(listOf(ProfileTestData.x01Raw(), ProfileTestData.cricketRaw()))
        val base = ProfileState(section = section, now = 10_000L)
        return base.copy(content = ProfileContent.Loaded(data, data.figures(base.query)))
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun scrollToTrend() {
        node(ProfileTags.CONTENT).performScrollToNode(hasTestTag(ProfileTags.figure("trend_last_date")))
    }

    /** PS5: the min, max and date labels are the ones of the state's summary, whatever the points are. */
    @Test
    fun labelsOnTheScreenAreTheSummaryValues() {
        val x01 = TrendTestData.points(listOf(50.0, 40.0, 70.0, 60.0))
        val state = TrendTestData.withTrend(loaded(), x01, emptyList())
        showState(state)
        scrollToTrend()
        val summary = requireNotNull(state.figures).x01.trendSummary
        node(ProfileTags.figure("trend_lowest")).assertTextEquals("Lowest", "40.00")
        node(ProfileTags.figure("trend_highest")).assertTextEquals("Highest", "70.00")
        val first = dateText(requireNotNull(summary.firstDate), Locale.ENGLISH)
        val last = dateText(requireNotNull(summary.lastDate), Locale.ENGLISH)
        node(ProfileTags.figure("trend_first_date")).assertTextEquals("First match", first)
        node(ProfileTags.figure("trend_last_date")).assertTextEquals("Last match", last)
        node(ProfileTags.figure("trend_matches")).assertTextEquals("Matches in the curve", "4 matches")
        node(ProfileTags.TREND_CURVE).assertIsDisplayed()
    }

    @Test
    fun cricketSectionShowsItsMprWithOneDecimal() {
        val cricket = TrendTestData.points(listOf(1.5, 2.5))
        val state = TrendTestData.withTrend(loaded(ProfileSection.CRICKET), emptyList(), cricket)
        showState(state)
        scrollToTrend()
        node(ProfileTags.figure("trend_lowest")).assertTextEquals("Lowest", "1.5")
        node(ProfileTags.figure("trend_highest")).assertTextEquals("Highest", "2.5")
    }

    @Test
    fun underTwoPointsATextReplacesTheCurve() {
        showState(TrendTestData.withTrend(loaded(), TrendTestData.points(listOf(48.0)), emptyList()))
        node(ProfileTags.CONTENT).performScrollToNode(hasTestTag(ProfileTags.TREND_TOO_FEW))
        node(ProfileTags.TREND_TOO_FEW).assertTextEquals("A curve needs at least 2 matches.")
        assertEquals(0, curveCount())
        node(ProfileTags.figure("trend_lowest")).assertTextEquals("Lowest", "48.00")
    }
}
