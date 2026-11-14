package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileFigures
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileSection

/**
 * What the user does on the Profile screen, and what the database and the figures computation answer (PS2 to PS6).
 * Opening is not an event: a caller opens the screen with `Screen.open(Screen.Profile(...))` (HS9).
 */
sealed interface ProfileEvent : AppEvent {
    /** "Retry" after a failed read (HS7). */
    data object RetryTapped : ProfileEvent

    /** The answer to read [loadId] of the Profile of [profileId]; it counts even when another screen is above. */
    data class Loaded(val profileId: Long, val loadId: Int, val result: DbResult<ProfileStatsData>) : ProfileEvent

    /** The answer to computation [figuresId] of the Profile of [profileId]. */
    data class FiguresComputed(val profileId: Long, val figuresId: Int, val figures: ProfileFigures) : ProfileEvent

    data class SectionSelected(val section: ProfileSection) : ProfileEvent

    data class X01FilterSelected(val filter: X01Filter) : ProfileEvent

    data class CricketFilterSelected(val filter: CricketFilter) : ProfileEvent

    data class X01PeriodSelected(val period: Period) : ProfileEvent

    data class CricketPeriodSelected(val period: Period) : ProfileEvent
}
