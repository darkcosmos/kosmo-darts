package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.NAME_MIN_REFERENCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout of the visit total boxes at the worst case (docs/rules/visit-total.md, VT11): 4 players with 20-character
 * names, on a Pixel 7 in portrait and landscape, at font scale 1.0 and 2.0 (the largest one Android offers). Also the
 * TalkBack order of a row and of the highlighted card (VT8).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: without native graphics, Robolectric measures text as almost zero wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VisitTotalLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTagUnmerged(tag)

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithTagUnmerged(tag: String) =
        onNode(hasTestTag(tag), useUnmergedTree = true)

    private fun bounds(tag: String): Rect = node(tag).performScrollTo().fetchSemanticsNode().boundsInRoot

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)) { "no text: $node" }
        action.action?.invoke(results)
        return results.single()
    }

    /** The text node inside a box (the box itself merges it with its description). */
    private fun boxText(player: Int): SemanticsNode = composeRule.onNode(
        hasAnyAncestor(hasTestTag(GameTags.visitTotal(player)))
            .and(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)),
        useUnmergedTree = true,
    ).fetchSemanticsNode()

    private fun assertOneCompleteLine(node: SemanticsNode, what: String) {
        val layout = textLayout(node)
        assertEquals("$what is on one line", 1, layout.lineCount)
        // hasVisualOverflow is not usable here: without soft wrap, the paragraph is laid out wider than the text.
        val text = "$what: line from ${layout.getLineLeft(0)} to ${layout.getLineRight(0)} in ${layout.size}"
        assertTrue("$text starts cut", layout.getLineLeft(0) >= -EPSILON)
        assertTrue("$text ends cut", layout.getLineRight(0) <= layout.size.width + EPSILON)
        assertTrue("$what is cut vertically", !layout.didOverflowHeight)
    }

    private fun assertInsideHorizontally(inner: Rect, outer: Rect, what: String) {
        assertTrue("$what starts outside: $inner in $outer", inner.left >= outer.left - EPSILON)
        assertTrue("$what ends outside: $inner in $outer", inner.right <= outer.right + EPSILON)
    }

    /** Minimum width of a name in a player row, in pixels: NAME_MIN_REFERENCE in titleMedium (the row name style). */
    private fun nameMinWidthPx(): Int {
        val context = composeRule.activity
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(context), LayoutDirection.Ltr)
        return measurer.measure(NAME_MIN_REFERENCE, Typography().titleMedium).size.width
    }

    private fun traversalIndex(matcher: SemanticsMatcher): Float = composeRule.onNode(matcher, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f

    /** Position of the card remaining inside its card, and its height: they must not move between states (no jump). */
    private fun remainingPlacement(): List<Float> {
        val card = bounds(GameTags.CURRENT_PLAYER)
        val remaining = bounds(GameTags.CURRENT_REMAINING)
        return listOf(remaining.left - card.left, remaining.top - card.top, remaining.height)
    }

    private fun assertRows(players: List<Int>, root: Rect) = players.forEach { player ->
        assertInsideHorizontally(bounds(GameTags.visitTotal(player)), root, "box $player")
        assertOneCompleteLine(boxText(player), "box $player text")
        assertInsideHorizontally(bounds(GameTags.remaining(player)), root, "remaining $player")
        assertOneCompleteLine(node(GameTags.remaining(player)).fetchSemanticsNode(), "remaining $player")
        val name = bounds(GameTags.playerName(player))
        assertTrue("name $player is ${name.width} px", name.width >= nameMinWidthPx() - EPSILON)
    }

    private fun checkLayout(expectTwoLineRows: Boolean) {
        // Cid's 180 waits in the card.
        send(*CID_180_WAITING)
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBox180 = bounds(GameTags.visitTotal(CID)).size
        assertOneCompleteLine(boxText(CID), "card box 180")

        // Ann's first dart of round 2: her card box shows 60.
        send(*ANN_FIRST_DART)
        val placementBefore = remainingPlacement()
        assertEquals("card box size", cardBox180, bounds(GameTags.visitTotal(0)).size)

        // U2, VT13: Ann's T20 T20 leaves 1, a bust waits. The remaining is complete on one line, the badge is inside
        // the card, there is no box in the card, and the remaining has not moved.
        send(*triple(20))
        val card = bounds(GameTags.CURRENT_PLAYER)
        val remaining = bounds(GameTags.CURRENT_REMAINING)
        assertInsideHorizontally(remaining, card, "card remaining")
        assertOneCompleteLine(node(GameTags.CURRENT_REMAINING).fetchSemanticsNode(), "card remaining")
        assertInsideHorizontally(bounds(GameTags.CARD_BUST), card, "BUST badge")
        node(GameTags.visitTotal(0)).assertDoesNotExist()
        assertEquals("card remaining moved during the bust", placementBefore, remainingPlacement())

        // Rows Bob (3), Cid (180) and Dan (0).
        assertRows(listOf(1, CID, DAN), root)
        val name = bounds(GameTags.playerName(DAN))
        val average = bounds(GameTags.average(DAN))
        assertEquals("average under the name", expectTwoLineRows, average.top >= name.bottom - EPSILON)

        // After "Next player": Ann's BUST box is in a row, Bob's card box shows 0. The remaining has not moved.
        send(AppEvent.NextPlayerTapped)
        assertRows(listOf(0, CID, DAN), root)
        val rowBoxSizes = listOf(0, CID, DAN).map { bounds(GameTags.visitTotal(it)).size }
        // VT11: a row box has the same size whatever it shows: BUST, 180 or 0.
        assertEquals(1, rowBoxSizes.distinct().size)
        assertEquals("card box size", cardBox180, bounds(GameTags.visitTotal(1)).size)
        assertEquals("card remaining moved after Next player", placementBefore, remainingPlacement())
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `VT11 portrait at font scale 1`() = checkLayout(expectTwoLineRows = false)

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun `VT11 portrait at the largest font scale`() = checkLayout(expectTwoLineRows = false)

    @Test
    @Config(qualifiers = "+en-land")
    fun `VT11 landscape at font scale 1`() = checkLayout(expectTwoLineRows = false)

    /** Without "Prev." (PR37-R1), a row fits on one line on a Pixel 7 even at the largest font in landscape. */
    @Test
    @Config(qualifiers = "+en-land", fontScale = LARGEST_FONT_SCALE)
    fun `VT11 landscape at the largest font scale`() = checkLayout(expectTwoLineRows = false)

    /** VT8, VT13: TalkBack reads a row as name, average, remaining, box; the card ends with its dart slots. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `VT8 TalkBack order of a row and of the card`() {
        send(*CID_180_WAITING, *ANN_FIRST_DART, *triple(20))
        val name = traversalIndex(hasTestTag(GameTags.playerName(DAN)))
        val average = traversalIndex(hasTestTag(GameTags.average(DAN)))
        val remaining = traversalIndex(hasTestTag(GameTags.remaining(DAN)))
        val box = traversalIndex(hasTestTag(GameTags.visitTotal(DAN)))
        assertTrue("row order $name $average $remaining $box", name < average && average < remaining && remaining < box)

        // Waiting bust: name, remaining, badge, dart slots (no box).
        val badge = traversalIndex(hasTestTag(GameTags.CARD_BUST))
        assertCardOrder(cardPlayer = 0, cardMiddle = badge)

        // After "Next player": name, remaining, box, dart slots.
        send(AppEvent.NextPlayerTapped)
        assertCardOrder(cardPlayer = 1, cardMiddle = traversalIndex(hasTestTag(GameTags.visitTotal(1))))
    }

    private fun assertCardOrder(cardPlayer: Int, cardMiddle: Float) {
        val cardRemaining = traversalIndex(hasTestTag(GameTags.CURRENT_REMAINING))
        val slots = traversalIndex(
            hasAnyDescendant(hasTestTag(GameTags.dartSlot(0)))
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.TraversalIndex)),
        )
        val cardName = traversalIndex(
            hasAnyDescendant(hasTestTag(GameTags.playerName(cardPlayer)))
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.TraversalIndex)),
        )
        assertTrue(
            "card order $cardName $cardRemaining $cardMiddle $slots",
            cardName < cardRemaining && cardRemaining < cardMiddle && cardMiddle < slots,
        )
    }

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f
        const val EPSILON = 0.5f
        const val CID = 2
        const val DAN = 3

        fun names(vararg names: String): Array<AppEvent> =
            names.mapIndexed { index, name -> AppEvent.NameChanged(index, name) }.toTypedArray()

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        fun singles(vararg numbers: Int): Array<AppEvent> = numbers.map { AppEvent.NumberTapped(it) }.toTypedArray()

        /**
         * 301 double out, four 20-character names. Round 1: Ann 180 (121 left), Bob 3, Cid 180, which waits for
         * "Next player".
         */
        val CID_180_WAITING: Array<AppEvent> = arrayOf(
            AppEvent.NewGame,
            AppEvent.StartScoreSelected(301),
            AppEvent.PlayerAdded,
            AppEvent.PlayerAdded,
            *names("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Jean-Baptiste Pierre"),
            AppEvent.StartRequested,
            *triple(20), *triple(20), *triple(20), AppEvent.NextPlayerTapped,
            *singles(1, 1, 1), AppEvent.NextPlayerTapped,
            *triple(20), *triple(20), *triple(20),
        )

        /** Then Dan scores 0 by misses, and Ann throws T20 (61 left): her next T20 leaves 1, a bust. */
        val ANN_FIRST_DART: Array<AppEvent> = arrayOf(
            AppEvent.NextPlayerTapped,
            AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped,
            *triple(20),
        )
    }
}
