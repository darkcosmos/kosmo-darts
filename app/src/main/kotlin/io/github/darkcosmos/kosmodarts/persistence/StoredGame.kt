package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal

/**
 * The game in progress as stored on the device, whatever its mode: there is only one (rule R20,
 * docs/adr/0008-save-format-v4-modes.md). An X01 game is a [SavedGame], a Cricket game a [SavedCricketGame].
 */
sealed interface StoredGame {

    /** Whether the last completed turn of the current leg still waits for "Next player"; a won leg always waits. */
    val isAwaitingNextPlayer: Boolean

    /** The match id, its start time and the dart journal of every leg, the current one last (R26, DJ1, AR4). */
    val journal: MatchJournal

    /** Whether the between-legs screen is shown. */
    val isBetweenLegs: Boolean

    /** Whether both stored flags agree with the replayed match; a decoded file must be consistent (R9, R24). */
    val isConsistent: Boolean
}
