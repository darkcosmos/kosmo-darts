package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #69, M15, design system 6.3: while an X01 checkout waits, the word "CHECKOUT" is whole, on one line, inside its
 * chip, with the name row, the line under the chip and the dart slots whole and apart, in portrait and landscape at
 * font scales 1.0, 1.3, 1.5 and 2.0. Above 1.0 the chip keeps its least height (D1); at 1.0 the sizes are the ones of
 * before the fix (D9). Until #69 the check on the text inside the chip was missing, so a clipped word passed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CheckoutBannerFitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun dp(px: Float): Float = with(composeRule.density) { px.toDp().value }

    private fun visible(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun textLayout(tag: String): TextLayoutResult =
        layoutOf(composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())

    private fun innerTextLayout(tag: String): TextLayoutResult = layoutOf(
        composeRule.onNode(
            hasAnyAncestor(hasTestTag(tag)).and(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)),
            useUnmergedTree = true,
        ).fetchSemanticsNode(),
    )

    private fun assertWhole(tag: String) {
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val rect = node.boundsInRoot
        assertEquals("$tag is cut: $rect", node.size.height.toFloat(), rect.height, EPSILON)
        assertTrue("$tag $rect below $screen", rect.bottom <= screen.bottom + EPSILON)
        assertTrue("$tag $rect above $screen", rect.top >= screen.top - EPSILON)
    }

    private fun assertApart(first: String, second: String) {
        val overlap = visible(first).intersect(visible(second))
        assertTrue("$first and $second overlap: $overlap", overlap.width <= EPSILON || overlap.height <= EPSILON)
    }

    /** Ann leaves 121 with three darts: the first player's zone shows the pending checkout (M15). */
    private fun reachPendingCheckout(names: List<String>, withSets: Boolean) {
        send(*startMatch(names, withSets), *ANN_AT_121, *triple(20), *triple(11))
        send(AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(14))
    }

    private fun checkFit(
        fontScale: Float,
        names: List<String> = NAMES,
        withSets: Boolean = false,
        preFix: PreFix? = null,
    ) {
        reachPendingCheckout(names, withSets)
        val chip = visible(GameTags.CHECKOUT_BANNER_LABEL)
        val text = innerTextLayout(GameTags.CHECKOUT_BANNER_LABEL)
        val line = visible(GameTags.CHECKOUT_LINE)
        assertEquals("banner lines", 1, text.lineCount)
        assertFalse("banner text overflows in height", text.didOverflowHeight)
        assertFalse("banner text overflows in width", text.didOverflowWidth)
        assertTrue(
            "banner text ${dp(text.size.height.toFloat())} dp higher than the chip ${dp(chip.height)} dp " +
                "less its padding at font scale $fontScale",
            text.size.height <= chip.height - px(CHIP_PADDING_DP * 2) + EPSILON,
        )
        assertTrue("banner text ${text.size} wider than its chip $chip", text.size.width <= chip.width + EPSILON)
        if (fontScale > 1f) {
            assertTrue(
                "chip ${dp(chip.height)} dp lower than the least $BANNER_MIN_DP dp at font scale $fontScale",
                chip.height >= px(BANNER_MIN_DP) - EPSILON,
            )
        }
        val nameLayout = textLayout(GameTags.playerName(0))
        assertEquals("name lines", 1, nameLayout.lineCount)
        assertFalse("checkout line overflows in height", textLayout(GameTags.CHECKOUT_LINE).didOverflowHeight)
        assertTrue("checkout line below its place: $line", line.bottom <= visible(GameTags.dartSlot(0)).top + EPSILON)
        listOf(GameTags.playerName(0), GameTags.CHECKOUT_BANNER_LABEL, GameTags.CHECKOUT_LINE, GameTags.dartSlot(0))
            .forEach(::assertWhole)
        assertWhole(GameTags.dartSlot(2))
        assertApart(GameTags.playerName(0), GameTags.CHECKOUT_BANNER_LABEL)
        assertApart(GameTags.CHECKOUT_BANNER_LABEL, GameTags.CHECKOUT_LINE)
        assertApart(GameTags.CHECKOUT_LINE, GameTags.dartSlot(0))
        preFix?.let {
            assertEquals("chip height as before #69", it.chipDp, dp(chip.height), PRE_FIX_TOLERANCE_DP)
            val textDp = dp(text.size.height.toFloat())
            assertEquals("banner text height as before #69", it.textDp, textDp, PRE_FIX_TOLERANCE_DP)
        }
    }

    // fr, portrait 411 x 814 (420 dpi)

    @Test
    @Config(qualifiers = "fr-w411dp-h814dp-port-420dpi", fontScale = 1.0f)
    fun `M15 portrait 411 x 814 at font scale 1_0`() = checkFit(1.0f, preFix = PRE_FIX["port411"])

    @Test
    @Config(qualifiers = "fr-w411dp-h814dp-port-420dpi", fontScale = 1.3f)
    fun `M15 portrait 411 x 814 at font scale 1_3`() = checkFit(1.3f)

    @Test
    @Config(qualifiers = "fr-w411dp-h814dp-port-420dpi", fontScale = 1.5f)
    fun `M15 portrait 411 x 814 at font scale 1_5`() = checkFit(1.5f)

    @Test
    @Config(qualifiers = "fr-w411dp-h814dp-port-420dpi", fontScale = 2.0f)
    fun `M15 portrait 411 x 814 at font scale 2_0`() = checkFit(2.0f)

    // fr, portrait 360 x 640 (xhdpi)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = 1.0f)
    fun `M15 portrait 360 x 640 at font scale 1_0`() = checkFit(1.0f, preFix = PRE_FIX["port360"])

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = 1.3f)
    fun `M15 portrait 360 x 640 at font scale 1_3`() = checkFit(1.3f)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = 1.5f)
    fun `M15 portrait 360 x 640 at font scale 1_5`() = checkFit(1.5f)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = 2.0f)
    fun `M15 portrait 360 x 640 at font scale 2_0`() = checkFit(2.0f)

    // fr, the Robolectric default landscape device

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.0f)
    fun `M15 fr-land at font scale 1_0`() = checkFit(1.0f)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.3f)
    fun `M15 fr-land at font scale 1_3`() = checkFit(1.3f)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.5f)
    fun `M15 fr-land at font scale 1_5`() = checkFit(1.5f)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 2.0f)
    fun `M15 fr-land at font scale 2_0`() = checkFit(2.0f)

    // fr, landscape 640 x 360 (xhdpi), the smallest supported landscape screen

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 1.0f)
    fun `M15 landscape 640 x 360 at font scale 1_0`() = checkFit(1.0f, preFix = PRE_FIX["land640"])

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 1.3f)
    fun `M15 landscape 640 x 360 at font scale 1_3`() = checkFit(1.3f)

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 1.5f)
    fun `M15 landscape 640 x 360 at font scale 1_5`() = checkFit(1.5f)

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 2.0f)
    fun `M15 landscape 640 x 360 at font scale 2_0`() = checkFit(2.0f)

    // Owner's phone: landscape 800 x 360 (xxhdpi)

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi", fontScale = 1.0f)
    fun `M15 landscape 800 x 360 at font scale 1_0`() = checkFit(1.0f, preFix = PRE_FIX["land800"])

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi", fontScale = 2.0f)
    fun `M15 landscape 800 x 360 at font scale 2_0`() = checkFit(2.0f)

    // Edge cases on the smallest screens at the largest font size: the sets match (S and M chips in the name row), and
    // one long player name.

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 2.0f)
    fun `M15 landscape 640 x 360 at 2_0 in a sets match`() = checkFit(2.0f, withSets = true)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = 2.0f)
    fun `M15 portrait 360 x 640 at 2_0 in a sets match`() = checkFit(2.0f, withSets = true)

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 2.0f)
    fun `M15 landscape 640 x 360 at 2_0 with a long name`() =
        checkFit(2.0f, names = listOf(LONG_NAME) + NAMES.drop(1), withSets = true)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = 2.0f)
    fun `M15 portrait 360 x 640 at 2_0 with a long name`() =
        checkFit(2.0f, names = listOf(LONG_NAME) + NAMES.drop(1), withSets = true)

    // T4: after "ANNULER" (undo of the final dart) the zone is back to normal mode at the user's font scale.

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 2.0f)
    fun `M15 undo of the final dart restores the name row at the user's font scale`() {
        send(*startMatch(NAMES, withSets = true), *ANN_AT_121, *triple(20), *triple(11))
        val nameBefore = textLayout(GameTags.playerName(0)).size.height
        send(AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(14))
        composeRule.onNodeWithTag(GameTags.CHECKOUT_BANNER, useUnmergedTree = true).fetchSemanticsNode()
        send(AppEvent.UndoTapped)
        composeRule.onNodeWithTag(GameTags.CHECKOUT_BANNER, useUnmergedTree = true).assertDoesNotExist()
        assertEquals("name row after undo", nameBefore, textLayout(GameTags.playerName(0)).size.height)
        assertWhole(GameTags.playerName(0))
        assertWhole(GameTags.dartSlot(0))
    }

    /** The chip and the text height, in dp, recorded on `main` at 7441ae3 at font scale 1.0 (D9). */
    private class PreFix(val chipDp: Float, val textDp: Float)

    private companion object {
        const val EPSILON = 1f
        const val CHIP_PADDING_DP = 4f
        const val BANNER_MIN_DP = 40f
        const val PRE_FIX_TOLERANCE_DP = 0.75f
        const val LONG_NAME = "Maximilien-Alexandre"
        val NAMES = listOf("Ann", "Bob", "Cid", "Dan")

        val PRE_FIX: Map<String, PreFix> = mapOf(
            "port411" to PreFix(109.333336f, 100.95238f),
            "port360" to PreFix(35f, 27f),
            "land640" to PreFix(35f, 27f),
            "land800" to PreFix(65.333336f, 57.333332f),
        )

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** From home: 301 double out, [names], and with [withSets] 3 legs per set and 3 sets (S and M chips), Start. */
        fun startMatch(names: List<String>, withSets: Boolean): Array<AppEvent> = buildList {
            add(AppEvent.NewGame(GameMode.X01))
            add(AppEvent.StartScoreSelected(301))
            repeat(names.size - 2) { add(AppEvent.PlayerAdded) }
            names.forEachIndexed { index, name -> add(AppEvent.NameChanged(index, name)) }
            if (withSets) {
                repeat(2) { add(AppEvent.LegsPerSetChanged(1)) }
                repeat(2) { add(AppEvent.SetsChanged(1)) }
            }
            add(AppEvent.StartRequested)
        }.toTypedArray()

        /** The first player leaves 121 (three triple 20), the three others miss: the first player throws next. */
        val ANN_AT_121: Array<AppEvent> = buildList {
            addAll(triple(20) + triple(20) + triple(20))
            add(AppEvent.NextPlayerTapped)
            repeat(3) {
                repeat(3) { add(AppEvent.MissTapped) }
                add(AppEvent.NextPlayerTapped)
            }
        }.toTypedArray()
    }
}
