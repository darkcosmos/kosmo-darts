package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.PickerProfile
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadMode
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadScreen
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadSelections
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadState
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadTestData
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Head-to-head screen (HH1 to HH9) for visual review, on a Pixel 7: the partner picker, the X01
 * comparison, the "All modes" comparison with a draw, and no common match, in portrait and landscape and at the
 * largest font. The state is given directly. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HeadToHeadScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val raws = listOf(
        HeadToHeadTestData.x01("1", startedAt = 2_000),
        HeadToHeadTestData.cricket("2", startedAt = 4_000),
        HeadToHeadTestData.x01("3", startedAt = 6_000, rankA = 2, rankB = 1, legsA = 0, legsB = 1),
        HeadToHeadTestData.x01("4", startedAt = 8_000, rankA = 1, rankB = 1, legsA = 1, legsB = 1),
    )

    private fun capture(name: String, partner: Long?, state: HeadToHeadState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { HeadToHeadScreen(partner, state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/h2h_$name.png")
    }

    private val pickerState get() = HeadToHeadTestData.pickerState(
        listOf(PickerProfile(B, "Zoe", 3), PickerProfile(3L, "Abe", 0), PickerProfile(4L, "Maximilienne-Charlotte", 1)),
    )
    private val x01State get() = HeadToHeadTestData.shown(
        HeadToHeadTestData.pair(raws),
        HeadToHeadSelections(mode = HeadToHeadMode.X01, x01Filter = X01Filter(startScore = 301)),
    )
    private val allState get() = HeadToHeadTestData.shown(HeadToHeadTestData.pair(raws, resetA = 1_500))
    private val emptyState get() = HeadToHeadTestData.shown(HeadToHeadTestData.pair(emptyList()))

    @Test
    @Config(qualifiers = "+en-port")
    fun pickerPortrait() = capture("picker_portrait_en", null, pickerState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun pickerLandscape() = capture("picker_landscape_fr", null, pickerState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun pickerPortraitLargestFont() = capture("picker_portrait_fr_font200", null, pickerState)

    @Test
    @Config(qualifiers = "+en-port")
    fun x01Portrait() = capture("x01_portrait_en", B, x01State)

    @Test
    @Config(qualifiers = "+fr-land")
    fun x01Landscape() = capture("x01_landscape_fr", B, x01State)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun x01PortraitLargestFont() = capture("x01_portrait_fr_font200", B, x01State)

    @Test
    @Config(qualifiers = "+en-port")
    fun allPortrait() = capture("all_portrait_en", B, allState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun allLandscape() = capture("all_landscape_fr", B, allState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun allLandscapeLargestFont() = capture("all_landscape_fr_font200", B, allState)

    @Test
    @Config(qualifiers = "+en-port")
    fun emptyPortrait() = capture("empty_portrait_en", B, emptyState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun emptyLandscape() = capture("empty_landscape_fr", B, emptyState)

    private companion object {
        const val B = HeadToHeadTestData.B
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
