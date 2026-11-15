package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HeadToHeadData
import io.github.darkcosmos.kosmodarts.history.HeadToHeadPicker
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadMode
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadResult

/**
 * What the user does on the Head-to-head screen, and what the database and the computation answer (HH1 to HH9).
 * Opening is not an event: a caller opens the screen with `Screen.open(Screen.HeadToHead(...))` (HS9). The answers name
 * the profile [a] of the screen they belong to, since a detail may be open above it.
 */
sealed interface HeadToHeadEvent : AppEvent {
    /** HH1: the profile [b] was picked in the picker. */
    data class PartnerPicked(val b: Long) : HeadToHeadEvent

    /** "Change" of the loaded screen: the picker shows again. */
    data object ChangePartnerTapped : HeadToHeadEvent

    /** "Retry" after a failed read (HS7), of the picker or of the pair. */
    data object RetryTapped : HeadToHeadEvent

    data class ModeSelected(val mode: HeadToHeadMode) : HeadToHeadEvent

    data class X01FilterSelected(val filter: X01Filter) : HeadToHeadEvent

    data class CricketFilterSelected(val filter: CricketFilter) : HeadToHeadEvent

    data class PeriodSelected(val period: Period) : HeadToHeadEvent

    /** HH7: a common match of the list was tapped; its detail opens above the screen (HS9). */
    data class MatchTapped(val sessionId: Long) : HeadToHeadEvent

    /** The answer to picker read [loadId] of the head-to-head of [a]. */
    data class PickerLoaded(val a: Long, val loadId: Int, val result: DbResult<HeadToHeadPicker>) : HeadToHeadEvent

    /** The answer to pair read [loadId] of the head-to-head of [a]. */
    data class PairLoaded(val a: Long, val loadId: Int, val result: DbResult<HeadToHeadData>) : HeadToHeadEvent

    /** The answer to computation [figuresId] of the head-to-head of [a]. */
    data class ResultComputed(val a: Long, val figuresId: Int, val result: HeadToHeadResult) : HeadToHeadEvent
}
