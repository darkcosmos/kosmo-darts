package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

private const val BULL_MARKS = 2

/**
 * The marks a dart gives (docs/rules/cricket.md, CR2): [count] marks on [target], or no target and 0 marks.
 *
 * @throws IllegalArgumentException if [count] is not 1 to 3 with a target, or not 0 without one.
 */
data class CricketMarks(val target: CricketTarget?, val count: Int) {

    init {
        require(if (target == null) count == 0 else count in 1..MAX_PER_DART) {
            "Invalid marks: $count on $target"
        }
    }

    companion object {
        /** Most marks one dart can give (a triple). */
        const val MAX_PER_DART: Int = 3

        /** A dart that gives no mark. */
        val NONE: CricketMarks = CricketMarks(null, 0)

        /**
         * The marks of [dart] under [config] (CR2): a single, double or triple of 15 to 20 gives 1, 2 or 3 marks on
         * that number; the outer bull (25) gives 1 mark and the bull 2 marks on the Bull when it is in play. Every
         * other dart gives [NONE].
         */
        fun of(dart: Dart, config: CricketConfig): CricketMarks = of(dart, config.isBullInPlay)

        /**
         * The marks of [dart] (CR2) when the Bull is in play or not: for a caller that only knows the stored settings
         * of a match (the History detail, HS5), not a whole [CricketConfig].
         */
        fun of(dart: Dart, isBullInPlay: Boolean): CricketMarks {
            val number = CricketTarget.ofNumber(dart.segment)
            return when {
                number != null -> CricketMarks(number, dart.multiplier.factor)
                !isBullInPlay -> NONE
                dart.segment == Dart.OUTER_BULL_SEGMENT -> CricketMarks(CricketTarget.BULL, 1)
                dart.segment == Dart.BULL_SEGMENT -> CricketMarks(CricketTarget.BULL, BULL_MARKS)
                else -> NONE
            }
        }
    }
}
