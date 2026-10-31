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
import androidx.compose.ui.test.performClick
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
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
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
 * plan R2.2): the fit with no scrolling, rows and target buttons of at least 42 dp (Kosmo, Q14 B) and 48 dp wide, the
 * 44 dp top band with the single-line zone, "ANNULER" right of "RATÉ" (Q16 A), the tall next key with its vertical
 * label (Q17 B), whole fixed texts, and the TalkBack order. Without any real screen: the state is given directly. The
 * 48 dp display cutout and the bars shown by a swipe are `WindowInsets` sent to the screen's view.
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
        targets(isBullInPlay).forEach { target ->
            assertTrue("$label: row $target inside", isInsideRoot(CricketTags.row(target)))
            val rowHeight = bounds(CricketTags.row(target)).height
            // Kosmo, Q14 B: 42 dp at least in landscape (43.5 dp at 7 rows on the Pixel 7, by the plan's estimate).
            assertTrue("$label: row $target is ${rowHeight / px(1f)} dp, 42 at least", rowHeight >= px(42f) - 1f)
        }
        assertBand(label, pending)
        assertHeaders(label, players)
        assertActionKeys(label, players, isBullInPlay)
        assertButtons(label, isBullInPlay)
        cutoutSide?.let { assertOutOfCutout(label, it) }
    }

    private fun targets(isBullInPlay: Boolean) = CricketTarget.entries.filter { it.isNumber || isBullInPlay }

    /** Every node of the screen is out of the cutout of [side] (U1, plan section 4). */
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
    }

    /**
     * R2.2, Q14 B, Q15 A: the 44 dp band at the top edge over the whole width inside the side margins; the title then
     * the single-line zone, every text whole, the three dart cells at the zone's right end; no zone outside the band.
     */
    private fun assertBand(label: String, pending: Boolean) {
        val screen = bounds(CricketTags.SCREEN)
        val band = bounds(CricketTags.BAND)
        val zone = bounds(GameTags.CURRENT_PLAYER)
        val title = bounds(CricketTags.TITLE)
        assertEquals("$label: band at the top edge", screen.top, band.top, 1f)
        assertEquals("$label: band 44 dp", px(44f), band.height, 1f)
        assertEquals("$label: band starts at the side margin", screen.left + px(12f), band.left, 1f)
        assertEquals("$label: band ends at the side margin", screen.right - px(12f), band.right, 1f)
        assertEquals("$label: one zone, in the band", 1, nodeCount(GameTags.CURRENT_PLAYER))
        assertTrue("$label: zone $zone inside the band $band", band.contains(zone))
        assertTrue("$label: title $title inside the band", band.contains(title))
        assertTrue("$label: the title before the zone", title.right <= zone.left + 1f)
        assertTextNotClipped(GameTags.HEADER_MODE)
        assertTextNotClipped(GameTags.HEADER_POSITION)
        listOf(GameTags.HEADER_MODE, GameTags.HEADER_POSITION).forEach {
            assertTrue("$label: $it inside the band", band.contains(bounds(it)))
        }
        assertTrue("$label: position after the mode",
            bounds(GameTags.HEADER_POSITION).left >= bounds(GameTags.HEADER_MODE).right - 1f)
        val chipNodes = composeRule.onAllNodes(
            SemanticsMatcher("match indicators") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("game_match_indicators_") == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("$label: one chips group", 1, chipNodes.size)
        val chips = chipNodes.single().boundsInRoot
        assertTrue("$label: chips $chips whole in the band", band.contains(chips))
        assertTrue("$label: chips at the end of the title",
            chips.right <= title.right + 1f && chips.left >= bounds(GameTags.HEADER_POSITION).right - 1f)
        assertTextNotClipped(GameTags.playerName(zoneIndex), ellipsis = true)
        assertEquals("$label: the name at 18 sp", 18f, layoutOf(GameTags.playerName(zoneIndex)).sizeSp(), 0.01f)
        if (pending) assertTextNotClipped(CricketTags.WIN_BANNER) else assertTextNotClipped(CricketTags.ZONE_STAT)
        (0..2).forEach { slot ->
            val rect = bounds(GameTags.dartSlot(slot))
            assertTrue("$label: dart slot $slot inside the zone", zone.contains(rect))
            assertEquals("$label: dart slot $slot 36 dp", px(36f), rect.height, 1f)
            if (textNodeOf(GameTags.dartSlot(slot)).boundsInRoot.height > 0f) {
                assertTextNotClipped(GameTags.dartSlot(slot))
            }
        }
        assertTrue("$label: the darts at the zone's right end",
            bounds(GameTags.dartSlot(2)).right >= zone.right - px(12f))
    }

    /** The column headers: the total above the name, both whole (R4, R2.2). */
    private fun assertHeaders(label: String, players: Int) {
        val band = bounds(CricketTags.BAND)
        (0 until players).forEach { player ->
            val header = bounds(CricketTags.columnHeader(player))
            assertTrue("$label: header $player inside", isInsideRoot(CricketTags.columnHeader(player)))
            assertTrue("$label: header $player under the band", header.top >= band.bottom - 1f)
            assertTextNotClipped(CricketTags.columnName(player), ellipsis = true)
            if (players > 1) {
                assertTextNotClipped(CricketTags.columnTotal(player))
                assertTrue("$label: total $player above the name",
                    bounds(CricketTags.columnTotal(player)).bottom <= bounds(CricketTags.columnName(player)).top + 1f)
            }
        }
    }

    /**
     * Q16 A, Q17 B: "RATE" right of the last header, "ANNULER" right of it after the 12 dp gap, same top and bottom,
     * both 48 x 48 dp at least; the tall next key under "ANNULER", from the first row's top to the last row's bottom.
     */
    private fun assertActionKeys(label: String, players: Int, isBullInPlay: Boolean) {
        val lastHeader = bounds(CricketTags.columnHeader(players - 1))
        val miss = bounds(KeypadTags.MISS)
        val undo = bounds(CricketTags.LANDSCAPE_UNDO)
        val next = bounds(CricketTags.LANDSCAPE_NEXT)
        val rows = targets(isBullInPlay)
        assertTrue("$label: Miss right of the last header", miss.left >= lastHeader.right - 1f)
        assertEquals("$label: Undo 12 dp right of Miss", miss.right + px(12f), undo.left, 1f)
        listOf(miss, undo).forEach {
            assertEquals("$label: same top as the header", lastHeader.top, it.top, 1f)
            assertEquals("$label: same bottom as the header", lastHeader.bottom, it.bottom, 1f)
            assertTrue("$label: $it 48 x 48 dp", it.width >= px(48f) - 1f && it.height >= px(48f) - 1f)
        }
        assertEquals("$label: Undo 120 dp", px(120f), undo.width, 1f)
        assertEquals("$label: Undo at the right margin", bounds(CricketTags.BAND).right, undo.right, 1f)
        assertEquals("$label: next key left", undo.left, next.left, 1f)
        assertEquals("$label: next key right", undo.right, next.right, 1f)
        assertEquals("$label: next key from the first row", bounds(CricketTags.row(rows.first())).top, next.top, 1f)
        assertEquals("$label: next key to the last row", bounds(CricketTags.row(rows.last())).bottom, next.bottom, 1f)
        assertTrue("$label: next key inside", isInsideRoot(CricketTags.LANDSCAPE_NEXT))
        assertTextNotClipped(CricketTags.LANDSCAPE_UNDO)
        assertTrue("$label: ANNULER at least 12 sp", layoutOf(CricketTags.LANDSCAPE_UNDO).sizeSp() >= 12f - 0.01f)
        assertVerticalLabel(label)
    }

    /**
     * Q17 B: the tall key's label, turned a quarter-turn (drawn taller than wide), inside the key, whole, at 28 sp
     * on one line or at least 20 sp broken only between words; TalkBack reads the label.
     */
    private fun assertVerticalLabel(label: String): String {
        val key = bounds(CricketTags.LANDSCAPE_NEXT)
        val textNode = textNodeOf(CricketTags.LANDSCAPE_NEXT)
        val drawn = textNode.boundsInRoot
        val layout = layoutOf(CricketTags.LANDSCAPE_NEXT)
        val text = layout.layoutInput.text.text
        val size = layout.sizeSp()
        assertTrue("$label: \"$text\" drawn $drawn, taller than wide", drawn.height > drawn.width)
        assertTrue("$label: \"$text\" $drawn inside the key $key", key.contains(drawn))
        assertFalse("$label: \"$text\" clipped", layout.didOverflowHeight || layout.didOverflowWidth)
        val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
        assertTrue("$label: \"$text\" breaks inside a word", breaksOnlyBetweenWords(text, ends))
        if (layout.lineCount == 1) {
            assertEquals("$label: \"$text\" on one line at 28 sp", 28f, size, 0.01f)
        } else {
            assertTrue("$label: \"$text\" at $size sp, 20 at least", size >= 20f - 0.01f)
        }
        val read = node(CricketTags.LANDSCAPE_NEXT).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertEquals("$label: TalkBack reads the label", listOf(text), read)
        return text
    }

    /**
     * Q14 B: the target buttons' touch areas are at least 48 dp wide and 42 dp tall, aligned with their row; the drawn
     * keys inset 2 dp (4 dp apart); the button column ends 12 dp before the action keys.
     */
    private fun assertButtons(label: String, isBullInPlay: Boolean) {
        val next = bounds(CricketTags.LANDSCAPE_NEXT)
        val darts = CricketTarget.entries.filter { it.isNumber }.flatMap {
            listOf(Dart.single(it.points), Dart.double(it.points), Dart.triple(it.points))
        } + if (isBullInPlay) listOf(Dart.OUTER_BULL, Dart.BULL) else emptyList()
        darts.forEach { dart ->
            val area = bounds(CricketTags.dartButton(dart))
            assertTrue("$label: $dart is ${area.width / px(1f)} x ${area.height / px(1f)} dp, 48 x 42 at least",
                area.width >= px(48f) - 1f && area.height >= px(42f) - 1f)
            val key = bounds(CricketTags.dartKey(dart))
            assertEquals("$label: $dart inset", px(2f), key.left - area.left, 1f)
            assertEquals("$label: $dart inset", px(2f), key.top - area.top, 1f)
            assertTrue("$label: $dart 12 dp before the next key", area.right <= next.left - px(12f) + 1f)
        }
        targets(isBullInPlay).forEach { target ->
            val row = bounds(CricketTags.row(target))
            val dart = if (target.isNumber) Dart.single(target.points) else Dart.OUTER_BULL
            val area = bounds(CricketTags.dartButton(dart))
            assertEquals("$label: $target buttons aligned with the row", row.top, area.top, 1f)
            assertEquals("$label: $target buttons aligned with the row", row.bottom, area.bottom, 1f)
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
        assertTrue("$label: Miss 168 dp wide", miss.width >= px(168f) - 1f)
        assertEquals("$label: Miss over the buttons", single.left - px(2f), miss.left, 1f)
        assertTrue("$label: Miss above the rows",
            miss.bottom <= bounds(CricketTags.dartButton(Dart.single(20))).top + 1f)
    }

    private fun nodeCount(tag: String): Int =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    /** The drawn size in sp at scale 1: every landscape text is fixed, so it must not move with the font scale. */
    private fun TextLayoutResult.sizeSp(): Float =
        with(layoutInput.density) { layoutInput.style.fontSize.toPx() / density }

    private fun Rect.contains(other: Rect): Boolean =
        other.left >= left - 1f && other.right <= right + 1f && other.top >= top - 1f && other.bottom <= bottom + 1f

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
            val tags = listOf(
                CricketTags.SCREEN,
                CricketTags.BAND,
                CricketTags.BOARD,
                GameTags.CURRENT_PLAYER,
                CricketTags.LANDSCAPE_UNDO,
                CricketTags.LANDSCAPE_NEXT,
            )
            val before = tags.map(::bounds)
            assertTrue("$side: the cutout reached the screen", bounds(CricketTags.SCREEN).width < px(914f - 40f))

            dispatchInsets(side, isBarShown = true)

            tags.zip(before).forEach { (tag, rect) -> assertEquals("$side: $tag moved", rect, bounds(tag)) }
        }
    }

    /** A pending win of the first player in a 4-player match of [legsPerSet] legs per set and [sets] sets. */
    private fun pendingWin(legsPerSet: Int, sets: Int) = CricketGameScreenState(
        CricketSaveFixtures.match(*fourPlayers.toTypedArray(), legsPerSet = legsPerSet, sets = sets).playLegWonBy(0),
        isAwaitingConfirmation = true,
    )

    /** Q17 B: every label of the tall key, in its 4-player screen, at scale [scale]. */
    private fun assertVerticalLabels(next: String, match: String, leg: String, set: String) {
        show(game(4, isBullInPlay = true, pending = false))
        assertEquals(next, assertVerticalLabel("next player"))
        listOf(Triple(1, 1, match), Triple(3, 3, leg), Triple(1, 3, set)).forEach { (legsPerSet, sets, text) ->
            show(pendingWin(legsPerSet, sets))
            assertEquals(text, assertVerticalLabel(text))
        }
    }

    @Test
    fun `CR25 Q17 B the French labels of the tall key are vertical and whole`() =
        assertVerticalLabels("JOUEUR SUIVANT", "VALIDER LA PARTIE", "VALIDER LA MANCHE", "VALIDER LE SET")

    @Test
    @Config(fontScale = 2.0f)
    fun `CR25 Q17 B the French labels of the tall key are the same at scale 2`() =
        assertVerticalLabels("JOUEUR SUIVANT", "VALIDER LA PARTIE", "VALIDER LA MANCHE", "VALIDER LE SET")

    @Test
    @Config(qualifiers = "en-w914dp-h411dp-land-420dpi", fontScale = 2.0f)
    fun `CR25 Q17 B the English labels of the tall key are vertical and whole at scale 2`() =
        assertVerticalLabels("NEXT PLAYER", "CONFIRM MATCH", "CONFIRM LEG", "CONFIRM SET")

    @Test
    @Config(fontScale = 2.0f)
    fun `CR22 U5 the landscape texts keep their size at scale 2`() {
        val state = game(4, isBullInPlay = true, pending = false)
        show(state)
        assertEquals(14f, layoutOf(CricketTags.columnName(0)).sizeSp(), 0.01f)
        assertEquals(14f, layoutOf(GameTags.HEADER_MODE).sizeSp(), 0.01f)
        assertEquals(18f, layoutOf(GameTags.playerName(checkNotNull(state.highlightedPlayerIndex))).sizeSp(), 0.01f)
        assertEquals(20f, layoutOf(CricketTags.LANDSCAPE_UNDO).sizeSp(), 0.01f)
        assertEquals("ANNULER", layoutOf(CricketTags.LANDSCAPE_UNDO).layoutInput.text.text)
        assertEquals(28f, layoutOf(CricketTags.LANDSCAPE_NEXT).sizeSp(), 0.01f)
    }

    @Test
    fun `CR22 Q16 A the landscape keys send the undo, next player and confirm events`() {
        val events = mutableListOf<AppEvent>()
        fun showWithEvents(state: CricketGameScreenState) {
            composeRule.runOnUiThread {
                val view = ComposeView(composeRule.activity)
                view.setContent { KosmoDartsTheme { CricketGameScreen(state, onEvent = { events += it }) } }
                composeRule.activity.setContentView(view)
            }
            composeRule.waitForIdle()
        }
        val match = CricketSaveFixtures.match("Ann", "Bob")
        showWithEvents(
            CricketGameScreenState(
                match.throwing(*CricketSaveFixtures.darts("T20 19 D18")),
                isAwaitingConfirmation = true,
            ),
        )
        node(CricketTags.LANDSCAPE_UNDO).performClick()
        node(CricketTags.LANDSCAPE_NEXT).performClick()
        showWithEvents(pendingWin(legsPerSet = 1, sets = 1))
        node(CricketTags.LANDSCAPE_NEXT).performClick()
        assertEquals(listOf(AppEvent.UndoTapped, AppEvent.NextPlayerTapped, AppEvent.FinishGameTapped), events)
    }

    /** The traversal index of [tag]'s node, 0 when it sets none. */
    private fun order(tag: String): Float =
        node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f

    @Test
    fun `CR23 R2_2 TalkBack reads the band, the headers, Miss, Undo, the rows, then the tall key`() {
        show(game(2, isBullInPlay = true, pending = false))
        assertTrue("band before the board", order(CricketTags.BAND) < order(CricketTags.BOARD))
        val band = bounds(CricketTags.BAND)
        assertTrue("the title before the zone in the band", bounds(CricketTags.TITLE).right <= band.right &&
            bounds(CricketTags.TITLE).left < bounds(GameTags.CURRENT_PLAYER).left)
        val current = checkNotNull(game(2, isBullInPlay = true, pending = false).highlightedPlayerIndex)
        val other = 1 - current
        val sequence = listOf(
            CricketTags.columnHeader(current),
            CricketTags.columnHeader(other),
            KeypadTags.MISS,
            CricketTags.LANDSCAPE_UNDO,
            CricketTags.BOARD_ROWS,
            CricketTags.LANDSCAPE_NEXT,
        )
        sequence.zipWithNext().forEach { (before, after) ->
            assertTrue("$before (${order(before)}) before $after (${order(after)})", order(before) < order(after))
        }
    }
    /** The side of the camera hole, depending on which way the phone is turned. */
    private enum class CutoutSide { LEFT, RIGHT }

    private companion object {
        /** The widest cutout the plan assumes on the Pixel 7 (section 4). */
        const val CUTOUT_DP = 48f
    }
}
