package io.github.darkcosmos.kosmodarts.ui.format

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DartFormatTest {

    @Test
    fun averageHasTwoDecimals() {
        assertEquals("60.00", formatAverage(60.0, Locale.ENGLISH))
        assertEquals("33.33", formatAverage(100 / 3.0, Locale.ENGLISH))
        assertEquals("66.67", formatAverage(200 / 3.0, Locale.ENGLISH))
    }

    @Test
    fun averageUsesTheLocaleDecimalSeparator() {
        assertEquals("33,33", formatAverage(100 / 3.0, Locale.FRENCH))
    }

    @Test
    fun everyDartHasAKind() {
        assertEquals(DartKind.SINGLE, Dart.single(20).kind)
        assertEquals(DartKind.DOUBLE, Dart.double(20).kind)
        assertEquals(DartKind.TRIPLE, Dart.triple(20).kind)
        assertEquals(DartKind.OUTER_BULL, Dart.OUTER_BULL.kind)
        assertEquals(DartKind.BULL, Dart.BULL.kind)
        assertEquals(DartKind.MISS, Dart.MISS.kind)
    }
}
