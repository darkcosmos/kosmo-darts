package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.components.breaksOnlyBetweenWords
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The Cricket game screen in landscape on the Pixel 7 with its bars hidden (914 x 411 dp, docs/rules/cricket.md CR22,
 * plan section 4): the fit with no scrolling, rows and buttons of at least 48 dp, one band height on both sides, the
 * single-line zone, whole texts, the action bar, and the TalkBack order. Without any real screen: the state is given
 * directly. The 48 dp display cutout and the bars shown by a swipe are `WindowInsets` sent to the screen's view.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w914dp-h411dp-land-420dpi")
class CricketLandscapeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val fourPlayers = listOf("Ann", "Bob", "Cid", "Dan")

    /** The player the zone shows in the current case. */
    private var zoneIndex = 0

    /** The side of the simulated 48 dp display cutout of the current test, or `null` for none. */
    private var cutoutSide: CutoutSide? = null

    private var view: ComposeView? = null

    private fun show(state: CricketGameScreenState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { CricketGameScreen(state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
            this.view = view
        }
        composeRule.waitForIdle()
        cutoutSide?.let { dispatchInsets(it, isBarShown = false) }
    }

    /**
     * Sends [side]'s 48 dp display cutout to the screen, and with [isBarShown] the bars a swipe shows for a moment
     * (24 dp status bar at the top, 48 dp navigation bar on the other side), as `WindowInsets` would on a phone.
     */
    private fun dispatchInsets(side: CutoutSide, isBarShown: Boolean) {
        val cutout = px(CUTOUT_DP).roundToInt()
        val builder = WindowInsetsCompat.Builder().setInsets(
            WindowInsetsCompat.Type.displayCutout(),
            if (side == CutoutSide.LEFT) Insets.of(cutout, 0, 0, 0) else Insets.of(0, 0, cutout, 0),
        )
        if (isBarShown) {
            val navigation = px(CUTOUT_DP).roundToInt()
            builder.setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(24f).roundToInt(), 0, 0))
            builder.setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                if (side == CutoutSide.LEFT) Insets.of(0, 0, navigation, 0) else Insets.of(navigation, 0, 0, 0),
            )
        }
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(checkNotNull(view), builder.build()) }
        composeRule.waitForIdle()
    }

    /**
     * A match with legs and sets (the longest title line, with the short position and the S/M chips); [pending] a won
     * leg that waits.
     */
    private fun game(players: Int, isBullInPlay: Boolean, pending: Boolean): CricketGameScreenState {
        val match = CricketSaveFixtures.match(
            *fourPlayers.take(players).toTypedArray(),
            isBullInPlay = isBullInPlay,
            legsPerSet = 3,
            sets = 3,
        )
        return if (pending) {
            CricketGameScreenState(match.playLegWonBy(0), isAwaitingConfirmation = true)
        } else {
            CricketGameScreenState(match.throwing(*CricketSaveFixtures.darts("T20 19 D18")))
        }
    }


    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String): Rect = node(tag).fetchSemanticsNode().boundsInRoot

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun isInsideRoot(tag: String): Boolean {
        val rect = bounds(tag)
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return rect.left >= screen.left - 1f && rect.right <= screen.right + 1f &&
            rect.top >= screen.top - 1f && rect.bottom <= screen.bottom + 1f
    }

    private fun textNodeOf(tag: String): SemanticsNode {
        fun find(node: SemanticsNode): SemanticsNode? =
            if (node.config.contains(SemanticsActions.GetTextLayoutResult)) {
                node
            } else {
                node.children.firstNotNullOfOrNull(::find)
            }
        return checkNotNull(find(node(tag).fetchSemanticsNode())) { "no text in $tag" }
    }

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        textNodeOf(tag).config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** The text of [tag] is on one line, inside its node, and not cut sideways unless [ellipsis] allows it. */
    private fun assertTextNotClipped(tag: String, ellipsis: Boolean = false) {
        val textNode = textNodeOf(tag)
        val layout = layoutOf(tag)
        val owner = node(tag).fetchSemanticsNode().boundsInRoot
        val text = textNode.boundsInRoot
        assertEquals("$tag on one line", 1, layout.lineCount)
        assertTrue("$tag text ${layout.multiParagraph.height} px in ${text.height} px",
            layout.multiParagraph.height <= text.height + 1f)
        assertTrue("$tag text $text inside $owner", text.top >= owner.top - 1f && text.bottom <= owner.bottom + 1f)
        if (!ellipsis) {
            assertFalse("$tag overflows vertically", layout.didOverflowHeight)
            val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
            assertTrue("$tag text is ${lineWidth.toInt()} px wide in ${text.width.toInt()} px",
                lineWidth <= text.width + 1f)
            assertFalse("$tag ellipsized", layout.isLineEllipsized(0))
        }
    }

    private fun assertFits(players: Int, isBullInPlay: Boolean, pending: Boolean) {
        val label = "$players players, bull $isBullInPlay, win pending $pending"
        val state = game(players, isBullInPlay, pending)
        zoneIndex = state.highlightedPlayerIndex ?: 0
        show(state)

        val rows = node(CricketTags.BOARD_ROWS).fetchSemanticsNode()
        val range = rows.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertTrue("$label: the rows scroll", range == null || range.maxValue() <= 0f)
        CricketTarget.entries.filter { it.isNumber || isBullInPlay }.forEach { target ->
            assertTrue("$label: row $target inside", isInsideRoot(CricketTags.row(target)))
            val rowHeight = bounds(CricketTags.row(target)).height
            assertTrue("$label: row $target is ${rowHeight / px(1f)} dp, 48 at least", rowHeight >= px(48f) - 1f)
        }
        assertBand(label, players)
        assertTitleAndZone(label, pending)
        assertButtons(label, isBullInPlay)
        assertBar(label, pending)
        cutoutSide?.let { assertOutOfCutout(label, it) }
    }

    /** Every node of the screen is out of the cutout of [side]; the right column keeps 340 dp (U1, plan section 4). */
    private fun assertOutOfCutout(label: String, side: CutoutSide) {
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        fun check(node: SemanticsNode) {
            val rect = node.boundsInRoot
            val isUnder = if (side == CutoutSide.LEFT) {
                rect.left < screen.left + px(CUTOUT_DP) - 1f
            } else {
                rect.right > screen.right - px(CUTOUT_DP) + 1f
            }
            // A node of no size draws nothing, so it cannot sit under the cutout.
            assertFalse("$label: $rect under the $side cutout", isUnder && !rect.isEmpty)
            node.children.forEach(::check)
        }
        check(node(CricketTags.SCREEN).fetchSemanticsNode())
        val rightColumn = bounds(CricketTags.TITLE).width / px(1f)
        assertTrue("$label: right column $rightColumn dp, 340 at least", rightColumn >= 340f - 0.5f)
    }

    /** One band height: the board header and the title plus the zone have the same top and bottom. */
    private fun assertBand(label: String, players: Int) {
        val header = bounds(CricketTags.columnHeader(0))
        val title = bounds(CricketTags.TITLE)
        val zone = bounds(GameTags.CURRENT_PLAYER)
        assertEquals("$label: title top", header.top, title.top, 1f)
        assertEquals("$label: zone bottom", header.bottom, zone.bottom, 1f)
        assertEquals("$label: Miss height", header.height, bounds(KeypadTags.MISS).height, 1f)
        assertTrue("$label: band under 64 dp, is ${header.height / px(1f)}", header.height <= px(64f))
        (0 until players).forEach { player ->
            assertTrue("$label: header $player", isInsideRoot(CricketTags.columnHeader(player)))
            assertTextNotClipped(CricketTags.columnName(player), ellipsis = true)
            if (players > 1) assertTextNotClipped(CricketTags.columnTotal(player))
        }
    }

    private fun assertTitleAndZone(label: String, pending: Boolean) {
        val zone = bounds(GameTags.CURRENT_PLAYER)
        val title = bounds(CricketTags.TITLE)
        val board = bounds(CricketTags.BOARD)
        assertTrue("$label: zone right of the board by 12 dp", zone.left >= board.right + px(12f) - 1f)
        assertTrue("$label: zone at least 340 dp wide", zone.width >= px(340f) - 1f)
        assertEquals("$label: title and zone align", title.left, zone.left, 1f)
        assertEquals("$label: zone one line, 28 dp", px(28f), zone.height, 1f)
        assertTrue("$label: the title is over the zone", title.bottom <= zone.top + 1f)
        assertTextNotClipped(GameTags.HEADER_MODE)
        assertPositionAndChips(label, title, zone)
        assertTextNotClipped(GameTags.playerName(zoneIndex), ellipsis = true)
        if (pending) assertTextNotClipped(CricketTags.WIN_BANNER) else assertTextNotClipped(CricketTags.ZONE_STAT)
        (0..2).forEach { slot ->
            val rect = bounds(GameTags.dartSlot(slot))
            assertTrue("$label: dart slot $slot inside the zone",
                rect.top >= zone.top && rect.bottom <= zone.bottom && rect.right <= zone.right)
            if (textNodeOf(GameTags.dartSlot(slot)).boundsInRoot.height > 0f) {
                assertTextNotClipped(GameTags.dartSlot(slot))
            }
        }
        assertTrue("$label: zone inside", isInsideRoot(GameTags.CURRENT_PLAYER))
    }

    /** The title is one line: the short position is beside the mode, clear of the zone, the chips at the end. */
    private fun assertPositionAndChips(label: String, title: Rect, zone: Rect) {
        assertTextNotClipped(GameTags.HEADER_POSITION)
        listOf(GameTags.HEADER_MODE, GameTags.HEADER_POSITION).forEach {
            assertTrue("$label: $it inside the title, above the zone",
                bounds(it).bottom <= title.bottom + 1f && bounds(it).bottom <= zone.top + 1f)
        }
        assertTrue("$label: position beside the mode",
            bounds(GameTags.HEADER_POSITION).top < bounds(GameTags.HEADER_MODE).bottom)
        val chipNodes = composeRule.onAllNodes(
            SemanticsMatcher("match indicators") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("game_match_indicators_") == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("$label: one chips group", 1, chipNodes.size)
        val chips = chipNodes.single().boundsInRoot
        assertTrue("$label: chips at the end of the title",
            chips.right <= title.right + 1f && chips.left > bounds(GameTags.HEADER_MODE).right)
    }

    private fun assertButtons(label: String, isBullInPlay: Boolean) {
        val zone = bounds(GameTags.CURRENT_PLAYER)
        val darts = CricketTarget.entries.filter { it.isNumber }.flatMap {
            listOf(Dart.single(it.points), Dart.double(it.points), Dart.triple(it.points))
        } + if (isBullInPlay) listOf(Dart.OUTER_BULL, Dart.BULL) else emptyList()
        darts.forEach { dart ->
            val area = bounds(CricketTags.dartButton(dart))
            assertTrue("$label: $dart is 48 x 48 dp", area.width >= px(48f) - 1f && area.height >= px(48f) - 1f)
            val key = bounds(CricketTags.dartKey(dart))
            assertEquals("$label: $dart inset", px(2f), key.left - area.left, 1f)
            assertEquals("$label: $dart inset", px(2f), key.top - area.top, 1f)
            assertTrue("$label: $dart before the zone column", area.right <= zone.left - px(12f) + 1f)
        }
        val single = bounds(CricketTags.dartKey(Dart.single(20)))
        assertTrue("$label: gap in a row",
            bounds(CricketTags.dartKey(Dart.double(20))).left - single.right >= px(4f) - 1f)
        assertTrue("$label: gap between rows",
            bounds(CricketTags.dartKey(Dart.single(19))).top - single.bottom >= px(4f) - 1f)
        val lastCell = bounds(CricketTags.cell(CricketTarget.TWENTY, 0))
        assertTrue("$label: buttons right of the player cells",
            bounds(CricketTags.dartButton(Dart.single(20))).left >= lastCell.right - 1f)
        val miss = bounds(KeypadTags.MISS)
        assertTrue("$label: Miss 144 dp wide", miss.width >= px(144f) - 1f)
        assertEquals("$label: Miss over the buttons", single.left - px(2f), miss.left, 1f)
        assertTrue("$label: Miss above the rows",
            miss.bottom <= bounds(CricketTags.dartButton(Dart.single(20))).top + 1f)
    }

    private fun assertBar(label: String, pending: Boolean) {
        val primary = if (pending) GameTags.CONFIRM else KeypadTags.NEXT_PLAYER
        val zone = bounds(GameTags.CURRENT_PLAYER)
        val undo = bounds(KeypadTags.UNDO)
        val next = bounds(primary)
        assertTrue("$label: Undo before the primary button", undo.right <= next.left + 1f)
        assertEquals("$label: same line", undo.top, next.top, 1f)
        assertTrue("$label: bar under the zone", undo.top >= zone.bottom)
        assertTrue("$label: bar in the right column", undo.left >= zone.left - 1f && next.right <= zone.right + 1f)
        listOf(KeypadTags.UNDO, primary).forEach { tag ->
            assertTrue("$label: $tag inside", isInsideRoot(tag))
            assertTrue("$label: $tag 48 dp", bounds(tag).height >= px(48f) - 1f)
            val layout = layoutOf(tag)
            val text = layout.layoutInput.text.text
            val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
            assertTrue("$label: $tag \"$text\" breaks inside a word", breaksOnlyBetweenWords(text, ends))
            assertFalse("$label: $tag \"$text\" overflows", layout.didOverflowHeight || layout.didOverflowWidth)
            val size = layout.layoutInput.style.fontSize.value
            assertTrue("$label: $tag at least 12 sp, is $size", size >= 12f - 0.01f)
        }
    }

    private fun assertEveryCase() {
        listOf(1, 2, 3, 4).forEach { players ->
            listOf(true, false).forEach { bull ->
                listOf(false, true).forEach { pending -> assertFits(players, bull, pending) }
            }
        }
    }

    @Test
    fun `CR22 landscape 914 x 411 scale 1 fits, 1 to 4 players, 6 and 7 rows, with and without the pending win`() =
        assertEveryCase()

    @Test
    @Config(fontScale = 2.0f)
    fun `CR22 landscape 914 x 411 scale 2 fits the same, the board and the title ignore the font size`() =
        assertEveryCase()

    @Test
    fun `CR22 U1 landscape with a 48 dp cutout on the left fits, nothing under it, 1 to 4 players`() {
        cutoutSide = CutoutSide.LEFT
        assertEveryCase()
    }

    @Test
    fun `CR22 U1 landscape with a 48 dp cutout on the right fits, nothing under it, 1 to 4 players`() {
        cutoutSide = CutoutSide.RIGHT
        assertEveryCase()
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `CR22 U1 landscape with a 48 dp cutout at scale 2 fits, on either side`() {
        CutoutSide.entries.forEach { side ->
            cutoutSide = side
            assertEveryCase()
        }
    }

    @Test
    fun `CR22 U1 the bars a swipe shows for a moment go over the screen, nothing moves`() {
        CutoutSide.entries.forEach { side ->
            cutoutSide = side
            show(game(players = 4, isBullInPlay = true, pending = false))
            val tags = listOf(CricketTags.SCREEN, CricketTags.BOARD, GameTags.CURRENT_PLAYER, KeypadTags.UNDO)
            val before = tags.map(::bounds)
            assertTrue("$side: the cutout reached the screen", bounds(CricketTags.SCREEN).width < px(914f - 40f))

            dispatchInsets(side, isBarShown = true)

            tags.zip(before).forEach { (tag, rect) -> assertEquals("$side: $tag moved", rect, bounds(tag)) }
        }
    }

    @Test
    @Config(qualifiers = "en-w914dp-h411dp-land-420dpi", fontScale = 2.0f)
    fun `CR25 landscape English CONFIRM MATCH is whole at scale 2`() {
        show(
            CricketGameScreenState(
                CricketSaveFixtures.match(*fourPlayers.toTypedArray()).playLegWonBy(0),
                isAwaitingConfirmation = true,
            ),
        )
        assertEquals("CONFIRM MATCH", layoutOf(GameTags.CONFIRM).layoutInput.text.text)
        assertBar("single game", pending = true)
    }

    @Test
    fun `CR25 landscape French VALIDER LA PARTIE is whole, broken between words only`() {
        show(
            CricketGameScreenState(
                CricketSaveFixtures.match(*fourPlayers.toTypedArray()).playLegWonBy(0),
                isAwaitingConfirmation = true,
            ),
        )
        assertEquals("VALIDER LA PARTIE", layoutOf(GameTags.CONFIRM).layoutInput.text.text)
        assertBar("single game", pending = true)
    }

    @Test
    fun `CR22 the board texts keep their size at scale 2`() {
        show(game(4, isBullInPlay = true, pending = false))
        val name = layoutOf(CricketTags.columnName(0)).layoutInput.style.fontSize.value
        val title = layoutOf(GameTags.HEADER_MODE).layoutInput.style.fontSize.value
        assertEquals(14f, name, 0.01f)
        assertEquals(14f, title, 0.01f)
    }

    @Test
    fun `CR23 the title is read first, then the board, the zone and the bar`() {
        show(game(2, isBullInPlay = true, pending = false))
        val title = node(CricketTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex]
        val board = node(CricketTags.BOARD).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex)
            ?: 0f
        assertTrue("title $title before the board $board", title < board)
        assertTrue(bounds(CricketTags.BOARD).left < bounds(GameTags.CURRENT_PLAYER).left)
    }

    /** The side of the camera hole, depending on which way the phone is turned. */
    private enum class CutoutSide { LEFT, RIGHT }

    private companion object {
        /** The widest cutout the plan assumes on the Pixel 7 (section 4). */
        const val CUTOUT_DP = 48f
    }
}
