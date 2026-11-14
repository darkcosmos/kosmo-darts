package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileAnswer
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileContent
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRequest

/** The effects of the Profile screen: both run on the read executor (HS7, ST7). */
sealed interface ProfileEffect : PersistenceEffect

/** Runs [effect] on the read executor and hands its answer to [onEvent], on that thread. */
internal fun Persistence.runProfileEffect(effect: ProfileEffect, onEvent: (AppEvent) -> Unit) {
    when (effect) {
        is PersistenceEffect.LoadProfile ->
            profileAccess.load(effect.profileId) { onEvent(ProfileEvent.Loaded(effect.profileId, effect.loadId, it)) }
        is PersistenceEffect.ComputeProfileFigures ->
            profileAccess.computeFigures(effect.data, effect.query) {
                onEvent(ProfileEvent.FiguresComputed(effect.profileId, effect.figuresId, it))
            }
        is PersistenceEffect.LoadProfileRoster ->
            playersAccess.load { onEvent(ProfileEvent.RosterLoaded(effect.profileId, effect.loadId, it)) }
        is PersistenceEffect.RunProfileRequest -> runProfileRequest(effect.profileId, effect.request, onEvent)
    }
}

/**
 * PM3 to PM6: runs [request] on the persistence queue (writes) or the read executor (the count of a deletion) and hands
 * its answer to [onEvent], on that thread: the caller moves it to the main thread.
 */
private fun Persistence.runProfileRequest(profileId: Long, request: ProfileRequest, onEvent: (AppEvent) -> Unit) {
    fun answer(value: ProfileAnswer) = onEvent(ProfileEvent.Answered(profileId, request, value))
    when (request) {
        is ProfileRequest.Rename ->
            playersAccess.change({ it.rename(request.profile.id, request.name) }) { answer(ProfileAnswer.Renamed(it)) }
        is ProfileRequest.Merge ->
            playersAccess.change({ it.merge(request.source.id, request.target.id) }) {
                answer(ProfileAnswer.Merged(it))
            }
        is ProfileRequest.Reset ->
            playersAccess.change({ it.reset(request.profile.id, request.at) }) { answer(ProfileAnswer.ResetDone(it)) }
        is ProfileRequest.CountDeletion ->
            playersAccess.countDeletion(request.profile.id) { answer(ProfileAnswer.Counted(it)) }
        is ProfileRequest.Delete ->
            playersAccess.change({ it.delete(request.profile.id, request.at) }) { answer(ProfileAnswer.Deleted(it)) }
    }
}

/**
 * HS7, ST7: a Profile reads when it opens and each time a retry raises its read number; its figures are computed each
 * time the data arrives or a selection changes (the figures number rises). Every Profile of the stack counts: opening
 * a detail above one asks nothing again, since its state is the same as before.
 */
internal fun profileEffects(before: AppState, after: AppState): List<PersistenceEffect> {
    val previous = before.screen.profilesOfStack().associateBy { it.profileId }
    return after.screen.profilesOfStack().flatMap { screen ->
        val old = previous[screen.profileId]?.profile
        val state = screen.profile
        val data = (state.content as? ProfileContent.Loaded)?.data
        val isNewRead = old?.loadId != state.loadId
        listOfNotNull(
            PersistenceEffect.LoadProfile(screen.profileId, state.loadId).takeIf { isNewRead },
            PersistenceEffect.LoadProfileRoster(screen.profileId, state.loadId).takeIf { isNewRead },
            data?.let { PersistenceEffect.ComputeProfileFigures(screen.profileId, state.figuresId, it, state.query) }
                ?.takeIf { old?.figuresId != state.figuresId },
            // PM3 to PM6: a request runs once, when it appears; its answer clears it.
            state.request?.let { PersistenceEffect.RunProfileRequest(screen.profileId, it) }
                ?.takeIf { old?.request != state.request },
        )
    }
}
