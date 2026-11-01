package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScroll
import io.github.darkcosmos.kosmodarts.ui.history.HistorySnapshot
import io.github.darkcosmos.kosmodarts.ui.history.HistoryState
import io.github.darkcosmos.kosmodarts.ui.history.HistoryView
import io.github.darkcosmos.kosmodarts.ui.history.okList
import io.github.darkcosmos.kosmodarts.ui.history.profilesOf
import io.github.darkcosmos.kosmodarts.ui.history.x01HistoryMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** HS1, HS4, HS6, HS7: History in the app's navigation, its reads and its return after a process kill. */
class HistoryNavigationTest {
    private val reducer = AppReducer(Random(0))
    private val home = AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN))

    private fun history(state: AppState): HistoryState = (state.screen as Screen.History).history

    private fun opened(): AppState = reducer.reduce(home, AppEvent.HistoryTapped)

    private fun loaded(state: AppState = opened()): AppState =
        reducer.reduce(
            state,
            AppEvent.HistoryLoaded(history(state).loadId, okList(listOf(x01HistoryMatch(1, "Ana")), profilesOf("Ana"))),
        )

    @Test
    fun `HS1 the History button opens History over the same home, and back returns to it`() {
        val state = opened()
        assertEquals(Screen.History(home.screen as Screen.Home), state.screen)
        assertEquals(home.screen, reducer.reduce(state, AppEvent.Back).screen)
    }

    @Test
    fun `HS1 opening History reads once, and leaving it reads nothing`() {
        val state = opened()
        assertEquals(
            listOf(PersistenceEffect.LoadHistory(HistoryFilters(), 1)),
            persistenceEffects(home, state),
        )
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(state, reducer.reduce(state, AppEvent.Back)))
    }

    @Test
    fun `HS1 opening and leaving History neither writes nor deletes the saved game`() {
        val state = opened()
        assertEquals(SaveFixtures.MID_TURN, savedGameOf(state))
        assertTrue(persistenceEffects(home, state).none { it is PersistenceEffect.SaveGame })
        assertTrue(persistenceEffects(state, reducer.reduce(state, AppEvent.Back)).none { it is PersistenceEffect.DeleteGame })
    }

    @Test
    fun `HS1 the button does nothing while the new game dialog is open`() {
        val dialog = AppState(screen = Screen.Home(SaveFixtures.MID_TURN, newGameDialogMode = GameMode.X01))
        assertEquals(dialog, reducer.reduce(dialog, AppEvent.HistoryTapped))
    }

    @Test
    fun `HS7 the answer to the current read shows the rows, an old answer is ignored`() {
        val state = loaded()
        assertEquals(HistoryView.ROWS, history(state).view)
        val stale = reducer.reduce(state, AppEvent.HistoryLoaded(99, DbResult.Failed))
        assertSame(state, stale)
    }

    @Test
    fun `HS3 a filter asks for a read with the new filters`() {
        val byMode = reducer.reduce(loaded(), AppEvent.HistoryModeSelected(ArchivedMode.CRICKET))
        assertEquals(
            listOf(PersistenceEffect.LoadHistory(HistoryFilters(ArchivedMode.CRICKET, null), 2)),
            persistenceEffects(loaded(), byMode),
        )
        val byProfile = reducer.reduce(byMode, AppEvent.HistoryProfileSelected(1))
        assertEquals(HistoryFilters(ArchivedMode.CRICKET, 1), history(byProfile).filters)
        assertEquals(3, history(byProfile).loadId)
    }

    @Test
    fun `HS4 show all resets the filters and reads again`() {
        val filtered = reducer.reduce(loaded(), AppEvent.HistoryModeSelected(ArchivedMode.X01))
        val all = reducer.reduce(filtered, AppEvent.HistoryShowAllTapped)
        assertEquals(HistoryFilters(), history(all).filters)
        assertEquals(
            listOf(PersistenceEffect.LoadHistory(HistoryFilters(), 3)),
            persistenceEffects(filtered, all),
        )
    }

    @Test
    fun `HS7 retry reads again after a failure, and does nothing otherwise`() {
        val failed = reducer.reduce(opened(), AppEvent.HistoryLoaded(1, DbResult.Failed))
        assertEquals(HistoryView.FAILED, history(failed).view)
        val retried = reducer.reduce(failed, AppEvent.HistoryRetryTapped)
        assertEquals(
            listOf(PersistenceEffect.LoadHistory(HistoryFilters(), 2)),
            persistenceEffects(failed, retried),
        )
        val ok = loaded()
        assertSame(ok, reducer.reduce(ok, AppEvent.HistoryRetryTapped))
    }

    @Test
    fun `HS6 the scroll position is kept in the state without a read`() {
        val before = loaded()
        val after = reducer.reduce(before, AppEvent.HistoryScrolled(4, 12))
        assertEquals(HistoryScroll(4, 12), history(after).scroll)
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(before, after))
    }

    @Test
    fun `a tap on a match is ignored until the detail screen exists`() {
        val state = loaded()
        assertSame(state, reducer.reduce(state, AppEvent.HistoryMatchTapped(1)))
    }

    @Test
    fun `HS6 a snapshot brings History back over the saved game's home, and reads again`() {
        val snapshot = HistorySnapshot(HistoryFilters(ArchivedMode.X01, 3), HistoryScroll(7, 20))
        val state = initialState(LoadResult(GameLoad.Valid(SaveFixtures.MID_TURN), null), wasOnGameScreen = false, snapshot)
        val screen = state.screen as Screen.History
        assertEquals(Screen.Home(resumableGame = SaveFixtures.MID_TURN), screen.home)
        assertEquals(snapshot, screen.history.snapshot())
        assertEquals(listOf(PersistenceEffect.LoadHistory(snapshot.filters, 1)), startupEffects(state))
    }

    @Test
    fun `HS6 a snapshot over an empty install shows History over a bare home`() {
        val snapshot = HistorySnapshot(HistoryFilters(), HistoryScroll())
        val state = initialState(LoadResult(GameLoad.Absent, null), wasOnGameScreen = false, snapshot)
        assertEquals(Screen.History(Screen.Home(), HistoryState.restored(snapshot)), state.screen)
    }

    @Test
    fun `HS6 without a snapshot, or on a game screen, there is no History and no startup read`() {
        val fresh = initialState(LoadResult(GameLoad.Absent, null), wasOnGameScreen = false)
        assertEquals(Screen.Home(), fresh.screen)
        assertEquals(emptyList<PersistenceEffect>(), startupEffects(fresh))
        val snapshot = HistorySnapshot(HistoryFilters(), HistoryScroll())
        val onGame = initialState(LoadResult(GameLoad.Valid(SaveFixtures.MID_TURN), null), wasOnGameScreen = true, snapshot)
        assertTrue(onGame.screen is Screen.Game)
        assertNull((onGame.screen as? Screen.History))
    }
}
