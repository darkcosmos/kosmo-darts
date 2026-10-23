package io.github.darkcosmos.kosmodarts.ui.resume

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketTags
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Cricket game from the home screen to the saved game and back, through [MainActivity] and the real files of the
 * app (docs/rules/cricket.md, CR20, CR21; docs/rules/persistence.md, R20, R21). See [AppProcess].
 */
@RunWith(AndroidJUnit4::class)
class CricketResumeScreensTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    @After
    fun tearDown() = process.close()

    private fun string(id: Int, vararg args: Any): String = process.string(id, *args)

    private fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun tapScrolled(tag: String) = key(tag).performScrollTo().performClick()

    private fun assertOnCricketScreen() = key(CricketTags.SCREEN).assertIsDisplayed()

    /** CR20: the card says CRICKET and the variant, the tiles show the points, the button names who throws. */
    @Test
    fun `CR20 the summary of a saved Cricket game`() {
        process.writeGame(CricketSaveFixtures.MID_TURN)

        process.launchFresh()

        key(HomeTags.SUMMARY).performScrollTo().assertIsDisplayed()
        val formatLine = listOf(R.string.cricket_title, R.string.cricket_variant_standard)
            .joinToString(string(R.string.format_separator)) { string(it) }
        composeRule.onNodeWithText(formatLine).assertIsDisplayed()
        key(HomeTags.player(0)).assert(hasText("ANN")).assert(hasText("0"))
        key(HomeTags.RESUME).assertTextEquals(string(R.string.home_resume, "ANN"))
    }

    /** R20, R21: resume restores the Cricket screen, and after a process kill it comes back directly. */
    @Test
    fun `R21 resume then process death keeps the Cricket game`() {
        process.writeGame(CricketSaveFixtures.MID_TURN)
        process.launchFresh()
        tapScrolled(HomeTags.RESUME)
        assertOnCricketScreen()
        key(CricketTags.cell(CricketTarget.TWENTY, 0)).assertIsDisplayed()

        process.killInBackgroundAndReturn()

        assertOnCricketScreen()
        key(HomeTags.SUMMARY).assertDoesNotExist()
    }

    /** R20, R21: killed on the home screen with a Cricket game in progress, the card is still offered. */
    @Test
    fun `R20 a fresh launch offers the saved Cricket game`() {
        process.writeGame(CricketSaveFixtures.MID_TURN)
        process.launchFresh()

        process.launchFresh()

        key(HomeTags.SUMMARY).performScrollTo().assertIsDisplayed()
        assertTrue(process.gameFile.exists())
    }

    /** R3, R20: X01 over a Cricket game asks first; Cancel keeps it, Delete replaces it by the X01 setup. */
    @Test
    fun `R20 starting X01 over a Cricket game asks first`() {
        process.writeGame(CricketSaveFixtures.MID_TURN)
        process.launchFresh()

        tapScrolled(HomeTags.MODE_X01)
        composeRule.onNodeWithText(string(R.string.new_game_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.new_game_confirm_cancel)).performClick()
        key(HomeTags.SUMMARY).performScrollTo().assertIsDisplayed()

        tapScrolled(HomeTags.MODE_X01)
        composeRule.onNodeWithText(string(R.string.new_game_confirm_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.setup_title_mode), substring = true).assertIsDisplayed()
    }

    /** R20: a Cricket game over an X01 game asks the same way. */
    @Test
    fun `R20 starting Cricket over an X01 game asks first and opens the Cricket setup`() {
        process.writeGame(SaveFixtures.MID_TURN)
        process.launchFresh()

        tapScrolled(HomeTags.MODE_CRICKET)
        composeRule.onNodeWithText(string(R.string.new_game_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.new_game_confirm_delete)).performClick()

        key(SetupTags.CRICKET_HINT).assertIsDisplayed()
        key(SetupTags.CRICKET_BULL).assertIsDisplayed()
    }

    /** CR21, R20: from a fresh install, the card opens the setup, Start opens the game, which is then saved. */
    @Test
    fun `CR21 a Cricket game started from the home screen is saved and resumes after a kill`() {
        process.launchFresh()

        tapScrolled(HomeTags.MODE_CRICKET)
        key(SetupTags.CRICKET_HINT).assertTextEquals(string(R.string.setup_cricket_hint_standard))
        composeRule.onNodeWithText(string(R.string.setup_cricket_variant_label)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.cricket_variant_cut_throat)).performClick()
        key(SetupTags.CRICKET_HINT).assertTextEquals(string(R.string.setup_cricket_hint_cut_throat))
        listOf("Ann", "Bob").forEachIndexed { index, name ->
            key(SetupTags.nameField(index)).performScrollTo().performTextReplacement(name)
        }
        key(SetupTags.START).performClick()
        assertOnCricketScreen()
        key(CricketTags.KEYPAD).assertIsDisplayed()

        process.killInBackgroundAndReturn()

        assertOnCricketScreen()
        assertTrue(process.gameFile.exists())
    }

    /** CR1: blank names stay on the setup with the error under the field. */
    @Test
    fun `CR1 a Cricket Start with blank names stays on the setup`() {
        process.launchFresh()

        tapScrolled(HomeTags.MODE_CRICKET)
        key(SetupTags.START).performClick()

        composeRule.onAllNodesWithText(string(R.string.setup_error_blank_name), useUnmergedTree = true)
            .assertCountEquals(2)
        key(CricketTags.SCREEN).assertDoesNotExist()
        assertFalse(process.gameFile.exists())
    }

    /** R6: abandoning a Cricket game deletes it. */
    @Test
    fun `R6 abandoning a resumed Cricket game deletes the saved game`() {
        process.writeGame(CricketSaveFixtures.MID_TURN)
        process.launchFresh()
        tapScrolled(HomeTags.RESUME)

        composeRule.waitForIdle()
        process.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.game_abandon_confirm)).performClick()

        key(HomeTags.SUMMARY).assertDoesNotExist()
        key(GameTags.CURRENT_PLAYER).assertDoesNotExist()
        process.persistence.awaitIdle()
        assertFalse(process.gameFile.exists())
    }
}
