package io.github.darkcosmos.kosmodarts.engine.x01

private const val DOUBLE_FACTOR = 2
private const val TRIPLE_FACTOR = 3

/** Ring of a numbered segment hit by a dart. The bull areas and a miss are always [SINGLE]. */
enum class Multiplier(val factor: Int) {
    SINGLE(1),
    DOUBLE(DOUBLE_FACTOR),
    TRIPLE(TRIPLE_FACTOR),
}

/**
 * One dart, as recorded in a game (rule D1): a single, double or triple of 1 to 20, the outer bull (25),
 * the bull (50) or a miss (0).
 *
 * [segment] is the number hit (1 to 20), [OUTER_BULL_SEGMENT], [BULL_SEGMENT] or [MISS_SEGMENT]. The bull areas
 * and a miss only accept [Multiplier.SINGLE], so triple bull and double outer bull cannot be created (rule D2):
 * the constructor (and therefore `copy`) rejects them with an [IllegalArgumentException].
 */
data class Dart(val segment: Int, val multiplier: Multiplier = Multiplier.SINGLE) {

    init {
        require(segment in NUMBERS || multiplier == Multiplier.SINGLE) {
            "Segment $segment cannot be hit with multiplier $multiplier"
        }
        require(segment in NUMBERS || segment in SPECIAL_SEGMENTS) { "Segment $segment does not exist" }
    }

    /** Points scored by this dart. */
    val value: Int
        get() = segment * multiplier.factor

    /** Whether this dart can finish a leg in double out: any double, and the bull (rule D3). */
    val countsAsDouble: Boolean
        get() = multiplier == Multiplier.DOUBLE || segment == BULL_SEGMENT

    companion object {
        /** Segment value of a dart that missed the scoring area. */
        const val MISS_SEGMENT: Int = 0

        /** Segment value of the outer bull, worth 25. */
        const val OUTER_BULL_SEGMENT: Int = 25

        /** Segment value of the bull, worth 50. */
        const val BULL_SEGMENT: Int = 50

        /** Numbers of the board segments. */
        val NUMBERS: IntRange = 1..20

        private val SPECIAL_SEGMENTS = setOf(MISS_SEGMENT, OUTER_BULL_SEGMENT, BULL_SEGMENT)

        /** A dart outside the scoring area. */
        val MISS: Dart = Dart(MISS_SEGMENT)

        /** The outer bull (25). */
        val OUTER_BULL: Dart = Dart(OUTER_BULL_SEGMENT)

        /** The bull (50). */
        val BULL: Dart = Dart(BULL_SEGMENT)

        /**
         * Every dart that scores (rule D1 without the miss): singles, doubles and triples of 1 to 20, then the outer
         * bull and the bull. The darts a checkout route can contain (docs/rules/checkout-suggestions.md, CS1).
         */
        val SCORING: List<Dart> =
            NUMBERS.flatMap { number -> Multiplier.entries.map { Dart(number, it) } } + OUTER_BULL + BULL

        /** Single of [number], which must be in [NUMBERS]. */
        fun single(number: Int): Dart = numbered(number, Multiplier.SINGLE)

        /** Double of [number], which must be in [NUMBERS]. */
        fun double(number: Int): Dart = numbered(number, Multiplier.DOUBLE)

        /** Triple of [number], which must be in [NUMBERS]. */
        fun triple(number: Int): Dart = numbered(number, Multiplier.TRIPLE)

        private fun numbered(number: Int, multiplier: Multiplier): Dart {
            require(number in NUMBERS) { "Number $number is not on the board" }
            return Dart(number, multiplier)
        }
    }
}
