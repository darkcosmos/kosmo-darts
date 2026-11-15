package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.players.PlayersState
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState

/**
 * PM2, PS1: back returns to the home screen Players came from; a tap on a row opens the Profile over this screen; the
 * other events go to [PlayersState]. The actions are the Profile's (PS7).
 */
internal fun reducePlayers(state: AppState, screen: Screen.Players, event: AppEvent, stamp: EventStamp): AppState =
    when (event) {
        AppEvent.Back -> state.copy(screen = screen.home)
        is PlayersEvent.ProfileTapped -> openProfile(state, screen, event.id, stamp)
        else -> {
            val next = screen.players.after(event)
            if (next == screen.players) state else state.copy(screen = screen.copy(players = next))
        }
    }

private fun openProfile(state: AppState, screen: Screen.Players, id: Long, stamp: EventStamp): AppState {
    val isListed = screen.players.players.any { it.id == id }
    val profile = Screen.Profile(screen, id, ProfileState(now = stamp.at))
    return if (isListed) state.copy(screen = screen.open(profile)) else state
}

/** The Players state after [event]; events it does not know change nothing. */
internal fun PlayersState.after(event: AppEvent): PlayersState = when (event) {
    PlayersEvent.RetryTapped -> retry()
    is PlayersEvent.Loaded -> loaded(event.loadId, event.result)
    else -> this
}
