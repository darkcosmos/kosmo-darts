package io.github.darkcosmos.kosmodarts.ui.headtohead

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileDates
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.HeadToHeadFigures
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.StatsFilter
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.engine.stats.headToHead
import io.github.darkcosmos.kosmodarts.engine.stats.selectCommon
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HeadToHeadData
import io.github.darkcosmos.kosmodarts.history.HeadToHeadPicker
import io.github.darkcosmos.kosmodarts.history.PickerProfile
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileSince

/** HH6: the mode of the comparison: every mode, X01 only or Cricket only. */
enum class HeadToHeadMode { ALL, X01, CRICKET }

/**
 * What the user chose (HH6, ST3, ST4): the mode, the filters of each mode (kept when the mode changes) and the period,
 * shared by the modes.
 */
data class HeadToHeadSelections(
    val mode: HeadToHeadMode = HeadToHeadMode.ALL,
    val x01Filter: X01Filter = X01Filter(),
    val cricketFilter: CricketFilter = CricketFilter(),
    val period: Period = Period.SINCE_ALWAYS,
) {
    /** The filter the engine applies for the chosen mode: none of its own for All modes (HH6). */
    val statsFilter: StatsFilter
        get() = when (mode) {
            HeadToHeadMode.ALL -> StatsFilter.AllModes
            HeadToHeadMode.X01 -> x01Filter
            HeadToHeadMode.CRICKET -> cricketFilter
        }

    /** Whether the filters of a mode show: none for All modes. */
    val showsModeFilters: Boolean
        get() = mode != HeadToHeadMode.ALL
}

/** HH3: who won a common match, by final rank; equal ranks are a draw. */
enum class DuelOutcome { A_WINS, B_WINS, DRAW }

/** A line of the list of common matches (HH7): [sessionId] opens the detail. */
data class CommonMatchRow(
    val sessionId: Long,
    val endedAt: Long,
    val mode: ArchivedMode,
    val variant: String,
    val outcome: DuelOutcome,
)

/**
 * What the head-to-head shows for one selection: [eligibleCount] is every common match of the two (HH2, all modes, any
 * period), [figures] and [matches] (newest first) cover the selection only (HH6).
 */
data class HeadToHeadResult(val eligibleCount: Int, val figures: HeadToHeadFigures, val matches: List<CommonMatchRow>)

/** HH2 to HH6, ST10: the result of [this] pair for [selections]; pure, blocking, run off the main thread. */
fun HeadToHeadData.result(selections: HeadToHeadSelections, now: Long): HeadToHeadResult {
    val filter = selections.statsFilter
    val all = StatsFilter.AllModes
    val eligible = selectCommon(matches, a.id, a.resetAt, b.id, b.resetAt, all, Period.SINCE_ALWAYS, now)
    val selected = selectCommon(matches, a.id, a.resetAt, b.id, b.resetAt, filter, selections.period, now)
    val figures = headToHead(matches, a.id, a.resetAt, b.id, b.resetAt, filter, selections.period, now)
    return HeadToHeadResult(eligible.size, figures, selected.asReversed().mapNotNull { row(it) })
}

private fun HeadToHeadData.row(match: StatsMatch): CommonMatchRow? {
    val sessionId = match.sessionId.toLongOrNull()
    val rankA = match.playerOf(a.id)?.rank
    val rankB = match.playerOf(b.id)?.rank
    if (sessionId == null || rankA == null || rankB == null) return null
    val outcome = duelOutcome(rankA, rankB)
    return CommonMatchRow(sessionId, match.endedAt, match.settings.mode, match.settings.variant, outcome)
}

/** HH3: the better (lower) final rank wins; equal ranks are a draw. */
private fun duelOutcome(rankA: Int, rankB: Int): DuelOutcome = when {
    rankA < rankB -> DuelOutcome.A_WINS
    rankB < rankA -> DuelOutcome.B_WINS
    else -> DuelOutcome.DRAW
}

/** What the picker read gave (HH1): nothing yet, a failure, or the list. */
sealed interface PickerContent {
    data object Loading : PickerContent

    data object Failed : PickerContent

    data class Loaded(val picker: HeadToHeadPicker) : PickerContent
}

/** What the read of the pair gave (HH9, ST10): nothing yet, a failure, or the data with its result once computed. */
sealed interface PairContent {
    data object Loading : PairContent

    data object Failed : PairContent

    /** [result] is `null` until the result of the current selection was first computed, off the main thread. */
    data class Loaded(val data: HeadToHeadData, val result: HeadToHeadResult? = null) : PairContent
}

/** Which of the views of the Head-to-head shows (HH1, HH9, PS8). */
enum class HeadToHeadView {
    PICKER_LOADING,
    PICKER_FAILED,
    PICKER_EMPTY,
    PICKER,
    PAIR_LOADING,
    PAIR_FAILED,
    NO_COMMON_MATCH,
    CONTENT,
}

/**
 * State of the Head-to-head screen (HH1 to HH9). Pure: every change returns a new state. The partner is chosen on the
 * screen (`Screen.HeadToHead.b`, `null` while the picker shows). A change of selection raises [figuresId], and the app
 * computes the result off the main thread; an answer to an older read or computation is ignored. Everything lives in
 * the app state, so it survives a rotation and a return from a common match detail (HH9).
 *
 * @property pickerLoadId number of the picker read this state waits for or shows.
 * @property pairLoadId number of the pair read this state waits for or shows; 0 until a partner is chosen.
 * @property figuresId number of the computation this state waits for or shows.
 * @property now clock reading of the last event that could change a selection, for "Last 30 days" (ST4).
 */
