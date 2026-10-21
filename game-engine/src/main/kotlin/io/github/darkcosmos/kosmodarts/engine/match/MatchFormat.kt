package io.github.darkcosmos.kosmodarts.engine.match

/** Thrown when a [MatchFormat] is outside the allowed bounds (docs/rules/match.md, M1). */
class InvalidMatchFormatException(legsPerSet: Int, sets: Int) :
    IllegalArgumentException(
        "Invalid match format: legsPerSet=$legsPerSet, sets=$sets, each must be from ${MatchFormat.MIN} to " +
            "${MatchFormat.MAX}",
    )

/**
 * Format of a match (M1): a player wins a set on reaching [legsPerSet] legs won in the set (M3), and the match on
 * reaching [sets] sets won (M4). Both are "first to N". The default, 1 and 1, is a single game (M5).
 *
 * @throws InvalidMatchFormatException if a value is outside [MIN] to [MAX].
 */
data class MatchFormat(val legsPerSet: Int = MIN, val sets: Int = MIN) {

    init {
        if (legsPerSet !in MIN..MAX || sets !in MIN..MAX) throw InvalidMatchFormatException(legsPerSet, sets)
    }

    /** A single game: one leg decides the match (M5). */
    val isSingleGame: Boolean
        get() = legsPerSet == 1 && sets == 1

    /** Whether the match is played in more than one set (M14: no set mention otherwise). */
    val hasSets: Boolean
        get() = sets > 1

    /**
     * Upper bound of the number of legs of a match between [playerCount] players (M12): a set lasts at most
     * n(L-1)+1 legs, and a match at most n(S-1)+1 sets.
     */
    fun maxLegs(playerCount: Int): Int {
        require(playerCount >= 1) { "playerCount must be at least 1" }
        return (playerCount * (legsPerSet - 1) + 1) * (playerCount * (sets - 1) + 1)
    }

    companion object {
        /** Lowest number of legs per set and of sets (M1). */
        const val MIN: Int = 1

        /** Highest number of legs per set and of sets (M1). */
        const val MAX: Int = 9
    }
}
