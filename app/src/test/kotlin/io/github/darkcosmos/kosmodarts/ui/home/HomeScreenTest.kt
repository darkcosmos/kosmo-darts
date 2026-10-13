package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun newGameOpensTheSetup() {
        composeRule.onNodeWithText(string(R.string.home_new_game))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText(string(R.string.setup_title)).assertIsDisplayed()
    }
}
