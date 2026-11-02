package io.github.darkcosmos.kosmodarts.ui.resume

import io.github.darkcosmos.kosmodarts.testing.savedGame
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.revealIfScrolled
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * VT7: the visit total boxes show exactly the same values after a rotation, after process death, and after resuming
 * from the home screen. Each test asserts the explicit values of every box before and after, and that there is one
 * box per player.
 */
@RunWith(AndroidJUnit4::class)
class VisitTotalResumeTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    @After
    fun tearDown() = process.close()

    /** Expected content of one box: its text and its TalkBack description. */
    private data class Box(val text: String, val description: String)

    private fun inProgress(points: Int) =
        Box("$points", process.string(R.string.game_visit_in_progress_description, points))

    private fun lastVisit(points: Int) = Box("$points", process.string(R.string.game_visit_last_description, points))

    private fun notPlayed() = Box("0", process.string(R.string.game_visit_not_played_description))

    private fun bust() =
        Box(process.string(R.string.game_bust), process.string(R.string.game_visit_last_bust_description))

    /** VT13 (as amended by issue #49): the zone box of a waiting bust shows 0, read "Last turn: bust". */
    private fun pendingBust() = Box("0", process.string(R.string.game_visit_last_bust_description))

    /** One entry per player; `null` means no box. */
    private fun assertBoxes(expected: List<Box?>) {
        composeRule.waitForIdle()
        expected.forEachIndexed { player, box ->
            val node = composeRule.onNodeWithTag(GameTags.visitTotal(player))
            if (box == null) {
                node.assertDoesNotExist()
            } else {
                node.revealIfScrolled().assertTextEquals(box.text).assertContentDescriptionEquals(box.description)
            }
        }
        composeRule.onNodeWithTag(GameTags.visitTotal(expected.size)).assertDoesNotExist()
    }

    private fun resume(saved: SavedGame) {
        process.writeGame(saved)
        process.launchFresh()
        composeRule.onNodeWithTag(HomeTags.RESUME).performScrollTo().performClick()
    }

    private fun darts(vararg codes: Int): List<Dart> = codes.map { Dart.single(it) }

    private val threePlayers = X01Config(listOf("Ann", "Bob", "Cid"))

    /** Ann 50, Bob 30, Cid 3, then Ann's 20 and 15: a turn in progress. */
    private val midTurn = savedGame(
        X01Game(threePlayers, darts(20, 15, 15, 10, 10, 10, 1, 1, 1, 20, 15)),
        isAwaitingNextPlayer = false,
    )

    @Test
    fun `VT7 rotating in the middle of a turn`() {
        resume(midTurn)
        val expected = listOf(inProgress(35), lastVisit(30), lastVisit(3))
        assertBoxes(expected)

        process.rotate()

        assertBoxes(expected)
    }

    @Test
    fun `VT7 rotating during a waiting bust`() {
        // 301: Ann leaves 121, Bob scores 3, then Ann's T20 T20 leaves 1, a bust in double out.
        val config = X01Config(listOf("Ann", "Bob"), startScore = 301)
        val t20 = Dart.triple(20)
        resume(savedGame(X01Game(config, listOf(t20, t20, t20) + darts(1, 1, 1) + listOf(t20, t20)), true))
        val expected = listOf(pendingBust(), lastVisit(3))
        assertBoxes(expected)
        composeRule.onNodeWithTag(GameTags.CARD_BUST).revealIfScrolled().assertIsDisplayed()

        process.rotate()

        assertBoxes(expected)
        composeRule.onNodeWithTag(GameTags.CARD_BUST).revealIfScrolled().assertIsDisplayed()
        composeRule.onNodeWithTag(KeypadTags.NEXT_PLAYER).revealIfScrolled().performClick()
        assertBoxes(listOf(bust(), inProgress(0)))
    }

    /** VT6 (Q3): Bob is not shown as throwing while Ann's turn waits, also after process death. */
    @Test
    fun `VT7 process death during a waiting turn`() {
        resume(savedGame(X01Game(threePlayers, darts(20, 15, 15)), isAwaitingNextPlayer = true))
        val expected = listOf(lastVisit(50), notPlayed(), notPlayed())
        assertBoxes(expected)

        process.killInBackgroundAndReturn()

        assertBoxes(expected)
    }

    /** VT6 (Q3), second round: Bob keeps his last visit while Ann's turn waits, also after process death. */
    @Test
    fun `VT7 process death during a waiting turn in a later round`() {
        val config = X01Config(listOf("Ann", "Bob"))
        resume(savedGame(X01Game(config, darts(20, 15, 15, 20, 20, 5, 1, 1, 1)), isAwaitingNextPlayer = true))
        val expected = listOf(lastVisit(3), lastVisit(45))
        assertBoxes(expected)

        process.killInBackgroundAndReturn()

        assertBoxes(expected)
    }

    @Test
    fun `VT7 a fresh launch and Resume from home`() {
        resume(midTurn)
        val expected = listOf(inProgress(35), lastVisit(30), lastVisit(3))
        assertBoxes(expected)

        process.finish()
        process.launchFresh()
        composeRule.onNodeWithTag(HomeTags.RESUME).performScrollTo().performClick()

        assertBoxes(expected)
    }
}
