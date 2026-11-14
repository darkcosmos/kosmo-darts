package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapMode
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileAnswer
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileFigures
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRequest
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

    /** The answer to read [loadId] of the profiles list of the Profile of [profileId] (PM3, PM4). */
    data class RosterLoaded(val profileId: Long, val loadId: Int, val result: DbResult<List<PlayerSummary>>) :
        ProfileEvent

    /** "Manage player" (PS2): unfolds or folds the four actions. */
    data object ManageToggled : ProfileEvent

    data object RenameTapped : ProfileEvent

    data class RenameTextChanged(val text: String) : ProfileEvent

    /** "Save" of the rename panel. */
    data object RenameSaved : ProfileEvent

    data object MergeTapped : ProfileEvent

    /** The profile [id] picked as the target of the merge. */
    data class MergeTargetPicked(val id: Long) : ProfileEvent

    /** "Merge into ..." of the rename panel, when the new name belongs to another profile (Q6). */
    data object MergeIntoTakenTapped : ProfileEvent

    data object ResetTapped : ProfileEvent

    data object DeleteTapped : ProfileEvent

    /** The confirm button of the merge, reset or delete panel. */
    data object Confirmed : ProfileEvent

    /** "Cancel" of any panel. */
    data object Cancelled : ProfileEvent

    /** The database answered [request] of the Profile of [profileId]. It counts even when the screen was left (PM9). */
    data class Answered(val profileId: Long, val request: ProfileRequest, val answer: ProfileAnswer) : ProfileEvent

    data class SectionSelected(val section: ProfileSection) : ProfileEvent

    data class X01FilterSelected(val filter: X01Filter) : ProfileEvent

    data class CricketFilterSelected(val filter: CricketFilter) : ProfileEvent

    data class X01PeriodSelected(val period: Period) : ProfileEvent

    data class CricketPeriodSelected(val period: Period) : ProfileEvent

    /** HM3: the mode of the Heatmap section (All modes, X01, Cricket). */
    data class HeatmapModeSelected(val mode: HeatmapMode) : ProfileEvent

    data class HeatmapX01FilterSelected(val filter: X01Filter) : ProfileEvent

    data class HeatmapCricketFilterSelected(val filter: CricketFilter) : ProfileEvent

    data class HeatmapPeriodSelected(val period: Period) : ProfileEvent

    /** HM6: a tap on a zone of the board. */
    data class HeatmapZoneTapped(val zone: BoardZone) : ProfileEvent
}
