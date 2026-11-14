package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.history.HistoryContent
import io.github.darkcosmos.kosmodarts.ui.history.HistoryState
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState

/** HS1: back returns to the home screen History came from; other events go to [after]. */
internal fun reduceHistory(state: AppState, screen: Screen.History, event: AppEvent): AppState {
    val next = screen.history.after(event)
    return when {
        event == AppEvent.Back -> state.copy(screen = screen.home)
        // HS5: the tap opens the detail over this very screen, so that Back finds the filters and scroll as left (HS6).
        event is AppEvent.HistoryMatchTapped ->
            state.copy(screen = Screen.MatchDetail(screen, MatchDetailState(event.sessionId)))
        next == screen.history -> state
        else -> state.copy(screen = screen.copy(history = next))
    }
}

/**
 * HS5, HS6, HS7: Back returns to the History screen the detail came from, as it was left. A late answer of the list
 * read (a screen restored after a process return reads both) goes to that list; "Retry" and the answer of the detail
 * read go to the detail. Other events change nothing.
 */
internal fun reduceMatchDetail(
    state: AppState,
    screen: Screen.MatchDetail,
    event: AppEvent,
    stamp: EventStamp,
): AppState {
    if (event is AppEvent.DetailPlayerTapped) return openPlayerProfile(state, screen, event.profileId, stamp)
    val next = when (event) {
        AppEvent.MatchDetailRetryTapped -> screen.detail.retry()
        is AppEvent.MatchDetailLoaded -> screen.detail.loaded(event.loadId, event.result)
        else -> screen.detail
    }
    val parent = screen.parent
    val list = if (event is AppEvent.HistoryLoaded && parent is Screen.History) parent.history.after(event) else null
    return when {
        event == AppEvent.Back -> state.copy(screen = parent)
        list != null && parent is Screen.History && list != parent.history ->
            state.copy(screen = screen.copy(parent = parent.copy(history = list), detail = next))
        next == screen.detail -> state
        else -> state.copy(screen = screen.copy(detail = next))
    }
}

/**
 * HS9: the match detail has its own events; for a Profile and a head-to-head (their screens come with their own
 * tasks) Back returns one level and nothing else changes.
 */
internal fun reduceStacked(
    state: AppState,
    screen: Screen.Stacked,
    event: AppEvent,
    stamp: EventStamp,
): AppState = when {
    screen is Screen.MatchDetail -> reduceMatchDetail(state, screen, event, stamp)
    event == AppEvent.Back -> state.copy(screen = screen.parent)
    else -> state
}

/** HS8: the name tapped in a match detail opens its Profile over the detail, or finds it already in the chain (HS9). */
private fun openPlayerProfile(state: AppState, screen: Screen.MatchDetail, profileId: Long, stamp: EventStamp) =
    state.copy(screen = screen.open(Screen.Profile(screen, profileId, ProfileState(now = stamp.at))))

/** HS1 to HS4, HS6, HS7: the History state after [event]; events History does not know change nothing. */
internal fun HistoryState.after(event: AppEvent): HistoryState = when (event) {
    is AppEvent.HistoryModeSelected -> withMode(event.mode)
    is AppEvent.HistoryProfileSelected -> withProfile(event.profileId)
    AppEvent.HistoryShowAllTapped -> showAll()
    AppEvent.HistoryRetryTapped -> if (content == HistoryContent.Failed) retry() else this
    is AppEvent.HistoryScrolled -> scrolled(event.index, event.offset)
    is AppEvent.HistoryLoaded -> loaded(event.loadId, event.result)
    else -> this
}
