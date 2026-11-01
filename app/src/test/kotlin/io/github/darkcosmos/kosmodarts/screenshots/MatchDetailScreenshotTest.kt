package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MatchDetail
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailScreen
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState
import io.github.darkcosmos.kosmodarts.ui.history.cricketDetail
import io.github.darkcosmos.kosmodarts.ui.history.x01Detail
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the match detail (HS5, HS7) for visual review, on a Pixel 7: an X01 match of 2 sets of 3 legs and a
 * four-player Cricket match, in portrait and landscape, French or English, at the largest font, and the read failure.
 * The state is given directly. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MatchDetailScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val longName = "Maximilien-Alexandre de la Tour d'Auvergne"

    private fun loaded(detail: MatchDetail) = MatchDetailState(detail.sessionId).loaded(1, DbResult.Ok(detail))

    private val x01 = loaded(x01Detail(names = listOf(longName, "Bob")))
    private val cricket = loaded(cricketDetail(names = listOf("Anna", longName, "Cleo", "Dan"), isBullInPlay = false))
    private val failed = MatchDetailState(7).loaded(1, DbResult.Failed)

    private fun detail(name: String, state: MatchDetailState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { Detail(state) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/detail_$name.png")
    }

    @Composable
    private fun Detail(state: MatchDetailState) = MatchDetailScreen(state, onEvent = {})

    @Test
    @Config(qualifiers = "+fr-port")
    fun x01PortraitFrench() = detail("x01_portrait_fr", x01)

    @Test
    @Config(qualifiers = "+en-port")
    fun x01PortraitEnglish() = detail("x01_portrait_en", x01)

    @Test
    @Config(qualifiers = "+fr-land")
    fun x01LandscapeFrench() = detail("x01_landscape_fr", x01)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun x01PortraitLargestFont() = detail("x01_portrait_fr_font200", x01)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun x01LandscapeLargestFont() = detail("x01_landscape_fr_font200", x01)

    @Test
    @Config(qualifiers = "fr-w411dp-h3600dp-xxhdpi")
    fun x01TallPortraitFrench() = detail("x01_tall_portrait_fr", x01)

    @Test
    @Config(qualifiers = "+fr-port")
    fun cricketPortraitFrench() = detail("cricket_portrait_fr", cricket)

    @Test
    @Config(qualifiers = "+en-port")
    fun cricketPortraitEnglish() = detail("cricket_portrait_en", cricket)

    @Test
    @Config(qualifiers = "+fr-land")
    fun cricketLandscapeFrench() = detail("cricket_landscape_fr", cricket)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun cricketPortraitLargestFont() = detail("cricket_portrait_fr_font200", cricket)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun cricketLandscapeLargestFont() = detail("cricket_landscape_fr_font200", cricket)

    @Test
    @Config(qualifiers = "+fr-port")
    fun failedPortraitFrench() = detail("failed_portrait_fr", failed)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun failedLandscapeLargestFont() = detail("failed_landscape_fr_font200", failed)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
