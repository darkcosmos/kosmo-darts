package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch

/**
 * The Cricket game in progress as stored on the device (rules R20, R21; docs/adr/0008-save-format-v4-modes.md): the
 * match, plus the same two flags as an X01 [SavedGame]. Everything else is derived by replaying the darts of the
 * current leg. A won leg always waits: it is shown with the VICTOIRE zone (docs/rules/cricket.md, CR25).
 *
 * Depends on the engine only, like [SavedGame].
 */
data class SavedCricketGame(
    val match: CricketMatch,
    override val isAwaitingNextPlayer: Boolean,
    override val isBetweenLegs: Boolean = false,
) : StoredGame {

    /** The current leg. */
    val game: CricketGame
        get() = match.currentLeg

    override val isConsistent: Boolean
        get() = isConsistent(game, isAwaitingNextPlayer) && (!isBetweenLegs || isBetweenLegsPossible(match))

    companion object {
        /**
         * Whether a stored waiting flag is consistent with [game], read from the replayed engine state: a won leg must
         * wait, and only a leg whose last dart completed a turn may wait (as for X01, [SavedGame.isConsistent]).
         */
        fun isConsistent(game: CricketGame, isAwaitingNextPlayer: Boolean): Boolean {
            val state = game.state
            val lastDartCompletedTurn = state.completedTurns.isNotEmpty() &&
                state.currentPlayerIndex?.let { state.players[it].currentTurnDarts.isEmpty() } ?: true
            return if (state.isFinished) isAwaitingNextPlayer else !isAwaitingNextPlayer || lastDartCompletedTurn
        }

        /** Between legs only after a confirmed leg, before the first dart of the next one (match.md, M16). */
        private fun isBetweenLegsPossible(match: CricketMatch): Boolean =
            match.completedLegs.isNotEmpty() && match.currentLeg.darts.isEmpty()
    }
}
