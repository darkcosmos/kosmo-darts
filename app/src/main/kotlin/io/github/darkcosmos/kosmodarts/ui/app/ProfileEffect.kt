package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileContent

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
        listOfNotNull(
            PersistenceEffect.LoadProfile(screen.profileId, state.loadId).takeIf { old?.loadId != state.loadId },
            data?.let { PersistenceEffect.ComputeProfileFigures(screen.profileId, state.figuresId, it, state.query) }
                ?.takeIf { old?.figuresId != state.figuresId },
        )
    }
}
