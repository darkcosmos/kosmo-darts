package io.github.darkcosmos.kosmodarts.engine.match

/** Thrown when a leg is added to a match that is already won (docs/rules/match.md, M4). */
class MatchFinishedException : IllegalStateException("The match is finished: no further leg can be played")

/**
 * Score of a match, derived by replaying the winners of its confirmed legs in order (M2 to M4, M6, M7). Immutable and
 * independent of the game mode (M11).
 *
 * @param legWinners index of the winner of each confirmed leg, in play order.
 * @throws IllegalArgumentException if [playerCount] is below 1, a winner is not a player index, or a leg follows the
 *   leg that decided the match.
 */
class MatchScore(val format: MatchFormat, val playerCount: Int, legWinners: List<Int> = emptyList()) {

    /** Winner of each confirmed leg, in play order. */
    val legWinners: List<Int> = legWinners.toList()

    /** Sets won by each player, in configuration order (M3). */
    val setsWon: List<Int>

    /** Legs won by each player in the current set, in configuration order; back to 0 after each set (M3). */
    val legsWonInSet: List<Int>

    /** Legs won by each player over the whole match, in configuration order (M10). */
    val legsWonTotal: List<Int>

    /** Index of the player who won the match (M4), or `null` while it is in progress. */
    val winnerIndex: Int?

    init {
        require(playerCount >= 1) { "playerCount must be at least 1" }
        val sets = IntArray(playerCount)
        val legsInSet = IntArray(playerCount)
        val legsTotal = IntArray(playerCount)
        var winner: Int? = null
        this.legWinners.forEach { player ->
            require(player in 0 until playerCount) { "Leg winner out of range" }
            require(winner == null) { "A leg follows the leg that decided the match" }
            legsTotal[player]++
            legsInSet[player]++
            if (legsInSet[player] == format.legsPerSet) {
                legsInSet.fill(0)
                sets[player]++
                if (sets[player] == format.sets) winner = player
            }
        }
        setsWon = sets.toList()
        legsWonInSet = legsInSet.toList()
        legsWonTotal = legsTotal.toList()
        winnerIndex = winner
    }

    /** Whether a player has won the match (M4). */
    val isFinished: Boolean
        get() = winnerIndex != null

    /** Number of confirmed legs. */
    val legsPlayed: Int
        get() = legWinners.size

    /** Index of the player who starts the next leg: legs are numbered from 0 over the match, sets ignored (M6). */
    val starterOfNextLeg: Int
        get() = legsPlayed % playerCount

    /**
     * What a leg won by [player] would decide (M7).
     *
     * @throws IllegalArgumentException if [player] is not a player index.
     * @throws MatchFinishedException if the match is already won.
     */
    fun outcomeIfWonBy(player: Int): LegOutcome {
        require(player in 0 until playerCount) { "Player out of range" }
        if (isFinished) throw MatchFinishedException()
        return when {
            legsWonInSet[player] + 1 < format.legsPerSet -> LegOutcome.LEG
            setsWon[player] + 1 < format.sets -> LegOutcome.SET
            else -> LegOutcome.MATCH
        }
    }

    /**
     * This score after a leg won by [player].
     *
     * @throws IllegalArgumentException if [player] is not a player index.
     * @throws MatchFinishedException if the match is already won.
     */
    fun withLegWonBy(player: Int): MatchScore {
        if (isFinished) throw MatchFinishedException()
        return MatchScore(format, playerCount, legWinners + player)
    }

    override fun equals(other: Any?): Boolean = other is MatchScore &&
        format == other.format &&
        playerCount == other.playerCount &&
        legWinners == other.legWinners

    override fun hashCode(): Int = (format.hashCode() * HASH_PRIME + playerCount) * HASH_PRIME + legWinners.hashCode()

    override fun toString(): String = "MatchScore(format=$format, playerCount=$playerCount, legWinners=$legWinners)"

    private companion object {
        const val HASH_PRIME = 31
    }
}
