package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cues
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState

/**
 * The cues of the event that changed [before] into [after] (docs/rules/announcements.md, AN1 to AN7, AN27, AN28).
 * Pure.
 *
 * Only these hook points produce cues: a dart entered (with the end of its turn, the win included: E3), an undo, and
 * a turn start (a game opened by Start or Rematch, "Next player", or "Next leg"). "Finish …" produces none, nor does
 * anything else, a resume included; a rotation or a recomposition sends no event at all.
 */
fun gameCues(before: AppState, event: AppEvent, after: AppState): List<X01Cue> {
    val screenBefore = before.screen
    val screenAfter = after.screen
    return when {
        // AN28: the first turn of the new leg, with the "throws first" line.
        screenBefore is Screen.BetweenLegs && screenAfter is Screen.Game && event == AppEvent.NextLegTapped ->
            listOfNotNull(X01Cues.turnStart(screenAfter.game.game))
        screenBefore !is Screen.Game && screenAfter is Screen.Game ->
            if (screenAfter.isNewGame) listOfNotNull(X01Cues.turnStart(screenAfter.game.game)) else emptyList()
        screenBefore is Screen.Game && screenAfter is Screen.Game ->
            inGameCues(screenBefore.game, event, screenAfter.game)
        else -> emptyList()
    }
}

private fun inGameCues(before: GameScreenState, event: AppEvent, after: GameScreenState): List<X01Cue> {
    val dartsBefore = before.game.darts.size
    val dartsAfter = after.game.darts.size
    return when {
        // AN27: the win cue tells whether the leg, a set or the match is won.
        dartsAfter == dartsBefore + 1 -> X01Cues.afterDart(before.match, after.match)
        dartsAfter == dartsBefore - 1 -> listOf(X01Cues.dartUndone)
        event == AppEvent.NextPlayerTapped && before.isAwaitingConfirmation && !after.isAwaitingConfirmation ->
            listOfNotNull(X01Cues.turnStart(after.game))
        else -> emptyList()
    }
}
