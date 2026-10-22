package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout of the visit total boxes at the worst case (docs/rules/visit-total.md, VT9 to VT13 as amended by issue #49):
 * 4 players with 20-character names, on a Pixel 7 in portrait and landscape, at font scale 1.0 and 2.0 (the largest
 * one Android offers). The filled box of the zone and the outlined boxes of the tiles are complete on one line and
 * keep their size whatever they show; the zone does not move during a bust. Also the TalkBack (screen) order.
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

    private fun node(tag: String): SemanticsNodeInteraction =
        composeRule.onNode(hasTestTag(tag), useUnmergedTree = true)

    /** Bounds of [tag], scrolled into view first when the screen scrolls (layout policy, last step). */
    private fun bounds(tag: String): Rect {
        val scrollable = hasTestTag(tag).and(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)))
        if (composeRule.onAllNodes(scrollable, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            node(tag).performScrollTo()
        }
        return node(tag).fetchSemanticsNode().boundsInRoot
    }

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

    private fun assertInside(inner: Rect, outer: Rect, what: String) {
        assertTrue("$what starts outside: $inner in $outer", inner.left >= outer.left - EPSILON)
        assertTrue("$what ends outside: $inner in $outer", inner.right <= outer.right + EPSILON)
        assertTrue("$what is above: $inner in $outer", inner.top >= outer.top - EPSILON)
        assertTrue("$what is below: $inner in $outer", inner.bottom <= outer.bottom + EPSILON)
    }

    /** The score of the zone: centered, so its center, top and height must not move between states (no jump). */
    private fun scorePlacement(): List<Float> {
        val zone = bounds(GameTags.CURRENT_PLAYER)
        val score = bounds(GameTags.CURRENT_REMAINING)
        return listOf(score.center.x - zone.left, score.top - zone.top, score.height, zone.height)
    }

    /** Same placement, to the pixel: a one pixel difference comes from rounding the measured lines, not a jump. */
    private fun assertSamePlacement(what: String, expected: List<Float>, actual: List<Float>) {
        assertEquals(what, expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertTrue("$what: $expected, $actual", kotlin.math.abs(e - a) <= PIXEL)
        }
    }

    private fun assertTiles(players: List<Int>, root: Rect) = players.forEach { player ->
        val tile = bounds(GameTags.tile(player))
        assertInside(tile, root, "tile $player")
        assertInside(bounds(GameTags.visitTotal(player)), tile, "box $player")
        assertOneCompleteLine(boxText(player), "box $player text")
        assertInside(bounds(GameTags.remaining(player)), tile, "remaining $player")
        assertOneCompleteLine(node(GameTags.remaining(player)).fetchSemanticsNode(), "remaining $player")
    }

    private fun checkLayout() {
        // Cid's 180 waits in the zone.
        send(*CID_180_WAITING)
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val zoneBox180 = bounds(GameTags.visitTotal(CID)).size
        assertOneCompleteLine(boxText(CID), "zone box 180")

        // Ann's first dart of round 2: her zone box shows 60.
        send(*ANN_FIRST_DART)
        val placementBefore = scorePlacement()
        assertEquals("zone box size", zoneBox180, bounds(GameTags.visitTotal(0)).size)

        // VT13: Ann's T20 T20 leaves 1, a bust waits. The score is complete, the banner is inside the zone, the box
        // shows 0 with the same size, and the score has not moved.
        send(*triple(20))
        val zone = bounds(GameTags.CURRENT_PLAYER)
        assertInside(bounds(GameTags.CURRENT_REMAINING), zone, "zone score")
        assertOneCompleteLine(node(GameTags.CURRENT_REMAINING).fetchSemanticsNode(), "zone score")
        assertInside(bounds(GameTags.CARD_BUST), zone, "BUST banner")
        assertEquals("0", textLayout(boxText(0)).layoutInput.text.text)
        assertEquals("zone box size during the bust", zoneBox180, bounds(GameTags.visitTotal(0)).size)
        assertSamePlacement("zone score moved during the bust", placementBefore, scorePlacement())

        // Tiles Bob (3), Cid (180) and Dan (0).
        assertTiles(listOf(1, CID, DAN), root)

        // After "Next player": Ann's BUST box is on a tile, Bob's zone box shows 0. The score has not moved.
        send(AppEvent.NextPlayerTapped)
        assertTiles(listOf(0, CID, DAN), root)
        val tileBoxSizes = listOf(0, CID, DAN).map { bounds(GameTags.visitTotal(it)).size }
        // VT11: a tile box has the same size whatever it shows: BUST, 180 or 0.
        assertEquals(1, tileBoxSizes.distinct().size)
        assertEquals("zone box size", zoneBox180, bounds(GameTags.visitTotal(1)).size)
        assertSamePlacement("zone score moved after Next player", placementBefore.drop(1), scorePlacement().drop(1))
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `VT11 portrait at font scale 1`() = checkLayout()

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun `VT11 portrait at the largest font scale`() = checkLayout()

    @Test
    @Config(qualifiers = "+en-land")
    fun `VT11 landscape at font scale 1`() = checkLayout()

    @Test
    @Config(qualifiers = "+en-land", fontScale = LARGEST_FONT_SCALE)
    fun `VT11 landscape at the largest font scale`() = checkLayout()

    @Test
    @Config(qualifiers = "w393dp-h852dp-port-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `VT11 narrower phone at the largest font scale`() = checkLayout()

    /**
     * VT8, VT13: TalkBack reads in screen order (no traversal index): in the zone, the name, the score, the BUST
     * banner, then the dart slots and the box; on a tile, the name, then the score and the box. The zone box of a
     * waiting bust is read "Last turn: bust".
     */
    @Test
    @Config(qualifiers = "+en-port")
    fun `VT8 TalkBack order of a tile and of the zone`() {
        send(*CID_180_WAITING, *ANN_FIRST_DART, *triple(20))
        val name = bounds(GameTags.playerName(DAN))
        val remaining = bounds(GameTags.remaining(DAN))
        val box = bounds(GameTags.visitTotal(DAN))
        assertTrue("tile order $name $remaining $box", name.bottom <= remaining.top + EPSILON)
        assertTrue("tile order $remaining $box", box.left > remaining.left)

        val zoneName = bounds(GameTags.playerName(0))
        val score = bounds(GameTags.CURRENT_REMAINING)
        val banner = bounds(GameTags.CARD_BUST)
        val slot = bounds(GameTags.dartSlot(0))
        val zoneBox = bounds(GameTags.visitTotal(0))
        assertTrue(zoneName.bottom <= score.top + EPSILON && score.bottom <= banner.top + EPSILON)
        assertTrue(banner.bottom <= slot.top + EPSILON && zoneBox.left >= slot.right)
        composeRule.onNode(hasTestTag(GameTags.visitTotal(0)))
            .assertContentDescriptionEquals(composeRule.activity.getString(R.string.game_visit_last_bust_description))
        val indices = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TraversalIndex), true)
            .fetchSemanticsNodes()
        assertTrue("no traversal index left", indices.isEmpty())
    }

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f
        const val EPSILON = 0.5f
        const val PIXEL = 1f
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
