package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadSelections
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadState
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState

/**
 * HH1 to HH9, HS7, HS9: the answers (picker, pair, result) reach their head-to-head wherever it is in the stack,
 * since a detail may be open above it; the user events apply to the head-to-head on top only. Back is not handled
 * here (see [reduceStacked]).
 */
internal fun reduceHeadToHeadEvent(state: AppState, event: HeadToHeadEvent, stamp: EventStamp): AppState {
    val screen = state.screen
    val next = when (event) {
        is HeadToHeadEvent.PickerLoaded ->
            screen.withHeadToHead(event.a) { it.pickerLoaded(event.loadId, event.result) }
        is HeadToHeadEvent.PairLoaded ->
            screen.withHeadToHead(event.a) { it.pairLoaded(event.loadId, event.result) }
        is HeadToHeadEvent.ResultComputed ->
            screen.withHeadToHead(event.a) { it.computed(event.figuresId, event.result) }
        else -> (screen as? Screen.HeadToHead)?.let { reduceOnTop(it, event, stamp) } ?: screen
    }
    return if (next == screen) state else state.copy(screen = next)
}

/** The user events of the head-to-head on top. */
private fun reduceOnTop(screen: Screen.HeadToHead, event: HeadToHeadEvent, stamp: EventStamp): Screen {
    val state = screen.state
    val selections = state.selections
    return when (event) {
        is HeadToHeadEvent.PartnerPicked ->
            if (event.b == screen.a) screen else screen.copy(b = event.b, state = state.partnerPicked(stamp.at))
        HeadToHeadEvent.ChangePartnerTapped ->
            if (screen.b == null) screen else screen.copy(b = null, state = state.partnerCleared())
        HeadToHeadEvent.RetryTapped -> screen.copy(
            state = if (screen.b == null) state.pickerRetried() else state.pairRetried(stamp.at),
        )
        is HeadToHeadEvent.ModeSelected -> screen.selected(selections.copy(mode = event.mode), stamp.at)
        is HeadToHeadEvent.X01FilterSelected -> screen.selected(selections.copy(x01Filter = event.filter), stamp.at)
        is HeadToHeadEvent.CricketFilterSelected ->
            screen.selected(selections.copy(cricketFilter = event.filter), stamp.at)
        is HeadToHeadEvent.PeriodSelected -> screen.selected(selections.copy(period = event.period), stamp.at)
        // HH7, HS9: the detail opens over this very screen, so that Back finds the pair and the selections as left.
        is HeadToHeadEvent.MatchTapped -> screen.open(Screen.MatchDetail(screen, MatchDetailState(event.sessionId)))
        else -> screen
    }
}

private fun Screen.HeadToHead.selected(selections: HeadToHeadSelections, at: Long): Screen =
    copy(state = state.selected(selections, at))

/** Applies [change] to the state of the head-to-head of [a] in this screen's chain; other screens are unchanged. */
internal fun Screen.withHeadToHead(a: Long, change: (HeadToHeadState) -> HeadToHeadState): Screen = when {
    this is Screen.HeadToHead && this.a == a -> copy(state = change(state))
    this is Screen.Stacked -> {
        val newParent = parent.withHeadToHead(a, change)
        if (newParent == parent) this else reparented(newParent)
    }
    else -> this
}

/** Every head-to-head of the chain that starts at this screen, top first. */
internal fun Screen.headToHeadsOfStack(): List<Screen.HeadToHead> = when (this) {
    is Screen.HeadToHead -> listOf(this) + parent.headToHeadsOfStack()
    is Screen.Stacked -> parent.headToHeadsOfStack()
    else -> emptyList()
}
