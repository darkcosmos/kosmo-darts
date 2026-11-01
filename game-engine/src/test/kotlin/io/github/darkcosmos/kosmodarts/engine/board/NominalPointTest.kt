package io.github.darkcosmos.kosmodarts.engine.board

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.atan2
import kotlin.math.hypot

class NominalPointTest {

    private val clockwise = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    private fun point(dart: Dart): BoardPoint = requireNotNull(nominalPoint(dart))

    /** BC2: radius is the mean radius of the zone and the angle is the sector bisector, for all 60 zones. */
    @Test
    fun `BC2 every zone is at its mean radius on its sector bisector`() {
        // Literal radii (WDF / PDC): single (107 + 162) / 2, triple (99 + 107) / 2, double (162 + 170) / 2.
        val expected = mapOf(Multiplier.SINGLE to 134.5, Multiplier.TRIPLE to 103.0, Multiplier.DOUBLE to 166.0)
        clockwise.forEachIndexed { index, number ->
            for ((multiplier, radius) in expected) {
                val p = point(Dart(number, multiplier))
                assertEquals(radius, hypot(p.xMm, p.yMm), 1e-9, "radius of $multiplier $number")
                val degrees = Math.toDegrees(atan2(p.xMm, p.yMm))
                val expectedDegrees = index * 18.0
                assertEquals(expectedDegrees, (degrees + 360.0) % 360.0, 1e-9, "angle of $multiplier $number")
            }
        }
    }

    /** BC1: y axis towards the 20, x axis towards the 6. */
    @Test
    fun `BC1 orientation of the axes`() {
        val d20 = point(Dart.double(20))
        assertEquals(0.0, d20.xMm, 1e-9)
        assertEquals(166.0, d20.yMm, 1e-9)
        val d6 = point(Dart.double(6))
        assertEquals(166.0, d6.xMm, 1e-9)
        assertEquals(0.0, d6.yMm, 1e-9)
        val d3 = point(Dart.double(3))
        assertEquals(0.0, d3.xMm, 1e-9)
        assertEquals(-166.0, d3.yMm, 1e-9)
        val d11 = point(Dart.double(11))
        assertEquals(-166.0, d11.xMm, 1e-9)
        assertEquals(0.0, d11.yMm, 1e-9)
    }

    /** BC2: literal anchors from the official dimensions; the single is the outer single zone. */
    @Test
    fun `BC2 anchors on the 20 in millimetres`() {
        val double20 = point(Dart.double(20))
        assertEquals(0.0, double20.xMm, 1e-9)
        assertEquals(166.0, double20.yMm, 1e-9)
        val triple20 = point(Dart.triple(20))
        assertEquals(0.0, triple20.xMm, 1e-9)
        assertEquals(103.0, triple20.yMm, 1e-9)
        val single20 = point(Dart.single(20))
        assertEquals(0.0, single20.xMm, 1e-9)
        assertEquals(134.5, single20.yMm, 1e-9)
        assertTrue(single20.yMm > 107.0, "S20 must be in the outer single zone")
    }

    /** BC2: the 25 and the bull are at the origin. */
    @Test
    fun `BC2 bulls are at the origin`() {
        assertEquals(BoardPoint(0.0, 0.0), nominalPoint(Dart.OUTER_BULL))
        assertEquals(BoardPoint(0.0, 0.0), nominalPoint(Dart.BULL))
    }

    /** BC2, D33: a miss has no position, never (0, 0). */
    @Test
    fun `BC2 a miss has no coordinates`() {
        assertNull(nominalPoint(Dart.MISS))
    }
}
