package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the pending-checkout banner (M15, design system 6.3) at font scales 1.0, 1.3, 1.5 and 2.0, English and
 * French, portrait and landscape, on a Pixel 7 (issue #69). Four players in a sets match: Ann leaves 121 (the zone
 * with the S and M chips), then T20 T11 D14 checks out. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CheckoutBannerScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { PENDING_CHECKOUT.forEach(viewModel::onEvent) }
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/$name.png")
    }

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 1.0f)
    fun frenchPortraitScale100() = capture("checkout_banner_portrait_fr_font100")

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 1.3f)
    fun frenchPortraitScale130() = capture("checkout_banner_portrait_fr_font130")

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 1.5f)
    fun frenchPortraitScale150() = capture("checkout_banner_portrait_fr_font150")

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun frenchPortraitScale200() = capture("checkout_banner_portrait_fr_font200")

    @Test
    @Config(qualifiers = "+en-port", fontScale = 1.0f)
    fun englishPortraitScale100() = capture("checkout_banner_portrait_en_font100")

    @Test
    @Config(qualifiers = "+en-port", fontScale = 1.3f)
    fun englishPortraitScale130() = capture("checkout_banner_portrait_en_font130")

    @Test
    @Config(qualifiers = "+en-port", fontScale = 1.5f)
    fun englishPortraitScale150() = capture("checkout_banner_portrait_en_font150")

    @Test
    @Config(qualifiers = "+en-port", fontScale = 2.0f)
    fun englishPortraitScale200() = capture("checkout_banner_portrait_en_font200")

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.0f)
    fun frenchLandscapeScale100() = capture("checkout_banner_landscape_fr_font100")

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.3f)
    fun frenchLandscapeScale130() = capture("checkout_banner_landscape_fr_font130")

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.5f)
    fun frenchLandscapeScale150() = capture("checkout_banner_landscape_fr_font150")

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 2.0f)
    fun frenchLandscapeScale200() = capture("checkout_banner_landscape_fr_font200")

    @Test
    @Config(qualifiers = "+en-land", fontScale = 1.0f)
    fun englishLandscapeScale100() = capture("checkout_banner_landscape_en_font100")

    @Test
    @Config(qualifiers = "+en-land", fontScale = 1.3f)
    fun englishLandscapeScale130() = capture("checkout_banner_landscape_en_font130")

    @Test
    @Config(qualifiers = "+en-land", fontScale = 1.5f)
    fun englishLandscapeScale150() = capture("checkout_banner_landscape_en_font150")

    @Test
    @Config(qualifiers = "+en-land", fontScale = 2.0f)
    fun englishLandscapeScale200() = capture("checkout_banner_landscape_en_font200")

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"

        fun triple(number: Int): Array<AppEvent> =
            arrayOf(AppEvent.MultiplierTapped(Multiplier.TRIPLE), AppEvent.NumberTapped(number))

        /** Ann leaves 121, the three others miss, then Ann throws T20 T11 D14: the checkout waits for confirmation. */
        val PENDING_CHECKOUT: List<AppEvent> = buildList {
            add(AppEvent.NewGame(GameMode.X01))
            add(AppEvent.StartScoreSelected(301))
            repeat(2) { add(AppEvent.PlayerAdded) }
            listOf("Ann", "Bob", "Cid", "Dan").forEachIndexed { index, name ->
                add(AppEvent.NameChanged(index, name))
            }
            repeat(2) { add(AppEvent.LegsPerSetChanged(1)) }
            repeat(2) { add(AppEvent.SetsChanged(1)) }
            add(AppEvent.StartRequested)
            addAll(triple(20) + triple(20) + triple(20))
            add(AppEvent.NextPlayerTapped)
            repeat(3) {
                repeat(3) { add(AppEvent.MissTapped) }
                add(AppEvent.NextPlayerTapped)
            }
            addAll(triple(20) + triple(11))
            add(AppEvent.MultiplierTapped(Multiplier.DOUBLE))
            add(AppEvent.NumberTapped(14))
        }
    }
}
