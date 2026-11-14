package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.FakeProfileStatsRepository
import io.github.darkcosmos.kosmodarts.history.HeadToHeadPicker
import io.github.darkcosmos.kosmodarts.history.PickerProfile
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.settings.inMemoryAudioSettings
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadMode
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadState
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadTestData
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadView
import io.github.darkcosmos.kosmodarts.ui.headtohead.PairContent
import io.github.darkcosmos.kosmodarts.ui.headtohead.result
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** HH1 to HH9, HS7, HS9, ST7, ST10: the head-to-head events, effects and executors of the app state. */
class HeadToHeadReducerTest {
    private val reducer = AppReducer(Random(0))
    private val a = HeadToHeadTestData.A
    private val b = HeadToHeadTestData.B
    private val players = Screen.Players(Screen.Home())
    private val profile = players.open(Screen.Profile(players, a, ProfileState(now = 1L)))
    private val data = HeadToHeadTestData.pair(
        listOf(HeadToHeadTestData.x01("1"), HeadToHeadTestData.cricket("2", startedAt = 4_000)),
    )
    private val picker = HeadToHeadPicker(a, "Kevin", listOf(PickerProfile(b, "Zoe", 2), PickerProfile(3, "Abe", 0)))

    private val opened = AppState(
        screen = profile.open(Screen.HeadToHead(profile, a, null, HeadToHeadState(now = 1L))),
    )

    private fun top(state: AppState) = state.screen.headToHeadsOfStack().first()

    private fun send(state: AppState, event: AppEvent) = reducer.reduce(state, event)

    private fun pickerShown(): AppState = send(opened, HeadToHeadEvent.PickerLoaded(a, 1, DbResult.Ok(picker)))

    private fun pairLoaded(state: AppState): AppState =
        send(state, HeadToHeadEvent.PairLoaded(a, top(state).state.pairLoadId, DbResult.Ok(data)))

    private fun shown(): AppState {
        val loaded = pairLoaded(send(pickerShown(), HeadToHeadEvent.PartnerPicked(b)))
        val state = top(loaded).state
        val result = data.result(state.selections, state.now)
        return send(loaded, HeadToHeadEvent.ResultComputed(a, state.figuresId, result))
    }

    @Test
    fun `HH1 HS7 opening reads the picker once, and the answer shows the profiles`() {
        val effects = persistenceEffects(AppState(screen = profile), opened)

        assertEquals(listOf<PersistenceEffect>(PersistenceEffect.LoadHeadToHeadPicker(a, 1)), effects)
        assertEquals(HeadToHeadView.PICKER_LOADING, top(opened).state.view(top(opened).b))
        val shown = pickerShown()
        assertEquals(HeadToHeadView.PICKER, top(shown).state.view(null))
        assertTrue(persistenceEffects(opened, shown).isEmpty())
    }

    @Test
    fun `HH1 HH2 picking a profile reads the pair once, then computes the result, none of it a second time`() {
        val picked = send(pickerShown(), HeadToHeadEvent.PartnerPicked(b))

        assertEquals(b, top(picked).b)
        assertEquals(
            listOf<PersistenceEffect>(PersistenceEffect.LoadHeadToHeadPair(a, b, 1)),
            persistenceEffects(pickerShown(), picked),
        )
        val loaded = pairLoaded(picked)
        val computing = persistenceEffects(picked, loaded).single() as PersistenceEffect.ComputeHeadToHead
        assertEquals(data, computing.data)
        assertEquals(top(loaded).state.figuresId, computing.figuresId)
        assertTrue(persistenceEffects(loaded, loaded).isEmpty())
        // Picking the shown profile itself does nothing.
        val shown = pickerShown()
        assertSame(shown, send(shown, HeadToHeadEvent.PartnerPicked(a)))
    }

    @Test
    fun `HH9 PS8 a partner with no common match opens the empty state`() {
        val none = HeadToHeadTestData.pair(emptyList())
        val picked = send(pickerShown(), HeadToHeadEvent.PartnerPicked(3))
        val loaded = send(picked, HeadToHeadEvent.PairLoaded(a, 1, DbResult.Ok(none)))
        val state = top(loaded).state
        val shown = send(loaded, HeadToHeadEvent.ResultComputed(a, state.figuresId, none.result(state.selections, 1L)))

        assertEquals(HeadToHeadView.NO_COMMON_MATCH, top(shown).state.view(3))
    }

