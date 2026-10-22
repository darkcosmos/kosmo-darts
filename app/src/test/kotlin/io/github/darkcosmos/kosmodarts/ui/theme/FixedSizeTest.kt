package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design system, section 3: the "fixed" styles ignore the Android font size setting: at font scale 1.0 and 2.0, the
 * score and the keys are exactly as high as the same texts measured at font scale 1.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FixedSizeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun heights(): Pair<Float, Float> {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                KosmoDartsTheme {
                    Text("501", style = KosmoTheme.typography.scoreHuge, modifier = Modifier.testTag(SCORE))
                    Text("20", style = KosmoTheme.typography.keypad, modifier = Modifier.testTag(KEY))
                }
            }
        }
        composeRule.waitForIdle()
        val score = composeRule.onNodeWithTag(SCORE).getUnclippedBoundsInRoot()
        val key = composeRule.onNodeWithTag(KEY).getUnclippedBoundsInRoot()
        return (score.bottom - score.top).value to (key.bottom - key.top).value
    }

    @Test
    fun `the score and the keys keep their size at font scale 1`() = check()

    @Test
    @Config(fontScale = 2.0f)
    fun `the score and the keys keep their size at the largest font scale`() = check()

    /** The heights the two texts have at font scale 1, measured with the same font and styles. */
    private fun reference(): Pair<Float, Float> {
        val context = composeRule.activity
        val unscaled = Density(context.resources.displayMetrics.density, fontScale = 1f)
        val styles = KosmoTypography.create(unscaled)
        val measurer = TextMeasurer(createFontFamilyResolver(context), unscaled, LayoutDirection.Ltr)
        fun heightDp(text: String, style: TextStyle) =
            with(unscaled) { measurer.measure(text, style).size.height.toDp().value }
        return heightDp("501", styles.scoreHuge) to heightDp("20", styles.keypad)
    }

    private fun check() {
        val (score, key) = heights()
        val (expectedScore, expectedKey) = reference()
        assertEquals(expectedScore, score, TOLERANCE)
        assertEquals(expectedKey, key, TOLERANCE)
    }

    private companion object {
        const val SCORE = "score"
        const val KEY = "key"
        const val TOLERANCE = 1f
    }
}
