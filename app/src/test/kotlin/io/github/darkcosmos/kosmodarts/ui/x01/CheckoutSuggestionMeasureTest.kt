package io.github.darkcosmos.kosmodarts.ui.x01

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRoutes
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.format.checkoutRouteText
import io.github.darkcosmos.kosmodarts.ui.game.checkoutReferenceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CS8 (docs/rules/checkout-suggestions.md), exhaustive: every route the engine can suggest (remaining 1 to 180, 1 to
 * 3 darts left, both rules) is never taller than the reserved line (the label then three times the widest dart text),
 * is never cut and only wraps after a dot, at every width from 320 dp to 420 dp (the card line of a Pixel 7 is about
 * 354 dp wide in portrait and 406 dp in landscape), in English and French, at font scale 1 and 2. Not checked
 * below 320 dp: at 200 dp and font scale 2, the label and the first dart alone no longer fit on one line.
 * The texts and the reference line come from the production formatters (`checkoutRouteText`,
 * `checkoutReferenceText`); [CheckoutSuggestionScreenTest] checks that the screen shows exactly this notation.
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: without native graphics, Robolectric measures text as almost zero wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CheckoutSuggestionMeasureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // The style of CheckoutSuggestionLine: titleMedium of the default Material 3 typography (as the app theme).
    private val style = Typography().titleMedium

    // The production formatter, as CheckoutSuggestionLine.
    private fun lineText(route: List<Dart>): String =
        context.getString(R.string.game_checkout_suggestion, checkoutRouteText(context.resources, route))

    private fun allRoutes(): List<List<Dart>> = CheckoutRule.entries.flatMap { rule ->
        (1..3).flatMap { dartsLeft -> (1..MAX_SCORE).mapNotNull { CheckoutRoutes.suggest(it, dartsLeft, rule) } }
    }.distinct()

    private fun assertComplete(layout: TextLayoutResult, text: String, what: String) {
        assertEquals("$what is cut", text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue("$what is cut vertically", !layout.didOverflowHeight)
        (0 until layout.lineCount).forEach { line ->
            assertTrue("$what line $line ends outside", layout.getLineRight(line) <= layout.size.width + EPSILON)
            if (line < layout.lineCount - 1) {
                assertEquals("$what breaks inside a dart", ' ', text[layout.getLineEnd(line) - 1])
            }
        }
    }

    private fun checkAllRoutesFitTheReservedLine() {
        val texts = allRoutes().map(::lineText)
        val density = Density(context)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        // The reference line of CheckoutSuggestionLine: the label, then three times the widest dart text.
        val reference = checkoutReferenceText(context.resources, style, measurer)
        (MIN_WIDTH_DP..MAX_WIDTH_DP step WIDTH_STEP_DP).forEach { widthDp ->
            val constraints = Constraints(maxWidth = with(density) { widthDp.dp.roundToPx() })
            val reserved = measurer.measure(reference, style, constraints = constraints)
            assertComplete(reserved, reference, "reference at $widthDp dp")
            texts.forEach { text ->
                val layout = measurer.measure(text, style, constraints = constraints)
                val what = "'$text' at $widthDp dp"
                assertTrue("$what is taller than the reserved line", layout.size.height <= reserved.size.height)
                assertComplete(layout, text, what)
            }
        }
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `CS8 every route fits the reserved line, English, font scale 1`() =
        checkAllRoutesFitTheReservedLine()

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 every route fits the reserved line, English, largest font scale`() =
        checkAllRoutesFitTheReservedLine()

    @Test
    @Config(qualifiers = "+fr-port")
    fun `CS8 every route fits the reserved line, French, font scale 1`() =
        checkAllRoutesFitTheReservedLine()

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 every route fits the reserved line, French, largest font scale`() =
        checkAllRoutesFitTheReservedLine()

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f
        const val EPSILON = 0.5f
        const val MAX_SCORE = 180
        const val MIN_WIDTH_DP = 320
        const val MAX_WIDTH_DP = 420
        const val WIDTH_STEP_DP = 4
    }
}
