package io.github.darkcosmos.kosmodarts.engine.board

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ring radii of a standard dartboard, in millimetres, from the centre of the bull: the standard WDF/PDC board values
 * (double ring 162 to 170 mm, treble ring 99 to 107 mm). The exact document and section are to be confirmed before
 * the release; until then these values are not tied to a source that was opened.
 */
internal object BoardGeometry {
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
