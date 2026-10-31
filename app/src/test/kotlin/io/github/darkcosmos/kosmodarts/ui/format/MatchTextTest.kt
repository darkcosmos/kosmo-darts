package io.github.darkcosmos.kosmodarts.ui.format

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** The texts of the match on the redesigned screens (M14, M19; U2, U4; owner Q12), in English and French. */
@RunWith(AndroidJUnit4::class)
class MatchTextTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    @Config(qualifiers = "en")
    fun `M14 M19 English positions, chips and tile line (owner Q12)`() {
        assertEquals("SET 2 · LEG 3", positionText(resources, MatchPosition(2, 3)))
        assertEquals("LEG 3", positionText(resources, MatchPosition(null, 3)))
        assertNull(positionText(resources, MatchPosition(null, null)))
        assertEquals("S2 · L3", positionText(resources, MatchPosition(2, 3), isShort = true))
        assertEquals("L3", positionText(resources, MatchPosition(null, 3), isShort = true))
        assertEquals(listOf("S1", "L2"), chipTexts(resources, MatchIndicator(1, 2)))
        assertEquals(listOf("L2"), chipTexts(resources, MatchIndicator(null, 2)))
        assertEquals("S0 · L1", tileMatchText(resources, MatchIndicator(0, 1)))
        assertEquals("L1", tileMatchText(resources, MatchIndicator(null, 1)))
        assertEquals("1 set won, 2 legs won", indicatorDescription(resources, MatchIndicator(1, 2)))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `M14 M19 French positions, chips and tile line`() {
        assertEquals("SET 2 · MANCHE 3", positionText(resources, MatchPosition(2, 3)))
        assertEquals("MANCHE 3", positionText(resources, MatchPosition(null, 3)))
        assertEquals(listOf("S1", "M2"), chipTexts(resources, MatchIndicator(1, 2)))
        assertEquals("S0 · M1", tileMatchText(resources, MatchIndicator(0, 1)))
        assertEquals("S2 · M3", positionText(resources, MatchPosition(2, 3), isShort = true))
        assertEquals("M3", positionText(resources, MatchPosition(null, 3), isShort = true))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `format lines in French`() {
        assertEquals("501 · SORTIE DOUBLE", modeText(resources, 501, CheckoutRule.DOUBLE_OUT))
        assertEquals("301 · SORTIE SIMPLE", formatText(resources, 301, CheckoutRule.SINGLE_OUT, MatchFormat()))
        assertEquals(
            "501 · 1ER À 2 SETS · 1ER À 3 MANCHES",
            formatText(resources, 501, CheckoutRule.DOUBLE_OUT, MatchFormat(legsPerSet = 3, sets = 2)),
        )
        assertEquals(
            "501 · 1ER À 2 SETS · 1ER À 1 MANCHE",
            formatText(resources, 501, CheckoutRule.DOUBLE_OUT, MatchFormat(legsPerSet = 1, sets = 2)),
        )
        assertEquals(
            "501 · 1ER À 3 MANCHES",
            formatText(resources, 501, CheckoutRule.DOUBLE_OUT, MatchFormat(legsPerSet = 3, sets = 1)),
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun `U2 U5 English checkout texts are plurals`() {
        assertEquals("141 IN 3 DARTS", checkoutDartsText(resources, 141, 3))
        assertEquals("40 IN 1 DART", checkoutDartsText(resources, 40, 1))
        assertEquals("Checkout, 141 in 3 darts", checkoutAnnouncement(resources, 141, 3))
        assertEquals("Checkout, 40 in 1 dart", checkoutAnnouncement(resources, 40, 1))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `U2 U5 French checkout texts are plurals`() {
        assertEquals("141 EN 3 FLÉCHETTES", checkoutDartsText(resources, 141, 3))
        assertEquals("40 EN 1 FLÉCHETTE", checkoutDartsText(resources, 40, 1))
        assertEquals("Checkout, 141 en 3 fléchettes", checkoutAnnouncement(resources, 141, 3))
    }

    @Test
    @Config(qualifiers = "en")
    fun `U4 next player description, plural on the points`() {
        assertEquals("Next player: Anna, 1 point left", nextPlayerDescription(resources, "Anna", 1))
        assertEquals("Next player: Anna, 301 points left", nextPlayerDescription(resources, "Anna", 301))
    }

    @Test
    fun `names are shown in capitals and averages with the decimal comma in French`() {
        assertEquals("ANNE-SOPHIE", displayName("Anne-Sophie", Locale.FRENCH))
        assertEquals("ZOÉ", displayName("Zoé", Locale.FRENCH))
        assertEquals("62,40", formatAverage(62.4, Locale.FRENCH))
        assertEquals("62.40", formatAverage(62.4, Locale.ENGLISH))
    }
}