data class HeadToHeadState(
    val picker: PickerContent = PickerContent.Loading,
    val pickerLoadId: Int = FIRST_LOAD,
    val pair: PairContent = PairContent.Loading,
    val pairLoadId: Int = 0,
    val figuresId: Int = 0,
    val selections: HeadToHeadSelections = HeadToHeadSelections(),
    val now: Long = 0,
) {
    private val data: HeadToHeadData?
        get() = (pair as? PairContent.Loaded)?.data

    val result: HeadToHeadResult?
        get() = (pair as? PairContent.Loaded)?.result

    /** The view for a screen whose partner is [partner]: the picker while there is none. */
    fun view(partner: Long?): HeadToHeadView = when {
        partner == null -> pickerView()
        pair == PairContent.Failed -> HeadToHeadView.PAIR_FAILED
        result == null -> HeadToHeadView.PAIR_LOADING
        result?.eligibleCount == 0 -> HeadToHeadView.NO_COMMON_MATCH
        else -> HeadToHeadView.CONTENT
    }

    private fun pickerView(): HeadToHeadView = when (val content = picker) {
        PickerContent.Loading -> HeadToHeadView.PICKER_LOADING
        PickerContent.Failed -> HeadToHeadView.PICKER_FAILED
        is PickerContent.Loaded ->
            if (content.picker.others.isEmpty()) HeadToHeadView.PICKER_EMPTY else HeadToHeadView.PICKER
    }

    /** HH1: the picker's profiles, most common matches first. */
    val pickerEntries: List<PickerProfile>
        get() = (picker as? PickerContent.Loaded)?.picker?.others.orEmpty()

    /** HH5: the names of the pair, once read. */
    val nameA: String?
        get() = data?.a?.name

    val nameB: String?
        get() = data?.b?.name

    /** HH8: "Since always", or "Since <latest real reset date>" when at least one of the two is really reset. */
    val since: ProfileSince?
        get() = data?.let { pair ->
            val resets = listOf(pair.a, pair.b)
                .filter { ProfileRules.isReallyReset(ProfileDates(it.resetAt, it.createdAt, 0)) }
                .map { it.resetAt }
            if (resets.isEmpty()) ProfileSince.Always else ProfileSince.Date(resets.max())
        }

    /** ST10: the number of ignored common matches. */
    val ignoredMatches: Int
        get() = data?.skippedMatches ?: 0

    /** ST10: the notice shows when at least one common match is ignored. */
    val isIgnoredNoticeShown: Boolean
        get() = ignoredMatches > 0

    /** PS8: the pair has common matches, none of them for the mode, filters and period. */
    val isSelectionEmpty: Boolean
        get() = result?.let { it.eligibleCount > 0 && it.figures.commonCount == 0 } ?: false

    /** The answer to picker read [id]; an older answer changes nothing. */
    fun pickerLoaded(id: Int, result: DbResult<HeadToHeadPicker>): HeadToHeadState = when {
        id != pickerLoadId -> this
        result is DbResult.Ok -> copy(picker = PickerContent.Loaded(result.value))
        else -> copy(picker = PickerContent.Failed)
    }

    /** "Retry" of a failed picker read (HS7). */
    fun pickerRetried(): HeadToHeadState =
        if (picker == PickerContent.Failed) {
            copy(picker = PickerContent.Loading, pickerLoadId = pickerLoadId + 1)
        } else {
            this
        }

    /** HH1: the pair with the chosen partner is read; the selections are kept (HH9). */
    fun partnerPicked(at: Long): HeadToHeadState =
        copy(pair = PairContent.Loading, pairLoadId = pairLoadId + 1, now = at)

    /** The picker comes back ("Change"): its list is read again if its read failed. */
    fun partnerCleared(): HeadToHeadState = pickerRetried()

    /** "Retry" of a failed pair read (HS7). */
    fun pairRetried(at: Long): HeadToHeadState =
        if (pair == PairContent.Failed) {
            copy(pair = PairContent.Loading, pairLoadId = pairLoadId + 1, now = at)
        } else {
            this
        }

    /** The answer to pair read [id]; an older answer changes nothing. The result of the selection is then computed. */
    fun pairLoaded(id: Int, result: DbResult<HeadToHeadData>): HeadToHeadState = when {
        id != pairLoadId -> this
        result is DbResult.Ok -> copy(pair = PairContent.Loaded(result.value), figuresId = figuresId + 1)
        else -> copy(pair = PairContent.Failed)
    }

    /** The answer to computation [id]; an older answer changes nothing. */
    fun computed(id: Int, result: HeadToHeadResult): HeadToHeadState {
        val loaded = pair as? PairContent.Loaded
        return if (id != figuresId || loaded == null) this else copy(pair = loaded.copy(result = result))
    }

    /** HH6, ST3, ST4: the mode, a filter or the period changes; the result is computed again, nothing is read. */
    fun selected(next: HeadToHeadSelections, at: Long): HeadToHeadState = when {
        next == selections -> this
        pair is PairContent.Loaded -> copy(selections = next, now = at, figuresId = figuresId + 1)
        else -> copy(selections = next, now = at)
    }

    private companion object {
        const val FIRST_LOAD = 1
    }
}
