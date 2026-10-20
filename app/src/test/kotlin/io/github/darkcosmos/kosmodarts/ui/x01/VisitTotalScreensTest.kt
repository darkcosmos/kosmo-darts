package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The visit total box of each player on the game screen (docs/rules/visit-total.md), in English, launched through
 * [MainActivity] and driven with the keypad like on a phone.
 */
@RunWith(AndroidJUnit4::class)
class VisitTotalScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun clickInScroll(textId: Int) {
        composeRule.onNodeWithText(string(textId)).performScrollTo().performClick()
    }

    private fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun tap(vararg tags: String) = tags.forEach { key(it).performClick() }

    private fun single(vararg numbers: Int) = numbers.forEach { tap(KeypadTags.number(it)) }

    private fun triple(number: Int) = tap(KeypadTags.TRIPLE, KeypadTags.number(number))

    private fun miss() = tap(KeypadTags.MISS)

    private fun undo() = tap(KeypadTags.UNDO)

    private fun nextPlayer() = tap(KeypadTags.NEXT_PLAYER)

    /** From home: 301, the given names, and the given checkout rule. */
    private fun startGame(vararg names: String, singleOut: Boolean = false) {
        clickInScroll(R.string.home_new_game)
        composeRule.onNodeWithText("301").performClick()
        if (singleOut) clickInScroll(R.string.setup_single_out)
        repeat(names.size - 2) {
            composeRule.onNodeWithContentDescription(string(R.string.setup_add_player)).performClick()
        }
        names.forEachIndexed { index, name ->
            composeRule.onNodeWithTag(SetupTags.nameField(index)).performScrollTo().performTextReplacement(name)
        }
        clickInScroll(R.string.setup_start)
    }

    private fun assertBox(player: Int, text: String, description: String) {
        key(GameTags.visitTotal(player)).performScrollTo()
            .assertTextEquals(text)
            .assertContentDescriptionEquals(description)
    }

    private fun assertInProgress(player: Int, points: Int) =
        assertBox(player, "$points", string(R.string.game_visit_in_progress_description, points))

    private fun assertLastVisit(player: Int, points: Int) =
        assertBox(player, "$points", string(R.string.game_visit_last_description, points))

    private fun assertNotPlayed(player: Int) =
        assertBox(player, "0", string(R.string.game_visit_not_played_description))

    private fun assertBust(player: Int) =
        assertBox(player, string(R.string.game_bust), string(R.string.game_visit_last_bust_description))

    /** VT1 to VT8: the phone scenario of the issue, three players, 301 double out. */
    @Test
    fun `VT2 to VT8 the issue scenario with three players`() {
        startGame("Ann", "Bob", "Cid")
        assertInProgress(0, 0)
        assertNotPlayed(1)
        assertNotPlayed(2)

        single(20)
        assertInProgress(0, 20)
        single(15)
        assertInProgress(0, 35)
        single(15)
        // VT6: Ann's turn waits for "Next player": Bob is not shown as throwing yet.
        assertLastVisit(0, 50)
        assertNotPlayed(1)

        nextPlayer()
        assertLastVisit(0, 50)
        assertInProgress(1, 0)
        assertNotPlayed(2)

        triple(20)
        triple(20)
        triple(20)
        assertLastVisit(1, 180)
        nextPlayer()

        // VT1: a miss counts 0.
        miss()
        assertInProgress(2, 0)
        miss()
        miss()
        assertLastVisit(2, 0)
        nextPlayer()
        single(1, 1, 1)
        nextPlayer()

        // VT4: Bob has 121; T20 T20 leaves 1, a bust in double out. His remaining does not change.
        triple(20)
        triple(20)
        assertBust(1)
        key(GameTags.CURRENT_REMAINING).assertTextEquals("121")
        nextPlayer()
        assertBust(1)
        miss()
        assertInProgress(2, 0)

        // VT7: undo the miss, then the bust dart: Bob's turn reopens with his running total.
        undo()
        assertInProgress(2, 0)
        assertBust(1)
        undo()
        assertInProgress(1, 60)
        assertLastVisit(2, 0)
        assertLastVisit(0, 3)
    }

    /** VT6, VT7: undo of a waiting turn reopens it, and the next player is back to "not played". */
    @Test
    fun `VT6 VT7 undo of a waiting turn`() {
        startGame("Ann", "Bob", "Cid")
        single(20, 15, 15)
        assertLastVisit(0, 50)

        undo()

        assertInProgress(0, 35)
        assertNotPlayed(1)
        assertNotPlayed(2)
    }

    /** VT6 (Q3): in a later round, the next player keeps his last visit until "Next player" is tapped. */
    @Test
    fun `VT6 the next player keeps his last visit while a turn waits`() {
        startGame("Ann", "Bob")
        single(20, 15, 15)
        nextPlayer()
        single(20, 20, 5)
        nextPlayer()
        single(1, 1, 1)

        assertLastVisit(0, 3)
        assertLastVisit(1, 45)
        nextPlayer()
        assertInProgress(1, 0)
    }

    /** VT3, VT2, VT5: the winning turn shows its total; after a rematch every box is back to 0. */
    @Test
    fun `VT3 the winning total then a rematch starts from 0`() {
        startGame("Ann", "Bob", singleOut = true)
        triple(20)
        triple(20)
        triple(20)
        nextPlayer()
        single(20, 20, 20)
        nextPlayer()
        triple(20)
        triple(20)
        single(1)
        assertLastVisit(0, 121)
        assertLastVisit(1, 60)

        composeRule.onNodeWithText(string(R.string.checkout_finish)).performClick()
        clickInScroll(R.string.end_rematch)

        // The rematch rotates the order: Bob throws first.
        assertInProgress(0, 0)
        assertNotPlayed(1)
    }
}
