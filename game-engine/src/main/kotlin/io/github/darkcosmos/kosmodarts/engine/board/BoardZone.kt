package io.github.darkcosmos.kosmodarts.engine.board

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/**
 * A heatmap zone (rules BC4, HM1). [number] is the board number 1 to 20, or [Dart.OUTER_BULL_SEGMENT] and
 * [Dart.BULL_SEGMENT] for the two bull zones. The inner and the outer single rings of a number are one zone.
 */
data class BoardZone(val number: Int, val ring: Ring) {

    enum class Ring { SINGLE, DOUBLE, TRIPLE, OUTER_BULL, BULL }

    companion object {
        /** The 62 zones (rule HM1): single, double and triple of 1 to 20, the outer bull and the bull. */
        val ALL: List<BoardZone> = Dart.NUMBERS.flatMap { number ->
            listOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE).map { BoardZone(number, it) }
        } + bull(Ring.OUTER_BULL) + bull(Ring.BULL)

        /** The zone a [dart] was aimed at or hit, or `null` for a miss. */
        fun of(dart: Dart): BoardZone? = when (dart.segment) {
            Dart.MISS_SEGMENT -> null
            Dart.OUTER_BULL_SEGMENT -> bull(Ring.OUTER_BULL)
            Dart.BULL_SEGMENT -> bull(Ring.BULL)
            else -> BoardZone(dart.segment, dart.multiplier.toRing())
        }

        internal fun bull(ring: Ring): BoardZone =
            BoardZone(if (ring == Ring.BULL) Dart.BULL_SEGMENT else Dart.OUTER_BULL_SEGMENT, ring)

        private fun Multiplier.toRing(): Ring = when (this) {
            Multiplier.SINGLE -> Ring.SINGLE
            Multiplier.DOUBLE -> Ring.DOUBLE
            Multiplier.TRIPLE -> Ring.TRIPLE
        }
    }
}
