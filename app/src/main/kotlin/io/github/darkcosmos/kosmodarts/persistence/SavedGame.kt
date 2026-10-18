package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState

/**
 * The game in progress as stored on the device (rules R4, R12): the leg, plus whether its last completed turn still
 * waits for "Next player". Everything else is derived by replay (docs/adr/0001-save-format.md, "Stored fields"). A
 * finished leg always waits: it is shown with the checkout dialog.
 */
data class SavedGame(val game: X01Game, val isAwaitingNextPlayer: Boolean) {

    /**
     * The game screen exactly as it was (rule R4), without an active Double/Triple modifier nor the abandon dialog
     * (owner decision, issue #30 Q5).
     */
    fun toGameScreenState(): GameScreenState = GameScreenState(game, isAwaitingConfirmation = isAwaitingNextPlayer)

    companion object {
        fun of(screen: GameScreenState): SavedGame = SavedGame(screen.game, screen.isAwaitingConfirmation)

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
