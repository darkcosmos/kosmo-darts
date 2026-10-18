package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01Game

/**
 * The game in progress as stored on the device (rules R4, R12): the leg, plus whether its last completed turn still
 * waits for "Next player". Everything else is derived by replay (docs/adr/0001-save-format.md, "Stored fields"). A
 * finished leg always waits: it is shown with the checkout dialog.
 *
 * Depends on the engine only: the conversions to and from the game screen live on the UI side
 * (`ui/game/SavedGameScreen.kt`; ADR 0002, consequences).
 */
data class SavedGame(val game: X01Game, val isAwaitingNextPlayer: Boolean) {

    companion object {
        /**
         * Whether a stored waiting flag is consistent with [game], read from the replayed engine state: a finished leg
         * must wait, and only a leg whose last dart completed a turn may wait.
         */
        fun isConsistent(game: X01Game, isAwaitingNextPlayer: Boolean): Boolean {
            val state = game.state
            val lastDartCompletedTurn = state.completedTurns.isNotEmpty() &&
                state.currentPlayerIndex?.let { state.players[it].currentTurnDarts.isEmpty() } ?: true
            return if (state.isFinished) isAwaitingNextPlayer else !isAwaitingNextPlayer || lastDartCompletedTurn
        }
    }
}
