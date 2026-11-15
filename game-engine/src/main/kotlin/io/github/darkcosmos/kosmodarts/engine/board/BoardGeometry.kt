package io.github.darkcosmos.kosmodarts.engine.board

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geometry of a standard dartboard (rules BC1, BC2, BC4, BC5), in millimetres from the centre of the bull: the
 * standard WDF/PDC board values (bull 12.7 mm and outer bull 31.8 mm in diameter, treble ring 99 to 107 mm and double
 * ring 162 to 170 mm from the centre). The exact document and section are to be confirmed before the release; until
 * then these values are not tied to a source that was opened.
 *
 * Borders (rule BC5): every inner limit belongs to the zone it starts, so a point on a ring radius or a sector edge
 * is in exactly one zone; the outer limit of the double ring is the only one that is inclusive.
 */
object BoardGeometry {
    const val BULL_RADIUS_MM = 6.35
    const val OUTER_BULL_RADIUS_MM = 15.9
    const val TRIPLE_INNER_MM = 99.0
    const val TRIPLE_OUTER_MM = 107.0
    const val DOUBLE_INNER_MM = 162.0
    const val DOUBLE_OUTER_MM = 170.0

    /** Numbers of the sectors, clockwise from the top (the 20 is centred on the +y axis). */
    val SECTOR_ORDER: List<Int> = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    const val SECTOR_ANGLE_DEGREES = 18.0

    /** Mean radius of the zone (rule BC2): the outer single zone, or the middle of the triple or double ring. */
    fun meanRadiusMm(multiplier: Multiplier): Double = when (multiplier) {
        Multiplier.SINGLE -> (TRIPLE_OUTER_MM + DOUBLE_INNER_MM) / 2
        Multiplier.TRIPLE -> (TRIPLE_INNER_MM + TRIPLE_OUTER_MM) / 2
        Multiplier.DOUBLE -> (DOUBLE_INNER_MM + DOUBLE_OUTER_MM) / 2
    }

    /** Zone under [point] (rule BC5), or `null` outside the double ring. A point on a border is in one zone only. */
    fun zoneAt(point: BoardPoint): BoardZone? {
        val radius = hypot(point.xMm, point.yMm)
        val ring = ringAt(radius)
        return when (ring) {
            null -> null
            BoardZone.Ring.BULL, BoardZone.Ring.OUTER_BULL -> BoardZone.bull(ring)
            else -> BoardZone(SECTOR_ORDER[sectorIndexAt(point)], ring)
        }
    }

    private fun ringAt(radius: Double): BoardZone.Ring? = when {
        radius < BULL_RADIUS_MM -> BoardZone.Ring.BULL
        radius < OUTER_BULL_RADIUS_MM -> BoardZone.Ring.OUTER_BULL
        radius < TRIPLE_INNER_MM -> BoardZone.Ring.SINGLE
        radius < TRIPLE_OUTER_MM -> BoardZone.Ring.TRIPLE
        radius < DOUBLE_INNER_MM -> BoardZone.Ring.SINGLE
        radius <= DOUBLE_OUTER_MM -> BoardZone.Ring.DOUBLE
        else -> null
    }

    /** Index in [SECTOR_ORDER]; sector edges are at 9 + 18k degrees clockwise from the top, lower edge inclusive. */
    private fun sectorIndexAt(point: BoardPoint): Int {
        val degrees = (Math.toDegrees(atan2(point.xMm, point.yMm)) + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
        val shifted = degrees + SECTOR_ANGLE_DEGREES / 2
        return floor(shifted / SECTOR_ANGLE_DEGREES).toInt() % SECTOR_ORDER.size
    }

    private const val FULL_TURN_DEGREES = 360.0
}

/**
 * Nominal point of [dart] (rules BC1 to BC3): the centre of its zone, on the bisector of its sector. The 25 and the
 * bull are at the origin. A miss has no position: `null`, never (0, 0).
 */
fun nominalPoint(dart: Dart): BoardPoint? = when (dart.segment) {
    Dart.MISS_SEGMENT -> null
    Dart.OUTER_BULL_SEGMENT, Dart.BULL_SEGMENT -> BoardPoint(0.0, 0.0)
    else -> {
        val radius = BoardGeometry.meanRadiusMm(dart.multiplier)
        val sectorIndex = BoardGeometry.SECTOR_ORDER.indexOf(dart.segment)
        val angle = Math.toRadians(sectorIndex * BoardGeometry.SECTOR_ANGLE_DEGREES)
        BoardPoint(radius * sin(angle), radius * cos(angle))
    }
}
