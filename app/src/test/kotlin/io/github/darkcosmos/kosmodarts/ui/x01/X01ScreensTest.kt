package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behaviour tests of the X01 screens (setup, game, end), launched through [MainActivity] like on a phone.
 *
 * Only AndroidJUnit4, androidx.test and Compose UI test APIs are used (plus the app's own ViewModel to observe the
 * turn completed hook), so this class can later run unchanged as an instrumented test on a device or an emulator.
 */
@RunWith(AndroidJUnit4::class)
class X01ScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val numberTags = Dart.NUMBERS.map { KeypadTags.number(it) }
    private val keypadTags = numberTags +
        listOf(KeypadTags.DOUBLE, KeypadTags.TRIPLE, KeypadTags.OUTER_BULL, KeypadTags.BULL, KeypadTags.MISS)

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun click(textId: Int) {
        composeRule.onNodeWithText(string(textId)).performClick()
    }

    /** Setup and end screens scroll when the screen is small or the font is large. */
    private fun clickInScroll(textId: Int) {
        composeRule.onNodeWithText(string(textId)).performScrollTo().performClick()
    }

    private fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun tap(vararg tags: String) = tags.forEach { key(it).performClick() }

    private fun triple(number: Int) = tap(KeypadTags.TRIPLE, KeypadTags.number(number))

    private fun double(number: Int) = tap(KeypadTags.DOUBLE, KeypadTags.number(number))

    private fun single(vararg numbers: Int) = numbers.forEach { tap(KeypadTags.number(it)) }

    private fun nextPlayer() = tap(KeypadTags.NEXT_PLAYER)

    private fun checkoutDialog(): SemanticsNodeInteraction = composeRule.onNodeWithTag(GameTags.CHECKOUT_DIALOG)

    private fun finishGame() = composeRule.onNodeWithText(string(R.string.checkout_finish)).performClick()

    /** The Undo button of the checkout dialog, not the Undo key of the keypad. */
    private fun undoInCheckoutDialog() {
        composeRule.onNode(hasText(string(R.string.key_undo)).and(hasAnyAncestor(hasTestTag(GameTags.CHECKOUT_DIALOG))))
            .performClick()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** From home: fills the setup and starts the game. Double out unless [singleOut]. */
    private fun startGame(vararg names: String, startScore: Int = 501, singleOut: Boolean = false) {
        click(R.string.home_new_game)
        composeRule.onNodeWithText(startScore.toString()).performClick()
        if (singleOut) clickInScroll(R.string.setup_single_out)
        repeat(names.size - 2) {
            composeRule.onNodeWithContentDescription(string(R.string.setup_add_player)).performClick()
        }
        if (names.size == 1) {
            composeRule.onNodeWithContentDescription(string(R.string.setup_remove_player)).performClick()
        }
        names.forEachIndexed { index, name ->
            composeRule.onNodeWithTag(SetupTags.nameField(index)).performScrollTo().performTextReplacement(name)
        }
        clickInScroll(R.string.setup_start)
    }

    private fun assertCurrentPlayer(name: String, remaining: Int) {
        composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER).assert(hasAnyDescendant(hasText(name)))
        composeRule.onNodeWithTag(GameTags.CURRENT_REMAINING).assertTextEquals(remaining.toString())
    }

    private fun assertDartSlot(index: Int, dart: String) {
        composeRule.onNodeWithTag(GameTags.dartSlot(index))
            .assertContentDescriptionEquals(string(R.string.game_dart_slot, index + 1, dart))
    }

    private fun assertLastTurnScore(playerIndex: Int, score: String) {
        composeRule.onNodeWithTag(GameTags.lastTurnScore(playerIndex))
            .assertTextEquals(string(R.string.game_last_turn_score, score))
    }

    /** 301 double out, Ann against Bob, every turn confirmed: Ann leaves 121, Bob 298, then T20 T19 leaves 4. */
    private fun playAnnToFourRemaining() {
        startGame("Ann", "Bob", startScore = 301)
        repeat(3) { triple(20) }
        nextPlayer()
        single(1, 1, 1)
        nextPlayer()
        triple(20)
        triple(19)
    }

    @Test
    fun doubleThenTwentyIsD20() {
        startGame("Ann", "Bob")

        double(20)

        assertDartSlot(0, "D20")
        assertCurrentPlayer("Ann", 461)
        key(KeypadTags.DOUBLE).assertIsNotSelected()
    }

    @Test
    fun bullKeysAreDisabledWhileAModifierIsActive() {
        startGame("Ann", "Bob")
        key(KeypadTags.OUTER_BULL).assertIsEnabled()
        key(KeypadTags.BULL).assertIsEnabled()

        tap(KeypadTags.TRIPLE)
        key(KeypadTags.TRIPLE).assertIsSelected()
        key(KeypadTags.OUTER_BULL).assertIsNotEnabled()
        key(KeypadTags.BULL).assertIsNotEnabled()

        tap(KeypadTags.TRIPLE)
        key(KeypadTags.TRIPLE).assertIsNotSelected()
        key(KeypadTags.OUTER_BULL).assertIsEnabled()
        key(KeypadTags.BULL).assertIsEnabled()
    }

    @Test
    fun afterThreeDartsPlayWaitsWithEntryKeysDisabledAndNextPlayerEnabled() {
        startGame("Ann", "Bob")

        single(20, 20, 5)

        assertCurrentPlayer("Ann", 456)
        assertDartSlot(0, "20")
        assertDartSlot(1, "20")
        assertDartSlot(2, "5")
        composeRule.onNodeWithTag(GameTags.CARD_BUST).assertDoesNotExist()
        keypadTags.forEach { key(it).assertIsNotEnabled() }
        key(KeypadTags.NEXT_PLAYER).assertIsEnabled()
        key(KeypadTags.UNDO).assertIsEnabled()
    }

    @Test
    fun nextPlayerIsDisabledWhenNoTurnIsWaiting() {
        startGame("Ann", "Bob")
        key(KeypadTags.NEXT_PLAYER).assertIsDisplayed().assertIsNotEnabled()

        single(20, 20)
        key(KeypadTags.NEXT_PLAYER).assertIsNotEnabled()

        single(5)
        nextPlayer()
        key(KeypadTags.NEXT_PLAYER).assertIsNotEnabled()
    }

    @Test
    fun nextPlayerMovesPlayOn() {
        startGame("Ann", "Bob")
        single(20, 20, 5)

        nextPlayer()

        assertCurrentPlayer("Bob", 501)
        keypadTags.forEach { key(it).assertIsEnabled() }
        assertLastTurnScore(0, "45")
    }

    @Test
    fun aBustIsShownInTheCardOfThePlayerWhileWaiting() {
        startGame("Ann", "Bob", startScore = 301)
        repeat(3) { triple(20) }
        nextPlayer()
        repeat(3) { triple(20) }
        nextPlayer()
        triple(20)

        triple(20)

        composeRule.onNodeWithTag(GameTags.CARD_BUST).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER)
            .assert(hasAnyDescendant(hasTestTag(GameTags.CARD_BUST)))
        assertCurrentPlayer("Ann", 121)
        assertDartSlot(0, "T20")
        assertDartSlot(1, "T20")
        key(KeypadTags.number(1)).assertIsNotEnabled()
        key(KeypadTags.NEXT_PLAYER).assertIsEnabled()

        nextPlayer()

        composeRule.onNodeWithTag(GameTags.CARD_BUST).assertDoesNotExist()
        assertCurrentPlayer("Bob", 121)
        assertLastTurnScore(0, string(R.string.game_bust))
    }

    @Test
    fun undoWhileWaitingReopensTheTurnWithItsFirstTwoDarts() {
        startGame("Ann", "Bob")
        single(20, 20, 1)

        tap(KeypadTags.UNDO)

        assertCurrentPlayer("Ann", 461)
        assertDartSlot(0, "20")
        assertDartSlot(1, "20")
        composeRule.onNodeWithTag(GameTags.dartSlot(2))
            .assertContentDescriptionEquals(string(R.string.game_dart_slot_empty, 3))
        key(KeypadTags.number(20)).assertIsEnabled()
        key(KeypadTags.NEXT_PLAYER).assertIsNotEnabled()
    }

    @Test
    fun undoWorksAcrossPlayers() {
        startGame("Ann", "Bob")
        key(KeypadTags.UNDO).assertIsNotEnabled()
        single(1, 2, 3)
        nextPlayer()
        assertCurrentPlayer("Bob", 501)

        tap(KeypadTags.UNDO)

        assertCurrentPlayer("Ann", 498)
        assertDartSlot(0, "1")
        assertDartSlot(1, "2")
    }

    @Test
    fun eachPlayerRowShowsTheirPreviousTurnScoreBustOrADash() {
        startGame("Ann", "Bob", "Cid", startScore = 301)
        listOf(0, 1, 2).forEach { assertLastTurnScore(it, string(R.string.game_last_turn_none)) }

        repeat(3) { triple(20) }
        nextPlayer()
        single(1, 2, 3)
        nextPlayer()
        repeat(3) { triple(20) }
        nextPlayer()
        triple(20)
        triple(20)
        nextPlayer()

        assertLastTurnScore(0, string(R.string.game_bust))
        assertLastTurnScore(1, "6")
        assertLastTurnScore(2, "180")
    }

    @Test
    fun backOpensTheAbandonConfirmation() {
        startGame("Ann", "Bob")
        single(20)

        pressBack()
        composeRule.onNodeWithText(string(R.string.game_abandon_title)).assertIsDisplayed()
        click(R.string.game_abandon_cancel)
        composeRule.onNodeWithText(string(R.string.game_abandon_title)).assertDoesNotExist()
        assertCurrentPlayer("Ann", 481)

        pressBack()
        click(R.string.game_abandon_confirm)
        composeRule.onNodeWithText(string(R.string.home_welcome)).assertIsDisplayed()
    }

    @Test
    fun theCheckoutDialogShowsThePlayerAndDartsThenFinishGameOpensTheEndScreen() {
        playAnnToFourRemaining()

        double(2)

        checkoutDialog().assertIsDisplayed()
        checkoutDialog().assert(hasAnyDescendant(hasText(string(R.string.checkout_title))))
        checkoutDialog().assert(hasAnyDescendant(hasText("Ann")))
        checkoutDialog().assert(hasAnyDescendant(hasText("T20 T19 D2")))
        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertDoesNotExist()

        finishGame()

        checkoutDialog().assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.end_rematch)).assertIsDisplayed()
    }

    @Test
    fun undoInTheCheckoutDialogReopensTheTurn() {
        playAnnToFourRemaining()
        double(2)

        undoInCheckoutDialog()

        checkoutDialog().assertDoesNotExist()
        assertCurrentPlayer("Ann", 4)
        assertDartSlot(0, "T20")
        assertDartSlot(1, "T19")
        key(KeypadTags.number(2)).assertIsEnabled()
        double(2)
        finishGame()
        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
    }

    @Test
    fun theCheckoutDialogIgnoresOutsideTapsAndSystemBack() {
        playAnnToFourRemaining()
        double(2)

        composeRule.onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(1f, 1f)) }
        pressBack()

        checkoutDialog().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_abandon_title)).assertDoesNotExist()
        finishGame()
        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
    }

    @Test
    fun theEndScreenHasNoUndoAction() {
        playAnnToFourRemaining()
        double(2)
        finishGame()

        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
        composeRule.onNodeWithText(string(R.string.end_rematch)).assertHasClickAction()
        composeRule.onNodeWithText(string(R.string.home_new_game)).assertHasClickAction()
        composeRule.onNodeWithText(string(R.string.key_undo)).assertDoesNotExist()
    }

    @Test
    fun rematchRotatesTheFirstPlayer() {
        startGame("Ann", "Bob", "Cid", startScore = 301, singleOut = true)
        repeat(3) { triple(20) }
        nextPlayer()
        single(1, 1, 1)
        nextPlayer()
        single(1, 1, 1)
        nextPlayer()
        triple(20)
        triple(20)
        single(1)
        finishGame()

        clickInScroll(R.string.end_rematch)

        assertCurrentPlayer("Bob", 301)
        single(1, 1, 1)
        nextPlayer()
        assertCurrentPlayer("Cid", 301)
        single(1, 1, 1)
        nextPlayer()
        assertCurrentPlayer("Ann", 301)
    }

    @Test
    fun setupErrorsAreShownUnderTheConcernedFields() {
        click(R.string.home_new_game)
        composeRule.onNodeWithContentDescription(string(R.string.setup_add_player)).performClick()
        composeRule.onNodeWithTag(SetupTags.nameField(0)).performTextReplacement("Ann")
        composeRule.onNodeWithTag(SetupTags.nameField(2)).performTextReplacement("ann")

        clickInScroll(R.string.setup_start)

        val blank = hasText(string(R.string.setup_error_blank_name))
        val duplicate = hasText(string(R.string.setup_error_duplicate_name))
        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(duplicate)
        composeRule.onNodeWithTag(SetupTags.nameField(1)).assert(blank)
        composeRule.onNodeWithTag(SetupTags.nameField(1)).assert(!duplicate)
        composeRule.onNodeWithTag(SetupTags.nameField(2)).assert(duplicate)
        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(!blank)
    }

    @Test
    fun theGameSurvivesAConfigurationChange() {
        startGame("Ann", "Bob")
        single(20, 20, 20)
        nextPlayer()
        triple(19)
        tap(KeypadTags.DOUBLE)

        composeRule.activityRule.scenario.recreate()

        assertCurrentPlayer("Bob", 444)
        assertDartSlot(0, "T19")
        key(KeypadTags.DOUBLE).assertIsSelected()
        assertLastTurnScore(0, "60")
    }

    @Test
    fun theWaitingTurnSurvivesAConfigurationChange() {
        startGame("Ann", "Bob")
        single(20, 20, 5)

        composeRule.activityRule.scenario.recreate()

        assertCurrentPlayer("Ann", 456)
        assertDartSlot(2, "5")
        key(KeypadTags.number(20)).assertIsNotEnabled()
        key(KeypadTags.NEXT_PLAYER).assertIsEnabled()
        nextPlayer()
        assertCurrentPlayer("Bob", 501)
    }

    @Test
    fun theCheckoutDialogSurvivesAConfigurationChange() {
        playAnnToFourRemaining()
        double(2)

        composeRule.activityRule.scenario.recreate()

        checkoutDialog().assert(hasAnyDescendant(hasText("T20 T19 D2")))
        finishGame()
        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
    }

    @Test
    fun eachTurnEndAndTheWinAreAnnouncedOnceEvenAcrossRecreations() {
        val cues = mutableListOf<X01Cue>()
        composeRule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[KosmoDartsViewModel::class.java].addCueSink { cues += it }
        }
        fun turnEnds() = cues.filter { it is X01Cue.TurnScore || it == X01Cue.OneEighty }
        startGame("Ann", "Bob", startScore = 301)

        repeat(3) { triple(20) }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertEquals(1, turnEnds().size)
        nextPlayer()
        single(1, 1)
        assertEquals(1, turnEnds().size)
        single(1)
        nextPlayer()
        triple(20)
        triple(19)
        double(2)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        finishGame()

        assertEquals(listOf(X01Cue.OneEighty, X01Cue.TurnScore(3)), turnEnds())
        assertEquals(listOf(X01Cue.GameShot(0, "Ann")), cues.filterIsInstance<X01Cue.GameShot>())
    }

    @Test
    fun aNewGameIsPrefilledWithTheLastNames() {
        playAnnToFourRemaining()
        double(2)
        finishGame()

        clickInScroll(R.string.home_new_game)

        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(hasText("Ann"))
        composeRule.onNodeWithTag(SetupTags.nameField(1)).assert(hasText("Bob"))
    }

}
