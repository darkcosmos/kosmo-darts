package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.ui.players.PlayersAnswer
import io.github.darkcosmos.kosmodarts.ui.players.PlayersRequest

/** What the user does on the Players screen, and what the database answers (PM2 to PM11). */
sealed interface PlayersEvent : AppEvent {
    /** "Retry" after a failed read (PM10). */
    data object RetryTapped : PlayersEvent

    /** The answer to read [loadId]; an answer to an older read is ignored. */
    data class Loaded(val loadId: Int, val result: DbResult<List<PlayerSummary>>) : PlayersEvent

    /** A tap on the card of the profile [id]. */
    data class ProfileTapped(val id: Long) : PlayersEvent

    data object RenameTapped : PlayersEvent

    data class RenameTextChanged(val text: String) : PlayersEvent

    /** "Save" of the rename panel. */
    data object RenameSaved : PlayersEvent

    data object MergeTapped : PlayersEvent

    /** The profile [id] picked as the target of the merge. */
    data class MergeTargetPicked(val id: Long) : PlayersEvent

    /** "Merge into ..." of the rename panel, when the new name belongs to another profile (Q6). */
    data object MergeIntoTakenTapped : PlayersEvent

    data object ResetTapped : PlayersEvent

    data object DeleteTapped : PlayersEvent

    /** The confirm button of the merge, reset or delete panel. */
    data object Confirmed : PlayersEvent

    /** "Cancel" of any panel. */
    data object Cancelled : PlayersEvent

    /** The database answered [request]. It counts even when the screen was left meanwhile (PM9). */
    data class Answered(val request: PlayersRequest, val answer: PlayersAnswer) : PlayersEvent
}