    @Test
    fun `HH6 a filter or period change computes again with the new clock, without reading the pair again`() {
        val before = shown()
        val after = send(before, HeadToHeadEvent.ModeSelected(HeadToHeadMode.X01))
        val effects = persistenceEffects(before, after)

        val compute = effects.single() as PersistenceEffect.ComputeHeadToHead
        assertEquals(HeadToHeadMode.X01, compute.selections.mode)
        assertEquals(1_000L, compute.now)
        val same = send(after, HeadToHeadEvent.ModeSelected(HeadToHeadMode.X01))
        assertTrue(persistenceEffects(after, same).isEmpty())
        val period = send(after, HeadToHeadEvent.PeriodSelected(Period.LAST_10_MATCHES))
        assertTrue(persistenceEffects(after, period).single() is PersistenceEffect.ComputeHeadToHead)
    }

    @Test
    fun `HH7 HS9 a common match opens its detail, and Back returns to the pair with its selections and no new read`() {
        val filtered = send(shown(), HeadToHeadEvent.ModeSelected(HeadToHeadMode.CRICKET))
        val withDetail = send(filtered, HeadToHeadEvent.MatchTapped(7))

        val detail = withDetail.screen as Screen.MatchDetail
        assertEquals(7L, detail.detail.sessionId)
        assertEquals(
            listOf<PersistenceEffect>(PersistenceEffect.LoadMatch(7, detail.detail.loadId)),
            persistenceEffects(filtered, withDetail),
        )
        // An answer of the pair still reaches it under the detail.
        val under = send(withDetail, HeadToHeadEvent.PeriodSelected(Period.LAST_30_DAYS))
        assertSame(withDetail, under)

        val back = send(withDetail, AppEvent.Back)
        assertEquals(filtered, back)
        assertTrue(persistenceEffects(withDetail, back).isEmpty())
    }

    @Test
    fun `HH9 the answers reach the head-to-head under a detail`() {
        val picked = send(pickerShown(), HeadToHeadEvent.PartnerPicked(b))
        val withDetail = send(picked, HeadToHeadEvent.MatchTapped(7))

        val loaded = send(withDetail, HeadToHeadEvent.PairLoaded(a, 1, DbResult.Ok(data)))

        assertTrue(withDetail.screen is Screen.MatchDetail && loaded.screen is Screen.MatchDetail)
        val hidden = loaded.screen.headToHeadsOfStack().single().state
        assertTrue(hidden.pair is PairContent.Loaded)
        assertTrue(persistenceEffects(withDetail, loaded).single() is PersistenceEffect.ComputeHeadToHead)
    }

    @Test
    fun `HH9 the picker, the pair, the filters and the period survive a rotation of the ViewModel state`() {
        val state = send(shown(), HeadToHeadEvent.PeriodSelected(Period.LAST_10_MATCHES))
        val viewModel = KosmoDartsViewModel(Random(0), state, inMemoryAudioSettings())

        // A rotation recreates the activity and composes again over the same ViewModel, whose state is not touched.
        val after = viewModel.state.value.screen as Screen.HeadToHead
        assertSame(state.screen, after)
        assertEquals(b, after.b)
        assertEquals(Period.LAST_10_MATCHES, after.state.selections.period)
        assertTrue(after.state.pair is PairContent.Loaded)
        assertNull(viewModel.historySnapshot)

        // The picker open keeps its list too.
        val picking = KosmoDartsViewModel(Random(0), pickerShown(), inMemoryAudioSettings())
        assertEquals(HeadToHeadView.PICKER, (picking.state.value.screen as Screen.HeadToHead).state.view(null))
    }

    @Test
    fun `HH1 Change opens the picker again without a new read, and the pair picked later reads again`() {
        val changed = send(shown(), HeadToHeadEvent.ChangePartnerTapped)

        assertNull(top(changed).b)
        assertTrue(persistenceEffects(shown(), changed).isEmpty())
        val picked = send(changed, HeadToHeadEvent.PartnerPicked(3))
        assertEquals(
            listOf<PersistenceEffect>(PersistenceEffect.LoadHeadToHeadPair(a, 3, 2)),
            persistenceEffects(changed, picked),
        )
    }

