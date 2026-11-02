package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch

/**
 * Where the archive of the confirmed match stands on a game screen (docs/rules/history.md, AR1 to AR3). Never saved:
 * after a process death the game resumes on its zone with [Idle].
 */
sealed interface ArchiveStatus {
    /** Nothing was asked yet, or the player undid the winning dart after a failure. */
    data object Idle : ArchiveStatus

    /** The match [archived] is being written: the confirm button is disabled and a second tap does nothing (AR3). */
    data class Saving(val archived: ArchivedMatch) : ArchiveStatus

    /** The last write failed: the zone stays, the message shows and the confirm button retries (AR3). */
    data object Failed : ArchiveStatus
}
