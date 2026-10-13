package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the home screen for visual review, on a Pixel 7 (JVM only, Robolectric + Roborazzi).
 *
 * Record only: images are written to the directory given by the `kosmodarts.screenshot.dir` system property (set by
 * the Gradle build), nothing is compared. Method-level qualifiers starting with `+` are added to the Pixel 7 ones.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/$name.png")
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun homePortraitEnglish() = capture("home_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun homeLandscapeEnglish() = capture("home_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun homePortraitFrench() = capture("home_portrait_fr")

    @Test
    @Config(qualifiers = "+fr-land")
    fun homeLandscapeFrench() = capture("home_landscape_fr")

    /** 2.0 is the largest font size offered by Android 14 and later. */
    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun homePortraitEnglishLargestFont() = capture("home_portrait_en_font200")

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f
    }
}
