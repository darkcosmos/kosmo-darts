package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.history.HistoryContent
import io.github.darkcosmos.kosmodarts.ui.history.HistoryState

/** HS1: back returns to the home screen History came from; other events go to [after]. */
internal fun reduceHistory(state: AppState, screen: Screen.History, event: AppEvent): AppState {
    if (event == AppEvent.Back) return state.copy(screen = screen.home)
    val next = screen.history.after(event)
    return if (next == screen.history) state else state.copy(screen = screen.copy(history = next))
}

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
