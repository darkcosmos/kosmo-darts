package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn

/**
 * The turn completed by the event that changed [before] into [after], or `null`. A turn is completed when a new dart
 * on the game screen makes the game wait for a confirmation, so recomposing or recreating the screen never completes
 * it again, and neither does resuming a saved game whose turn was already waiting (rule R4).
 */
fun completedTurn(before: AppState, after: AppState): X01Turn? {
    val gameBefore = (before.screen as? Screen.Game)?.game
    val gameAfter = (after.screen as? Screen.Game)?.game
    if (gameBefore == null || gameAfter == null || gameBefore.isAwaitingConfirmation) return null
    val isNewDart = gameAfter.game.darts.size == gameBefore.game.darts.size + 1
    return if (isNewDart) gameAfter.pendingTurn else null
}
