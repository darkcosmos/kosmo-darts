package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HM5: the legend shows the numeric end values of the relative scale and describes it to TalkBack. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
class HeatmapLegendTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun show(maxShare: Double) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { HeatmapLegend(maxShare) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun legendShowsTheMaxOfTheBoardAndTheNeutralSwatch() {
        show(0.047)
        composeRule.onNodeWithTag(HeatmapLegendTags.NEUTRAL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(HeatmapLegendTags.BAR, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(HeatmapLegendTags.MAX, useUnmergedTree = true).assertExists()
    }

    @Test
    fun legendIsOneNodeDescribedForTalkBackWithTheMax() {
        show(0.30)
        val description = composeRule.onNodeWithTag(HeatmapLegendTags.LEGEND)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(description, description.contains("30 %"))
        assertTrue(description, description.contains("dark grey"))
    }
}
