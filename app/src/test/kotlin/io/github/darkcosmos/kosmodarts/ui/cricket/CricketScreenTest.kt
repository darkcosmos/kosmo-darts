package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsNotEnabled
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
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
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

/**
 * The Cricket game screen (docs/rules/cricket.md, CR22 to CR25) in French: the keypad, the TalkBack texts and order,
 * the dead rows (review T3) and the fit contract (reviews U1, T5), without any real screen: the state is given
 * directly.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
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

    @Test
    fun `CR24 the keys send the right events`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20")))

        node(KeypadTags.number(19)).performClick()
        node(KeypadTags.DOUBLE).performClick()
        node(KeypadTags.TRIPLE).performClick()
        node(KeypadTags.OUTER_BULL).performClick()
        node(KeypadTags.BULL).performClick()
        node(KeypadTags.MISS).performClick()
        node(KeypadTags.UNDO).performClick()

        assertEquals(
            listOf(
                AppEvent.NumberTapped(19),
                AppEvent.MultiplierTapped(Multiplier.DOUBLE),
                AppEvent.MultiplierTapped(Multiplier.TRIPLE),
                AppEvent.OuterBullTapped,
                AppEvent.BullTapped,
                AppEvent.MissTapped,
                AppEvent.UndoTapped,
            ),
            events,
        )
    }

    @Test
    fun `CR24 25 and Bull are absent with 25-Bull off and disabled under a modifier`() {
        show(match(listOf("Ann", "Bob"), isBullInPlay = false))
        node(KeypadTags.OUTER_BULL).assertDoesNotExist()
        node(KeypadTags.BULL).assertDoesNotExist()
        node(KeypadTags.number(15)).assertIsEnabled()
    }

    @Test
    fun `CR24 a modifier disables 25 and Bull`() {
        show(match(listOf("Ann", "Bob")).toggleMultiplier(Multiplier.TRIPLE))
        node(KeypadTags.OUTER_BULL).assertIsNotEnabled()
        node(KeypadTags.BULL).assertIsNotEnabled()
        node(KeypadTags.number(20)).assertIsEnabled()
    }

    @Test
    fun `CR7 CR24 a waiting turn locks the keys and enables Next player`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20", "T19", "T18")).copy(isAwaitingConfirmation = true))
        node(KeypadTags.number(20)).assertIsNotEnabled()
        node(KeypadTags.MISS).assertIsNotEnabled()
        node(KeypadTags.NEXT_PLAYER).assertIsEnabled().performClick()
        node(KeypadTags.UNDO).assertIsEnabled()
        assertEquals(listOf<AppEvent>(AppEvent.NextPlayerTapped), events)
    }

    @Test
    fun `CR24 Next player is disabled and Undo too while nothing was thrown`() {
        show(match(listOf("Ann", "Bob")))
        node(KeypadTags.NEXT_PLAYER).assertIsNotEnabled()
        node(KeypadTags.UNDO).assertIsNotEnabled()
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
    fun `CR23 U7 the headers come first, the current one first, then the rows, the points row last`() {
        show(match(fourPlayers, darts = listOf("T20", "0", "0")).nextPlayer())

        val headerOrders = (0..3).map { traversalIndex(CricketTags.columnHeader(it)) }
        // Bob (index 1) throws now: his header leads, then Ann, Cid, Dan in configuration order.
        assertTrue(headerOrders[1] < headerOrders[0])
        assertTrue(headerOrders[0] < headerOrders[2] && headerOrders[2] < headerOrders[3])
        assertTrue(traversalIndex(CricketTags.BOARD_ROWS) > headerOrders.max())
        // The points row is the last child of the rows group, so it is read after every row.
        val rowsNode = node(CricketTags.BOARD_ROWS).fetchSemanticsNode()
        assertEquals(CricketTags.POINTS_ROW, rowsNode.children.last().config[SemanticsProperties.TestTag])
        node(CricketTags.columnHeader(1)).assertContentDescriptionEquals("Bob, au lancer, Volée en cours : 0 marque")
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

    // Fit contract (CR22).

    private fun assertPinnedPartsFit() {
        listOf(KeypadTags.number(20), KeypadTags.number(15), KeypadTags.MISS, KeypadTags.UNDO, KeypadTags.NEXT_PLAYER)
            .forEach { tag ->
                assertTrue("$tag inside the root", isInsideRoot(tag))
                assertFalse("$tag not in a scroll container", isInScrollContainer(tag))
                assertTrue("$tag at least 48 dp", bounds(tag).height >= px(48f) - 1f)
            }
    }

    private fun assertBoardPartsExist() {
        (0..3).forEach { player ->
            node(CricketTags.columnHeader(player)).assertExists()
            node(CricketTags.columnName(player)).assertExists()
            node(CricketTags.columnVisit(player)).assertExists()
            node(CricketTags.points(player)).assertExists()
        }
        node(CricketTags.rowLabel(CricketTarget.TWENTY)).assertExists()
        node(CricketTags.rowLabel(CricketTarget.BULL)).assertExists()
        assertBoardTextNotClipped()
        assertPointsNotClipped()
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

    private fun assertBoardTextNotClipped() {
        CricketTarget.entries.forEach { assertTextNotClipped(CricketTags.rowLabel(it)) }
        (0..3).forEach { player ->
            assertTextNotClipped(CricketTags.columnName(player), ellipsis = true)
            assertTextNotClipped(CricketTags.columnVisit(player))
        }
    }

    private fun assertPointsNotClipped() {
        (0..3).forEach { player ->
            assertTextNotClipped(CricketTags.points(player))
        }
    }

    private fun fourPlayerGame() = match(fourPlayers, darts = listOf("T20"))

    @Test
    fun `CR22 portrait 360 x 640 scale 1 the board needs no scrolling`() {
        show(fourPlayerGame())

        assertPinnedPartsFit()
        assertBoardPartsExist()
        val range = node(CricketTags.BOARD_ROWS).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), 0f)
        (0..3).forEach { assertTrue("header $it", isInsideRoot(CricketTags.columnHeader(it))) }
        CricketTarget.entries.forEach { assertTrue("$it", isInsideRoot(CricketTags.row(it))) }
        assertTrue(isInsideRoot(CricketTags.POINTS_ROW))
        assertTrue(isInsideRoot(CricketTags.ZONE_SCORE))
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `CR22 portrait 360 x 640 scale 2 the keys stay and the board scrolls`() {
        show(fourPlayerGame())

        assertPinnedPartsFit()
        assertBoardPartsExist()
        assertTrue(isInsideRoot(CricketTags.columnHeader(0)))
        node(CricketTags.points(3)).performScrollTo()
        assertTrue(isInsideRoot(CricketTags.points(3)))
    }

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi")
    fun `CR22 landscape 640 x 360 the keys and the action bar are pinned`() {
        show(fourPlayerGame())

        assertPinnedPartsFit()
        assertBoardPartsExist()
        assertTrue(isInsideRoot(CricketTags.columnHeader(0)))
    }

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = 2.0f)
    fun `CR22 landscape 640 x 360 scale 2 the keys and the action bar are pinned`() {
        show(fourPlayerGame())

        assertPinnedPartsFit()
        assertBoardPartsExist()
    }

    // Zone (CR22, CR25).

    @Test
    fun `CR22 the zone shows the points, or the closed targets when alone`() {
        show(match(listOf("Ann", "Bob"), darts = listOf("T20")))
        node(CricketTags.ZONE_SCORE).assertExists()
        assertTrue(isInsideRoot(CricketTags.ZONE_SCORE))
    }

    @Test
    fun `CR23 a solo game has no points row`() {
        show(match(listOf("Ann")))
        node(CricketTags.POINTS_ROW).assertDoesNotExist()
    }

    @Test
    fun `CR25 a win shows the banner and the confirm button`() {
        val state = CricketGameScreenState(CricketSaveFixtures.PENDING_MATCH_WIN.match, isAwaitingConfirmation = true)
        show(state)
        node(CricketTags.WIN_BANNER).assertExists()
        node(GameTags.CONFIRM).assertExists()
    }
}
