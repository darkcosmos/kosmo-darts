package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.stats.X01Filter
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData
import io.github.darkcosmos.kosmodarts.history.ProfileStatsRepository
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.testing.TEST_STAMP
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileContent
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileQuery
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileSection
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileView
import io.github.darkcosmos.kosmodarts.ui.profile.figures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.random.Random

/** PS3, PS6, HS7, HS9, ST7: the Profile events, effects and executors of the app state. */
class ProfileReducerTest {
    private val reducer = AppReducer(Random(0))
    private val players = Screen.Players(Screen.Home())
    private val opened = AppState(screen = players.open(Screen.Profile(players, ProfileTestData.PROFILE_ID)))
    private val data = ProfileTestData.data(listOf(ProfileTestData.x01Raw("a"), ProfileTestData.cricketRaw("b")))

    private fun profile(state: AppState) = state.screen.profilesOfStack().single()

    private fun send(state: AppState, event: AppEvent) = reducer.reduce(state, event)

    private fun loaded(state: AppState): AppState =
        send(state, ProfileEvent.Loaded(ProfileTestData.PROFILE_ID, profile(state).profile.loadId, DbResult.Ok(data)))

    private fun shown(state: AppState): AppState {
        val after = loaded(state)
        val id = profile(after).profile.figuresId
        return send(after, ProfileEvent.FiguresComputed(1, id, data.figures(profile(after).profile.query)))
    }

    @Test
    fun `HS7 opening the Profile reads it once, and the open state is X01 since always with All filters`() {
        val effects = persistenceEffects(AppState(screen = players), opened)
        val reads = listOf(PersistenceEffect.LoadProfile(1, 1), PersistenceEffect.LoadProfileRoster(1, 1))
        assertEquals(reads, effects)
        assertEquals(ProfileSection.X01, profile(opened).profile.section)
        assertEquals(ProfileView.LOADING, profile(opened).profile.view)
    }

    @Test
    fun `ST7 the answer of the read asks for the figures, then the answer fills them`() {
        val after = loaded(opened)
        val effects = persistenceEffects(opened, after)
        assertEquals(1, effects.size)
        val compute = effects.single() as PersistenceEffect.ComputeProfileFigures
        assertEquals(1, compute.figuresId)
        assertSame(data, compute.data)

        val done = shown(opened)
        assertEquals(ProfileView.CONTENT, profile(done).profile.view)
        assertTrue(persistenceEffects(after, done).isEmpty())
    }

    @Test
    fun `HS7 a failed read shows the failure and Retry reads again with a new number`() {
        val failed = send(opened, ProfileEvent.Loaded(1, 1, DbResult.Failed))
        assertEquals(ProfileView.FAILED, profile(failed).profile.view)
        val retried = send(failed, ProfileEvent.RetryTapped)
        val reload = PersistenceEffect.LoadProfile(1, 2)
        val reloads = listOf(reload, PersistenceEffect.LoadProfileRoster(1, 2))
        assertEquals(reloads, persistenceEffects(failed, retried))
        // The answer of the first read, arriving late, is ignored.
        assertSame(retried, send(retried, ProfileEvent.Loaded(1, 1, DbResult.Ok(data))))
    }

    @Test
    fun `PS3 a filter or period change computes again and never reads again`() {
        val start = shown(opened)
        listOf(
            ProfileEvent.X01FilterSelected(X01Filter(startScore = 501)),
            ProfileEvent.CricketFilterSelected(CricketFilter(CricketVariant.CUT_THROAT)),
            ProfileEvent.X01PeriodSelected(Period.LAST_30_DAYS),
            ProfileEvent.CricketPeriodSelected(Period.LAST_10_MATCHES),
        ).forEach { event ->
            val after = send(start, event)
            val effects = persistenceEffects(start, after)
            assertEquals(event.toString(), 1, effects.size)
            assertTrue(effects.single() is PersistenceEffect.ComputeProfileFigures)
        }
        val section = send(start, ProfileEvent.SectionSelected(ProfileSection.HEATMAP))
        assertEquals(ProfileSection.HEATMAP, profile(section).profile.section)
        assertTrue(persistenceEffects(start, section).isEmpty())
    }

