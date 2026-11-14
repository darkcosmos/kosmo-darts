package io.github.darkcosmos.kosmodarts.ui.headtohead

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HeadToHeadPicker
import io.github.darkcosmos.kosmodarts.history.PickerProfile
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadTestData.A
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadTestData.B
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileSince
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HH1 to HH9, ST10: the head-to-head state holder and its result. */
class HeadToHeadStateTest {
    private val data = HeadToHeadTestData.pair(
        listOf(
            HeadToHeadTestData.x01("1", startedAt = 2_000),
            HeadToHeadTestData.cricket("2", startedAt = 4_000),
            HeadToHeadTestData.x01("3", startedAt = 6_000, rankA = 2, rankB = 1, legsA = 0, legsB = 1),
        ),
    )

    private fun shown(state: HeadToHeadState = HeadToHeadState()): HeadToHeadState {
        val picked = state.partnerPicked(1L)
        val loaded = picked.pairLoaded(picked.pairLoadId, DbResult.Ok(data))
        return loaded.computed(loaded.figuresId, data.result(loaded.selections, loaded.now))
    }

    @Test
    fun `HH1 the picker is read once on opening, then shows the profiles, or the empty list, or the failure`() {
        val opened = HeadToHeadState()
        assertEquals(1, opened.pickerLoadId)
        assertEquals(HeadToHeadView.PICKER_LOADING, opened.view(null))

        val picker = HeadToHeadPicker(A, "Kevin", listOf(PickerProfile(B, "Zoe", 3), PickerProfile(3, "Abe", 0)))
        val loaded = opened.pickerLoaded(1, DbResult.Ok(picker))
        assertEquals(HeadToHeadView.PICKER, loaded.view(null))
        // A profile with no common match is listed (Q9 A).
        assertEquals(listOf(3, 0), loaded.pickerEntries.map { it.commonCount })

        val alone = opened.pickerLoaded(1, DbResult.Ok(HeadToHeadPicker(A, "Kevin", emptyList())))
        assertEquals(HeadToHeadView.PICKER_EMPTY, alone.view(null))
        assertEquals(HeadToHeadView.PICKER_FAILED, opened.pickerLoaded(1, DbResult.Failed).view(null))
    }

    @Test
    fun `HS7 a failed picker read is read again by Retry, and a late answer of the old read changes nothing`() {
        val failed = HeadToHeadState().pickerLoaded(1, DbResult.Failed)
        val retried = failed.pickerRetried()

        assertEquals(HeadToHeadView.PICKER_LOADING, retried.view(null))
        assertEquals(2, retried.pickerLoadId)
        val late = retried.pickerLoaded(1, DbResult.Ok(HeadToHeadPicker(A, "Kevin", emptyList())))
        assertEquals(retried, late)
        // Retry without a failure asks nothing.
        assertEquals(HeadToHeadState(), HeadToHeadState().pickerRetried())
    }

    @Test
    fun `HH2 HH3 HH4 the figures and the list cover the common matches, newest first with the duel outcome`() {
        val result = checkNotNull(shown().result)

        assertEquals(3, result.eligibleCount)
        assertEquals(3, result.figures.commonCount)
        assertEquals(listOf(2, 1, 0), listOf(result.figures.winsA, result.figures.winsB, result.figures.draws))
        assertEquals(listOf(6_000L + 1_000, 4_000L + 1_000, 2_000L + 1_000), result.matches.map { it.endedAt })
        assertEquals(
            listOf(DuelOutcome.B_WINS, DuelOutcome.A_WINS, DuelOutcome.A_WINS),
            result.matches.map { it.outcome },
        )
        assertEquals(listOf(3L, 2L, 1L), result.matches.map { it.sessionId })
        assertEquals(HeadToHeadView.CONTENT, shown().view(B))
        assertEquals("Kevin", shown().nameA)
        assertEquals("Zoe", shown().nameB)
    }

    @Test
    fun `HH3 equal final ranks are a draw, counted apart`() {
        val draw = HeadToHeadTestData.pair(listOf(HeadToHeadTestData.x01("1", rankA = 1, rankB = 1)))
        assertEquals(1, draw.matches.size)
        val result = draw.result(HeadToHeadSelections(), 0L)

        assertEquals(listOf(DuelOutcome.DRAW), result.matches.map { it.outcome })
        assertEquals(1, result.figures.draws)
        assertEquals(0, result.figures.winsA + result.figures.winsB)
    }

    @Test
    fun `HH6 the mode, the filters and the period recompute the result, never the read`() {
        val state = shown()
        val x01 = state.selected(state.selections.copy(mode = HeadToHeadMode.X01), 5L)
        assertEquals(state.pairLoadId, x01.pairLoadId)
        assertEquals(state.figuresId + 1, x01.figuresId)
        val x01Data = (x01.pair as PairContent.Loaded).data
        val onX01 = x01.computed(x01.figuresId, x01Data.result(x01.selections, 5L))
        assertEquals(2, onX01.result?.figures?.commonCount)
        assertEquals(listOf(ArchivedMode.X01, ArchivedMode.X01), onX01.result?.matches?.map { it.mode })

        // The eligible count does not follow the selection (PS8).
        val filtered = x01.selected(x01.selections.copy(x01Filter = X01Filter(501, CheckoutRule.SINGLE_OUT)), 6L)
        val onFiltered = filtered.computed(
            filtered.figuresId,
            (filtered.pair as PairContent.Loaded).data.result(filtered.selections, 6L),
        )
        assertEquals(3, onFiltered.result?.eligibleCount)
        assertEquals(0, onFiltered.result?.figures?.commonCount)
        assertTrue(onFiltered.isSelectionEmpty)

        // The filters of a mode are kept when the mode changes.
        val back = filtered.selected(filtered.selections.copy(mode = HeadToHeadMode.ALL), 7L)
        assertEquals(X01Filter(501, CheckoutRule.SINGLE_OUT), back.selections.x01Filter)
        assertFalse(back.selections.showsModeFilters)
    }

