package io.github.darkcosmos.kosmodarts.engine.cricket

private const val HIGHEST_NUMBER = 20
private const val BULL_POINTS = 25

/**
 * A Cricket target (docs/rules/cricket.md, CR1), in board order: 20 down to 15, then the Bull. The numbers are
 * declared from 20 down, one per step, so their points follow from their position.
 */
enum class CricketTarget {
    TWENTY,
    NINETEEN,
    EIGHTEEN,
    SEVENTEEN,
    SIXTEEN,
    FIFTEEN,
    BULL,
    ;

    /** Whether this target is one of the numbers 15 to 20. */
    val isNumber: Boolean
        get() = this != BULL

    /** What one surplus mark on this target is worth (CR4, CR5): the number for 15 to 20, 25 for the Bull. */
    val points: Int
        get() = if (isNumber) HIGHEST_NUMBER - ordinal else BULL_POINTS

    companion object {
        /** The targets 20 down to 15, in board order. */
        val NUMBERS: List<CricketTarget> = entries.filter(CricketTarget::isNumber)

        /** The numbered target of [segment], or null when [segment] is not 15 to 20. */
        fun ofNumber(segment: Int): CricketTarget? = NUMBERS.firstOrNull { it.points == segment }
    }
}
