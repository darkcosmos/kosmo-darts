package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.lifecycle.ViewModelProvider
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import io.github.darkcosmos.kosmodarts.ui.components.breaksOnlyBetweenWords
import io.github.darkcosmos.kosmodarts.ui.components.lineEnds
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Issue #70, VT11 (as amended), U3, U4, U11: the name on the tile of another player is never broken inside a word. It
 * shrinks from the user's size down to its size at font scale 1.0, never below, then ends with "…" on one line. The
 * tests check the final state, after the screen is idle. Parameter names are ASCII only (an accent breaks the HTML
 * report of Gradle).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TileNameFitTest(private val case: NameCase) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun nameLayout(player: Int): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(GameTags.playerName(player), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun shownText(player: Int): String {
        val node = composeRule.onNodeWithTag(GameTags.playerName(player), useUnmergedTree = true).fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    private fun check(isLandscape: Boolean, fontScale: Float) {
        val script = MatchScript().start(
            *case.names.toTypedArray(),
            legsPerSet = if (case.isMatch) 3 else 1,
            sets = if (case.isMatch) 3 else 1,
        )
        send(*script.events.toTypedArray())
        val density: Density = composeRule.density
        val oneZeroPx = STYLE_SP * density.density
        val stylePx = with(density) { STYLE_SP.sp.toPx() }
        assertEquals("font scale", fontScale, density.fontScale, SCALE_TOLERANCE)

        case.names.drop(1).forEachIndexed { offset, name ->
            val player = offset + 1
            val layout = nameLayout(player)
            val text = layout.layoutInput.text.text
            val label = "$case ${if (isLandscape) "land" else "port"} x$fontScale player $player \"$text\""
            assertEquals("$label semantics text", displayName(name, Locale.FRENCH), shownText(player))
            assertTrue("$label breaks inside a word: ${layout.lineEnds()}", breaksOnlyBetweenWords(text, layout.lineEnds()))
            assertTrue("$label overflows its width", !layout.didOverflowWidth)
            val maxLines = if (name.trim().contains(' ')) 2 else 1
            assertTrue("$label on ${layout.lineCount} lines, at most $maxLines", layout.lineCount <= maxLines)
            assertEquals("$label left aligned", 0f, layout.getLineLeft(0), EPSILON)

            val sizePx = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
            assertTrue("$label size $sizePx below its 1.0 size $oneZeroPx", sizePx >= oneZeroPx - EPSILON)
            assertTrue("$label size $sizePx above the style size $stylePx", sizePx <= stylePx + EPSILON)
            if (layout.isLineEllipsized(layout.lineCount - 1)) {
                assertEquals("$label ends with \"…\" before shrinking to its 1.0 size", oneZeroPx, sizePx, EPSILON)
            }
            if (name == "Cid") {
                assertEquals("$label stays on one line", 1, layout.lineCount)
                assertEquals("$label stays at the full size", stylePx, sizePx, EPSILON)
            }
        }
    }

    /** U4: the next player's tile keeps its full name for TalkBack, even when the name ends with "…". */
    private fun checkNextPlayerDescription() {
        val name = case.names[1]
        val remaining = START_SCORE
        val expected = composeRule.activity.resources
            .getQuantityString(R.plurals.next_player_tile_description, remaining, name, remaining)
        composeRule.onNodeWithTag(GameTags.playerName(1), useUnmergedTree = true)
            .assertContentDescriptionEquals(expected)
    }

    private fun run(isLandscape: Boolean, fontScale: Float) {
        check(isLandscape, fontScale)
        checkNextPlayerDescription()
        val ends = nameLayout(1).let { it.isLineEllipsized(it.lineCount - 1) }
        if (isLandscape && fontScale == LARGEST && case == BARTHOLOMEW_MATCH) {
            assertTrue("BARTHOLOMEW ends with an ellipsis in landscape at 2.0 in a match", ends)
        }
    }

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.0f)
    fun `VT11 U3 landscape scale 1 point 0`() = run(true, 1.0f)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.3f)
    fun `VT11 U3 landscape scale 1 point 3`() = run(true, 1.3f)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.5f)
    fun `VT11 U3 landscape scale 1 point 5`() = run(true, 1.5f)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST)
    fun `VT11 U3 landscape scale 2 point 0`() = run(true, LARGEST)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 1.0f)
    fun `VT11 U3 portrait scale 1 point 0`() = run(false, 1.0f)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 1.3f)
    fun `VT11 U3 portrait scale 1 point 3`() = run(false, 1.3f)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 1.5f)
    fun `VT11 U3 portrait scale 1 point 5`() = run(false, 1.5f)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST)
    fun `VT11 U3 portrait scale 2 point 0`() = run(false, LARGEST)

    /** A name case: [names] are the players (the first is the current one), in a sets match or a single game. */
    class NameCase(val label: String, val names: List<String>, val isMatch: Boolean) {
        override fun toString(): String = label
        override fun equals(other: Any?): Boolean = other is NameCase && other.label == label
        override fun hashCode(): Int = label.hashCode()
    }

    companion object {
        private const val LARGEST = 2.0f
        private const val STYLE_SP = 18f
        private const val START_SCORE = 301
        private const val SCALE_TOLERANCE = 0.01f
        private const val EPSILON = 0.5f

        private val FOUR_PLAYERS = listOf(
            "bob_cid_dan" to listOf("Ann", "Bob", "Cid", "Dan"),
            "mim_kevin_kosmo" to listOf("Ann", "Mim", "Kevin", "Kosmo"),
            "julien_maxime_jeanpaul" to listOf("Ann", "Julien", "Maxime", "Jean Paul"),
            "bartholomew_two_words" to listOf("Ann", "Bartholomew", "Maxime Dupont", "Bartholomew Jonathan"),
        )

        val BARTHOLOMEW_MATCH = NameCase("2p_match_bartholomew", listOf("Ann", "Bartholomew"), isMatch = true)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            FOUR_PLAYERS.forEach { (label, names) ->
                add(NameCase("4p_match_$label", names, isMatch = true))
                add(NameCase("4p_single_$label", names, isMatch = false))
            }
            val three = listOf("Ann", "Kosmo", "Bartholomew")
            add(NameCase("3p_match_kosmo_bartholomew", three, isMatch = true))
            add(NameCase("3p_single_kosmo_bartholomew", three, isMatch = false))
            add(BARTHOLOMEW_MATCH)
        }.map { arrayOf<Any>(it) }
    }
}
