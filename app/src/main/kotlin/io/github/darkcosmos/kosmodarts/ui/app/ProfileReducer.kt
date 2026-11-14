package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState

/**
 * PS3, PS6, HS7, HS9: the answers (read and figures) reach their Profile wherever it is in the stack, since a detail
 * may be open above it; the user events apply to the Profile on top only. Back is not handled here (see
 * [reduceStacked]).
 */
internal fun reduceProfileEvent(state: AppState, event: ProfileEvent, stamp: EventStamp): AppState {
    val screen = state.screen
    val next = when (event) {
        is ProfileEvent.Loaded ->
            screen.withProfile(event.profileId) { it.loaded(event.loadId, event.result) }
        is ProfileEvent.FiguresComputed ->
            screen.withProfile(event.profileId) { it.computed(event.figuresId, event.figures) }
        else -> (screen as? Screen.Profile)?.let { it.copy(profile = it.profile.after(event, stamp.at)) } ?: screen
    }
    return if (next == screen) state else state.copy(screen = next)
}

private fun ProfileState.after(event: ProfileEvent, at: Long): ProfileState = when (event) {
    ProfileEvent.RetryTapped -> retry(at)
    is ProfileEvent.SectionSelected -> sectionSelected(event.section)
    is ProfileEvent.X01FilterSelected -> x01FilterSelected(event.filter, at)
    is ProfileEvent.CricketFilterSelected -> cricketFilterSelected(event.filter, at)
    is ProfileEvent.X01PeriodSelected -> x01PeriodSelected(event.period, at)
    is ProfileEvent.CricketPeriodSelected -> cricketPeriodSelected(event.period, at)
    is ProfileEvent.Loaded, is ProfileEvent.FiguresComputed -> this
}

/** Applies [change] to the state of the Profile of [profileId] in this screen's chain; other screens are unchanged. */
private fun Screen.withProfile(profileId: Long, change: (ProfileState) -> ProfileState): Screen = when {
    this is Screen.Profile && this.profileId == profileId -> copy(profile = change(profile))
    this is Screen.Stacked -> {
        val newParent = parent.withProfile(profileId, change)
        if (newParent == parent) this else reparented(newParent)
    }
    else -> this
}

/** Every Profile of the chain that starts at this screen, top first. */
internal fun Screen.profilesOfStack(): List<Screen.Profile> = when (this) {
    is Screen.Profile -> listOf(this) + parent.profilesOfStack()
    is Screen.Stacked -> parent.profilesOfStack()
    else -> emptyList()
}