    @Test
    fun `PS3 the clock of the event feeds Last 30 days`() {
        val after = send(shown(opened), ProfileEvent.X01PeriodSelected(Period.LAST_30_DAYS))
        assertEquals(TEST_STAMP.at, profile(after).profile.now)
    }

    @Test
    fun `HS9 the selections survive a pushed detail and Back, which reads nothing`() {
        val selected = send(shown(opened), ProfileEvent.X01FilterSelected(X01Filter(startScore = 501)))
        val detail = Screen.MatchDetail(selected.screen, MatchDetailState(9))
        val withDetail = AppState(screen = selected.screen.open(detail))
        assertTrue(persistenceEffects(selected, withDetail).none { it is PersistenceEffect.LoadProfile })
        val back = send(withDetail, AppEvent.Back)
        assertSame(selected.screen, back.screen)
        assertEquals(X01Filter(startScore = 501), profile(back).profile.x01Filter)
        assertTrue(persistenceEffects(withDetail, back).none { it is PersistenceEffect.LoadProfile })
    }

    @Test
    fun `HS9 an answer that arrives under a detail reaches the Profile`() {
        val withDetail = AppState(screen = opened.screen.open(Screen.MatchDetail(opened.screen, MatchDetailState(9))))
        val after = loaded(withDetail)
        assertTrue(after.screen is Screen.MatchDetail)
        assertTrue(profile(after).profile.content is ProfileContent.Loaded)
        assertTrue(persistenceEffects(withDetail, after).single() is PersistenceEffect.ComputeProfileFigures)
        // User events never reach a Profile that is not on top.
        assertSame(after, send(after, ProfileEvent.SectionSelected(ProfileSection.CRICKET)))
    }

    @Test
    fun `an answer for another profile or a screen without Profile changes nothing`() {
        assertSame(opened, send(opened, ProfileEvent.Loaded(2, 1, DbResult.Ok(data))))
        val home = AppState(screen = Screen.Home())
        assertSame(home, send(home, ProfileEvent.RetryTapped))
        assertNull(home.screen.profilesOfStack().firstOrNull())
    }

    @Test
    fun `ST7 the read and the figures run on the read executor, never inline`() {
        val reads = ArrayDeque<Runnable>()
        val saves = ArrayDeque<Runnable>()
        val repository = FakeProfileStatsRepository(DbResult.Ok(data))
        val persistence = Persistence(
            MemoryStore(),
            MemoryStore(),
            Executor { saves += it },
            readExecutor = Executor { reads += it },
            profileStats = repository,
        )
        val events = mutableListOf<AppEvent>()
        persistence.applyEffects(listOf(PersistenceEffect.LoadProfile(1, 4))) { events += it }
        assertEquals(0, repository.loads.size)
        assertEquals(1, reads.size)
        reads.removeFirst().run()
        assertEquals(listOf(1L), repository.loads)
        assertEquals(listOf<AppEvent>(ProfileEvent.Loaded(1, 4, DbResult.Ok(data))), events)

        val query = ProfileQuery(now = 1)
        persistence.applyEffects(listOf(PersistenceEffect.ComputeProfileFigures(1, 5, data, query))) { events += it }
        assertEquals(1, reads.size)
        assertEquals(1, events.size)
        reads.removeFirst().run()
        assertEquals(ProfileEvent.FiguresComputed(1, 5, data.figures(query)), events.last())
        assertTrue(saves.isEmpty())
    }

    @Test
    fun `HS7 without a repository the read answers Failed`() {
        val reads = ArrayDeque<Runnable>()
        val persistence = Persistence(MemoryStore(), MemoryStore(), Runnable::run, readExecutor = { reads += it })
        val events = mutableListOf<AppEvent>()
        persistence.applyEffects(listOf(PersistenceEffect.LoadProfile(1, 1))) { events += it }
        reads.removeFirst().run()
        assertEquals(listOf<AppEvent>(ProfileEvent.Loaded(1, 1, DbResult.Failed)), events)
    }
}

private class FakeProfileStatsRepository(private val result: DbResult<ProfileStatsData>) : ProfileStatsRepository {
    val loads = mutableListOf<Long>()

    override fun load(profileId: Long): DbResult<ProfileStatsData> {
        loads += profileId
        return result
    }
}
