package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01State

/** Score of a player's last completed turn, shown next to their average. */
sealed interface LastTurnScore {
    /** The player has not completed a turn yet. */
    data object None : LastTurnScore

    data object Bust : LastTurnScore

    /** Points scored by the turn (a checkout scores the whole remaining). */
    data class Points(val points: Int) : LastTurnScore
}

/** One entry per player, in configuration order, from the completed turns of [state] (rule A3). */
fun lastTurnScores(state: X01State): List<LastTurnScore> = state.players.indices.map { player ->
    val turn = state.completedTurns.lastOrNull { it.playerIndex == player }
    when {
        turn == null -> LastTurnScore.None
        turn.outcome == TurnOutcome.BUST -> LastTurnScore.Bust
        else -> LastTurnScore.Points(turn.remainingBefore - turn.remainingAfter)
    }
}