    @Test
    fun `HS7 a failed picker or pair read is read again by Retry`() {
        val failedPicker = send(opened, HeadToHeadEvent.PickerLoaded(a, 1, DbResult.Failed))
        val retriedPicker = send(failedPicker, HeadToHeadEvent.RetryTapped)
        assertEquals(
            listOf<PersistenceEffect>(PersistenceEffect.LoadHeadToHeadPicker(a, 2)),
            persistenceEffects(failedPicker, retriedPicker),
        )

        val picked = send(pickerShown(), HeadToHeadEvent.PartnerPicked(b))
        val failedPair = send(picked, HeadToHeadEvent.PairLoaded(a, 1, DbResult.Failed))
        assertEquals(HeadToHeadView.PAIR_FAILED, top(failedPair).state.view(b))
        val retriedPair = send(failedPair, HeadToHeadEvent.RetryTapped)
        assertEquals(
            listOf<PersistenceEffect>(PersistenceEffect.LoadHeadToHeadPair(a, b, 2)),
            persistenceEffects(failedPair, retriedPair),
        )
        // The late answer of the first read is ignored.
        assertEquals(retriedPair, send(retriedPair, HeadToHeadEvent.PairLoaded(a, 1, DbResult.Ok(data))))
    }

    @Test
    fun `HH9 events of another screen than the head-to-head on top change nothing`() {
        val home = AppState(screen = profile)

        assertSame(home, send(home, HeadToHeadEvent.PartnerPicked(b)))
        assertSame(home, send(home, HeadToHeadEvent.RetryTapped))
    }

    @Test
    fun `HS9 Back returns to the Profile, and the effects run on the read executor with the repository`() {
        assertEquals(profile, send(opened, AppEvent.Back).screen)

        val reads = ArrayDeque<Runnable>()
        val repository = FakeProfileStatsRepository(
            pickers = mapOf(a to picker),
            pairs = mapOf((a to b) to data),
        )
        val persistence = Persistence(
            MemoryStore(),
            MemoryStore(),
            Runnable::run,
            readExecutor = { reads += it },
            profileStats = repository,
        )
        val events = mutableListOf<AppEvent>()
        val state = top(shown()).state
        persistence.applyEffects(
            listOf(
                PersistenceEffect.LoadHeadToHeadPicker(a, 1),
                PersistenceEffect.LoadHeadToHeadPair(a, b, 1),
                PersistenceEffect.LoadHeadToHeadPair(a, 3, 2),
                PersistenceEffect.ComputeHeadToHead(a, 4, data, state.selections, 9L),
            ),
        ) { events += it }
        // Nothing runs on the caller's thread: everything waits for the read executor (ST7).
        assertTrue(events.isEmpty())
        assertEquals(4, reads.size)
        while (reads.isNotEmpty()) reads.removeFirst().run()

        assertEquals(
            listOf<AppEvent>(
                HeadToHeadEvent.PickerLoaded(a, 1, DbResult.Ok(picker)),
                HeadToHeadEvent.PairLoaded(a, 1, DbResult.Ok(data)),
                HeadToHeadEvent.PairLoaded(a, 2, DbResult.Failed),
                HeadToHeadEvent.ResultComputed(a, 4, data.result(state.selections, 9L)),
            ),
            events,
        )
    }

    @Test
    fun `HS7 without a repository the reads answer Failed`() {
        val reads = ArrayDeque<Runnable>()
        val persistence = Persistence(MemoryStore(), MemoryStore(), Runnable::run, readExecutor = { reads += it })
        val events = mutableListOf<AppEvent>()
        persistence.applyEffects(
            listOf(PersistenceEffect.LoadHeadToHeadPicker(a, 1), PersistenceEffect.LoadHeadToHeadPair(a, b, 1)),
        ) { events += it }
        while (reads.isNotEmpty()) reads.removeFirst().run()

        assertEquals(
            listOf<AppEvent>(
                HeadToHeadEvent.PickerLoaded(a, 1, DbResult.Failed),
                HeadToHeadEvent.PairLoaded(a, 1, DbResult.Failed),
            ),
            events,
        )
    }
}
