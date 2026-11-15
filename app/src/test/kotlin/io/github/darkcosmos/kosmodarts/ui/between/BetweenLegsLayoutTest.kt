package io.github.darkcosmos.kosmodarts.ui.between

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M16, the owner's review of PR #57: in landscape, the scoreboard stands right of the leg (or set) winner's zone, and
 * "NEXT LEG" right under the scoreboard; in portrait the screen keeps its single column (mockup 08).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BetweenLegsLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun showBetweenLegs(vararg names: String) {
        val script = MatchScript().start(*names, legsPerSet = 3, sets = 2).playLegWonBy(1).finish()
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { script.events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun screen(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun checkLandscape(vararg names: String) {
        showBetweenLegs(*names)
        // Owner decision U3: "NEXT LEG" is pinned at the bottom, displayed without scrolling, even with 4 players.
        val winner = bounds(BetweenLegsTags.WINNER_ZONE)
        val board = bounds(BetweenLegsTags.SCOREBOARD)
        val next = bounds(BetweenLegsTags.NEXT_LEG)

        assertTrue("scoreboard $board not right of the winner $winner", board.left >= winner.right)
        assertEquals("scoreboard and winner tops", winner.top, board.top, EPSILON)
        assertTrue("next leg $next not under the winner top ${winner.top}", next.top > winner.top)
        assertTrue("next leg $next off screen", next.bottom <= screen().bottom + EPSILON && next.height > 0f)
    }

    @Test
    @Config(qualifiers = TestDevices.FR_PIXEL7_LANDSCAPE_INSIDE_BARS)
    fun `M16 landscape 800 x 360, scoreboard right of the winner, next leg pinned at the bottom, 2 players`() =
        checkLandscape("Ann", "Bob")

    @Test
    @Config(qualifiers = TestDevices.FR_PIXEL7_LANDSCAPE_INSIDE_BARS)
    fun `M16 landscape 800 x 360, scoreboard right of the winner, next leg pinned at the bottom, 4 players`() =
        checkLandscape("Ann", "Bob", "Cid", "Dan")

    @Test
    @Config(qualifiers = TestDevices.FR_PIXEL7_PORTRAIT)
    fun `M16 portrait keeps the scoreboard under the winner`() {
        showBetweenLegs("Ann", "Bob")
        val winner = bounds(BetweenLegsTags.WINNER_ZONE)
        val board = bounds(BetweenLegsTags.SCOREBOARD)
        assertTrue("scoreboard $board not under the winner $winner", board.top >= winner.bottom)
    }

    private companion object {
        const val EPSILON = 1f
    }
}
