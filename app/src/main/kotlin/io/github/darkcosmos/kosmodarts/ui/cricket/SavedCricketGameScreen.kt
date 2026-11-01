package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame

// Conversions between the Cricket game screen and its saved game (rules R12, R20, R21). They live on the UI side so
// that the `persistence` package depends on the engine only (ADR 0002, consequences), like `SavedGameScreen.kt`.

/** The part of this screen that is saved: the match and whether its last completed turn or its win waits. */
fun CricketGameScreenState.toSavedGame(journal: MatchJournal): SavedCricketGame =
    SavedCricketGame(match, isAwaitingConfirmation, journal = journal)

/**
 * The Cricket game screen exactly as it was (R21, as R4), a pending win in the VICTORY zone included (CR25), without an
 * active Double/Triple modifier nor the abandon dialog (owner decision, issue #30 Q5).
 */
fun SavedCricketGame.toGameScreenState(): CricketGameScreenState =
    CricketGameScreenState(match, isAwaitingConfirmation = isAwaitingNextPlayer)
