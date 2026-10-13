package io.github.darkcosmos.kosmodarts.ui.end

import io.github.darkcosmos.kosmodarts.engine.x01.X01Game

/** One line of the end screen ranking. Tied players share a [rank] (rule E2). */
data class EndRow(
    val rank: Int,
    val name: String,
    val threeDartAverage: Double?,
    val dartsThrown: Int,
    val isWinner: Boolean,
)

/** End screen ranking, in the engine order (rules E1, E2). Empty while the leg is in progress. */
fun endRows(game: X01Game): List<EndRow> {
    val state = game.state
    return state.ranking.map { rank ->
        val player = state.players[rank.playerIndex]
        EndRow(
            rank = rank.rank,
            name = player.name,
            threeDartAverage = player.threeDartAverage,
            dartsThrown = player.dartsThrown,
            isWinner = rank.playerIndex == state.winnerIndex,
        )
    }
}
