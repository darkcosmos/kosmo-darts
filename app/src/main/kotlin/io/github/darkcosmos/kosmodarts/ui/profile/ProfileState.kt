package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.engine.stats.CricketFigures
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.StatsFilter
import io.github.darkcosmos.kosmodarts.engine.stats.TrendPoint
import io.github.darkcosmos.kosmodarts.engine.stats.TrendSummary
import io.github.darkcosmos.kosmodarts.engine.stats.X01Figures
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.engine.stats.cricketFigures
import io.github.darkcosmos.kosmodarts.engine.stats.select
import io.github.darkcosmos.kosmodarts.engine.stats.summarize
import io.github.darkcosmos.kosmodarts.engine.stats.trendSeries
import io.github.darkcosmos.kosmodarts.engine.stats.x01Figures
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData

/** The three sections of the Profile (PS3, owner P4 A). */
enum class ProfileSection { X01, CRICKET, HEATMAP }

/** The "Since ..." of the header (PS2, ST9): the profile was never really reset, or really reset on [Date]. */
sealed interface ProfileSince {
    data object Always : ProfileSince

    /** [millis] is the reset date, to show in the device locale format. */
    data class Date(val millis: Long) : ProfileSince
}

/** The selections that decide the figures (ST2 to ST4); [now] is the clock reading for "Last 30 days". */
data class ProfileQuery(
    val x01Filter: X01Filter = X01Filter(),
    val x01Period: Period = Period.SINCE_ALWAYS,
    val cricketFilter: CricketFilter = CricketFilter(),
    val cricketPeriod: Period = Period.SINCE_ALWAYS,
    val now: Long = 0,
)

/** The X01 section for one selection (PS4, PS5): the figures, the trend and its text summary, from the engine. */
data class X01Selection(
    val matchCount: Int,
    val figures: X01Figures,
    val trend: List<TrendPoint>,
    val trendSummary: TrendSummary,
)

/** The Cricket section for one selection (PS4, PS5). */
data class CricketSelection(
    val matchCount: Int,
    val figures: CricketFigures,
    val trend: List<TrendPoint>,
    val trendSummary: TrendSummary,
)

/** Everything the Profile shows, computed once per selection. [eligibleCount] is every match that counts (ST2). */
data class ProfileFigures(val eligibleCount: Int, val x01: X01Selection, val cricket: CricketSelection)

/** The figures of [this] profile for [query]: ST2 to ST8, ST10 (invalid matches are already out). Pure, blocking. */
fun ProfileStatsData.figures(query: ProfileQuery): ProfileFigures {
    val eligible = select(matches, profileId, resetAt, StatsFilter.AllModes, Period.SINCE_ALWAYS, query.now)
    val x01 = select(matches, profileId, resetAt, query.x01Filter, query.x01Period, query.now)
    val cricket = select(matches, profileId, resetAt, query.cricketFilter, query.cricketPeriod, query.now)
    val x01Trend = trendSeries(x01, profileId)
    val cricketTrend = trendSeries(cricket, profileId)
    return ProfileFigures(
        eligibleCount = eligible.size,
        x01 = X01Selection(x01.size, x01Figures(x01, profileId), x01Trend, summarize(x01Trend)),
        cricket = CricketSelection(
            cricket.size,
            cricketFigures(cricket, profileId, query.cricketFilter.variant),
            cricketTrend,
            summarize(cricketTrend),
        ),
    )
}

/** What the last read gave (PS6, HS7): nothing yet, a failure, or the data, with its figures once computed. */
sealed interface ProfileContent {
    data object Loading : ProfileContent

    data object Failed : ProfileContent

    /** [figures] is `null` until the figures of the current selection were first computed, off the main thread. */
    data class Loaded(val data: ProfileStatsData, val figures: ProfileFigures? = null) : ProfileContent
}

/** Which of the screens of the Profile shows (PS6). */
enum class ProfileView { LOADING, FAILED, NO_MATCH, CONTENT }

/**
 * State of the Profile screen (PS2 to PS6, HS9). Pure: every change returns a new state. A change of selection raises
 * [figuresId], and the app computes the figures off the main thread; an answer to an older computation or read is
 * ignored. The selections live here, in the app state, so they survive a rotation and a return from a pushed screen.
 *
 * @property loadId number of the read this state waits for or shows.
 * @property figuresId number of the computation this state waits for or shows.
 * @property now clock reading of the last event that could change a selection, for "Last 30 days" (ST4).
 */
