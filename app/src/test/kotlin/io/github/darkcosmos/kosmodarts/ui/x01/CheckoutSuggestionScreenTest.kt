package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.revealIfScrolled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checkout suggestion on the game screen (docs/rules/checkout-suggestions.md, CS5 to CS9), on a Pixel 7: text,
 * TalkBack description and order, the reserved line (the card never moves) and no truncation at font scale 2.
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: without native graphics, Robolectric measures text as almost zero wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CheckoutSuggestionScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction =
        composeRule.onNode(hasTestTag(tag), useUnmergedTree = true)

    private fun bounds(tag: String): Rect = node(tag).revealIfScrolled().fetchSemanticsNode().boundsInRoot

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    /** Text shown in the suggestion line, or null when the line is empty. */
    private fun shownText(): String? = composeRule.onAllNodes(hasTestTag(GameTags.CHECKOUT_SUGGESTION), true)
        .fetchSemanticsNodes()
        .singleOrNull()
        ?.config
        ?.getOrNull(SemanticsProperties.Text)
        ?.joinToString("") { it.text }
        ?.takeIf { it.isNotEmpty() }

    /** TalkBack description of the suggestion line, or null when it is silent. */
    private fun description(): String? = composeRule.onAllNodes(hasTestTag(GameTags.CHECKOUT_SUGGESTION_LINE), true)
        .fetchSemanticsNodes()
        .singleOrNull()
        ?.config
        ?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("")

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)) { "no text: $node" }
        action.action?.invoke(results)
        return results.single()
    }

    /**
     * No visual overflow: `hasVisualOverflow` itself is not usable, because a wrap-content text is reported as
     * overflowing its paragraph width. Equivalent checks: every character laid out, every line inside the text
     * width, no vertical overflow.
     */
    private fun assertComplete(layout: TextLayoutResult, expected: String) {
        assertEquals("never cut", expected.length, layout.getLineEnd(layout.lineCount - 1))
        assertFalse("cut vertically", layout.didOverflowHeight)
        (0 until layout.lineCount).forEach { line ->
            assertTrue("line $line starts cut", layout.getLineLeft(line) >= -EPSILON)
            assertTrue("line $line ends cut", layout.getLineRight(line) <= layout.size.width + EPSILON)
        }
    }

    /** Position of the first dart slot inside the card, and the card height: they never change (CS8). */
    private fun cardPlacement(): List<Float> {
        val card = bounds(GameTags.CURRENT_PLAYER)
        val slot = bounds(GameTags.dartSlot(0))
        return listOf(slot.top - card.top, card.height)
    }

    /** CS7, CS9: the owner's scenario (issue #38, steps 1 to 3) in English. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `CS7 CS9 English text and description follow each dart and undo`() {
        send(*ANN_301)
        assertNull(shownText())
        send(*ANN_AT_170)
        assertEquals("T20${SEP}T20${SEP}BULL", shownText())
        assertEquals("Checkout: Triple 20, Triple 20, Bull", description())
        send(*triple(20))
        assertEquals("T20${SEP}BULL", shownText())
        send(AppEvent.NumberTapped(20))
        assertNull(shownText())
        assertNull(description())
        send(AppEvent.UndoTapped)
        assertEquals("T20${SEP}BULL", shownText())
        send(AppEvent.UndoTapped)
        assertEquals("T20${SEP}T20${SEP}BULL", shownText())
    }

    /** CS7, CS9: French label with non-breaking spaces, and a single written without "S". */
    @Test
    @Config(qualifiers = "+fr-port")
    fun `CS7 CS9 French text and description, single without S`() {
        send(*ANN_301, *ANN_AT_170)
        assertEquals("T20${SEP}T20${SEP}BULL", shownText())
        assertEquals("Sortie$NBSP: Triple 20, Triple 20, Bull", description())
        send(*ANN_AT_60_FROM_170)
        assertEquals("20${SEP}D20", shownText())
        assertEquals("Sortie$NBSP: 20, Double 20", description())
    }

    /** CS7: the label chip reads "SORTIE"; the French description puts a non-breaking space before the colon. */
    @Test
    @Config(qualifiers = "+fr-port")
    fun `CS7 French label chip and description`() {
        send(*ANN_301, *ANN_AT_170)
        assertEquals("SORTIE", string(R.string.checkout_route_label))
        val label = SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("SORTIE")))
        composeRule.onNode(label, useUnmergedTree = true)
            .assertExists()
        assertTrue(string(R.string.game_checkout_suggestion_description, "x").startsWith("Sortie$NBSP:"))
    }

    /** CS5: hidden while a normal turn or a bust waits for "Next player", and in single out after "Next player". */
    @Test
    @Config(qualifiers = "+en-port")
    fun `CS5 hidden while a turn waits`() {
        send(*ANN_301, *triple(20), *triple(20), AppEvent.NumberTapped(11))
        assertNull("third dart waits", shownText())
        send(AppEvent.NextPlayerTapped, *triple(20), *triple(20), *triple(20))
        assertNull("bust waits", shownText())
        assertNull(description())
        send(AppEvent.NextPlayerTapped)
        assertEquals("T20${SEP}T20${SEP}BULL", shownText())
    }

    /** CS4, CS7: single out (issue #38, step 5). */
    @Test
    @Config(qualifiers = "+en-port")
    fun `CS4 single out`() {
        send(
            AppEvent.NewGame(GameMode.X01),
            AppEvent.StartScoreSelected(301),
            AppEvent.CheckoutRuleSelected(CheckoutRule.SINGLE_OUT),
            AppEvent.PlayerRemoved(1), AppEvent.NameChanged(0, "Ann"), AppEvent.StartRequested,
            *triple(20), *triple(20), AppEvent.NumberTapped(1), AppEvent.NextPlayerTapped,
        )
        assertEquals("T20${SEP}T20${SEP}T20", shownText())
    }

    /**
     * CS9 (as amended by issue #49): TalkBack reads the zone in screen order, with no traversal index: the name, the
     * score, the suggestion, then the dart slots and the visit box of the bottom line.
     */
    @Test
    @Config(qualifiers = "+en-port")
    fun `CS9 TalkBack order in the zone`() {
        send(*ANN_301, *ANN_AT_170)
        val name = bounds(GameTags.playerName(0))
        val remaining = bounds(GameTags.CURRENT_REMAINING)
        val suggestion = bounds(GameTags.CHECKOUT_SUGGESTION_LINE)
        val slot = bounds(GameTags.dartSlot(0))
        val box = bounds(GameTags.visitTotal(0))
        assertTrue(name.bottom <= remaining.top + EPSILON)
        assertTrue(remaining.bottom <= suggestion.top + EPSILON)
        assertTrue(suggestion.bottom <= slot.top + EPSILON)
        assertTrue("box after the slots on the same line", box.left >= slot.right && box.top < slot.bottom)
        val indices = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TraversalIndex), true)
            .fetchSemanticsNodes()
        assertTrue("no traversal index left", indices.isEmpty())
    }

    private fun checkCardNeverMoves() {
        send(*ANN_301)
        val withoutSuggestion = cardPlacement()
        send(*ANN_AT_170)
        assertEquals("card with a suggestion", withoutSuggestion, cardPlacement())
        send(*triple(20), *triple(20), *triple(20))
        assertEquals("card while a bust waits", withoutSuggestion, cardPlacement())
        // CS5: undo the bust dart (50 left, "Bull"), then the bull waits in the CHECKOUT zone: no suggestion.
        send(AppEvent.UndoTapped, AppEvent.BullTapped)
        composeRule.onNode(hasTestTag(GameTags.CHECKOUT_BANNER)).assertExists()
        assertNull("checkout pending", shownText())
        assertNull(description())
        // CS8, U10: while the checkout waits the zone has no route area, and it never grows (it may give height back).
        composeRule.onAllNodes(hasTestTag(GameTags.CHECKOUT_SUGGESTION_LINE), true).assertCountEquals(0)
        val card = bounds(GameTags.CURRENT_PLAYER)
        assertTrue("card $card while the checkout waits", card.height <= withoutSuggestion[1] + EPSILON)
    }

    /** CS8: the card does not move when the suggestion appears or disappears. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `CS8 card never moves, portrait`() = checkCardNeverMoves()

    @Test
    @Config(qualifiers = "+en-land")
    fun `CS8 card never moves, landscape`() = checkCardNeverMoves()

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 card never moves, portrait at the largest font scale`() = checkCardNeverMoves()

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 card never moves, landscape at the largest font scale`() = checkCardNeverMoves()

    private fun checkLongestRouteComplete(expected: String) {
        send(*ANN_301, *ANN_AT_170)
        assertEquals(expected, shownText())
        val text = node(GameTags.CHECKOUT_SUGGESTION).fetchSemanticsNode()
        val layout = textLayout(text)
        assertComplete(layout, expected)
        val card = bounds(GameTags.CURRENT_PLAYER)
        val line = bounds(GameTags.CHECKOUT_SUGGESTION_LINE)
        assertTrue("line $line inside card $card", card.contains(line.topLeft) && card.contains(line.bottomRight))
        // The reserved line holds the whole text.
        assertTrue("text ${text.boundsInRoot} in line $line", text.boundsInRoot.bottom <= line.bottom + EPSILON)
    }

    /** U2: at the largest font scale, the longest route is shown in full (it may wrap after a dot). */
    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 longest route complete at the largest font scale, English`() =
        checkLongestRouteComplete("T20${SEP}T20${SEP}BULL")

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 longest route complete at the largest font scale, French`() =
        checkLongestRouteComplete("T20${SEP}T20${SEP}BULL")

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f
        const val EPSILON = 0.5f
        const val NBSP = "\u00A0"
        const val SEP = "\u2003"

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** One player, Ann, 301 double out. */
        val ANN_301: Array<AppEvent> = arrayOf(
            AppEvent.NewGame(GameMode.X01),
            AppEvent.StartScoreSelected(301),
            AppEvent.PlayerRemoved(1),
            AppEvent.NameChanged(0, "Ann"),
            AppEvent.StartRequested,
        )

        /** T20, T20, 11 then "Next player": 170 left, 3 darts. */
        val ANN_AT_170: Array<AppEvent> =
            arrayOf(*triple(20), *triple(20), AppEvent.NumberTapped(11), AppEvent.NextPlayerTapped)

        /** From 170: T20, 20, T10 then "Next player": 60 left, 3 darts. */
        val ANN_AT_60_FROM_170: Array<AppEvent> =
            arrayOf(*triple(20), AppEvent.NumberTapped(20), *triple(10), AppEvent.NextPlayerTapped)
    }
}
