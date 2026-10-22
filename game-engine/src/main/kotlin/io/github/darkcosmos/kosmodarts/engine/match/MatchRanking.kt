package io.github.darkcosmos.kosmodarts.engine.match

/**
 * Place of a player at the end of a match (M10). Tied players share a [rank] and the next rank is skipped.
 *
 * @property playerIndex index of the player in the configuration.
 */
data class MatchRank(val rank: Int, val playerIndex: Int)

/** Final ranking of a match (docs/rules/match.md, M10), independent of the game mode (M11). */
object MatchRanking {

    /**
     * The match winner first, then the others by sets won (more is better), then by legs won over the whole match,
     * then by [tieBreak] (lower is better). Players equal on every criterion share a rank and the next rank is skipped
     * (1, 2, 2, 4). Sorted by rank, then by configuration order.
     *
     * @param tieBreak the mode value of each player, in configuration order (X01: remaining score in the last leg).
     * @throws IllegalArgumentException if [tieBreak] does not have one value per player.
     */
    fun rank(score: MatchScore, tieBreak: List<Int>): List<MatchRank> {
        require(tieBreak.size == score.playerCount) { "One tie-break value per player expected" }
        val order = compareBy<Int>(
            { if (it == score.winnerIndex) 0 else 1 },
            { -score.setsWon[it] },
            { -score.legsWonTotal[it] },
            { tieBreak[it] },
        )
        val players = 0 until score.playerCount
        return players
            .map { player ->
                MatchRank(rank = 1 + players.count { order.compare(it, player) < 0 }, playerIndex = player)
            }
            .sortedBy(MatchRank::rank)
    }
}
