package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behaviour tests of the home screen (mockup 06), launched through [MainActivity] like on a phone.
 *
 * Only AndroidJUnit4, androidx.test and Compose UI test APIs are used, so this class can later run unchanged as an
 * instrumented test on a device or an emulator.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    /** The title "KOSMO DARTS" is a heading read as the app name. */
    @Test
    fun titleIsDisplayedAsHeading() {
        composeRule.onNodeWithContentDescription(string(R.string.home_title))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /** Issue #49: "NEW GAME" then the X01 mode card only (the Cricket card is out of scope). */
    @Test
    fun newGameSectionShowsTheX01Card() {
        composeRule.onNodeWithText(string(R.string.home_new_game_section)).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.MODE_X01)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText(string(R.string.mode_x01_description), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun newGameOpensTheSetup() {
        composeRule.onNodeWithTag(HomeTags.MODE_X01)
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText(string(R.string.setup_title), substring = true).assertIsDisplayed()
    }

    /** Accessibility (design system, section 8): the settings icon is read "Settings" and is at least 48 dp. */
    @Test
    fun settingsButtonIsLabelledAnd48Dp() {
        val button = composeRule.onNodeWithTag(HomeTags.SETTINGS)
        button.assertContentDescriptionEquals(string(R.string.home_settings))
        val bounds = button.getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp)
    }
}
