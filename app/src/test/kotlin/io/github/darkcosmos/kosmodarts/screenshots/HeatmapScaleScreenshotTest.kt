package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.TestDevices
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import io.github.darkcosmos.kosmodarts.ui.board.Dartboard
import io.github.darkcosmos.kosmodarts.ui.board.HeatmapLegend
import io.github.darkcosmos.kosmodarts.ui.board.HeatmapScale
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the relative heatmap scale (HM5, gate G1) with its legend, for visual review against
 * `docs/design/mockups/heatmap/heatmap-relative.svg`, on a Pixel 7 in portrait and landscape: the spread data set
 * (at most 7 darts per zone) and the concentrated one (T20 45, S20 30, S5 20, S1 17 of 150 darts). Record only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HeatmapScaleScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val total = 150.0

    private val concentratedMain: Map<BoardZone, Double> = mapOf(
        BoardZone(20, Ring.TRIPLE) to 45,
        BoardZone(20, Ring.SINGLE) to 30,
        BoardZone(5, Ring.SINGLE) to 20,
        BoardZone(1, Ring.SINGLE) to 17,
    ).mapValues { it.value / total }

    /** 14 other zones with one or two darts; the four main zones are applied last so that they keep their value. */
    private val concentrated: Map<BoardZone, Double> = BoardZone.ALL.drop(CONCENTRATED_SKIP).take(CONCENTRATED_TAIL)
        .withIndex().associate { it.value to (1 + it.index % 2) / total } + concentratedMain

    /** 48 zones with 1 to 7 darts each (138 darts): a deterministic spread in the spirit of the mockup. */
    private val spread: Map<BoardZone, Double> = BoardZone.ALL.filterIndexed { i, _ -> i % SPREAD_STEP != 0 }
        .take(SPREAD_ZONES).withIndex().associate { it.value to (1 + it.index * SPREAD_MUL % SPREAD_MAX) / total }

    @Composable
    private fun Screen(shares: Map<BoardZone, Double>) {
        KosmoDartsTheme {
            Column(
                Modifier.fillMaxSize().background(KosmoTheme.colors.background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Dartboard(
                        colors = HeatmapScale.colors(shares),
                        neutral = HeatmapScale.NEUTRAL,
                        selected = null,
                        onZoneTapped = {},
                        contentDescription = "Dartboard",
                        zoneDescription = { "${it.number}" },
                    )
                }
                HeatmapLegend(HeatmapScale.maxShare(shares), Modifier.widthIn(max = LEGEND_MAX_WIDTH))
            }
        }
    }

    private fun capture(name: String, shares: Map<BoardZone, Double>) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { Screen(shares) }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/heatmap_scale_$name.png")
    }

    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
    fun spreadPortrait() = capture("spread_portrait", spread)

    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_PORTRAIT)
    fun concentratedPortrait() = capture("concentrated_portrait", concentrated)

    @Test
    @Config(qualifiers = TestDevices.FR_PIXEL7_LANDSCAPE)
    fun spreadLandscapeFrench() = capture("spread_landscape_fr", spread)

    @Test
    @Config(qualifiers = TestDevices.EN_PIXEL7_LANDSCAPE)
    fun concentratedLandscape() = capture("concentrated_landscape", concentrated)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val CONCENTRATED_SKIP = 10
        const val CONCENTRATED_TAIL = 14
        const val SPREAD_STEP = 4
        const val SPREAD_ZONES = 48
        const val SPREAD_MUL = 5
        const val SPREAD_MAX = 7
        val LEGEND_MAX_WIDTH = 480.dp
    }
}
