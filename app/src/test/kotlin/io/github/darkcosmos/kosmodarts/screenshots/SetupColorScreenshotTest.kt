package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.ui.setup.SetupScreen
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import io.github.darkcosmos.kosmodarts.ui.setup.withColorPicked
import io.github.darkcosmos.kosmodarts.ui.setup.withSwatchesToggled
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Captures of the setup swatch row (PC9, PC14, PC15) on a Pixel 7, French, with a seeded random. Record only: see
 * [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SetupColorScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, state: SetupState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { SetupScreen(state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/setup_colors_$name.png")
    }

    private val four = SetupState(names = listOf("Anna", "Bob", "Cid", "Dan"))

    /** Player 1 holds a generated color, then his row opens (and so offers generated colors). */
    private fun fourWithGeneratedOpen(openIndex: Int): SetupState {
        val first = SetupState(names = listOf("Anna", "Bob")).withSwatchesToggled(0, Random(SEED))
        val generated = first.openSwatches!!.colors.first { it !in PlayerColor.PALETTE }
        return first.withColorPicked(generated).withPlayerAdded(Random(SEED)).withPlayerAdded(Random(SEED))
            .withName(2, "Cid").withName(3, "Dan")
            .withSwatchesToggled(openIndex, Random(SEED))
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun defaultPalette() = capture("default_fr", four)

    @Test
    @Config(qualifiers = "+fr-port")
    fun rowOpenWithGeneratedColors() = capture("open_generated_fr", fourWithGeneratedOpen(1))

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun rowOpenLargestFont() = capture("open_generated_font200_fr", fourWithGeneratedOpen(1))

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun rowOpenUnderFourthPlayerLandscapeLargestFont() =
        capture("open_4th_land_font200_fr", fourWithGeneratedOpen(3))

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
        const val SEED = 7
    }
}
