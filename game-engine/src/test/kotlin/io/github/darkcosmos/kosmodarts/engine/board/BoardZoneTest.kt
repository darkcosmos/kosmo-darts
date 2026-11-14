package io.github.darkcosmos.kosmodarts.engine.board

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BoardZoneTest {

    @Test
    fun `HM1 BoardZone has 62 distinct zones`() {
        assertEquals(62, BoardZone.ALL.size)
        assertEquals(62, BoardZone.ALL.toSet().size)
        assertEquals(20, BoardZone.ALL.count { it.ring == BoardZone.Ring.SINGLE })
    }

    @Test
    fun `HM1 BoardZone of a dart maps both single rings to the one single zone`() {
        assertEquals(BoardZone(20, BoardZone.Ring.SINGLE), BoardZone.of(Dart.single(20)))
        assertEquals(BoardZone(7, BoardZone.Ring.DOUBLE), BoardZone.of(Dart.double(7)))
        assertEquals(BoardZone(7, BoardZone.Ring.TRIPLE), BoardZone.of(Dart.triple(7)))
        assertEquals(BoardZone.Ring.OUTER_BULL, BoardZone.of(Dart.OUTER_BULL)?.ring)
        assertEquals(BoardZone.Ring.BULL, BoardZone.of(Dart.BULL)?.ring)
    }

    @Test
    fun `HM1 BoardZone of a miss is null`() {
        assertNull(BoardZone.of(Dart.MISS))
    }

    @Test
    fun `HM1 BoardZone of every scoring dart is one of the 62 zones`() {
        Dart.SCORING.forEach { assertEquals(true, BoardZone.of(it) in BoardZone.ALL, "$it") }
    }
}
