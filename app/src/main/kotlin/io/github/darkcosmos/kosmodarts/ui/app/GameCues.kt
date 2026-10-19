package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cues
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState

/**
 * The cues of the event that changed [before] into [after] (docs/rules/announcements.md, AN1 to AN7). Pure.
 *
 * Only five hook points produce cues: a dart entered (with the end of its turn), an undo, a turn start (a game
 * opened by Start or Rematch, or "Next player"), and a confirmed win. Anything else, a resume included, produces
 * none, and a rotation or a recomposition sends no event at all.
 */
fun gameCues(before: AppState, event: AppEvent, after: AppState): List<X01Cue> {
    val screenBefore = before.screen
    val screenAfter = after.screen
    return when {
        screenBefore !is Screen.Game && screenAfter is Screen.Game ->
            if (screenAfter.isNewGame) listOfNotNull(X01Cues.turnStart(screenAfter.game.game)) else emptyList()
        screenBefore is Screen.Game && screenAfter is Screen.Game ->
            inGameCues(screenBefore.game, event, screenAfter.game)
        screenBefore is Screen.Game && screenAfter is Screen.End && event == AppEvent.FinishGameTapped ->
            listOfNotNull(X01Cues.checkoutConfirmed(screenAfter.game))
        else -> emptyList()
    }
}

private fun inGameCues(before: GameScreenState, event: AppEvent, after: GameScreenState): List<X01Cue> {
    val dartsBefore = before.game.darts.size
    val dartsAfter = after.game.darts.size
    return when {
        dartsAfter == dartsBefore + 1 -> X01Cues.afterDart(before.game, after.game)
        dartsAfter == dartsBefore - 1 -> listOf(X01Cues.dartUndone)
        event == AppEvent.NextPlayerTapped && before.isAwaitingConfirmation && !after.isAwaitingConfirmation ->
            listOfNotNull(X01Cues.turnStart(after.game))
        else -> emptyList()
    }
}
