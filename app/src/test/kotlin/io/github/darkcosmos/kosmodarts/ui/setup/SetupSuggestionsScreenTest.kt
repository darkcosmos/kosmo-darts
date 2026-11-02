package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.Profile
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.editSetup
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** PF3 on the setup screen: the suggestions under the focused field, with the real focus handling (review U3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-port-xhdpi")
class SetupSuggestionsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun profile(name: String, at: Long) = Profile(name, PlayerListRules.nameKey(name), at)

    private var setup by mutableStateOf(SetupState())

    private fun show(initial: SetupState) {
        setup = initial
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent {
                KosmoDartsTheme {
                    SetupScreen(setup, onEvent = { setup = editSetup(setup, it, Random(0)) })
                }
            }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private val known = listOf(profile("Kevin", 3), profile("Zoé", 2), profile("Anna", 1))

    @Test
    fun `suggestions show under the focused field only, with role and description`() {
        show(SetupState(names = listOf("", "")).withProfiles(known))
        composeRule.onNodeWithTag(SetupTags.suggestion(0, 0)).assertDoesNotExist()
        composeRule.onNodeWithTag(SetupTags.nameField(0)).performClick()
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithContentDescription("Use the name Kevin")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val bounds = row.getBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
        composeRule.onNodeWithTag(SetupTags.suggestion(1, 0)).assertDoesNotExist()
    }

    @Test
    fun `tapping a suggestion fills the field and keeps the focus`() {
        show(SetupState(names = listOf("", "")).withProfiles(known))
        composeRule.onNodeWithTag(SetupTags.nameField(0)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Use the name Zoé").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("Zoé", ""), setup.names)
        composeRule.onNodeWithTag(SetupTags.nameField(0)).assertIsFocused()
        composeRule.onNodeWithTag(SetupTags.nameField(0)).assertTextContains("Zoé")
    }

    @Test
    fun `typing filters the rows of the focused field`() {
        show(SetupState(names = listOf("", "")).withProfiles(known))
        composeRule.onNodeWithTag(SetupTags.nameField(1)).performClick()
        composeRule.waitForIdle()
        setup = editSetup(setup, AppEvent.NameChanged(1, "an"), Random(0))
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Use the name Anna").assertExists()
        composeRule.onNodeWithContentDescription("Use the name Kevin").assertDoesNotExist()
    }

    @Test
    fun `an empty profile list shows nothing and the setup still works`() {
        show(SetupState(names = listOf("Ann", "Bob")))
        composeRule.onNodeWithTag(SetupTags.nameField(0)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SetupTags.suggestion(0, 0)).assertDoesNotExist()
        assertTrue(setup.start() is SetupResult.Valid)
    }
}

