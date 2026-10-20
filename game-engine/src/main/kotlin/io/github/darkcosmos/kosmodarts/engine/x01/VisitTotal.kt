package io.github.darkcosmos.kosmodarts.engine.x01

/** What a player's visit total box shows (docs/rules/visit-total.md, VT1). */
sealed interface VisitTotal {
    /** The player has no completed turn and is not throwing (VT5). */
    data object NotPlayed : VisitTotal

    /** The player is throwing: sum of the darts of his turn in progress, 0 before his first dart (VT2). */
    data class InProgress(val points: Int) : VisitTotal

    /** Sum of the darts of the player's last completed turn, ended normally or by a checkout (VT3). */
    data class LastVisit(val points: Int) : VisitTotal

    /** The player's last completed turn ended by a bust (VT4). */
    data object Bust : VisitTotal
}

/** Derives the visit totals from an [X01State] (VT1 to VT7). Nothing is stored. */
object VisitTotals {

    /** VT1 to VT5: one state per player, in configuration order. */
    fun of(state: X01State): List<VisitTotal> = state.players.indices.map { player ->
        if (player == state.currentPlayerIndex) {
            VisitTotal.InProgress(state.players[player].currentTurnDarts.sumOf { it.value })
        } else {
            lastCompleted(state, player)
        }
    }

    /**
     * VT6: the player's last completed visit, ignoring the turn in progress: [VisitTotal.NotPlayed],
     * [VisitTotal.LastVisit] or [VisitTotal.Bust].
     */
    fun lastCompleted(state: X01State, playerIndex: Int): VisitTotal {
        val turn = state.completedTurns.lastOrNull { it.playerIndex == playerIndex }
        return when {
            turn == null -> VisitTotal.NotPlayed
            turn.outcome == TurnOutcome.BUST -> VisitTotal.Bust
            else -> VisitTotal.LastVisit(turn.darts.sumOf { it.value })
        }
    }
}
