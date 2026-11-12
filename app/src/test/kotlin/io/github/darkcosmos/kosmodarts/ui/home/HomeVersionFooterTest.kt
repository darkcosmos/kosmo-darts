package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fixed version footer of the home screen (docs/rules/history.md, AV1 and AV3), with a 5-digit build number: the
 * text is whole (never cut or ellipsized), the footer stays put when the column scrolls, and every control can be
 * scrolled to fully above it, in both languages, both orientations, at font scales 1 and 2, with and without a saved
 * 4-player match (the longest resume card).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeVersionFooterTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val savedFourPlayers = SaveFixtures.savedAfter(SaveFixtures.config(players = 4), listOf())

    private fun show(saved: Boolean) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent {
                KosmoDartsTheme {
                    HomeScreen(
                        home = Screen.Home(resumableGame = if (saved) savedFourPlayers else null),
                        onEvent = {},
                        versionName = VERSION_NAME,
                        buildNumber = BUILD_NUMBER,
                    )
                }
            }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun footerNode(): SemanticsNode =
        composeRule.onNodeWithTag(HomeTags.VERSION, useUnmergedTree = true).fetchSemanticsNode()

    private fun hasVerticalScrollAncestor(node: SemanticsNode): Boolean {
        var parent = node.parent
        while (parent != null) {
            if (SemanticsProperties.VerticalScrollAxisRange in parent.config) return true
            parent = parent.parent
        }
        return false
    }

    private fun assertFooter(expectedText: String, maxLines: Int) {
        composeRule.onNodeWithTag(HomeTags.VERSION, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(expectedText)
        val node = footerNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        assertTrue("at most $maxLines lines, got ${layout.lineCount}", layout.lineCount <= maxLines)
        assertFalse("footer overflows", layout.hasVisualOverflow)
        assertFalse("footer ellipsized", layout.isLineEllipsized(layout.lineCount - 1))
        assertTrue(
            "the last line ends with the build number",
            layout.layoutInput.text.text.endsWith("$BUILD_NUMBER)"),
        )
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("footer inside the window", node.boundsInRoot.bottom <= root.bottom && node.boundsInRoot.top >= 0f)
        assertFalse("footer is not inside the scroll container", hasVerticalScrollAncestor(node))
    }

    private fun assertControlAboveFooter(tag: String, minHeightDp: Int) {
        val control = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        control.performScrollTo()
        control.assertIsDisplayed().assertHasClickAction()
        val bounds = control.fetchSemanticsNode().boundsInRoot
        val footerTop = footerNode().boundsInRoot.top
        val minHeight = with(composeRule.density) { minHeightDp.dp.toPx() }
        assertTrue("$tag bottom ${bounds.bottom} is above the footer top $footerTop", bounds.bottom <= footerTop)
        assertTrue("$tag is at least $minHeightDp dp tall, ${bounds.height} px", bounds.height >= minHeight - 1f)
    }

    private fun check(expectedText: String, maxLines: Int, minHeightDp: Int) {
        for (saved in listOf(false, true)) {
            show(saved)
            assertFooter(expectedText, maxLines)
            val tags = listOf(HomeTags.SETTINGS, HomeTags.MODE_X01, HomeTags.MODE_CRICKET, HomeTags.HISTORY) +
                listOfNotNull(HomeTags.RESUME.takeIf { saved })
            tags.forEach { assertControlAboveFooter(it, minHeightDp) }
            assertFooter(expectedText, maxLines)
        }
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `AV1 AV3 english portrait font 1`() = check(EN_TEXT, maxLines = 1, minHeightDp = PORTRAIT_MIN_DP)

    @Test
    @Config(qualifiers = "+fr-port")
    fun `AV1 AV3 french portrait font 1`() = check(FR_TEXT, maxLines = 1, minHeightDp = PORTRAIT_MIN_DP)

    @Test
    @Config(qualifiers = "+en-land")
    fun `AV1 AV3 english landscape font 1`() = check(EN_TEXT, maxLines = 1, minHeightDp = LANDSCAPE_MIN_DP)

    @Test
    @Config(qualifiers = "+fr-land")
    fun `AV1 AV3 french landscape font 1`() = check(FR_TEXT, maxLines = 1, minHeightDp = LANDSCAPE_MIN_DP)

    @Test
    @Config(qualifiers = "+en-port", fontScale = 2.0f)
    fun `AV1 AV3 english portrait font 2`() = check(EN_TEXT, maxLines = 2, minHeightDp = PORTRAIT_MIN_DP)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun `AV1 AV3 french portrait font 2`() = check(FR_TEXT, maxLines = 2, minHeightDp = PORTRAIT_MIN_DP)

    @Test
    @Config(qualifiers = "+en-land", fontScale = 2.0f)
    fun `AV1 AV3 english landscape font 2`() = check(EN_TEXT, maxLines = 2, minHeightDp = LANDSCAPE_MIN_DP)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 2.0f)
    fun `AV1 AV3 french landscape font 2`() = check(FR_TEXT, maxLines = 2, minHeightDp = LANDSCAPE_MIN_DP)

    private companion object {
        const val VERSION_NAME = "0.1.0"
        const val BUILD_NUMBER = "12345"
        const val EN_TEXT = "Version 0.1.0 (build 12345)"
        const val FR_TEXT = "Version 0.1.0 (build 12345)"

        /** CLAUDE.md: 48 dp touch targets, 42 dp tall buttons allowed in landscape. */
        const val PORTRAIT_MIN_DP = 48
        const val LANDSCAPE_MIN_DP = 42
    }
}
