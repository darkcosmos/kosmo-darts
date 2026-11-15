package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.ui.board.Dartboard
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the dartboard component (HM8) for visual review, on a Pixel 7: all zones neutral, a test pattern
 * (the zones graded by their number, to check the drawing of every ring) with a selected zone, in portrait,
 * landscape and at the largest font. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DartboardScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val pattern: Map<BoardZone, Color> = BoardZone.ALL.associateWith { zone ->
        Color(red = 0.1f + 0.8f * (zone.number % PATTERN_STEPS) / PATTERN_STEPS, green = 0.4f, blue = 0.7f)
    }

    private fun capture(name: String, colors: Map<BoardZone, Color>, selected: BoardZone?) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent {
                KosmoDartsTheme {
                    Box(
                        Modifier.fillMaxSize().background(KosmoTheme.colors.background).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Dartboard(
                            colors = colors,
                            neutral = KosmoTheme.colors.surfaceHigh,
                            selected = selected,
                            onZoneTapped = {},
                            contentDescription = "Dartboard",
                            zoneDescription = { "${it.number}" },
                        )
                    }
                }
            }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/dartboard_$name.png")
    }

    private val selectedZone = BoardZone(20, BoardZone.Ring.TRIPLE)

    @Test
    @Config(qualifiers = "+en-port")
    fun neutralPortrait() = capture("neutral_portrait", emptyMap(), null)

    @Test
    @Config(qualifiers = "+en-port")
    fun patternSelectedPortrait() = capture("pattern_selected_portrait", pattern, selectedZone)

    @Test
    @Config(qualifiers = "+en-land")
    fun patternSelectedLandscape() = capture("pattern_selected_landscape", pattern, selectedZone)

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun patternPortraitLargestFont() = capture("pattern_portrait_font200", pattern, BoardZone(1, BoardZone.Ring.SINGLE))

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
        const val PATTERN_STEPS = 7
    }
}
