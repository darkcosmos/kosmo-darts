package io.github.darkcosmos.kosmodarts.ui.x01

import android.content.Context
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
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRoutes
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.components.checkoutReferenceRoute
import io.github.darkcosmos.kosmodarts.ui.format.checkoutRouteText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CS8 (docs/rules/checkout-suggestions.md, as amended by issue #49), exhaustive: every route the engine can suggest
 * (remaining 1 to 180, 1 to 3 darts left, both rules), written in the route style of the zone (Saira, `route`), is
 * never taller than the reserved route (three times the widest dart text), is never cut and only wraps between two
 * darts, at every width of the darts part of the bar from 120 dp to 420 dp, in English and French, at font scale 1
 * and 2. The texts and the reference come from the production code (`checkoutRouteText`, `checkoutReferenceRoute`);
 * [CheckoutSuggestionScreenTest] checks that the screen shows exactly this notation.
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: without native graphics, Robolectric measures text as almost zero wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CheckoutSuggestionMeasureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun allRoutes(): List<List<Dart>> = CheckoutRule.entries.flatMap { rule ->
        (1..3).flatMap { dartsLeft -> (1..MAX_SCORE).mapNotNull { CheckoutRoutes.suggest(it, dartsLeft, rule) } }
    }.distinct()

    private fun assertComplete(layout: TextLayoutResult, text: String, what: String) {
        assertEquals("$what is cut", text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue("$what is cut vertically", !layout.didOverflowHeight)
        (0 until layout.lineCount).forEach { line ->
            assertTrue("$what line $line ends outside", layout.getLineRight(line) <= layout.size.width + EPSILON)
            if (line < layout.lineCount - 1) {
                assertTrue("$what breaks inside a dart", text[layout.getLineEnd(line) - 1].isWhitespace())
            }
        }
    }

    private fun checkAllRoutesFitTheReservedRoute() {
        val texts = allRoutes().map { checkoutRouteText(context.resources, it) }
        val density = Density(context)
        val style = KosmoTypography.create(density).route
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val reference = checkoutRouteText(context.resources, checkoutReferenceRoute(context.resources, style, measurer))
        (MIN_WIDTH_DP..MAX_WIDTH_DP step WIDTH_STEP_DP).forEach { widthDp ->
            val constraints = Constraints(maxWidth = with(density) { widthDp.dp.roundToPx() })
            val reserved = measurer.measure(reference, style, constraints = constraints)
            assertComplete(reserved, reference, "reference at $widthDp dp")
            texts.forEach { text ->
                val layout = measurer.measure(text, style, constraints = constraints)
                val what = "'$text' at $widthDp dp"
                assertTrue("$what is taller than the reserved route", layout.size.height <= reserved.size.height)
                assertComplete(layout, text, what)
            }
        }
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `CS8 every route fits the reserved route, English, font scale 1`() = checkAllRoutesFitTheReservedRoute()

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 every route fits the reserved route, English, largest font scale`() =
        checkAllRoutesFitTheReservedRoute()

    @Test
    @Config(qualifiers = "+fr-port")
    fun `CS8 every route fits the reserved route, French, font scale 1`() = checkAllRoutesFitTheReservedRoute()

    @Test
    @Config(qualifiers = "+fr-port", fontScale = LARGEST_FONT_SCALE)
    fun `CS8 every route fits the reserved route, French, largest font scale`() =
        checkAllRoutesFitTheReservedRoute()

    private companion object {
        const val LARGEST_FONT_SCALE = 2.0f
        const val EPSILON = 0.5f
        const val MAX_SCORE = 180
        const val MIN_WIDTH_DP = 120
        const val MAX_WIDTH_DP = 420
        const val WIDTH_STEP_DP = 4
    }
}
