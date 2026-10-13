package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behaviour tests of the home screen, launched through [MainActivity] like on a phone.
 *
 * Only AndroidJUnit4, androidx.test and Compose UI test APIs are used, so this class can later run unchanged as an
 * instrumented test on a device or an emulator.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    @Test
    fun titleIsDisplayedAsHeading() {
        composeRule.onNodeWithText(string(R.string.home_title))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun welcomeTextIsDisplayed() {
        composeRule.onNodeWithText(string(R.string.home_welcome)).assertIsDisplayed()
    }

    /** The only button is disabled until the first game mode exists, so there is nothing to click yet. */
    @Test
    fun newGameButtonIsDisplayedButDisabled() {
        composeRule.onNode(hasClickAction())
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.home_new_game), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_coming_soon)).assertIsDisplayed()
    }
}
