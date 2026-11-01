package io.github.darkcosmos.kosmodarts.ui.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** HS3, HS4, HS6, HS7: the pure state of the History screen. */
class HistoryStateTest {
    private val one = x01HistoryMatch(1, "Ana", "Ben")
    private val loaded = HistoryState().loaded(1, okList(listOf(one), profilesOf("Ana", "Ben")))

    @Test
    fun `starts loading, then shows rows, a failure, the empty archive or no match`() {
        assertEquals(HistoryView.LOADING, HistoryState().view)
        assertEquals(HistoryView.ROWS, loaded.view)
        assertEquals(HistoryView.FAILED, HistoryState().loaded(1, DbResult.Failed).view)
        assertEquals(HistoryView.EMPTY_ARCHIVE, HistoryState().loaded(1, okList(emptyList())).view)
        val noMatch = loaded.withMode(ArchivedMode.CRICKET).loaded(2, okList(emptyList(), profilesOf("Ana")))
        assertEquals(HistoryView.NO_MATCH, noMatch.view)
    }

    @Test
    fun `a filter change asks for a new read and goes back to the top`() {
        val scrolled = loaded.scrolled(5, 30)
        val byMode = scrolled.withMode(ArchivedMode.X01)
        assertEquals(2, byMode.loadId)
        assertEquals(HistoryView.LOADING, byMode.view)
        assertEquals(HistoryScroll(), byMode.scroll)
        val byProfile = byMode.withProfile(2)
        assertEquals(HistoryFilters(ArchivedMode.X01, 2), byProfile.filters)
        assertEquals(3, byProfile.loadId)
    }

    @Test
    fun `choosing the filter already chosen changes nothing`() {
        assertSame(loaded, loaded.withMode(null))
        assertSame(loaded, loaded.withProfile(null))
    }

    @Test
    fun `an answer to an older read is ignored`() {
        val waiting = loaded.withMode(ArchivedMode.X01)
        assertSame(waiting, waiting.loaded(1, okList(emptyList())))
        assertSame(waiting, waiting.loaded(1, DbResult.Failed))
    }

    @Test
    fun `show all resets both filters (HS4)`() {
        val filtered = loaded.withMode(ArchivedMode.X01).withProfile(1)
        val all = filtered.showAll()
        assertEquals(HistoryFilters(), all.filters)
        assertEquals(HistoryView.LOADING, all.view)
        assertEquals(filtered.loadId + 1, all.loadId)
    }

    @Test
    fun `retry reads again with the same filters and scroll (HS7)`() {
        val failed = loaded.withProfile(1).scrolled(3, 4).loaded(2, DbResult.Failed)
        assertEquals(HistoryView.FAILED, failed.view)
        val retried = failed.retry()
        assertEquals(HistoryView.LOADING, retried.view)
        assertEquals(failed.filters, retried.filters)
        assertEquals(HistoryScroll(3, 4), retried.scroll)
        assertEquals(failed.loadId + 1, retried.loadId)
    }

    @Test
    fun `a failure keeps the profiles already known so the filter stays usable`() {
        val failed = loaded.withMode(ArchivedMode.X01).loaded(2, DbResult.Failed)
        assertEquals(profilesOf("Ana", "Ben"), failed.profiles)
    }

    @Test
    fun `an unchanged scroll position is the same state`() {
        val scrolled = loaded.scrolled(2, 7)
        assertSame(scrolled, scrolled.scrolled(2, 7))
    }

    @Test
    fun `the snapshot round-trips filters and scroll`() {
        val state = loaded.withMode(ArchivedMode.CRICKET).withProfile(42).scrolled(9, 120)
        val decoded = HistorySnapshot.decode(state.snapshot().encode())
        assertEquals(state.snapshot(), decoded)
        val restored = HistoryState.restored(decoded!!)
        assertEquals(state.filters, restored.filters)
        assertEquals(state.scroll, restored.scroll)
        assertEquals(HistoryView.LOADING, restored.view)
    }

    @Test
    fun `the snapshot of an open match detail round-trips its session`() {
        val filters = HistoryFilters(ArchivedMode.CRICKET, 4)
        val snapshot = HistorySnapshot(filters, HistoryScroll(3, 8), detailSessionId = 17)
        assertEquals(snapshot, HistorySnapshot.decode(snapshot.encode()))
        assertNull(HistorySnapshot.decode(snapshot.copy(detailSessionId = null).encode())?.detailSessionId)
    }

    @Test
    fun `the default snapshot round-trips`() {
        val snapshot = HistoryState().snapshot()
        assertEquals(snapshot, HistorySnapshot.decode(snapshot.encode()))
    }

    @Test
    fun `an invalid or missing snapshot is refused`() {
        listOf(
            null,
            "",
            "-;-;0",
            "-;-;0;0",
            "-;-;0;0;0",
            "-;-;0;0;-3",
            "-;-;0;0;abc",
            "-;-;0;0;-;-",
            "DARTS;-;0;0;-",
            "X01;abc;0;0;-",
            "-;-;x;0;-",
            "-;-;0;y;-",
            "-;-;-1;0;-",
            "-;-;0;-5;-",
        ).forEach { assertNull("should refuse: $it", HistorySnapshot.decode(it)) }
    }
}
