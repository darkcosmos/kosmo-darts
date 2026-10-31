package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen with the longest Cricket text it can show (docs/rules/cricket.md, CR20; reviews U6, U7): the
 * resume card keeps its title on one horizontal line above the format line, and the "15-20" thumbnail is whole, at
 * font scales 1.0 and 2.0, in French and in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeFitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun showCutThroatMatch() {
        val saved = CricketSaveFixtures.saved(
            CricketSaveFixtures.match(
                "Anna",
                "Bob",
                "Cid",
                variant = CricketVariant.CUT_THROAT,
                isBullInPlay = false,
                legsPerSet = 2,
                sets = 2,
            ).confirmLegsWonBy(0, 0, 1),
        )
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { HomeScreen(Screen.Home(resumableGame = saved), onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun layoutOf(tag: String, text: String? = null): Pair<TextLayoutResult, Rect> {
        val interaction = if (text == null) {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        } else {
            composeRule.onNodeWithText(text, useUnmergedTree = true)
        }
        if (text == null) interaction.performScrollTo()
        val node = interaction.fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single() to node.boundsInRoot
    }

    private fun assertWholeOnOneLine(tag: String) {
        val (layout, bounds) = layoutOf(tag)
        assertEquals("$tag on one line", 1, layout.lineCount)
        assertFalse("$tag ellipsized", layout.isLineEllipsized(0))
        assertTrue("$tag is a horizontal line, ${bounds.width} px wide", bounds.width > bounds.height)
    }

    private fun assertCardFits() {
        assertWholeOnOneLine(HomeTags.SUMMARY_TITLE)
        val card = composeRule.onNodeWithTag(HomeTags.SUMMARY, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = layoutOf(HomeTags.SUMMARY_TITLE).second
        val format = layoutOf(HomeTags.SUMMARY_FORMAT).second
        assertTrue("the format line is below the title", format.top >= title.bottom - 1f)
        assertTrue("the title is wide", title.width >= card.width / 2)
        assertTrue("the format line is not cut", layoutOf(HomeTags.SUMMARY_FORMAT).first.let { !it.didOverflowHeight })
    }

    private fun assertThumbnailWhole() {
        val (layout, bounds) = layoutOf(tag = "", text = "15–20")
        val box = with(composeRule.density) { 64.dp.toPx() }
        assertEquals("one line", 1, layout.lineCount)
        assertFalse("not ellipsized", layout.isLineEllipsized(0))
        assertFalse("not cut", layout.didOverflowWidth || layout.didOverflowHeight)
        assertTrue("inside the 64 dp box: ${bounds.width} px", bounds.width <= box && bounds.height <= box)
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `U6 U7 the long Cricket card and the thumbnail fit in French`() {
        showCutThroatMatch()

        assertCardFits()
        assertThumbnailWhole()
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `U6 U7 the long Cricket card and the thumbnail fit in English`() {
        showCutThroatMatch()

        assertCardFits()
        assertThumbnailWhole()
    }

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun `U6 U7 the long Cricket card and the thumbnail fit in French at font scale 2`() {
        showCutThroatMatch()

        assertCardFits()
        assertThumbnailWhole()
    }

    @Test
    @Config(qualifiers = "+en-port", fontScale = 2.0f)
    fun `U6 U7 the long Cricket card and the thumbnail fit in English at font scale 2`() {
        showCutThroatMatch()

        assertCardFits()
        assertThumbnailWhole()
    }
}
