package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.ui.app.editSetup
import io.github.darkcosmos.kosmodarts.ui.components.swatchTag
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** The swatch row on the setup screen (PC9, PC14, PC16): descriptions, "selected", 48 dp targets, picking. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT_INSIDE_BARS)
class SetupColorFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var setup by mutableStateOf(SetupState(names = listOf("Ann", "Bob")))

    private fun show() {
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

    private fun swatch(color: PlayerColor) = composeRule.onNodeWithTag(swatchTag(SetupTags.swatches(0), color))

    @Test
    fun `PC9 the color strip is a 48 dp button told Change the color of the player`() {
        show()
        val strip = composeRule.onNodeWithContentDescription("Change the color of Ann")
        val bounds = strip.getBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp)
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
        composeRule.onNodeWithTag(SetupTags.swatches(0)).assertDoesNotExist()
    }

    @Test
    fun `PC14 a tap opens the row with palette names, selected state and 48 dp targets, a second tap closes it`() {
        show()
        composeRule.onNodeWithTag(SetupTags.color(0)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SetupTags.swatches(0)).assertIsDisplayed()
        val own = swatch(PlayerColor.BLUE)
        own.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        own.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "selected"))
        swatch(PlayerColor.TEAL).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        composeRule.onNodeWithContentDescription("Teal").assertIsDisplayed()
        setup.openSwatches!!.colors.forEach { color ->
            val bounds = swatch(color).getBoundsInRoot()
            assertTrue(bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp)
        }

        composeRule.onNodeWithTag(SetupTags.color(0)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SetupTags.swatches(0)).assertDoesNotExist()
    }

    @Test
    fun `PC14 picking a swatch gives the player that color and closes the row`() {
        show()
        composeRule.onNodeWithTag(SetupTags.color(0)).performClick()
        composeRule.waitForIdle()
        swatch(PlayerColor.TEAL).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(PlayerColor.TEAL, PlayerColor.ORANGE), setup.roster.colors)
        composeRule.onNodeWithTag(SetupTags.swatches(0)).assertDoesNotExist()
    }

    @Test
    fun `PC15 a generated swatch is told Color n of m`() {
        show()
        composeRule.onNodeWithTag(SetupTags.color(0)).performClick()
        composeRule.waitForIdle()
        val colors = setup.openSwatches!!.colors
        val generated = colors.first { it !in PlayerColor.PALETTE }

        swatch(generated).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Color ${colors.indexOf(generated) + 1} of ${colors.size}"),
            ),
        )
    }
}
