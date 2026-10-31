package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.annScoring
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.components.breaksOnlyBetweenWords
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

/**
 * The Cricket game screen (docs/rules/cricket.md, CR22 to CR25) in French: the board buttons, the TalkBack texts
 * and order, the dead rows (review T3) and the fit contract (reviews U1, T5), without any real screen: the state is
 * given directly.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w411dp-h814dp-port-420dpi")
class CricketScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()

    private fun show(state: CricketGameScreenState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { CricketGameScreen(state, onEvent = { events += it }) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun match(names: List<String>, isBullInPlay: Boolean = true, darts: List<String> = emptyList()) =
        CricketGameScreenState(
            CricketSaveFixtures.match(*names.toTypedArray(), isBullInPlay = isBullInPlay)
                .throwing(*CricketSaveFixtures.darts(*darts.toTypedArray())),
        )

    private val fourPlayers = listOf("Ann", "Bob", "Cid", "Dan")

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String): Rect = node(tag).fetchSemanticsNode().boundsInRoot

    private fun root(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun isInsideRoot(tag: String): Boolean {
        val rect = bounds(tag)
        val screen = root()
        return rect.left >= screen.left - 1f && rect.right <= screen.right + 1f &&
            rect.top >= screen.top - 1f && rect.bottom <= screen.bottom + 1f
    }

    private fun isInScrollContainer(tag: String): Boolean {
        var parent = node(tag).fetchSemanticsNode().parent
        while (parent != null) {
            if (parent.config.contains(SemanticsActions.ScrollBy)) return true
            parent = parent.parent
        }
        return false
    }

    // Keypad (CR24).

    /** Owner review: the zone shows the darts of the visit (a miss when a dart gives no mark), not a visit box. */
    @Test
    fun `CR2 CR22 the zone shows the scoring darts and a miss, without a visit box`() {
        show(match(listOf("Ann", "Bob")).toggleMultiplier(Multiplier.TRIPLE).throwNumber(20).throwNumber(5))

        node(GameTags.dartSlot(0)).assertContentDescriptionEquals("Fléchette 1 : T20")
        node(GameTags.dartSlot(1)).assertContentDescriptionEquals("Fléchette 2 : RATÉ")
        node(GameTags.dartSlot(2)).assertContentDescriptionEquals("Fléchette 3 : pas encore lancée")
        node(GameTags.visitTotal(0)).assertDoesNotExist()
    }

    @Test
    fun `CR24 each button sends its dart, Miss and Undo their own events`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20")))

        listOf(Dart.single(20), Dart.double(20), Dart.triple(20), Dart.OUTER_BULL, Dart.BULL)
            .forEach { node(CricketTags.dartButton(it)).performClick() }
        node(KeypadTags.MISS).performClick()
        node(KeypadTags.UNDO).performClick()

        assertEquals(
            listOf(
                AppEvent.CricketDartTapped(Dart.single(20)),
                AppEvent.CricketDartTapped(Dart.double(20)),
                AppEvent.CricketDartTapped(Dart.triple(20)),
                AppEvent.CricketDartTapped(Dart.OUTER_BULL),
                AppEvent.CricketDartTapped(Dart.BULL),
                AppEvent.MissTapped,
                AppEvent.UndoTapped,
            ),
            events,
        )
    }

    @Test
    fun `CR24 the dots count 0 for a single, 2 for a double, 3 for a triple, and the Bull row 0 and 2`() {
        show(match(listOf("Ann", "Bob")))

        val expected = mapOf(
            Dart.single(20) to 0, Dart.double(20) to 2, Dart.triple(20) to 3,
            Dart.OUTER_BULL to 0, Dart.BULL to 2,
        )
        expected.forEach { (dart, count) ->
            node(CricketTags.dartDots(dart)).assert(SemanticsMatcher.expectValue(CricketSemantics.DotCount, count))
        }
    }

    @Test
    fun `CR24 TalkBack reads 20, Double 20, Triple 20, 25 and Bull`() {
        show(match(listOf("Ann", "Bob")))

        node(CricketTags.dartButton(Dart.single(20))).assertContentDescriptionEquals("20")
        node(CricketTags.dartButton(Dart.double(20))).assertContentDescriptionEquals("Double 20")
        node(CricketTags.dartButton(Dart.triple(20))).assertContentDescriptionEquals("Triple 20")
        node(CricketTags.dartButton(Dart.OUTER_BULL)).assertContentDescriptionEquals("25")
        node(CricketTags.dartButton(Dart.BULL)).assertContentDescriptionEquals("Bull")
        node(KeypadTags.MISS).assertContentDescriptionEquals("RATÉ")
    }

    @Test
    fun `CR24 25 and Bull are absent with 25-Bull off`() {
        show(match(listOf("Ann", "Bob"), isBullInPlay = false))
        node(CricketTags.dartButton(Dart.OUTER_BULL)).assertDoesNotExist()
        node(CricketTags.dartButton(Dart.BULL)).assertDoesNotExist()
        node(CricketTags.row(CricketTarget.BULL)).assertDoesNotExist()
        node(CricketTags.dartButton(Dart.single(15))).assertIsEnabled()
    }

    @Test
    fun `CR7 CR24 a waiting turn locks the buttons and Miss and enables Next player`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20", "T19", "T18")).copy(isAwaitingConfirmation = true))
        node(CricketTags.dartButton(Dart.single(20))).assertIsNotEnabled()
        node(CricketTags.dartButton(Dart.BULL)).assertIsNotEnabled()
        node(KeypadTags.MISS).assertIsNotEnabled()
        node(KeypadTags.NEXT_PLAYER).assertIsEnabled().performClick()
        node(KeypadTags.UNDO).assertIsEnabled()
        assertEquals(listOf<AppEvent>(AppEvent.NextPlayerTapped), events)
    }

    @Test
    fun `CR25 a pending win locks the buttons`() {
        show(pendingWin(listOf("Ann", "Bob")))
        node(CricketTags.dartButton(Dart.triple(20))).assertIsNotEnabled()
        node(KeypadTags.MISS).assertIsNotEnabled()
    }

    @Test
    fun `CR15 CR24 the buttons of a dead row stay enabled`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20", "0", "0", "T20")))
        node(CricketTags.row(CricketTarget.TWENTY)).assert(SemanticsMatcher.expectValue(CricketSemantics.RowDead, true))
        listOf(Dart.single(20), Dart.double(20), Dart.triple(20)).forEach {
            node(CricketTags.dartButton(it)).assertIsEnabled()
        }
    }

    @Test
    fun `CR24 Next player is disabled and Undo too while nothing was thrown`() {
        show(match(listOf("Ann", "Bob")))
        node(KeypadTags.NEXT_PLAYER).assertIsNotEnabled()
        node(KeypadTags.UNDO).assertIsNotEnabled()
    }

    // Geometry (CR22, CR24, Q7 B).

    @Test
    fun `CR22 Q7 B the buttons are right of the last player cell and the triple ends at the board edge`() {
        show(match(fourPlayers))

        CricketTarget.entries.filter { it.isNumber }.forEach { target ->
            val lastCell = bounds(CricketTags.cell(target, 3))
            val single = bounds(CricketTags.dartButton(Dart.single(target.points)))
            val triple = bounds(CricketTags.dartButton(Dart.triple(target.points)))
            assertTrue("$target button after the cells", single.left >= lastCell.right - 1f)
            assertEquals("$target triple at the board edge", bounds(CricketTags.BOARD).right, triple.right, 1f)
            assertEquals("$target aligned with its row", bounds(CricketTags.row(target)).top, single.top, 1f)
            assertEquals(bounds(CricketTags.row(target)).bottom, single.bottom, 1f)
        }
    }

    @Test
    fun `CR22 Miss is aligned with the headers and as wide as the button column`() {
        show(match(fourPlayers))

        val miss = bounds(KeypadTags.MISS)
        val header = bounds(CricketTags.columnHeader(0))
        val triple = bounds(CricketTags.dartButton(Dart.triple(20)))
        val single = bounds(CricketTags.dartButton(Dart.single(20)))
        assertEquals(header.top, miss.top, 1f)
        assertEquals(header.bottom, miss.bottom, 1f)
        assertEquals(single.left, miss.left, 1f)
        assertEquals(triple.right, miss.right, 1f)
        assertTrue(miss.bottom <= single.top + 1f)
    }

    @Test
    fun `CR22 Undo lives only in the action bar, left of the primary button, on the same line`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20")))

        val undo = bounds(KeypadTags.UNDO)
        val next = bounds(KeypadTags.NEXT_PLAYER)
        assertTrue(undo.right <= next.left + 1f)
        assertEquals(undo.top, next.top, 1f)
        assertEquals(undo.bottom, next.bottom, 1f)
        assertTrue(undo.top >= bounds(CricketTags.row(CricketTarget.BULL)).bottom - 1f)
        composeRule.onAllNodes(hasTestTag(KeypadTags.UNDO), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `CR24 touch areas are 48 dp, keys are 4 dp apart, in a row and between rows`() {
        show(match(fourPlayers, darts = listOf("T20")))

        val darts = CricketTarget.entries.filter { it.isNumber }.flatMap { target ->
            listOf(Dart.single(target.points), Dart.double(target.points), Dart.triple(target.points))
        }
        darts.forEach { dart ->
            val area = bounds(CricketTags.dartButton(dart))
            assertTrue("$dart width", area.width >= px(48f) - 1f)
            assertTrue("$dart height", area.height >= px(48f) - 1f)
            val key = bounds(CricketTags.dartKey(dart))
            assertEquals("$dart inset", px(2f), key.left - area.left, 1f)
            assertEquals("$dart inset", px(2f), key.top - area.top, 1f)
        }
        assertTrue(bounds(KeypadTags.MISS).width >= px(144f) - 1f)
        val single = bounds(CricketTags.dartKey(Dart.single(20)))
        val double = bounds(CricketTags.dartKey(Dart.double(20)))
        assertTrue("gap in a row", double.left - single.right >= px(4f) - 1f)
        val below = bounds(CricketTags.dartKey(Dart.single(19)))
        assertTrue("gap between rows", below.top - single.bottom >= px(4f) - 1f)
    }

    // Zone (CR22).

    @Test
    fun `CR22 the zone has no total and no n over 7`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20")))
        composeRule.onAllNodes(hasText("/7", substring = true), useUnmergedTree = true).assertCountEquals(0)
        node(CricketTags.columnTotal(0)).assertExists()
        node(CricketTags.ZONE_STAT).assertExists()
    }

    @Test
    fun `CR22 CR23 a solo game has no total anywhere`() {
        show(match(listOf("Ann"), darts = listOf("T20")))
        composeRule.onAllNodes(hasText("/7", substring = true), useUnmergedTree = true).assertCountEquals(0)
        node(CricketTags.columnTotal(0)).assertDoesNotExist()
    }

    // TalkBack (CR23).

    @Test
    fun `CR23 a live cell is read with its marks and a closed one as closed`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("D20", "T19", "18")))

        node(CricketTags.cell(CricketTarget.TWENTY, 0))
            .assertContentDescriptionEquals("20, Ann : 2 marques")
        node(CricketTags.cell(CricketTarget.NINETEEN, 0))
            .assertContentDescriptionEquals("19, Ann : fermée")
        node(CricketTags.cell(CricketTarget.EIGHTEEN, 0))
            .assertContentDescriptionEquals("18, Ann : 1 marque")
        node(CricketTags.cell(CricketTarget.TWENTY, 1))
            .assertContentDescriptionEquals("20, Bob : 0 marque")
    }

    @Test
    fun `CR23 a dead row has a strike node and RowDead and is one merged node, live rows have neither`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20", "0", "0", "T20")))

        CricketTarget.entries.filter { it.isNumber || it == CricketTarget.BULL }.forEach { target ->
            val isDead = target == CricketTarget.TWENTY
            node(CricketTags.row(target)).assert(SemanticsMatcher.expectValue(CricketSemantics.RowDead, isDead))
            if (isDead) {
                node(CricketTags.rowStrike(target)).assertExists()
            } else {
                node(CricketTags.rowStrike(target)).assertDoesNotExist()
            }
        }
        // The dead row is read once, without its cells.
        composeRule.onNode(
            hasContentDescription("20\u00A0: fermée par tous, ne compte plus")
                .and(hasAnyAncestor(hasTestTag(CricketTags.row(CricketTarget.TWENTY)))),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(CricketTags.cell(CricketTarget.TWENTY, 0)).assertDoesNotExist()
    }

    @Test
    fun `CR23 U7 the headers come first, the current one first, then Miss, then the rows`() {
        show(match(fourPlayers, darts = listOf("T20", "0", "0")).nextPlayer())

        val headerOrders = (0..3).map { traversalIndex(CricketTags.columnHeader(it)) }
        // Bob (index 1) throws now: his header leads, then Ann, Cid, Dan in configuration order.
        assertTrue(headerOrders[1] < headerOrders[0])
        assertTrue(headerOrders[0] < headerOrders[2] && headerOrders[2] < headerOrders[3])
        val miss = traversalIndex(KeypadTags.MISS)
        assertTrue("Miss after the headers", miss > headerOrders.max())
        assertTrue("Miss before the rows", miss < traversalIndex(CricketTags.BOARD_ROWS))
        // Each row reads its cells, then its buttons.
        val row = node(CricketTags.row(CricketTarget.TWENTY)).fetchSemanticsNode()
        fun tagsOf(node: SemanticsNode): List<String> =
            listOfNotNull(node.config.getOrNull(SemanticsProperties.TestTag)) + node.children.flatMap(::tagsOf)
        val tags = tagsOf(row)
        val cell = tags.indexOf(CricketTags.cell(CricketTarget.TWENTY, 3))
        assertTrue(cell < tags.indexOf(CricketTags.dartButton(Dart.single(20))))
        node(CricketTags.columnHeader(1))
            .assertContentDescriptionEquals("Bob, au lancer, 0 point, Volée en cours\u00A0: 0 marque")
    }

    private fun traversalIndex(tag: String): Float =
        node(tag).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex]

    @Test
    fun `CR23 the marks of 1, 2 and 3 are drawn at the same size`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T19", "D20", "18")))

        val sizes = listOf(CricketTarget.TWENTY, CricketTarget.NINETEEN, CricketTarget.EIGHTEEN)
            .map { bounds(CricketTags.mark(it, 0)).size }
        assertEquals(1, sizes.toSet().size)
        assertTrue(sizes.first().width > 0f)
    }

    // No clipped text (reviews U1, U2, T9): the text bounds are inside the node bounds, whatever the font scale.

    private fun textNodeOf(tag: String): SemanticsNode {
        fun find(node: SemanticsNode): SemanticsNode? =
            if (node.config.contains(SemanticsActions.GetTextLayoutResult)) {
                node
            } else {
                node.children.firstNotNullOfOrNull(::find)
            }
        return checkNotNull(find(node(tag).fetchSemanticsNode())) { "no text in $tag" }
    }

    /** The text of [tag] (or of its first text child) is not cut: not vertically, nor sideways unless [ellipsis]. */
    private fun assertTextNotClipped(tag: String, ellipsis: Boolean = false) {
        if (isInScrollContainer(tag)) node(tag).performScrollTo()
        val textNode = textNodeOf(tag)
        val results = mutableListOf<TextLayoutResult>()
        textNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val owner = node(tag).fetchSemanticsNode().boundsInRoot
        val text = textNode.boundsInRoot
        // didOverflowHeight also tells that the one line is too long for its width, which an ellipsis allows.
        if (!ellipsis) assertFalse("$tag overflows vertically", layout.didOverflowHeight)
        assertTrue(
            "$tag text ${layout.multiParagraph.height} px in ${text.height} px",
            layout.multiParagraph.height <= text.height + 1f,
        )
        assertTrue("$tag text $text inside $owner", text.top >= owner.top - 1f && text.bottom <= owner.bottom + 1f)
        assertEquals("$tag on one line", 1, layout.lineCount)
        if (!ellipsis) {
            val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
            val wide = "$tag text is ${lineWidth.toInt()} px wide in ${text.width.toInt()} px"
            assertTrue(wide, lineWidth <= text.width + 1f)
            assertFalse("$tag ellipsized", layout.isLineEllipsized(0))
        }
    }

    // Fit contract (CR22): the Pixel 7 with its bars (411 x 814 dp), whatever the font scale.

    private fun names(players: Int) = fourPlayers.take(players)

    private fun pendingWin(names: List<String>) = CricketGameScreenState(
        CricketSaveFixtures.match(*names.toTypedArray()).playLegWonBy(0),
        isAwaitingConfirmation = true,
    )

    private fun primaryTag(isWinPending: Boolean) = if (isWinPending) GameTags.CONFIRM else KeypadTags.NEXT_PLAYER

    private fun assertFits(players: Int, isWinPending: Boolean) {
        val label = "$players players, win pending $isWinPending"
        show(if (isWinPending) pendingWin(names(players)) else match(names(players), darts = listOf("T20")))

        val rowsNode = node(CricketTags.BOARD_ROWS).fetchSemanticsNode()
        val range = rowsNode.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertTrue("$label: the rows scroll", range == null || range.maxValue() <= 0f)
        (0 until players).forEach { player ->
            assertTrue("$label: header $player", isInsideRoot(CricketTags.columnHeader(player)))
            assertTextNotClipped(CricketTags.columnName(player), ellipsis = true)
            if (players > 1) {
                assertTrue("$label: total $player", isInsideRoot(CricketTags.columnTotal(player)))
                assertTextNotClipped(CricketTags.columnTotal(player))
            }
        }
        CricketTarget.entries.forEach { target ->
            assertTrue("$label: row $target", isInsideRoot(CricketTags.row(target)))
            assertFalse("$label: row $target scrolls", isInScrollContainer(CricketTags.row(target)))
        }
        listOf(
            KeypadTags.MISS, KeypadTags.UNDO, primaryTag(isWinPending),
            CricketTags.dartButton(Dart.single(15)), CricketTags.dartButton(Dart.triple(20)),
            CricketTags.dartButton(Dart.BULL),
        ).forEach { tag ->
            assertTrue("$label: $tag inside the root", isInsideRoot(tag))
            assertTrue("$label: $tag at least 48 dp", bounds(tag).height >= px(48f) - 1f)
        }
    }

    private fun assertFitsAtThisScale() {
        listOf(1, 2, 4).forEach { players ->
            listOf(false, true).forEach { isWinPending -> assertFits(players, isWinPending) }
        }
    }

    @Test
    fun `CR22 portrait 411 x 814 scale 1 nothing scrolls`() = assertFitsAtThisScale()

    @Test
    @Config(fontScale = 1.3f)
    fun `CR22 portrait 411 x 814 scale 1_3 nothing scrolls`() = assertFitsAtThisScale()

    @Test
    @Config(fontScale = 1.5f)
    fun `CR22 portrait 411 x 814 scale 1_5 nothing scrolls`() = assertFitsAtThisScale()

    @Test
    @Config(fontScale = 2.0f)
    fun `CR22 portrait 411 x 814 scale 2 nothing scrolls`() = assertFitsAtThisScale()

    @Test
    fun `CR22 four digit totals are whole in four columns`() {
        show(CricketGameScreenState(CricketSaveFixtures.match(*fourPlayers.toTypedArray()).annScoring(6)))
        node(CricketTags.columnTotal(0)).assertTextEquals("1020")
        (0..3).forEach { assertTextNotClipped(CricketTags.columnTotal(it)) }
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `CR22 four digit totals are whole at scale 2`() {
        show(CricketGameScreenState(CricketSaveFixtures.match(*fourPlayers.toTypedArray()).annScoring(6)))
        (0..3).forEach { assertTextNotClipped(CricketTags.columnTotal(it)) }
    }

    // Action bar (CR22): unchanged, with the 12 sp floor and whole words.

    private fun actionBarLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        textNodeOf(tag).config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun assertActionBarLabels() {
        show(pendingWin(fourPlayers))
        listOf(KeypadTags.UNDO, GameTags.CONFIRM).forEach { tag ->
            val layout = actionBarLayout(tag)
            val text = layout.layoutInput.text.text
            val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
            assertTrue("$tag \"$text\" breaks inside a word", breaksOnlyBetweenWords(text, ends))
            assertFalse("$tag \"$text\" overflows", layout.didOverflowHeight || layout.didOverflowWidth)
            val size = layout.layoutInput.style.fontSize.value
            assertTrue("$tag at least 12 sp, is $size", size >= 12f - 0.01f)
            assertTrue("$tag at least 48 dp", bounds(tag).height >= px(48f) - 1f)
        }
        assertEquals(bounds(KeypadTags.UNDO).height, bounds(GameTags.CONFIRM).height, 1f)
        assertEquals(bounds(KeypadTags.UNDO).top, bounds(GameTags.CONFIRM).top, 1f)
        assertTrue(actionBarLayout(GameTags.CONFIRM).layoutInput.text.text.isNotBlank())
    }

    @Test
    fun `CR25 action bar labels, VALIDER LA PARTIE, scale 1`() {
        assertActionBarLabels()
        assertEquals("VALIDER LA PARTIE", actionBarLayout(GameTags.CONFIRM).layoutInput.text.text)
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `CR25 action bar labels, VALIDER LA PARTIE, scale 2`() = assertActionBarLabels()

    @Test
    @Config(qualifiers = "en-w411dp-h814dp-port-420dpi")
    fun `CR25 action bar label in English is CONFIRM MATCH`() {
        show(pendingWin(fourPlayers))
        assertEquals("CONFIRM MATCH", actionBarLayout(GameTags.CONFIRM).layoutInput.text.text)
    }

    // Win (CR25).

    @Test
    fun `CR25 a win shows the banner and the confirm button`() {
        show(CricketGameScreenState(CricketSaveFixtures.PENDING_MATCH_WIN.match, isAwaitingConfirmation = true))
        node(CricketTags.WIN_BANNER).assertExists()
        node(GameTags.CONFIRM).assertExists()
    }
}
