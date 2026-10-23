package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.revealIfScrolled
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The redesigned screens (issue #49), on a Pixel 7 unless a test says otherwise: BUST and CHECKOUT zones, the action
 * bar, TalkBack texts and live regions (U4, U5, U8), plurals (U2), buttons that grow (U6), landscape tiles at 4
 * players (U3), the zone that does not jump (T5), the setup player list (M13) and short screens (U1).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RedesignScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun unmerged(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String): Rect = unmerged(tag).revealIfScrolled().fetchSemanticsNode().boundsInRoot

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        unmerged(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** BUST: the zone turns to the BUST banner, the keypad locks, both action buttons stay enabled (owner Q3). */
    @Test
    @Config(qualifiers = "+en-port")
    fun `VT13 Q3 a bust keeps both action buttons enabled and locks the keypad`() {
        send(*ANN_AT_121, *triple(20), *triple(20), *triple(20))

        node(GameTags.CARD_BUST).assertExists()
        node(KeypadTags.UNDO).assertIsEnabled()
        node(KeypadTags.NEXT_PLAYER).assertIsEnabled()
        node(KeypadTags.number(20)).assertIsNotEnabled()
        node(KeypadTags.DOUBLE).assertIsNotEnabled()
    }

    /** U5: the BUST banner is a polite live region with the text "BUST". */
    @Test
    @Config(qualifiers = "+en-port")
    fun `U5 the BUST banner is a polite live region`() {
        send(*ANN_AT_121, *triple(20), *triple(20), *triple(20))

        node(GameTags.CARD_BUST)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextEquals(string(R.string.game_bust))
    }

    /** M15, U2, U5: a one-dart checkout reads "40 IN 1 DART", announced politely; undo and confirm in the bar. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `U2 U5 a one-dart checkout in English`() {
        send(*ANN_AT_40, AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(20))

        unmerged(GameTags.CHECKOUT_LINE).assertTextEquals("40 IN 1 DART")
        node(GameTags.CHECKOUT_BANNER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertContentDescriptionEquals("Checkout, 40 in 1 dart")
        node(GameTags.CONFIRM).assertTextEquals("CONFIRM MATCH")
        node(KeypadTags.UNDO).assertIsEnabled().performClick()
        node(GameTags.CHECKOUT_BANNER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `U2 a one-dart checkout in French`() {
        send(*ANN_AT_40, AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(20))

        unmerged(GameTags.CHECKOUT_LINE).assertTextEquals("40 EN 1 FLÉCHETTE")
    }

    /** U4: the first tile is the next player, and TalkBack says so. The others are read as they are. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `U4 the first tile is read as the next player`() {
        send(*start("Ann", "Bob", "Cid"))

        unmerged(GameTags.playerName(1)).assertContentDescriptionEquals("Next player: Bob, 301 points left")
        assertEquals(
            null,
            unmerged(GameTags.playerName(2)).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription),
        )
    }

    /** U8, owner X1: the active Double shows a check mark and is selected; Triple is not; Triple then replaces it. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `U8 the active multiplier has a check mark and is selected`() {
        send(*start("Ann", "Bob"), AppEvent.MultiplierTapped(Multiplier.DOUBLE))

        node(KeypadTags.DOUBLE).assertIsSelected().assertContentDescriptionEquals("DOUBLE")
        assertTrue(textOf(KeypadTags.DOUBLE).startsWith(string(R.string.key_selected_mark)))
        node(KeypadTags.TRIPLE).assertIsNotSelected()
        assertEquals("TRIPLE", textOf(KeypadTags.TRIPLE))

        send(AppEvent.MultiplierTapped(Multiplier.TRIPLE))
        node(KeypadTags.DOUBLE).assertIsNotSelected()
        node(KeypadTags.TRIPLE).assertIsSelected()
    }

    private fun textOf(tag: String): String =
        node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    /** U6: at the largest font size, the French confirm button wraps and grows instead of being cut. */
    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun `U6 the confirm button grows at the largest font size`() {
        send(*ANN_AT_40, AppEvent.MultiplierTapped(Multiplier.DOUBLE), AppEvent.NumberTapped(20))

        val button = bounds(GameTags.CONFIRM)
        assertTrue("button $button", button.height >= with(composeRule.density) { MIN_TOUCH_DP.toPx() })
        val label = composeRule.onNode(
            hasAnyAncestor(hasTestTag(GameTags.CONFIRM))
                .and(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals("never cut", layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue("not cut vertically", !layout.didOverflowHeight)
    }

    /** U3, T5, owner Q9: landscape with 4 long names: scores whole, names with at least 8 characters before "…". */
    @Test
    @Config(qualifiers = "+en-land", fontScale = LARGEST_FONT_SCALE)
    fun `U3 landscape 4 players with long names at the largest font size`() = checkLandscapeTiles()

    @Test
    @Config(qualifiers = "+en-land")
    fun `U3 landscape 4 players with long names`() = checkLandscapeTiles()

    private fun checkLandscapeTiles() {
        send(*start("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Jean-Baptiste Pierre"))
        (1..3).forEach { player ->
            val score = textLayout(GameTags.remaining(player))
            assertEquals("score $player whole", score.layoutInput.text.length, score.getLineEnd(0))
            assertTrue("score $player inside", score.getLineRight(0) <= score.size.width + EPSILON)
            val name = textLayout(GameTags.playerName(player))
            val shown = name.getLineEnd(name.lineCount - 1, visibleEnd = true)
            assertTrue("name $player shows $shown characters", shown >= MIN_NAME_CHARACTERS)
            val tile = bounds(GameTags.tile(player))
            val box = bounds(GameTags.visitTotal(player))
            assertTrue("box $player inside its tile", box.left >= tile.left && box.right <= tile.right + EPSILON)
        }
    }

    /** T5, CS8: on a 360 × 640 phone, the zone and the dart slots do not move when a route appears. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun `T5 the zone does not move with a route on a small phone`() {
        send(*start("Ann"))
        val zone = bounds(GameTags.CURRENT_PLAYER)
        val slot = bounds(GameTags.dartSlot(0))
        send(*triple(20), *triple(20), AppEvent.NumberTapped(11), AppEvent.NextPlayerTapped)
        unmerged(GameTags.CHECKOUT_SUGGESTION).assertExists()

        assertEquals(zone, bounds(GameTags.CURRENT_PLAYER))
        assertEquals(slot, bounds(GameTags.dartSlot(0)))
    }

    /** M13, U7: the remove cross on each row (48 dp, "Remove Ann" or "Remove Player 2"), hidden with one player. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `M13 the remove cross and add player`() {
        send(AppEvent.NewGame(GameMode.X01), AppEvent.NameChanged(0, "Ann"))

        node(SetupTags.remove(0)).performScrollTo().assertContentDescriptionEquals("Remove Ann")
        node(SetupTags.remove(1)).assertContentDescriptionEquals("Remove Player 2")
        val cross = bounds(SetupTags.remove(0))
        val minimum = with(composeRule.density) { MIN_TOUCH_DP.toPx() }
        assertTrue("cross $cross", cross.width >= minimum - EPSILON && cross.height >= minimum - EPSILON)

        node(SetupTags.remove(0)).performClick()
        unmerged(SetupTags.nameField(0)).assert(hasText(""))
        composeRule.onAllNodesWithTag(SetupTags.remove(0)).assertCountEquals(0)

        send(AppEvent.PlayerAdded, AppEvent.PlayerAdded, AppEvent.PlayerAdded)
        composeRule.onAllNodesWithTag(SetupTags.ADD_PLAYER).assertCountEquals(0)
        node(SetupTags.remove(3)).performScrollTo().assertExists()
    }

    /** U1: on short screens every key and action button is on screen or reachable by scrolling. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U1 small portrait at the largest font size`() = checkReachable()

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun `U1 small portrait`() = checkReachable()

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U1 small landscape at the largest font size`() = checkReachable()

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun `U1 small landscape`() = checkReachable()

    private fun checkReachable() {
        send(*start("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Jean-Baptiste Pierre"))
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val tags = Dart.NUMBERS.map { KeypadTags.number(it) } + listOf(
            KeypadTags.DOUBLE, KeypadTags.TRIPLE, KeypadTags.OUTER_BULL, KeypadTags.BULL, KeypadTags.MISS,
            KeypadTags.UNDO, KeypadTags.NEXT_PLAYER, GameTags.CURRENT_REMAINING, GameTags.dartSlot(2),
        )
        tags.forEach { tag ->
            val rect = bounds(tag)
            assertTrue("$tag $rect above $root", rect.top >= root.top - EPSILON)
            assertTrue("$tag $rect below $root", rect.bottom <= root.bottom + EPSILON)
            assertTrue("$tag is empty", rect.height > 0f && rect.width > 0f)
        }
        // U10: the action bar is pinned, so it is whole on screen even when the rest is scrolled back to the top.
        node(GameTags.CURRENT_PLAYER).revealIfScrolled()
        listOf(KeypadTags.UNDO, KeypadTags.NEXT_PLAYER).forEach { tag ->
            val rect = unmerged(tag).fetchSemanticsNode().boundsInRoot
            val minimum = with(composeRule.density) { MIN_TOUCH_DP.toPx() }
            assertTrue("$tag $rect too low", rect.height >= minimum - EPSILON)
            assertTrue("$tag $rect below $root", rect.bottom <= root.bottom + EPSILON)
        }
    }

    /** U7: the setup content scrolls and "Start" stays pinned at the bottom, with 4 players at the largest size. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi", fontScale = LARGEST_FONT_SCALE)
    fun `U7 start stays visible under the focused field`() {
        send(AppEvent.NewGame(GameMode.X01), AppEvent.PlayerAdded, AppEvent.PlayerAdded)
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        node(SetupTags.nameField(3)).performScrollTo().performClick()
        composeRule.waitForIdle()

        val start = bounds(SetupTags.START)
        val field = bounds(SetupTags.nameField(3))
        assertTrue("start $start in $root", start.bottom <= root.bottom + EPSILON && start.top >= root.top)
        assertTrue("field $field above start $start", field.bottom <= start.top + EPSILON)
        assertTrue("field $field on screen", field.top >= root.top - EPSILON)
    }

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f
        const val EPSILON = 0.5f
        const val MIN_NAME_CHARACTERS = 8
        val MIN_TOUCH_DP = 48.dp

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** From home: 301 double out with [names] (one to four), then Start. */
        fun start(vararg names: String): Array<AppEvent> = buildList {
            add(AppEvent.NewGame(GameMode.X01))
            add(AppEvent.StartScoreSelected(301))
            repeat(names.size - 2) { add(AppEvent.PlayerAdded) }
            if (names.size == 1) add(AppEvent.PlayerRemoved(1))
            names.forEachIndexed { index, name -> add(AppEvent.NameChanged(index, name)) }
            add(AppEvent.StartRequested)
        }.toTypedArray()

        /** Ann and Bob, 301 double out: Ann leaves 121, Bob scores 3; Ann throws next. */
        val ANN_AT_121: Array<AppEvent> = arrayOf(
            *start("Ann", "Bob"),
            *triple(20), *triple(20), *triple(20), AppEvent.NextPlayerTapped,
            AppEvent.NumberTapped(1), AppEvent.NumberTapped(1), AppEvent.NumberTapped(1), AppEvent.NextPlayerTapped,
        )

        /** Then Ann leaves 40 (T20 T7), Bob scores 3 again; Ann throws next, 40 left. */
        val ANN_AT_40: Array<AppEvent> = arrayOf(
            *ANN_AT_121,
            *triple(20), *triple(7), AppEvent.MissTapped, AppEvent.NextPlayerTapped,
            AppEvent.NumberTapped(1), AppEvent.NumberTapped(1), AppEvent.NumberTapped(1), AppEvent.NextPlayerTapped,
        )
    }
}
