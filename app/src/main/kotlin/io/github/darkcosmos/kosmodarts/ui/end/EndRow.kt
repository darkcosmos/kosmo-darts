package io.github.darkcosmos.kosmodarts.ui.end

import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/**
 * One line of the end screen ranking. Tied players share a [rank] (rules E2, M10).
 *
 * @property threeDartAverage over the whole match (M9).
 * @property dartsThrown over the whole match (M9).
 * @property setsWon sets won, or `null` in a single game or with one set: the column is then not shown (M17).
 * @property legsWon legs won over the whole match, or `null` in a single game (M17).
 */
data class EndRow(
    val rank: Int,
    val name: String,
    val threeDartAverage: Double?,
    val dartsThrown: Int,
    val isWinner: Boolean,
    val setsWon: Int? = null,
    val legsWon: Int? = null,
)

/** End screen ranking of a single game, in the engine order (rules E1, E2). Empty while the leg is in progress. */
fun endRows(game: X01Game): List<EndRow> = endRows(X01Match(X01MatchConfig(game.config), emptyList(), game))

/** End screen ranking of a match, in the engine order (M10, M17). Empty while the match is in progress. */
fun endRows(match: X01Match): List<EndRow> {
    val score = match.finalScore ?: return emptyList()
    val format = match.format
    return match.ranking.map { rank ->
        val player = rank.playerIndex
        val stats = match.playerStats[player]
        EndRow(
            rank = rank.rank,
            name = match.config.players[player],
            threeDartAverage = stats.threeDartAverage,
            dartsThrown = stats.dartsThrown,
            isWinner = player == score.winnerIndex,
            setsWon = score.setsWon[player].takeIf { format.hasSets },
            legsWon = score.legsWonTotal[player].takeUnless { format.isSingleGame },
        )
    }
}
