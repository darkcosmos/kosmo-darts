package io.github.darkcosmos.kosmodarts.ui.theme

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The embedded Saira variable font is condensed through its width axis (design system, section 3; plan of issue #49,
 * "Saira under Robolectric"): "501" at width 75 is narrower than at width 100, so the captures show the real width.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SairaWidthSpikeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val measurer = TextMeasurer(createFontFamilyResolver(context), Density(context), LayoutDirection.Ltr)

    private fun width(family: FontFamily): Int =
        measurer.measure("501", TextStyle(fontFamily = family, fontSize = SIZE)).size.width

    @Test
    fun `the width axis condenses the display font`() {
        val condensed = width(FontFamily(sairaFont(WEIGHT, width = SAIRA_DISPLAY_WIDTH)))
        val normal = width(FontFamily(sairaFont(WEIGHT, width = NORMAL_WIDTH)))

        assertTrue("condensed $condensed, normal $normal", condensed < normal * MAX_RATIO)
    }

    private companion object {
        val SIZE = 100.sp
        const val WEIGHT = 800
        const val NORMAL_WIDTH = 100f

        /** The spike measured 330 px against 443 px: width 75 is about three quarters of width 100. */
        const val MAX_RATIO = 0.85f
    }
}
