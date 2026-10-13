package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
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
 * Behaviour tests of the X01 screens (setup, game, end), launched through [MainActivity] like on a phone.
 *
 * Only AndroidJUnit4, androidx.test and Compose UI test APIs are used, so this class can later run unchanged as an
 * instrumented test on a device or an emulator.
 */
@RunWith(AndroidJUnit4::class)
class X01ScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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

    /** 301 double out, Ann against Bob: Ann leaves 121, Bob 298, then Ann's T20 T19 leaves 4. */
    private fun playAnnToFourRemaining() {
        startGame("Ann", "Bob", startScore = 301)
        triple(20)
        triple(20)
        triple(20)
        single(1, 1, 1)
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
    fun aBustShowsBustUntilTheNextDart() {
        startGame("Ann", "Bob", startScore = 301)
        repeat(6) { triple(20) }
        triple(20)
        composeRule.onNodeWithTag(GameTags.BUST).assertDoesNotExist()

        triple(20)

        composeRule.onNodeWithTag(GameTags.BUST).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_bust)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_last_turn_bust, "Ann", "T20 T20"))
            .performScrollTo()
            .assertIsDisplayed()
        assertCurrentPlayer("Bob", 121)

        single(1)

        composeRule.onNodeWithTag(GameTags.BUST).assertDoesNotExist()
    }

    @Test
    fun undoWorksAcrossPlayers() {
        startGame("Ann", "Bob")
        key(KeypadTags.UNDO).assertIsNotEnabled()
        single(1, 2, 3)
        assertCurrentPlayer("Bob", 501)

        tap(KeypadTags.UNDO)

        assertCurrentPlayer("Ann", 498)
        assertDartSlot(0, "1")
        assertDartSlot(1, "2")
        composeRule.onNodeWithTag(GameTags.dartSlot(2))
            .assertContentDescriptionEquals(string(R.string.game_dart_slot_empty, 3))
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
    fun aFull301LegEndsOnTheEndScreen() {
        playAnnToFourRemaining()

        double(2)

        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.end_rematch)).assertIsDisplayed()
    }

    @Test
    fun undoFromTheEndScreenReturnsToTheGame() {
        playAnnToFourRemaining()
        double(2)

        clickInScroll(R.string.end_undo)

        assertCurrentPlayer("Ann", 4)
        assertDartSlot(0, "T20")
        assertDartSlot(1, "T19")
        double(2)
        composeRule.onNodeWithText(string(R.string.end_winner, "Ann")).assertIsDisplayed()
    }

    @Test
    fun rematchRotatesTheFirstPlayer() {
        startGame("Ann", "Bob", "Cid", startScore = 301, singleOut = true)
        repeat(3) { triple(20) }
        single(1, 1, 1)
        single(1, 1, 1)
        triple(20)
        triple(20)
        single(1)

        clickInScroll(R.string.end_rematch)

        assertCurrentPlayer("Bob", 301)
        single(1, 1, 1)
        assertCurrentPlayer("Cid", 301)
        single(1, 1, 1)
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
        triple(19)
        tap(KeypadTags.DOUBLE)

        composeRule.activityRule.scenario.recreate()

        assertCurrentPlayer("Bob", 444)
        assertDartSlot(0, "T19")
        key(KeypadTags.DOUBLE).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.game_last_turn, "Ann", "20 20 20", 60))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aNewGameIsPrefilledWithTheLastNames() {
        playAnnToFourRemaining()
        double(2)

        click(R.string.home_new_game)

        composeRule.onNodeWithTag(SetupTags.nameField(0)).assert(hasText("Ann"))
        composeRule.onNodeWithTag(SetupTags.nameField(1)).assert(hasText("Bob"))
    }
}
