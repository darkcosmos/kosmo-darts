package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn

/**
 * The turn completed by the event that changed [before] into [after], or `null`. A turn is completed when the game
 * starts waiting for a confirmation, so recomposing or recreating the screen never completes it again.
 */
fun completedTurn(before: AppState, after: AppState): X01Turn? {
    val wasWaiting = (before.screen as? Screen.Game)?.game?.isAwaitingConfirmation == true
    val pending = (after.screen as? Screen.Game)?.game?.pendingTurn
    return if (wasWaiting) null else pending
}
