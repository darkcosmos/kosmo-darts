package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileContent
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileScreen
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileSection
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData
import io.github.darkcosmos.kosmodarts.ui.profile.figures
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Profile screen (PS2 to PS6, PS8) for visual review, on a Pixel 7: no match yet, the X01 section
 * filled, the Cricket section with "All variants" (two result blocks) and no match for the filters, in portrait and
 * landscape, and at the largest font. The state is given directly. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ProfileScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val matches = listOf(ProfileTestData.x01Raw(), ProfileTestData.cricketRaw())

    private fun stateOf(
        raws: List<RawStatsMatch>,
        section: ProfileSection = ProfileSection.X01,
        x01Filter: X01Filter = X01Filter(),
        resetAt: Long = ProfileTestData.CREATED_AT,
    ): ProfileState {
        val data = ProfileTestData.data(raws + ProfileTestData.corruptRaw("bad"), resetAt)
        val base = ProfileState(section = section, x01Filter = x01Filter, now = NOW)
        return base.copy(content = ProfileContent.Loaded(data, data.figures(base.query)))
    }

    private fun capture(name: String, state: ProfileState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { ProfileScreen(state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/profile_$name.png")
    }

    private val emptyState get() = stateOf(emptyList())
    private val x01State get() = stateOf(matches, resetAt = ProfileTestData.CREATED_AT + 1)
    private val cricketState get() = stateOf(matches, ProfileSection.CRICKET)
    private val noMatchState get() = stateOf(matches, x01Filter = X01Filter(startScore = 501))

    @Test
    @Config(qualifiers = "+en-port")
    fun emptyPortrait() = capture("empty_portrait_en", emptyState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun emptyLandscape() = capture("empty_landscape_fr", emptyState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun emptyPortraitLargestFont() = capture("empty_portrait_fr_font200", emptyState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun emptyLandscapeLargestFont() = capture("empty_landscape_fr_font200", emptyState)

    @Test
    @Config(qualifiers = "+en-port")
    fun x01Portrait() = capture("x01_portrait_en", x01State)

    @Test
    @Config(qualifiers = "+fr-land")
    fun x01Landscape() = capture("x01_landscape_fr", x01State)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun x01PortraitLargestFont() = capture("x01_portrait_fr_font200", x01State)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun x01LandscapeLargestFont() = capture("x01_landscape_fr_font200", x01State)

    @Test
    @Config(qualifiers = "+en-port")
    fun cricketPortrait() = capture("cricket_portrait_en", cricketState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun cricketLandscape() = capture("cricket_landscape_fr", cricketState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun cricketPortraitLargestFont() = capture("cricket_portrait_fr_font200", cricketState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun cricketLandscapeLargestFont() = capture("cricket_landscape_fr_font200", cricketState)

    @Test
    @Config(qualifiers = "+en-port")
    fun noMatchForFiltersPortrait() = capture("no_match_portrait_en", noMatchState)

    @Test
    @Config(qualifiers = "+fr-land")
    fun noMatchForFiltersLandscape() = capture("no_match_landscape_fr", noMatchState)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun noMatchForFiltersPortraitLargestFont() = capture("no_match_portrait_fr_font200", noMatchState)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun noMatchForFiltersLandscapeLargestFont() = capture("no_match_landscape_fr_font200", noMatchState)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
        const val NOW = 10_000L
    }
}
