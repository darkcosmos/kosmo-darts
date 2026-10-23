package io.github.darkcosmos.kosmodarts.engine.cricket

/**
 * What a player's visit box shows in Cricket (docs/rules/cricket.md, CR14): VT1 to VT7 of visit-total.md with marks
 * instead of points, and without the bust state.
 */
sealed interface CricketVisit {
    /** The player has no completed turn in this leg and is not throwing (VT5). */
    data object NotPlayed : CricketVisit

    /** The player is throwing: marks of the darts of his turn in progress, 0 before his first dart (VT2). */
    data class InProgress(val marks: Int) : CricketVisit

    /** Marks of the player's last completed turn, the winning turn included (VT3). */
    data class LastVisit(val marks: Int) : CricketVisit
}

/** Derives the visit marks from a [CricketState] (CR14). Nothing is stored, so they restart with each leg (M9). */
object CricketVisits {

    /** One state per player, in configuration order. */
    fun of(state: CricketState): List<CricketVisit> = state.players.indices.map { player ->
        if (player == state.currentPlayerIndex) {
            CricketVisit.InProgress(state.players[player].currentTurnMarks)
        } else {
            lastCompleted(state, player)
        }
    }

    /**
     * VT6: the player's last completed visit, ignoring the turn in progress: [CricketVisit.NotPlayed] or
     * [CricketVisit.LastVisit].
     */
    fun lastCompleted(state: CricketState, playerIndex: Int): CricketVisit =
        state.completedTurns.lastOrNull { it.playerIndex == playerIndex }
            ?.let { CricketVisit.LastVisit(it.marks) }
            ?: CricketVisit.NotPlayed
}