data class ProfileState(
    val content: ProfileContent = ProfileContent.Loading,
    val loadId: Int = FIRST_LOAD,
    val figuresId: Int = 0,
    val section: ProfileSection = ProfileSection.X01,
    val x01Filter: X01Filter = X01Filter(),
    val x01Period: Period = Period.SINCE_ALWAYS,
    val cricketFilter: CricketFilter = CricketFilter(),
    val cricketPeriod: Period = Period.SINCE_ALWAYS,
    val now: Long = 0,
    val roster: ProfileRoster = ProfileRoster.Loading,
    val isManageOpen: Boolean = false,
    val panel: ProfilePanel? = null,
    val message: ProfileMessage? = null,
    val request: ProfileRequest? = null,
) {
    private val data: ProfileStatsData?
        get() = (content as? ProfileContent.Loaded)?.data

    val figures: ProfileFigures?
        get() = (content as? ProfileContent.Loaded)?.figures

    /** The selections as the engine takes them. */
    val query: ProfileQuery
        get() = ProfileQuery(x01Filter, x01Period, cricketFilter, cricketPeriod, now)

    val view: ProfileView
        get() = when {
            content == ProfileContent.Failed -> ProfileView.FAILED
            figures == null -> ProfileView.LOADING
            figures?.eligibleCount == 0 -> ProfileView.NO_MATCH
            else -> ProfileView.CONTENT
        }

    /** PS6: the shown section has no match for its filters or period, while the profile has matches. */
    val isSelectionEmpty: Boolean
        get() = when (section) {
            ProfileSection.X01 -> figures?.x01?.matchCount == 0
            ProfileSection.CRICKET -> figures?.cricket?.matchCount == 0
            ProfileSection.HEATMAP -> false
        }

    /** PS2: the name of the header. */
    val name: String?
        get() = data?.name

    /** PS2, ST9: "Since always" until a real reset (the reset date is after the creation date). */
    val since: ProfileSince?
        get() = data?.let { if (it.resetAt > it.createdAt) ProfileSince.Date(it.resetAt) else ProfileSince.Always }

    /** ST10: the number of ignored matches. */
    val ignoredMatches: Int
        get() = data?.skippedMatches ?: 0

    /** ST10: the notice shows when at least one match is ignored. */
    val isIgnoredNoticeShown: Boolean
        get() = ignoredMatches > 0

    /** The profiles that the merge can target (PM4): every other one. */
    val mergeTargets: List<PlayerSummary>
        get() = (roster as? ProfileRoster.Loaded)?.others.orEmpty()

    /** Whether an action waits for the database: every user action is ignored meanwhile (PM3 to PM6). */
    val isBusy: Boolean
        get() = request != null

    /** "Retry" after a failed read (HS7). */
    fun retry(at: Long): ProfileState = if (content == ProfileContent.Failed) {
        copy(content = ProfileContent.Loading, roster = ProfileRoster.Loading, loadId = loadId + 1, now = at)
    } else {
        this
    }

    /** PS7: asks for a new read of the profile and of the profiles list; what is shown stays until the answer. */
    fun reloaded(): ProfileState = copy(loadId = loadId + 1)

    /**
     * The answer to the read of the profiles list for read [id] of the profile [profileId]; an older answer changes
     * nothing. A failure is only noted: the actions then stay unavailable until the next read.
     */
    fun rosterLoaded(id: Int, profileId: Long, result: DbResult<List<PlayerSummary>>): ProfileState = when {
        id != loadId -> this
        result is DbResult.Ok -> {
            val (self, others) = result.value.partition { it.id == profileId }
            val target = (panel as? ProfilePanel.MergeConfirm)?.target
            // A panel closes when the shown profile is no longer listed, or when the target of its merge is gone.
            val isTargetGone = target != null && others.none { it.id == target.id }
            val loaded = copy(roster = ProfileRoster.Loaded(self.firstOrNull(), others))
            if (isTargetGone || self.isEmpty()) loaded.copy(panel = null, message = null) else loaded
        }
        else -> copy(roster = ProfileRoster.Failed)
    }

    /** The answer to read [id]; an older answer changes nothing. The figures of the selection are then computed. */
    fun loaded(id: Int, result: DbResult<ProfileStatsData>): ProfileState = when {
        id != loadId -> this
        result is DbResult.Ok -> copy(content = ProfileContent.Loaded(result.value), figuresId = figuresId + 1)
        else -> copy(content = ProfileContent.Failed)
    }

    /** The answer to computation [id]; an older answer changes nothing. */
    fun computed(id: Int, result: ProfileFigures): ProfileState {
        val loaded = content as? ProfileContent.Loaded
        return if (id == figuresId && loaded != null) copy(content = loaded.copy(figures = result)) else this
    }

    fun sectionSelected(selected: ProfileSection): ProfileState = copy(section = selected)

    /** PS3, ST3: the X01 filter changes; the figures are computed again, the data is not read again. */
    fun x01FilterSelected(filter: X01Filter, at: Long): ProfileState =
        if (filter == x01Filter) this else copy(x01Filter = filter).recomputed(at)

    fun cricketFilterSelected(filter: CricketFilter, at: Long): ProfileState =
        if (filter == cricketFilter) this else copy(cricketFilter = filter).recomputed(at)

    /** ST4: the X01 period changes. */
    fun x01PeriodSelected(period: Period, at: Long): ProfileState =
        if (period == x01Period) this else copy(x01Period = period).recomputed(at)

    fun cricketPeriodSelected(period: Period, at: Long): ProfileState =
        if (period == cricketPeriod) this else copy(cricketPeriod = period).recomputed(at)

    private fun recomputed(at: Long): ProfileState =
        if (content is ProfileContent.Loaded) copy(now = at, figuresId = figuresId + 1) else copy(now = at)

    private companion object {
        const val FIRST_LOAD = 1
    }
}
