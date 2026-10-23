package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The owner's review of PR #57 on a Pixel 7 (issue #49): in landscape, about 800 × 360 dp once the system bars and the
 * cutout are taken off, the tiles and the zone fit without scrolling (M6); the route and the BUST banner stand on one
 * line, clear of the score and the dart slots (CS7, CS8, VT13); the CHECKOUT banner is centred and spans the zone
 * (M15, design system 6.3). In both orientations the « SORTIE » box is as high as the route panel and shares its
 * rounded start corners (CS7).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoneLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun effectiveScale(): Float = with(composeRule.density) { 24.sp.toDp().value } / 24f

    /** Bounds as drawn, clipped by a scrolling column: a node partly below the fold is shorter than its layout. */
    private fun visible(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun textLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** The text laid out inside the node tagged [tag]. */
    private fun innerTextLayout(tag: String): TextLayoutResult {
        val node = composeRule.onNode(
            hasAnyAncestor(hasTestTag(tag)).and(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** [tag] is drawn whole, inside the screen, without any scrolling. */
    private fun assertWhole(tag: String) {
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val rect = node.boundsInRoot
        assertEquals("$tag is cut: $rect", node.size.height.toFloat(), rect.height, EPSILON)
        assertTrue("$tag $rect below $screen", rect.bottom <= screen.bottom + EPSILON)
        assertTrue("$tag $rect above $screen", rect.top >= screen.top - EPSILON)
    }

    private fun assertApart(first: String, second: String) {
        val a = visible(first)
        val b = visible(second)
        val overlap = a.intersect(b)
        assertTrue("$first $a overlaps $second $b", overlap.width <= EPSILON || overlap.height <= EPSILON)
    }

    // M6: the tiles and the zone fit the owner's phone in landscape, in a match (tiles with their match line).

    private fun checkLandscapeFit(playerCount: Int) {
        send(*startMatch(*NAMES.take(playerCount).toTypedArray()))
        assertWhole(GameTags.CURRENT_PLAYER)
        (1 until playerCount).forEach { assertWhole(GameTags.tile(it)) }
        assertWhole(GameTags.dartSlot(2))
    }

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `M6 landscape 800 x 360, 4 players in a match, tiles whole`() = checkLandscapeFit(4)

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `M6 landscape 800 x 360, 3 players in a match, tiles whole`() = checkLandscapeFit(3)

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `M6 landscape 800 x 360, 2 players in a match, tiles whole`() = checkLandscapeFit(2)

    // U3: in landscape, at 2 and 3 players too, a long name keeps at least 8 characters before "…", tiles whole.

    private fun checkLongNames(playerCount: Int) {
        send(*startMatch(*LONG_NAMES.take(playerCount).toTypedArray()))
        (1 until playerCount).forEach { player ->
            val name = textLayout(GameTags.playerName(player))
            val shown = name.getLineEnd(name.lineCount - 1, visibleEnd = true)
            assertTrue("name $player shows $shown characters", shown >= MIN_NAME_CHARACTERS)
            assertWhole(GameTags.tile(player))
        }
    }

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `U3 landscape 800 x 360, 2 players, 8 name characters`() = checkLongNames(2)

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `U3 landscape 800 x 360, 3 players, 8 name characters`() = checkLongNames(3)

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `U3 landscape 915 x 412, 2 players, 8 name characters`() = checkLongNames(2)

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `U3 landscape 915 x 412, 3 players, 8 name characters`() = checkLongNames(3)

    // CS7, CS8: the route on one line in landscape, clear of the score and the dart slots.

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `CS7 landscape route on one line, clear of the score and the darts`() = checkOneLineRoute(1f)

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `CS7 landscape route on one line at the largest font size`() = checkOneLineRoute(LARGEST_EFFECTIVE)

    private fun checkOneLineRoute(expectedScale: Float) {
        assertEquals("effective text scale", expectedScale, effectiveScale(), SCALE_TOLERANCE)
        send(*startMatch(*NAMES.toTypedArray()), *ANN_AT_121)
        val darts = visible(GameTags.CHECKOUT_SUGGESTION)
        val label = visible(GameTags.CHECKOUT_ROUTE_LABEL)

        assertEquals("route lines", 1, textLayout(GameTags.CHECKOUT_SUGGESTION).lineCount)
        val sameLine = label.top < darts.bottom && darts.top < label.bottom
        assertTrue("label $label and darts $darts not on one line", sameLine)
        assertTrue("label $label not before darts $darts", label.right <= darts.left + EPSILON)
        assertApart(GameTags.CHECKOUT_SUGGESTION_LINE, GameTags.CURRENT_REMAINING)
        assertApart(GameTags.CHECKOUT_SUGGESTION_LINE, GameTags.dartSlot(0))
        assertWhole(GameTags.CHECKOUT_SUGGESTION_LINE)
    }

    // VT13: the BUST banner on one line in landscape.

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `VT13 landscape BUST banner on one line`() {
        send(*startMatch(*NAMES.toTypedArray()), *ANN_AT_121, *triple(20), *triple(20))
        val banner = visible(GameTags.CARD_BUST)
        val text = textLayout(GameTags.CARD_BUST_TEXT)

        assertEquals("BUST lines", 1, text.lineCount)
        assertTrue("banner $banner higher than one line", banner.height <= text.size.height + px(BAR_PADDING_DP * 2))
        assertApart(GameTags.CARD_BUST, GameTags.CURRENT_REMAINING)
        assertApart(GameTags.CARD_BUST, GameTags.dartSlot(0))
    }

    // CS7: the « SORTIE » box as high as the panel, with the panel's rounded start corners.

    private fun checkRouteLabel() {
        send(*startMatch(*NAMES.toTypedArray()), *ANN_AT_121)
        val panel = visible(GameTags.CHECKOUT_SUGGESTION_LINE)
        val label = visible(GameTags.CHECKOUT_ROUTE_LABEL)
        assertEquals("label top", panel.top, label.top, EPSILON)
        assertEquals("label bottom", panel.bottom, label.bottom, EPSILON)
        assertEquals("label start", panel.left, label.left, EPSILON)

        val pixels = composeRule.onNodeWithTag(GameTags.CHECKOUT_ROUTE_LABEL, useUnmergedTree = true)
            .captureToImage().toPixelMap()
        val accent = Color(ACCENT)
        assertEquals("label middle", accent, pixels[0, pixels.height / 2])
        assertNotEquals("top start corner is square", accent, pixels[0, 0])
        assertNotEquals("bottom start corner is square", accent, pixels[0, pixels.height - 1])
    }

    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi")
    fun `CS7 portrait route label fills the panel with rounded start corners`() = checkRouteLabel()

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `CS7 landscape route label fills the panel with rounded start corners`() = checkRouteLabel()

    // M15, design system 6.3: the CHECKOUT banner centred and as wide as the zone in landscape.

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi")
    fun `M15 landscape CHECKOUT banner centred and wide, clear of the other parts`() = checkLandscapeBanner(1f)

    @Test
    @Config(qualifiers = "fr-w800dp-h360dp-land-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `M15 landscape CHECKOUT banner at the largest font size`() = checkLandscapeBanner(LARGEST_EFFECTIVE)

    private fun checkLandscapeBanner(expectedScale: Float) {
        assertEquals("effective text scale", expectedScale, effectiveScale(), SCALE_TOLERANCE)
        send(
            *startMatch(*NAMES.toTypedArray()), *ANN_AT_121, *triple(20), *triple(11),
            AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(14),
        )
        val zone = visible(GameTags.CURRENT_PLAYER)
        val chip = visible(GameTags.CHECKOUT_BANNER_LABEL)
        val inner = zone.width - px(ZONE_PADDING_DP * 2)

        assertEquals("banner centre", zone.center.x, chip.center.x, px(1f))
        assertTrue("banner ${chip.width} narrower than the zone $inner", chip.width >= inner - EPSILON)
        val text = innerTextLayout(GameTags.CHECKOUT_BANNER_LABEL)
        assertEquals("banner lines", 1, text.lineCount)
        assertTrue("banner text ${text.size} wider than its chip $chip", text.size.width <= chip.width)
        // The chip takes the whole middle of the zone: from under the name row to the line above the dart slots.
        val line = visible(GameTags.CHECKOUT_LINE)
        assertEquals("gap under the chip", px(INNER_GAP_DP), line.top - chip.bottom, EPSILON)
        val middle = visible(GameTags.dartSlot(0)).top - visible(GameTags.playerName(0)).bottom
        val unused = middle - chip.height - line.height - px(INNER_GAP_DP * 3)
        assertTrue("chip ${chip.height} leaves $unused px of the middle $middle", unused <= px(NAME_ROW_SLACK_DP))
        assertApart(GameTags.CHECKOUT_BANNER_LABEL, GameTags.CHECKOUT_LINE)
        assertApart(GameTags.CHECKOUT_BANNER_LABEL, GameTags.playerName(0))
        assertApart(GameTags.CHECKOUT_LINE, GameTags.dartSlot(0))
        assertWhole(GameTags.CHECKOUT_BANNER_LABEL)
    }

    private companion object {
        const val EPSILON = 1f
        const val LARGEST_FONT_SCALE = 2.0f

        /** The effective scale of a 24 sp text at the largest font size (non-linear scaling, see GameScreenFitTest). */
        const val LARGEST_EFFECTIVE = 1.5f
        const val SCALE_TOLERANCE = 0.01f
        const val MIN_NAME_CHARACTERS = 8
        val LONG_NAMES =
            listOf("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Jean-Baptiste Pierre")
        const val BAR_PADDING_DP = 8f
        const val ZONE_PADDING_DP = 12f
        const val INNER_GAP_DP = 8f

        /** The name row may be a little higher than the name (chips); the rest of the middle is the banner's. */
        const val NAME_ROW_SLACK_DP = 8f
        const val ACCENT = 0xFFFFD23F

        val NAMES = listOf("Ann", "Bob", "Cid", "Dan")

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** From home: 301 double out, 3 legs per set and 3 sets (tiles with their match line), [names], Start. */
        fun startMatch(vararg names: String): Array<AppEvent> = buildList {
            add(AppEvent.NewGame)
            add(AppEvent.StartScoreSelected(301))
            repeat(names.size - 2) { add(AppEvent.PlayerAdded) }
            names.forEachIndexed { index, name -> add(AppEvent.NameChanged(index, name)) }
            repeat(2) { add(AppEvent.LegsPerSetChanged(1)) }
            repeat(2) { add(AppEvent.SetsChanged(1)) }
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
