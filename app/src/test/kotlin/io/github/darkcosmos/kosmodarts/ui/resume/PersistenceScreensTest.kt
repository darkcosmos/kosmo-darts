package io.github.darkcosmos.kosmodarts.ui.resume

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.app.TurnCompletedHook
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The game survives process death, force stop and reboot (docs/rules/persistence.md), through [MainActivity] and the
 * real files of the app. See [AppProcess] for how each kind of launch is simulated.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceScreensTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    @After
    fun tearDown() = process.close()

    private fun string(id: Int, vararg args: Any): String = process.string(id, *args)

    private fun text(id: Int, vararg args: Any): SemanticsNodeInteraction =
        composeRule.onNodeWithText(string(id, *args))

    private fun click(id: Int) = text(id).performScrollTo().performClick()

    private fun clickInDialog(id: Int) = text(id).performClick()

    private fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun tap(vararg tags: String) = tags.forEach { key(it).performClick() }

    private fun back() {
        composeRule.waitForIdle()
        process.pressBack()
        composeRule.waitForIdle()
    }

    private fun assertSummaryShown(shown: Boolean) {
        val summary = composeRule.onNodeWithTag(HomeTags.SUMMARY)
        if (shown) summary.performScrollTo().assertIsDisplayed() else summary.assertDoesNotExist()
        if (!shown) text(R.string.home_resume_game).assertDoesNotExist()
    }

    /** The card of the player shown as throwing, as the X01 screen tests check it. */
    private fun assertOnGameScreen(player: String) {
        composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER).assert(hasAnyDescendant(hasText(player)))
    }

    private fun assertDartSlot(index: Int, dart: String) {
        key(GameTags.dartSlot(index)).assertContentDescriptionEquals(string(R.string.game_dart_slot, index + 1, dart))
    }

    private val ann2Darts = SaveFixtures.MID_TURN

    /** R1: back on the game screen directly, with the same player, darts and score. */
    @Test
    fun `R1 returning after process death shows the game directly`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_resume_game)

        process.killInBackgroundAndReturn()

        assertSummaryShown(false)
        assertOnGameScreen("Ann")
        key(GameTags.CURRENT_REMAINING).assertTextEquals("436")
        key(KeypadTags.number(20)).assertIsEnabled()
    }

    /** R1, R4: a turn waiting for "Next player" still waits, BUST shown, keys disabled. */
    @Test
    fun `R4 a waiting bust is restored after process death`() {
        process.writeGame(SaveFixtures.WAITING_BUST)
        process.launchFresh()
        click(R.string.home_resume_game)

        process.killInBackgroundAndReturn()

        assertOnGameScreen("Ann")
        key(GameTags.CARD_BUST).performScrollTo().assertIsDisplayed()
        key(KeypadTags.number(20)).assertIsNotEnabled()
        key(KeypadTags.NEXT_PLAYER).assertIsEnabled().performClick()
        assertOnGameScreen("Bob")
    }

    /** R4: the checkout dialog is open again, and both buttons behave as before. */
    @Test
    fun `R4 the checkout dialog is restored and both buttons work`() {
        process.writeGame(SaveFixtures.WAITING_CHECKOUT)
        process.launchFresh()
        click(R.string.home_resume_game)

        process.killInBackgroundAndReturn()

        key(GameTags.CHECKOUT_DIALOG).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.key_undo)).and(hasAnyAncestor(hasTestTag(GameTags.CHECKOUT_DIALOG))))
            .performClick()
        key(GameTags.CHECKOUT_DIALOG).assertDoesNotExist()
        tap(KeypadTags.number(1))
        clickInDialog(R.string.checkout_finish)
        text(R.string.end_winner, "Ann").assertIsDisplayed()
    }

    /** R2: a fresh launch shows the summary, then "New game", then "Resume game" under it. */
    @Test
    fun `R2 a fresh launch shows the summary and the two buttons in order`() {
        process.writeGame(SaveFixtures.WAITING_NORMAL)
        process.launchFresh()

        assertSummaryShown(true)
        text(R.string.home_saved_game_mode, 501, string(R.string.setup_double_out)).assertIsDisplayed()
        key(HomeTags.player(0)).assert(hasText("Ann")).assert(hasText("361"))
        key(HomeTags.player(1)).assert(hasText("Bob")).assert(hasText("501"))
        text(R.string.game_to_throw, "Ann").assertIsDisplayed()
        val newGame = text(R.string.home_new_game).getUnclippedBoundsInRoot()
        val resume = text(R.string.home_resume_game).getUnclippedBoundsInRoot()
        assertTrue(resume.top >= newGame.bottom)

        click(R.string.home_resume_game)
        assertOnGameScreen("Ann")
        key(KeypadTags.NEXT_PLAYER).assertIsEnabled()
    }

    @Test
    fun `R2 the summary of an open checkout names the winner`() {
        process.writeGame(SaveFixtures.WAITING_CHECKOUT)
        process.launchFresh()

        text(R.string.home_saved_game_checkout, "Ann").performScrollTo().assertIsDisplayed()
    }

    /** R5: undo works back to the first dart of the game. */
    @Test
    fun `R5 undo after a resume goes back to the first dart`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_resume_game)

        tap(KeypadTags.UNDO, KeypadTags.UNDO)

        key(GameTags.CURRENT_REMAINING).assertTextEquals("501")
        key(KeypadTags.UNDO).assertIsNotEnabled()
    }

    /** R3: Cancel keeps the game; Delete deletes it at once; back then leaves nothing to resume. */
    @Test
    fun `R3 the new-game confirmation`() {
        process.writeGame(ann2Darts)
        process.launchFresh()

        click(R.string.home_new_game)
        text(R.string.new_game_confirm_title).assertIsDisplayed()
        clickInDialog(R.string.new_game_confirm_cancel)
        assertSummaryShown(true)

        click(R.string.home_new_game)
        clickInDialog(R.string.new_game_confirm_delete)
        text(R.string.setup_title).assertIsDisplayed()
        back()
        assertSummaryShown(false)

        process.launchFresh()
        assertSummaryShown(false)
        assertFalse(process.gameFile.exists())
    }

    /** R6: a confirmed win deletes the saved game; killed on the end screen, the app reopens on home. */
    @Test
    fun `R6 the saved game is deleted when the win is confirmed`() {
        process.writeGame(SaveFixtures.WAITING_CHECKOUT)
        process.launchFresh()
        click(R.string.home_resume_game)
        clickInDialog(R.string.checkout_finish)
        text(R.string.end_winner, "Ann").assertIsDisplayed()

        process.killInBackgroundAndReturn()

        text(R.string.home_new_game).assertIsDisplayed()
        assertSummaryShown(false)
        process.launchFresh()
        assertSummaryShown(false)
    }

    /** R6 (owner decision Q2): Abandon deletes the saved game. */
    @Test
    fun `R6 Abandon deletes the saved game`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_resume_game)

        back()
        clickInDialog(R.string.game_abandon_confirm)
        assertSummaryShown(false)

        process.launchFresh()
        assertSummaryShown(false)
    }

    /** R7: names in order, start score and checkout rule prefill the next setup, after a restart too. */
    @Test
    fun `R7 the last setup prefills the next game after a restart`() {
        process.launchFresh()
        click(R.string.home_new_game)
        composeRule.onNodeWithContentDescription(string(R.string.setup_add_player)).performScrollTo().performClick()
        listOf("Cid", "Ann", "Bob").forEachIndexed { index, name ->
            composeRule.onNodeWithTag(SetupTags.nameField(index)).performScrollTo().performTextReplacement(name)
        }
        text(R.string.setup_start_score, 301).performScrollTo().performClick()
        click(R.string.setup_single_out)
        click(R.string.setup_start)

        process.launchFresh()
        click(R.string.home_new_game)
        clickInDialog(R.string.new_game_confirm_delete)

        listOf("Cid", "Ann", "Bob").forEachIndexed { index, name ->
            composeRule.onNodeWithTag(SetupTags.nameField(index)).assert(hasText(name))
        }
        text(R.string.setup_start_score, 301).assertIsSelected()
        text(R.string.setup_single_out).assertIsSelected()
        text(R.string.setup_double_out).assertIsNotSelected()
    }

    /** R7 (owner decision Q3): a rematch is retained in its rotated order. */
    @Test
    fun `R7 a rematch is retained in its order`() {
        process.writeSetup(SaveFixtures.WAITING_CHECKOUT.game.config)
        process.writeGame(SaveFixtures.WAITING_CHECKOUT)
        process.launchFresh()
        click(R.string.home_resume_game)
        clickInDialog(R.string.checkout_finish)
        click(R.string.end_rematch)

        process.launchFresh()
        click(R.string.home_new_game)
        clickInDialog(R.string.new_game_confirm_delete)

        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(hasText("Bob"))
        composeRule.onNodeWithTag(SetupTags.nameField(1)).assert(hasText("Ann"))
    }

    /** R9, R8: an unreadable game shows the message once over the prefilled setup, and its file is deleted. */
    @Test
    fun `R9 an unreadable saved game shows the message once`() {
        process.writeRawGame("not a save".toByteArray())
        process.writeSetup(X01Config(listOf("Dan", "Eve"), 301, CheckoutRule.DOUBLE_OUT))
        process.launchFresh()

        text(R.string.restore_failed_message).assertIsDisplayed()
        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(hasText("Dan"))
        clickInDialog(R.string.restore_failed_ok)
        text(R.string.restore_failed_message).assertDoesNotExist()
        text(R.string.setup_title).assertIsDisplayed()
        assertFalse(process.gameFile.exists())

        process.launchFresh()
        text(R.string.restore_failed_message).assertDoesNotExist()
        assertSummaryShown(false)
    }

    /** R10, R8: an unreadable last setup falls back to the defaults and does not touch the saved game. */
    @Test
    fun `R10 an unreadable last setup falls back silently to the defaults`() {
        process.writeRawSetup("not a setup".toByteArray())
        process.writeGame(ann2Darts)
        process.launchFresh()

        assertSummaryShown(true)
        click(R.string.home_new_game)
        clickInDialog(R.string.new_game_confirm_delete)
        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(hasText(""))
        text(R.string.setup_start_score, 501).assertIsSelected()
        text(R.string.setup_double_out).assertIsSelected()
    }

    /** R4: resuming never announces a completed turn again. */
    @Test
    fun `R4 resuming a waiting turn fires the turn-completed hook zero times`() {
        val turns = mutableListOf<X01Turn>()
        process.writeGame(SaveFixtures.WAITING_NORMAL)
        process.launchFresh()
        process.viewModel.turnCompletedHook = TurnCompletedHook { turns += it }

        click(R.string.home_resume_game)
        process.killInBackgroundAndReturn()
        process.viewModel.turnCompletedHook = TurnCompletedHook { turns += it }
        composeRule.waitForIdle()

        assertEquals(emptyList<X01Turn>(), turns)
    }

    /** R4 (owner decision Q5): an active Double is not restored. */
    @Test
    fun `R4 an active modifier is not restored`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_resume_game)
        tap(KeypadTags.DOUBLE)
        key(KeypadTags.DOUBLE).assertIsSelected()

        process.killInBackgroundAndReturn()

        key(KeypadTags.DOUBLE).assertIsNotSelected()
    }

    /** R1, second sentence: from any other screen, the app comes back on home. */
    @Test
    fun `R1 returning to the home screen with the summary keeps the summary`() {
        process.writeGame(ann2Darts)
        process.launchFresh()

        process.killInBackgroundAndReturn()

        assertSummaryShown(true)
        key(GameTags.CURRENT_PLAYER).assertDoesNotExist()
    }

    @Test
    fun `R1 returning to the setup screen shows home`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_new_game)
        clickInDialog(R.string.new_game_confirm_delete)

        process.killInBackgroundAndReturn()

        text(R.string.home_new_game).assertIsDisplayed()
        assertSummaryShown(false)
    }

    @Test
    fun `R1 returning to the end screen shows home`() {
        process.writeGame(SaveFixtures.WAITING_CHECKOUT)
        process.launchFresh()
        click(R.string.home_resume_game)
        clickInDialog(R.string.checkout_finish)

        process.killInBackgroundAndReturn()

        text(R.string.home_new_game).assertIsDisplayed()
        assertSummaryShown(false)
    }

    /** R12: a rotation keeps the screen and reads or writes nothing. */
    @Test
    fun `R12 rotating during a game reads and writes nothing`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_resume_game)
        tap(KeypadTags.MISS)
        process.persistence.awaitIdle()
        val reads = process.gameStore.reads
        val writes = process.gameStore.writes

        process.rotate()
        composeRule.waitForIdle()
        process.persistence.awaitIdle()

        assertOnGameScreen("Ann")
        assertDartSlot(2, string(R.string.dart_miss))
        assertEquals(reads, process.gameStore.reads)
        assertEquals(writes, process.gameStore.writes)
    }

    @Test
    fun `R12 rotating keeps the new-game dialog open`() {
        process.writeGame(ann2Darts)
        process.launchFresh()
        click(R.string.home_new_game)

        process.rotate()
        composeRule.waitForIdle()

        text(R.string.new_game_confirm_title).assertIsDisplayed()
        clickInDialog(R.string.new_game_confirm_cancel)
        assertSummaryShown(true)
        assertEquals(1, process.gameStore.reads)
    }

    @Test
    fun `R12 rotating keeps the restore-failed message open`() {
        process.writeRawGame("not a save".toByteArray())
        process.launchFresh()

        process.rotate()
        composeRule.waitForIdle()

        text(R.string.restore_failed_message).assertIsDisplayed()
        assertEquals(1, process.gameStore.reads)
    }

    /** R2 layout: 4 players with 20-character names in landscape; no score is clipped, Resume is reachable. */
    @Test
    @Config(qualifiers = "land")
    fun `R2 long names and 4 players fit in landscape`() {
        val names = listOf("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Dan")
        process.writeGame(SavedGame(X01Game(X01Config(names), listOf(Dart.triple(20))), isAwaitingNextPlayer = false))
        process.launchFresh()

        val card = composeRule.onNodeWithTag(HomeTags.SUMMARY).performScrollTo().getBoundsInRoot()
        names.indices.forEach { index ->
            val score = composeRule.onNodeWithTag(HomeTags.score(index), useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
                .getBoundsInRoot()
            assertTrue("score $index is clipped", score.right <= card.right && score.right > score.left)
        }
        text(R.string.home_resume_game).performScrollTo().assertIsDisplayed()
    }
}
