package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
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
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTags
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData
import io.github.darkcosmos.kosmodarts.ui.profile.TrendTestData
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

    private fun capture(name: String, state: ProfileState, scrollToTrend: Boolean = false) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { ProfileScreen(state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        if (scrollToTrend) {
            composeRule.onNodeWithTag(ProfileTags.CONTENT)
                .performScrollToNode(hasTestTag(ProfileTags.figure("trend_last_date")))
            composeRule.waitForIdle()
        }
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/profile_$name.png")
    }

    private val emptyState get() = stateOf(emptyList())
    private val x01State get() = stateOf(matches, resetAt = ProfileTestData.CREATED_AT + 1)
    private val cricketState get() = stateOf(matches, ProfileSection.CRICKET)
    private val noMatchState get() = stateOf(matches, x01Filter = X01Filter(startScore = 501))
    private val x01TrendState get() = TrendTestData.withTrend(
        x01State,
        TrendTestData.points(TrendTestData.wave(10)),
        emptyList(),
    )
    private val cricketTrendState get() = TrendTestData.withTrend(
        stateOf(matches, ProfileSection.CRICKET),
        emptyList(),
        TrendTestData.points(TrendTestData.wave(30, base = 2.0, amplitude = 0.6)),
    )
    private val onePointState get() = TrendTestData.withTrend(
        x01State,
        TrendTestData.points(listOf(48.0)),
        emptyList(),
    )

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

    @Test
    @Config(qualifiers = "+en-port")
    fun trendX01Portrait() = capture("trend_x01_portrait_en", x01TrendState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+fr-land")
    fun trendX01Landscape() = capture("trend_x01_landscape_fr", x01TrendState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun trendX01PortraitLargestFont() = capture("trend_x01_portrait_fr_font200", x01TrendState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+en-port")
    fun trendCricketPortrait() = capture("trend_cricket_portrait_en", cricketTrendState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+fr-land")
    fun trendCricketLandscape() = capture("trend_cricket_landscape_fr", cricketTrendState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun trendCricketLandscapeLargestFont() =
        capture("trend_cricket_landscape_fr_font200", cricketTrendState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+en-port")
    fun trendOnePointPortrait() = capture("trend_one_point_portrait_en", onePointState, scrollToTrend = true)

    @Test
    @Config(qualifiers = "+fr-land")
    fun trendOnePointLandscape() = capture("trend_one_point_landscape_fr", onePointState, scrollToTrend = true)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
        const val NOW = 10_000L
    }
}