    @Test
    fun `HH6 the last 10 matches period counts the common matches only`() {
        val state = shown()
        val last = state.selected(state.selections.copy(period = Period.LAST_10_MATCHES), 5L)
        val result = (last.pair as PairContent.Loaded).data.result(last.selections, 5L)

        assertEquals(3, result.figures.commonCount)
        // The same selection changes nothing.
        assertEquals(last, last.selected(last.selections, 9L))
    }

    @Test
    fun `HH9 PS8 a pair without common match shows the empty state, even with a partner picked`() {
        val none = HeadToHeadTestData.pair(emptyList())
        val picked = HeadToHeadState().partnerPicked(1L)
        val loaded = picked.pairLoaded(picked.pairLoadId, DbResult.Ok(none))
        val shown = loaded.computed(loaded.figuresId, none.result(loaded.selections, 1L))

        assertEquals(HeadToHeadView.PAIR_LOADING, loaded.view(B))
        assertEquals(HeadToHeadView.NO_COMMON_MATCH, shown.view(B))
        assertFalse(shown.isSelectionEmpty)
    }

    @Test
    fun `HH2 matches before the latest reset date are not common`() {
        val reset = HeadToHeadTestData.pair(
            listOf(HeadToHeadTestData.x01("14", startedAt = 2_000), HeadToHeadTestData.x01("15", startedAt = 9_000)),
            resetA = 5_000,
        )

        assertEquals(1, reset.result(HeadToHeadSelections(), 0L).eligibleCount)
    }

    @Test
    fun `HH8 the date line is Since always unless one of the two was really reset, then the latest real reset`() {
        fun since(resetA: Long, resetB: Long): ProfileSince? {
            val pair = HeadToHeadTestData.pair(emptyList(), resetA, resetB)
            val state = HeadToHeadState().partnerPicked(1L)
            return state.pairLoaded(state.pairLoadId, DbResult.Ok(pair)).since
        }
        val created = HeadToHeadTestData.CREATED_AT

        assertNull(HeadToHeadState().since)
        assertEquals(ProfileSince.Always, since(created, created))
        assertEquals(ProfileSince.Date(5_000), since(5_000, created))
        assertEquals(ProfileSince.Date(7_000), since(created, 7_000))
        assertEquals(ProfileSince.Date(8_000), since(5_000, 8_000))
        assertEquals(ProfileSince.Date(8_000), since(8_000, 5_000))
        // A reset just after the creation is a real one (ST9).
        assertEquals(ProfileSince.Date(1_500), since(1_500, created))
    }

    @Test
    fun `ST10 a corrupt common match is skipped and counted, the notice data is set, the others stay intact`() {
        val corrupt = HeadToHeadTestData.x01("13").copy(legs = emptyList())
        val ok1 = HeadToHeadTestData.x01("11")
        val ok2 = HeadToHeadTestData.x01("12", 5_000)
        val mixed = HeadToHeadTestData.pair(listOf(ok1, corrupt, ok2))
        val clean = HeadToHeadTestData.pair(listOf(ok1, ok2))
        val picked = HeadToHeadState().partnerPicked(1L)
        val state = picked.pairLoaded(picked.pairLoadId, DbResult.Ok(mixed))
        val noNotice = picked.pairLoaded(picked.pairLoadId, DbResult.Ok(clean))

        assertEquals(1, state.ignoredMatches)
        assertTrue(state.isIgnoredNoticeShown)
        assertFalse(noNotice.isIgnoredNoticeShown)
        assertEquals(clean.matches.map { it.sessionId }, mixed.matches.map { it.sessionId })
        assertEquals(2, mixed.result(HeadToHeadSelections(), 0L).figures.commonCount)
    }

    @Test
    fun `HS7 a failed pair read shows the failure, Retry reads again and a late answer of the old read is ignored`() {
        val picked = HeadToHeadState().partnerPicked(1L)
        val failed = picked.pairLoaded(picked.pairLoadId, DbResult.Failed)
        assertEquals(HeadToHeadView.PAIR_FAILED, failed.view(B))

        val retried = failed.pairRetried(2L)
        assertEquals(HeadToHeadView.PAIR_LOADING, retried.view(B))
        assertEquals(picked.pairLoadId + 1, retried.pairLoadId)
        assertEquals(retried, retried.pairLoaded(picked.pairLoadId, DbResult.Ok(data)))
        assertEquals(picked, picked.pairRetried(2L))
    }

    @Test
    fun `HH9 picking another partner drops the old pair and its late computation, and keeps the selections`() {
        val state = shown().let { it.selected(it.selections.copy(period = Period.LAST_30_DAYS), 3L) }
        val firstId = state.figuresId

        val other = state.partnerPicked(4L)

        assertEquals(HeadToHeadView.PAIR_LOADING, other.view(3L))
        assertEquals(Period.LAST_30_DAYS, other.selections.period)
        assertEquals(other, other.computed(firstId, checkNotNull(state.result)))
    }
}
