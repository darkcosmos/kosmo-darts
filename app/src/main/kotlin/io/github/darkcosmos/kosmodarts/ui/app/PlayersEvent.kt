package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary

/** What the user does on the Players screen, and what the database answers (PM2, PM10). */
sealed interface PlayersEvent : AppEvent {
    /** "Retry" after a failed read (PM10). */
    data object RetryTapped : PlayersEvent

    /** The answer to read [loadId]; an answer to an older read is ignored. */
    data class Loaded(val loadId: Int, val result: DbResult<List<PlayerSummary>>) : PlayersEvent

    /** A tap on the row of the profile [id]: it opens the Profile (PM2, PS1). */
    data class ProfileTapped(val id: Long) : PlayersEvent
}
