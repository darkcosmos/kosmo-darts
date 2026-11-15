package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** HM6, PS8: the texts of a zone (name, darts, percentage) in English and in French, with plurals. */
@RunWith(AndroidJUnit4::class)
class ZoneTextTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources
    private val triple20 = BoardZone(20, Ring.TRIPLE)

    private fun figure(zone: BoardZone, count: Int, share: Double) =
        ZoneFigure(zone, zone.displayName(), count, share)

    @Test
    @Config(qualifiers = "en")
    fun zoneNamesAreEnglish() {
        assertEquals("Triple 20", triple20.displayName().text(resources))
        assertEquals("Single 5", BoardZone(5, Ring.SINGLE).displayName().text(resources))
        assertEquals("Double 16", BoardZone(16, Ring.DOUBLE).displayName().text(resources))
        assertEquals("Outer bull (25)", BoardZone(Dart.BULL_SEGMENT, Ring.OUTER_BULL).displayName().text(resources))
        assertEquals("Bull", BoardZone(Dart.BULL_SEGMENT, Ring.BULL).displayName().text(resources))
    }

    @Test
    @Config(qualifiers = "fr")
    fun zoneNamesAreFrench() {
        assertEquals("Triple 20", triple20.displayName().text(resources))
        assertEquals("Simple 5", BoardZone(5, Ring.SINGLE).displayName().text(resources))
        assertEquals("Bull extérieur (25)", BoardZone(Dart.BULL_SEGMENT, Ring.OUTER_BULL).displayName().text(resources))
    }

    @Test
    @Config(qualifiers = "en")
    fun dartCountsAreEnglishPlurals() {
        assertEquals("0 darts", dartsText(resources, 0))
        assertEquals("1 dart", dartsText(resources, 1))
        assertEquals("12 darts", dartsText(resources, 12))
    }

    @Test
    @Config(qualifiers = "fr")
    fun dartCountsAreFrenchPlurals() {
        assertEquals("0 fléchette", dartsText(resources, 0))
        assertEquals("1 fléchette", dartsText(resources, 1))
        assertEquals("12 fléchettes", dartsText(resources, 12))
    }

    @Test
    @Config(qualifiers = "en")
    fun detailAndDescriptionCarryTheCountAndThePercentage() {
        val figure = figure(triple20, 12, 0.0467)
        assertEquals("12 darts · 4.7 %", figure.detail(resources, Locale.ENGLISH))
        assertEquals("Triple 20, 12 darts, 4.7 %", figure.description(resources, Locale.ENGLISH))
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchPercentageUsesTheDecimalComma() {
        val figure = figure(triple20, 1, 0.0467)
        assertEquals("1 fléchette · 4,7 %", figure.detail(resources, Locale.FRENCH))
        assertEquals("Triple 20, 1 fléchette, 4,7 %", figure.description(resources, Locale.FRENCH))
    }
}
