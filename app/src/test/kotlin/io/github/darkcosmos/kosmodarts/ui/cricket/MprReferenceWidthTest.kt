package io.github.darkcosmos.kosmodarts.ui.cricket

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CR22: the MPR reference is written with a comma, and the point of the English format must measure the same, so one
 * reference gives the same size in both languages.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MprReferenceWidthTest {

    @Test
    fun `the comma and the point have the same width in the score font`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val density = Density(context)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val style = KosmoTypography.create(density).scoreHuge
            .copy(fontSize = density.fixedSp(KosmoTypography.SCORE_PORTRAIT))

        val comma = measurer.measure("8,8", style, softWrap = false, maxLines = 1).size.width
        val point = measurer.measure("8.8", style, softWrap = false, maxLines = 1).size.width

        assertEquals("8,8", ZONE_MPR_REFERENCE)
        assertEquals("comma $comma px, point $point px", comma, point)
    }
}
