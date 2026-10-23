package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.playLegWonByScoring20
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsTags
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Cricket between-legs and end screens (docs/rules/cricket.md, CR26, CR27) with four players at 360 x 640, also at
 * font scale 2.0 (review U3): every row, the PTS and MPR MATCH figures and the buttons can be reached, and the buttons
 * are at least 48 dp high.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
class CricketEndScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()

    private val names = listOf("Ann", "Bob", "Cid", "Dan")

    private fun finished(): CricketMatch = CricketSaveFixtures.match(*names.toTypedArray()).playLegWonByScoring20(2)

    private fun betweenLegs(): CricketMatch =
        CricketSaveFixtures.match(*names.toTypedArray(), legsPerSet = 3).playLegWonByScoring20(0).confirmLeg()

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String): Rect = node(tag).fetchSemanticsNode().boundsInRoot

    private fun assertReachable(tag: String, scrolls: Boolean = true) {
        if (scrolls) node(tag).performScrollTo()
        val rect = bounds(tag)
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$tag $rect inside $screen",
            rect.left >= screen.left - 1f && rect.right <= screen.right + 1f &&
                rect.top >= screen.top - 1f && rect.bottom <= screen.bottom + 1f,
        )
    }

    private fun assertButton(tag: String) {
        assertReachable(tag, scrolls = false)
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("$tag at least 48 dp", bounds(tag).height >= minimum - 1f)
    }

    private fun checkEnd() {
        show { CricketEndScreen(finished(), onEvent = { events += it }) }
        names.indices.forEach { position ->
            assertReachable(EndTags.row(position))
            assertReachable(EndTags.points(position))
            assertReachable(EndTags.average(position))
            assertReachable(EndTags.darts(position))
        }
        assertReachable(EndTags.POINTS_HEADER)
        assertButton(EndTags.REMATCH)
        assertButton(EndTags.NEW_GAME)
    }

    private fun checkBetweenLegs() {
        show { CricketBetweenLegsScreen(betweenLegs(), onEvent = { events += it }) }
        assertReachable(BetweenLegsTags.WINNER_ZONE)
        assertReachable(BetweenLegsTags.CHECKOUT)
        names.indices.forEach {
            assertReachable(BetweenLegsTags.legs(it))
            assertReachable(BetweenLegsTags.average(it))
        }
        assertReachable(BetweenLegsTags.NEXT_STARTER)
        assertButton(BetweenLegsTags.NEXT_LEG)
    }

    @Test
    fun `CR27 end with four players at 360 x 640`() = checkEnd()

    @Test
    @Config(fontScale = 2.0f)
    fun `CR27 end with four players at 360 x 640 and font scale 2`() = checkEnd()

    @Test
    fun `CR26 between legs with four players at 360 x 640`() = checkBetweenLegs()

    @Test
    @Config(fontScale = 2.0f)
    fun `CR26 between legs with four players at 360 x 640 and font scale 2`() = checkBetweenLegs()

    @Test
    fun `CR28 new game from a Cricket end screen asks for Cricket`() {
        show { CricketEndScreen(finished(), onEvent = { events += it }) }
        node(EndTags.NEW_GAME).performClick()
        node(EndTags.REMATCH).performClick()

        assertEquals(listOf(AppEvent.NewGame(GameMode.CRICKET), AppEvent.Rematch), events)
    }

    @Test
    fun `CR26 next leg sends the event`() {
        show { CricketBetweenLegsScreen(betweenLegs(), onEvent = { events += it }) }
        node(BetweenLegsTags.NEXT_LEG).performClick()

        assertEquals(listOf<AppEvent>(AppEvent.NextLegTapped), events)
    }
}
