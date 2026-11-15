package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndTab
import io.github.darkcosmos.kosmodarts.ui.board.FOUR_NAMES
import io.github.darkcosmos.kosmodarts.ui.board.LONG_NAMES
import io.github.darkcosmos.kosmodarts.ui.board.cricketEnd
import io.github.darkcosmos.kosmodarts.ui.board.reach
import io.github.darkcosmos.kosmodarts.ui.board.showEndScreen
import io.github.darkcosmos.kosmodarts.ui.board.x01BetweenLegs
import io.github.darkcosmos.kosmodarts.ui.board.x01End
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the tabs "Result" and "Leg heatmap" of the between-legs and end screens (HM9, HM10, D10, D11) on a
 * Pixel 7 only: portrait and landscape, 200 % font, 4 players with long names, Cricket, solo, and the scrolled right
 * column. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EndHeatmapScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, screen: EndHeatmapScreen, tab: EndTab, scrollToAction: String? = null) {
        val shown = screen.withHeatmap(tab, screen.heatmapPlayer, null) as EndHeatmapScreen
        showEndScreen(composeRule, shown, mutableListOf())
        scrollToAction?.let { composeRule.onNodeWithTag(it, useUnmergedTree = true).reach() }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/$name.png")
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun resultTabBetweenLegsPortrait() =
        capture("end_tabs_result_between_portrait_en", x01BetweenLegs(FOUR_NAMES.take(2)), EndTab.RESULT)

    @Test
    @Config(qualifiers = "+en-land")
    fun resultTabBetweenLegsLandscape() =
        capture("end_tabs_result_between_landscape_en", x01BetweenLegs(FOUR_NAMES.take(2)), EndTab.RESULT)

    @Test
    @Config(qualifiers = "+en-land", fontScale = 2.0f)
    fun resultTabBetweenLegsLandscapeFourPlayersLargeFont() =
        capture("end_tabs_result_between_landscape_4p_font200_en", x01BetweenLegs(FOUR_NAMES), EndTab.RESULT)

    @Test
    @Config(qualifiers = "+en-port")
    fun heatmapTabBetweenLegsPortrait() =
        capture("end_tabs_heatmap_between_portrait_en", x01BetweenLegs(FOUR_NAMES.take(2)), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+fr-port")
    fun heatmapTabBetweenLegsPortraitFrench() =
        capture("end_tabs_heatmap_between_portrait_fr", x01BetweenLegs(FOUR_NAMES.take(2)), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+en-land")
    fun heatmapTabBetweenLegsLandscape() =
        capture("end_tabs_heatmap_between_landscape_en", x01BetweenLegs(FOUR_NAMES.take(2)), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+en-port", fontScale = 2.0f)
    fun heatmapTabEndPortraitLargeFont() =
        capture("end_tabs_heatmap_end_portrait_font200_en", x01End(FOUR_NAMES.take(2)), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun heatmapTabEndPortraitLargeFontFrench() =
        capture("end_tabs_heatmap_end_portrait_font200_fr", x01End(FOUR_NAMES.take(2)), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+en-port")
    fun heatmapTabEndFourLongNames() =
        capture("end_tabs_heatmap_end_4p_long_names_en", x01End(LONG_NAMES), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+en-port")
    fun heatmapTabCricketEndCombinedMissLabel() =
        capture("end_tabs_heatmap_cricket_end_3p_en", cricketEnd(FOUR_NAMES.take(3)), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+en-port")
    fun heatmapTabEndSolo() = capture("end_tabs_heatmap_end_solo_en", x01End(listOf("Ann")), EndTab.LEG_HEATMAP)

    @Test
    @Config(qualifiers = "+en-land", fontScale = 2.0f)
    fun heatmapTabEndLandscapeRightColumnScrolled() = capture(
        "end_tabs_heatmap_end_landscape_font200_scrolled_en",
        x01End(FOUR_NAMES),
        EndTab.LEG_HEATMAP,
        scrollToAction = EndTags.NEW_GAME,
    )

    @Test
    @Config(qualifiers = "+en-land")
    fun heatmapTabEndLandscape() =
        capture("end_tabs_heatmap_end_landscape_en", x01End(FOUR_NAMES.take(2)), EndTab.LEG_HEATMAP)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
    }
}
