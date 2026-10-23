package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
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
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.components.breaksOnlyBetweenWords
import io.github.darkcosmos.kosmodarts.ui.components.lineEnds
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.revealIfScrolled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game screen on real sizes (issue #49, code review U10 to U12, T8, T9), without any scrolling: the action bar is
 * pinned (U10), the CHECKOUT zone gives its unused height back, touch targets stay at 48 dp, labels never break inside
 * a word, the header takes at most two lines, and narrow tiles keep 8 name characters.
 *
 * Font sizes: Compose enlarges large text less than the setting (non-linear scaling, on every Android version it runs
 * on), so the largest setting, 2.0, gives an effective text scale of 1.5 for a 24 sp text, above the 1.42 of the
 * acceptance criterion; [effectiveScale] checks it. A linear 2.0 scale does not happen in a Compose app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameScreenFitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun root(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    /** Bounds as drawn now, without scrolling: a node below the fold of a scrolling column is clipped to nothing. */
    private fun visibleBounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun effectiveScale(): Float = with(composeRule.density) { 24.sp.toDp().value } / 24f

    private fun labelLayout(tag: String): TextLayoutResult {
        val node = composeRule.onNode(
            hasAnyAncestor(hasTestTag(tag)).and(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** U10: [tags] are whole and inside the screen as drawn, with no scrolling at all. */
    private fun assertOnScreen(vararg tags: String) {
        val screen = root()
        tags.forEach { tag ->
            val rect = visibleBounds(tag)
            assertTrue("$tag is hidden: $rect", rect.height > px(MIN_TOUCH_DP) - EPSILON && rect.width > 0f)
            assertTrue("$tag $rect above $screen", rect.top >= screen.top - EPSILON)
            assertTrue("$tag $rect below $screen", rect.bottom <= screen.bottom + EPSILON)
        }
    }

    private fun checkActionBar(playerCount: Int, expectedScale: Float) {
        assertEquals("effective text scale", expectedScale, effectiveScale(), SCALE_TOLERANCE)
        send(*start(*LONG_NAMES.take(playerCount).toTypedArray()))
        assertOnScreen(KeypadTags.UNDO, KeypadTags.NEXT_PLAYER)
        send(*triple(20), AppEvent.NumberTapped(1))
        assertOnScreen(KeypadTags.UNDO, KeypadTags.NEXT_PLAYER)
    }

    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi")
    fun `U10 412 x 915 portrait, 2 players, scale 1`() = checkActionBar(2, 1f)

    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi")
    fun `U10 412 x 915 portrait, 4 players, scale 1`() = checkActionBar(4, 1f)

    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 412 x 915 portrait, 2 players, largest font size`() = checkActionBar(2, LARGEST_EFFECTIVE)

    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 412 x 915 portrait, 4 players, largest font size`() = checkActionBar(4, LARGEST_EFFECTIVE)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
    fun `U10 360 x 640 portrait, 4 players, scale 1`() = checkActionBar(4, 1f)

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 360 x 640 portrait, 4 players, largest font size`() = checkActionBar(4, LARGEST_EFFECTIVE)

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `U10 915 x 412 landscape, 4 players, scale 1`() = checkActionBar(4, 1f)

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 915 x 412 landscape, 4 players, largest font size`() = checkActionBar(4, LARGEST_EFFECTIVE)

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 640 x 360 landscape, 4 players, largest font size`() = checkActionBar(4, LARGEST_EFFECTIVE)

    /**
     * U10 (3): with a checkout waiting at the largest font size, 4 players on 412 × 915, the zone is lower than with a
     * score, and the whole keypad and the action bar fit without scrolling; the confirm button is on screen.
     */
    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 checkout gives the zone height back, 412 x 915, 4 players, largest font size`() {
        send(*start(*LONG_NAMES.toTypedArray()), *ANN_AT_121)
        val zoneWithScore = visibleBounds(GameTags.CURRENT_PLAYER)
        send(*triple(20), *triple(11), AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(14))
        composeRule.onNodeWithTag(GameTags.CHECKOUT_BANNER, useUnmergedTree = true).assertExists()

        val zone = visibleBounds(GameTags.CURRENT_PLAYER)
        assertTrue("zone $zone not lower than $zoneWithScore", zone.height < zoneWithScore.height)
        assertOnScreen(KeypadTags.UNDO, GameTags.CONFIRM, KeypadTags.MISS, KeypadTags.DOUBLE, KeypadTags.number(1))
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U10 checkout confirm on screen, 360 x 640, 4 players, largest font size`() {
        send(*start(*LONG_NAMES.toTypedArray()), *ANN_AT_121)
        send(*triple(20), *triple(11), AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(14))

        assertOnScreen(KeypadTags.UNDO, GameTags.CONFIRM)
    }

    /** T9: every key and both action buttons are drawn at least 48 dp high on a small phone. */
    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
    fun `T9 touch targets at 48 dp, 360 x 640, scale 1`() = checkTouchTargets()

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `T9 touch targets at 48 dp, 360 x 640, largest font size`() = checkTouchTargets()

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `T9 touch targets at 48 dp, 640 x 360, largest font size`() = checkTouchTargets()

    private fun checkTouchTargets() {
        send(*start(*LONG_NAMES.toTypedArray()))
        (KEY_TAGS + listOf(KeypadTags.UNDO, KeypadTags.NEXT_PLAYER)).forEach { tag ->
            val rect = composeRule.onNodeWithTag(tag).revealIfScrolled().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $rect", rect.height >= px(MIN_TOUCH_DP) - EPSILON)
        }
    }

    /** U11: action and Double / Triple labels are whole and only wrap at a space, the active "✓ DOUBLE" included. */
    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U11 labels never break inside a word, 640 x 360, largest font size`() = checkLabels()

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U11 labels never break inside a word, 360 x 640, largest font size`() = checkLabels()

    private fun checkLabels() {
        send(*start(*LONG_NAMES.toTypedArray()))
        val tags = listOf(KeypadTags.UNDO, KeypadTags.NEXT_PLAYER, KeypadTags.DOUBLE, KeypadTags.TRIPLE)
        tags.forEach(::assertWholeWords)
        send(AppEvent.MultiplierTapped(Multiplier.DOUBLE))
        assertWholeWords(KeypadTags.DOUBLE)
    }

    private fun assertWholeWords(tag: String) {
        val layout = labelLayout(tag)
        val text = layout.layoutInput.text.text
        assertEquals("$tag \"$text\" is cut", text.length, layout.getLineEnd(layout.lineCount - 1))
        assertFalse("$tag \"$text\" overflows", layout.didOverflowHeight || layout.didOverflowWidth)
        assertTrue("$tag \"$text\" breaks inside a word", breaksOnlyBetweenWords(text, layout.lineEnds()))
    }

    /** U12: in a match on 360 dp at the largest font size, the header takes at most two lines. */
    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U12 header on two lines at most, 360 x 640, largest font size`() {
        send(
            AppEvent.NewGame, AppEvent.StartScoreSelected(501), AppEvent.CheckoutRuleSelected(CheckoutRule.SINGLE_OUT),
            AppEvent.SetsChanged(1), AppEvent.NameChanged(0, "Ann"), AppEvent.NameChanged(1, "Bob"),
            AppEvent.StartRequested,
        )
        val mode = textLayout(GameTags.HEADER_MODE)
        val position = textLayout(GameTags.HEADER_POSITION)
        val sameLine = visibleBounds(GameTags.HEADER_POSITION).top < visibleBounds(GameTags.HEADER_MODE).bottom
        val lines = if (sameLine) maxOf(mode.lineCount, position.lineCount) else mode.lineCount + position.lineCount
        assertTrue("header on $lines lines", lines <= 2)
    }

    /** T8: the narrow portrait tiles (4 players) show at least 8 characters of a long name at the largest size. */
    @Test
    @Config(qualifiers = "fr-w412dp-h915dp-port-xxhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `T8 narrow tiles keep 8 name characters at the largest font size`() {
        send(*start(*LONG_NAMES.toTypedArray()))
        (1..3).forEach { player ->
            val name = textLayout(GameTags.playerName(player))
            val shown = name.getLineEnd(name.lineCount - 1, visibleEnd = true)
            assertTrue("name $player shows $shown characters", shown >= MIN_NAME_CHARACTERS)
        }
    }

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f

        /** The effective scale of a 24 sp text at the largest font size (non-linear scaling). */
        const val LARGEST_EFFECTIVE = 1.5f
        const val SCALE_TOLERANCE = 0.01f
        const val EPSILON = 0.5f
        const val MIN_TOUCH_DP = 48f
        const val MIN_NAME_CHARACTERS = 8

        val LONG_NAMES =
            listOf("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Jean-Baptiste Pierre")

        val KEY_TAGS: List<String> = Dart.NUMBERS.map { KeypadTags.number(it) } + listOf(
            KeypadTags.DOUBLE, KeypadTags.TRIPLE, KeypadTags.OUTER_BULL, KeypadTags.BULL, KeypadTags.MISS,
        )

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** From home: 301 double out with [names] (two to four), then Start. */
        fun start(vararg names: String): Array<AppEvent> = buildList {
            add(AppEvent.NewGame)
            add(AppEvent.StartScoreSelected(301))
            repeat(names.size - 2) { add(AppEvent.PlayerAdded) }
            names.forEachIndexed { index, name -> add(AppEvent.NameChanged(index, name)) }
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
