package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.components.breaksOnlyBetweenWords
import io.github.darkcosmos.kosmodarts.ui.components.lineEnds
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R2.2 (Kosmo, Q17 B): the quarter-turn label of the tall next-player key, in the 120 x 304.5 dp box of the landscape
 * key. Drawn taller than wide, inside the box, not clipped, 28 sp on one line or at least 20 sp broken between words,
 * the same at both font scales, and its semantics text is the label.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w914dp-h411dp-land-420dpi")
class VerticalKeyLabelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val labels = listOf(
        "JOUEUR SUIVANT", "NEXT PLAYER", "VALIDER LA PARTIE", "VALIDER LA MANCHE", "VALIDER LE SET",
        "CONFIRM MATCH", "CONFIRM LEG", "CONFIRM SET",
    )

    private fun show(label: String, widthDp: Float, heightDp: Float) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent {
                KosmoDartsTheme {
                    Box(Modifier.wrapContentSize(Alignment.TopStart).size(widthDp.dp, heightDp.dp).testTag(BOX)) {
                        VerticalKeyLabel(label, KosmoTheme.typography.keypad, Modifier.testTag(LABEL))
                    }
                }
            }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNode = composeRule.onNodeWithTag(tag).fetchSemanticsNode()

    private fun textNode(): SemanticsNode {
        fun find(node: SemanticsNode): SemanticsNode? =
            if (node.config.contains(SemanticsActions.GetTextLayoutResult)) {
                node
            } else {
                node.children.firstNotNullOfOrNull(::find)
            }
        return checkNotNull(find(node(LABEL))) { "no text" }
    }

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        textNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun assertFits(label: String, widthDp: Float, heightDp: Float): Float {
        show(label, widthDp, heightDp)
        val box = node(BOX).boundsInRoot
        val drawn = textNode().boundsInRoot
        val layout = layout()
        val sizeSp = effectiveSp(layout)
        assertTrue("$label: bounds $drawn", drawn.height > drawn.width)
        assertTrue("$label: $drawn not inside $box", box.contains(drawn))
        assertTrue("$label: clipped", !layout.didOverflowHeight && !layout.didOverflowWidth)
        val lineEnds = layout.lineEnds()
        assertTrue("$label: broken inside a word", breaksOnlyBetweenWords(layout.layoutInput.text.text, lineEnds))
        assertTrue("$label: $sizeSp sp under 20", sizeSp >= MIN_SP - EPSILON)
        assertTrue("$label: $sizeSp sp over 28", sizeSp <= MAX_SP + EPSILON)
        assertEquals(label, textNode().config[SemanticsProperties.Text].joinToString("") { it.text })
        return sizeSp
    }

    /** The drawn size in sp of scale 1: the key labels are fixed, so it must not move with the font scale. */
    private fun effectiveSp(layout: TextLayoutResult): Float = with(layout.layoutInput.density) {
        layout.layoutInput.style.fontSize.toPx() / density
    }

    private fun assertEveryLabelFits() {
        labels.forEach { assertFits(it, WIDTH, HEIGHT) }
    }

    private fun Rect.contains(other: Rect): Boolean =
        other.left >= left - 1f && other.right <= right + 1f && other.top >= top - 1f && other.bottom <= bottom + 1f

    @Test
    fun `every label fits the tall key at scale 1`() = assertEveryLabelFits()

    @Test
    @Config(fontScale = 2.0f)
    fun `every label fits the tall key at scale 2`() = assertEveryLabelFits()

    @Test
    fun `a short label stays on one line at 28 sp`() {
        val size = assertFits("CONFIRM SET", WIDTH, HEIGHT)

        assertEquals(1, layout().lineCount)
        assertEquals(MAX_SP, size, EPSILON)
    }

    @Test
    fun `a half-height box forces the two-line break between words`() {
        show("JOUEUR SUIVANT", WIDTH, SHORT_HEIGHT)

        val layout = layout()
        assertEquals(2, layout.lineCount)
        assertTrue(breaksOnlyBetweenWords(layout.layoutInput.text.text, layout.lineEnds()))
        assertTrue(effectiveSp(layout) >= MIN_SP - EPSILON)
        assertTrue(node(BOX).boundsInRoot.contains(textNode().boundsInRoot))
    }

    private companion object {
        const val BOX = "box"
        const val LABEL = "label"
        const val WIDTH = 120f
        const val HEIGHT = 304.5f
        const val SHORT_HEIGHT = 150f
        const val MIN_SP = 20f
        const val MAX_SP = 28f
        const val EPSILON = 0.01f
    }
}
