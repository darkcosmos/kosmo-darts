package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState
import io.github.darkcosmos.kosmodarts.ui.profile.cancelled
import io.github.darkcosmos.kosmodarts.ui.profile.confirmed
import io.github.darkcosmos.kosmodarts.ui.profile.deleteStarted
import io.github.darkcosmos.kosmodarts.ui.profile.manageToggled
import io.github.darkcosmos.kosmodarts.ui.profile.mergeIntoTakenTapped
import io.github.darkcosmos.kosmodarts.ui.profile.mergeStarted
import io.github.darkcosmos.kosmodarts.ui.profile.mergeTargetPicked
import io.github.darkcosmos.kosmodarts.ui.profile.renameSaved
import io.github.darkcosmos.kosmodarts.ui.profile.renameStarted
import io.github.darkcosmos.kosmodarts.ui.profile.renameTextChanged
import io.github.darkcosmos.kosmodarts.ui.profile.resetStarted

/**
 * PS3, PS6, PS7, HS7, HS9: the answers (read, profiles list, figures, request) reach their Profile wherever it is in
 * stack, since a detail may be open above it; the user events apply to the Profile on top only. Back is not handled
 * here (see [reduceStacked]).
 */
internal fun reduceProfileEvent(state: AppState, event: ProfileEvent, stamp: EventStamp): AppState {
    val screen = state.screen
    val next = when (event) {
        is ProfileEvent.Loaded ->
            screen.withProfile(event.profileId) { it.loaded(event.loadId, event.result) }
        is ProfileEvent.RosterLoaded ->
            screen.withProfile(event.profileId) { it.rosterLoaded(event.loadId, event.profileId, event.result) }
        is ProfileEvent.FiguresComputed ->
            screen.withProfile(event.profileId) { it.computed(event.figuresId, event.figures) }
        is ProfileEvent.Answered -> return reduceProfileAnswer(state, event, stamp)
        else -> (screen as? Screen.Profile)?.let {
            val savedNames = screen.homeOfStack()?.resumableGame?.playerNames().orEmpty()
            it.copy(profile = it.profile.after(event, savedNames, stamp.at))
        } ?: screen
    }
    return if (next == screen) state else state.copy(screen = next)
}

private fun ProfileState.after(event: ProfileEvent, savedNames: List<String>, at: Long): ProfileState = when (event) {
    ProfileEvent.RetryTapped -> retry(at)
    is ProfileEvent.SectionSelected -> sectionSelected(event.section)
    is ProfileEvent.X01FilterSelected -> x01FilterSelected(event.filter, at)
    is ProfileEvent.CricketFilterSelected -> cricketFilterSelected(event.filter, at)
    is ProfileEvent.X01PeriodSelected -> x01PeriodSelected(event.period, at)
    is ProfileEvent.CricketPeriodSelected -> cricketPeriodSelected(event.period, at)
    else -> afterAction(event, savedNames, at)
}

/** The part of [after] for the actions of the Profile (PS7); answers are not applied here. */
private fun ProfileState.afterAction(event: ProfileEvent, savedNames: List<String>, at: Long): ProfileState =
    when (event) {
        ProfileEvent.ManageToggled -> manageToggled()
        ProfileEvent.RenameTapped -> renameStarted(savedNames)
        is ProfileEvent.RenameTextChanged -> renameTextChanged(event.text)
        ProfileEvent.RenameSaved -> renameSaved()
        ProfileEvent.MergeTapped -> mergeStarted(savedNames)
        is ProfileEvent.MergeTargetPicked -> mergeTargetPicked(event.id, savedNames)
        ProfileEvent.MergeIntoTakenTapped -> mergeIntoTakenTapped(savedNames)
        ProfileEvent.ResetTapped -> resetStarted()
        ProfileEvent.DeleteTapped -> deleteStarted(savedNames)
        ProfileEvent.Confirmed -> confirmed(at)
        ProfileEvent.Cancelled -> cancelled()
        else -> this
    }

/** Applies [change] to the state of the Profile of [profileId] in this screen's chain; other screens are unchanged. */
internal fun Screen.withProfile(profileId: Long, change: (ProfileState) -> ProfileState): Screen =
    mapProfile(profileId) { it.copy(profile = change(it.profile)) }

/** Replaces the Profile of [profileId] in this screen's chain by what [change] gives; the screens above it follow. */
internal fun Screen.mapProfile(profileId: Long, change: (Screen.Profile) -> Screen): Screen = when {
    this is Screen.Profile && this.profileId == profileId -> change(this)
    this is Screen.Stacked -> {
        val newParent = parent.mapProfile(profileId, change)
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
