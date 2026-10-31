package io.github.darkcosmos.kosmodarts.capture.ui.keypad

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.capture.CaptureActivity
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The copied keypad, rendered alone: taps, enabled state, 42 dp landscape keys, and recorded screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class KeypadScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    private val darts = mutableListOf<Dart>()

    private fun show(isEnabled: Boolean = true) {
        darts.clear()
        composeRule.runOnUiThread { composeRule.activity.setContent {
            KosmoDartsTheme {
                Keypad(
                    isEnabled = isEnabled,
                    onDart = { darts += it },
                    keyHeight = KEY_HEIGHT,
                    gap = 4.dp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } }
        composeRule.waitForIdle()
    }

    private fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()

    @Test
    @Config(qualifiers = "+en-land")
    fun tripleThenTwentyEmitsTripleTwenty() {
        show()
        tap(KeypadTags.TRIPLE)
        tap(KeypadTags.number(20))
        assertEquals(listOf(Dart.triple(20)), darts)
        composeRule.onNodeWithTag(KeypadTags.BULL).assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "+en-land")
    fun specialKeysEmitSegments25And50And0() {
        show()
        tap(KeypadTags.OUTER_BULL)
        tap(KeypadTags.BULL)
        tap(KeypadTags.MISS)
        assertEquals(listOf(25, 50, 0), darts.map { it.segment })
    }

    @Test
    @Config(qualifiers = "+en-land")
    fun bullKeysAreDisabledUnderATripleOrDouble() {
        show()
        tap(KeypadTags.TRIPLE)
        composeRule.onNodeWithTag(KeypadTags.BULL).assertIsNotEnabled()
        composeRule.onNodeWithTag(KeypadTags.OUTER_BULL).assertIsNotEnabled()
        tap(KeypadTags.DOUBLE)
        composeRule.onNodeWithTag(KeypadTags.OUTER_BULL).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "+en-land")
    fun aDisabledKeypadEmitsNothing() {
        show(isEnabled = false)
        listOf(KeypadTags.number(1), KeypadTags.TRIPLE, KeypadTags.BULL, KeypadTags.MISS).forEach {
            composeRule.onNodeWithTag(it).assertIsNotEnabled()
            tap(it)
        }
        assertTrue(darts.isEmpty())
    }

    @Test
    @Config(qualifiers = "+en-land")
    fun keysAre42dpTallInLandscape() {
        show()
        composeRule.onNodeWithTag(KeypadTags.number(7)).assertHeightIsEqualTo(KEY_HEIGHT)
        composeRule.onNodeWithTag(KeypadTags.MISS).assertHeightIsEqualTo(KEY_HEIGHT)
    }

    @Test
    @Config(qualifiers = "+en-land")
    fun screenshotEnabledEnglish() = capture("keypad_enabled_en", isEnabled = true)

    @Test
    @Config(qualifiers = "+en-land")
    fun screenshotDisabledEnglish() = capture("keypad_disabled_en", isEnabled = false)

    @Test
    @Config(qualifiers = "+fr-land")
    fun screenshotEnabledFrench() = capture("keypad_enabled_fr", isEnabled = true)

    @Test
    @Config(qualifiers = "+fr-land")
    fun screenshotDisabledFrench() = capture("keypad_disabled_fr", isEnabled = false)

    private fun capture(name: String, isEnabled: Boolean) {
        show(isEnabled)
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/$name.png")
    }

    private companion object {
        val KEY_HEIGHT = 42.dp
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
    }
}
