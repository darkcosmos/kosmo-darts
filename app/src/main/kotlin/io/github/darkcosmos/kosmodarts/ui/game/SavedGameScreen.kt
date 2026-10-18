package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.persistence.SavedGame

// Conversions between the game screen and the saved game (rules R4, R12). They live on the UI side so that the
// `persistence` package depends on the engine only (ADR 0002, consequences).

/** The part of this screen that is saved: the leg and whether its last completed turn waits for "Next player". */
fun GameScreenState.toSavedGame(): SavedGame = SavedGame(game, isAwaitingConfirmation)

/**
 * The game screen exactly as it was (rule R4), without an active Double/Triple modifier nor the abandon dialog
 * (owner decision, issue #30 Q5).
 */
fun SavedGame.toGameScreenState(): GameScreenState =
    GameScreenState(game, isAwaitingConfirmation = isAwaitingNextPlayer)
