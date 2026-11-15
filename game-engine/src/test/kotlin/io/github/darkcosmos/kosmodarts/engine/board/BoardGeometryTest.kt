package io.github.darkcosmos.kosmodarts.engine.board

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class BoardGeometryTest {

    private fun polar(radiusMm: Double, degreesClockwiseFromTop: Double): BoardPoint {
        val angle = Math.toRadians(degreesClockwiseFromTop)
        return BoardPoint(radiusMm * sin(angle), radiusMm * cos(angle))
    }

    private fun zone(number: Int, ring: BoardZone.Ring) = BoardZone(number, ring)

    @Test
    fun `BC4 BoardGeometry radii are ordered from the bull to the double ring`() {
        val radii = listOf(
            BoardGeometry.BULL_RADIUS_MM, BoardGeometry.OUTER_BULL_RADIUS_MM, BoardGeometry.TRIPLE_INNER_MM,
            BoardGeometry.TRIPLE_OUTER_MM, BoardGeometry.DOUBLE_INNER_MM, BoardGeometry.DOUBLE_OUTER_MM,
        )
        assertEquals(radii.sorted(), radii)
        assertEquals(20, BoardGeometry.SECTOR_ORDER.size)
        assertEquals(360.0, BoardGeometry.SECTOR_ANGLE_DEGREES * BoardGeometry.SECTOR_ORDER.size)
    }

    @Test
    fun `BC4 every nominal point lies inside its own zone`() {
        // The nominal point of the 25 is the origin (BC2), which is the bull: the only exception.
        Dart.SCORING.filter { it != Dart.OUTER_BULL }.forEach { dart ->
            val point = requireNotNull(nominalPoint(dart))
            assertEquals(BoardZone.of(dart), BoardGeometry.zoneAt(point), "$dart")
        }
        assertEquals(BoardZone.of(Dart.BULL), BoardGeometry.zoneAt(BoardPoint(0.0, 0.0)))
    }

    @Test
    fun `BC5 zoneAt gives the zone of each ring along the 20 axis`() {
        fun at(radius: Double) = BoardGeometry.zoneAt(BoardPoint(0.0, radius))
        assertEquals(zone(50, BoardZone.Ring.BULL), at(0.0))
        assertEquals(zone(25, BoardZone.Ring.OUTER_BULL), at(10.0))
        assertEquals(zone(20, BoardZone.Ring.SINGLE), at(50.0))
        assertEquals(zone(20, BoardZone.Ring.TRIPLE), at(103.0))
        assertEquals(zone(20, BoardZone.Ring.SINGLE), at(130.0))
        assertEquals(zone(20, BoardZone.Ring.DOUBLE), at(166.0))
    }

    @Test
    fun `BC5 zoneAt is null outside the double ring`() {
        assertNull(BoardGeometry.zoneAt(BoardPoint(0.0, BoardGeometry.DOUBLE_OUTER_MM + 0.001)))
        assertNull(BoardGeometry.zoneAt(BoardPoint(-300.0, 300.0)))
    }

    @Test
    fun `BC5 a point on a ring radius belongs to exactly one zone`() {
        fun at(radius: Double) = BoardGeometry.zoneAt(BoardPoint(0.0, radius))
        assertEquals(zone(25, BoardZone.Ring.OUTER_BULL), at(BoardGeometry.BULL_RADIUS_MM))
        assertEquals(zone(20, BoardZone.Ring.SINGLE), at(BoardGeometry.OUTER_BULL_RADIUS_MM))
        assertEquals(zone(20, BoardZone.Ring.TRIPLE), at(BoardGeometry.TRIPLE_INNER_MM))
        assertEquals(zone(20, BoardZone.Ring.SINGLE), at(BoardGeometry.TRIPLE_OUTER_MM))
        assertEquals(zone(20, BoardZone.Ring.DOUBLE), at(BoardGeometry.DOUBLE_INNER_MM))
        assertEquals(zone(20, BoardZone.Ring.DOUBLE), at(BoardGeometry.DOUBLE_OUTER_MM))
    }

    @Test
    fun `BC5 a point on a sector edge belongs to one of its two sectors only`() {
        BoardGeometry.SECTOR_ORDER.indices.forEach { index ->
            val edgeDegrees = index * BoardGeometry.SECTOR_ANGLE_DEGREES + BoardGeometry.SECTOR_ANGLE_DEGREES / 2
            val next = BoardGeometry.SECTOR_ORDER[(index + 1) % BoardGeometry.SECTOR_ORDER.size]
            val current = BoardGeometry.SECTOR_ORDER[index]
            val onEdge = BoardGeometry.zoneAt(polar(130.0, edgeDegrees))
            assertEquals(true, onEdge?.number == current || onEdge?.number == next, "edge after $current")
            assertEquals(current, BoardGeometry.zoneAt(polar(130.0, edgeDegrees - 0.01))?.number)
            assertEquals(next, BoardGeometry.zoneAt(polar(130.0, edgeDegrees + 0.01))?.number)
        }
    }

    @Test
    fun `BC1 zoneAt follows the axes of the board`() {
        assertEquals(6, BoardGeometry.zoneAt(BoardPoint(130.0, 0.0))?.number)
        assertEquals(3, BoardGeometry.zoneAt(BoardPoint(0.0, -130.0))?.number)
        assertEquals(11, BoardGeometry.zoneAt(BoardPoint(-130.0, 0.0))?.number)
    }
}
