package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.ui.headtohead.PairContent

/** The effects of the Head-to-head screen: all run on the read executor (HS7, ST7). */
sealed interface HeadToHeadEffect : PersistenceEffect

/** Runs [effect] on the read executor and hands its answer to [onEvent], on that thread. */
internal fun Persistence.runHeadToHeadEffect(effect: HeadToHeadEffect, onEvent: (AppEvent) -> Unit) {
    when (effect) {
        is PersistenceEffect.LoadHeadToHeadPicker ->
            profileAccess.loadPicker(effect.a) { onEvent(HeadToHeadEvent.PickerLoaded(effect.a, effect.loadId, it)) }
        is PersistenceEffect.LoadHeadToHeadPair ->
            profileAccess.loadPair(effect.a, effect.b) {
                onEvent(HeadToHeadEvent.PairLoaded(effect.a, effect.loadId, it))
            }
        is PersistenceEffect.ComputeHeadToHead ->
            profileAccess.computeHeadToHead(effect.data, effect.selections, effect.now) {
                onEvent(HeadToHeadEvent.ResultComputed(effect.a, effect.figuresId, it))
            }
    }
}

/**
 * HH1, HH2, HS7, ST7: the picker is read when the screen opens and each time a retry raises its read number; the pair
 * is read each time its read number rises (a partner picked, a retry); the result is computed each time the data
 * or a selection changes (the figures number rises). A screen that is only hidden under a detail asks nothing again.
 */
internal fun headToHeadEffects(before: AppState, after: AppState): List<PersistenceEffect> {
    val previous = before.screen.headToHeadsOfStack().associateBy { it.a }
    return after.screen.headToHeadsOfStack().flatMap { screen ->
        val old = previous[screen.a]?.state
        val state = screen.state
        val data = (state.pair as? PairContent.Loaded)?.data
        listOfNotNull(
            PersistenceEffect.LoadHeadToHeadPicker(screen.a, state.pickerLoadId)
                .takeIf { old?.pickerLoadId != state.pickerLoadId },
            screen.b?.let { PersistenceEffect.LoadHeadToHeadPair(screen.a, it, state.pairLoadId) }
                ?.takeIf { old?.pairLoadId != state.pairLoadId },
            data?.let {
                PersistenceEffect.ComputeHeadToHead(screen.a, state.figuresId, it, state.selections, state.now)
            }?.takeIf { old?.figuresId != state.figuresId },
        )
    }
}
