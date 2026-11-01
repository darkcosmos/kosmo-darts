package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** HS2, HS3, HS5, HS7, AR9: the History list as the repository reads it from a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = inMemoryHistoryDatabase(context)
    private val archive = ArchiveRepository(db, testNameKey)
    private val repository = RoomHistoryRepository(db)
    private val clock = FakeClock()

    @After
    fun closeDatabase() = db.close()

    private fun archive(match: ArchivedMatch) {
        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), archive.archive(match, colorsOf(match), APP_VERSION))
    }

    private fun read(filters: HistoryFilters = HistoryFilters()): HistoryList =
        (repository.list(filters) as DbResult.Ok).value

    private fun profileId(name: String): Long = read().profiles.single { it.name == name }.id

    private fun archiveFour() {
        archive(x01Match(matchId = "m1", names = listOf("Ana", "Bob"), clock = clock))
        archive(cricketMatch(matchId = "m2", names = listOf("Ana", "Cleo"), clock = clock))
        archive(x01Match(matchId = "m3", names = listOf("Bob", "Cleo"), clock = clock))
        archive(cricketMatch(matchId = "m4", names = listOf("Bob"), clock = clock))
    }

    @Test
    fun `an empty archive gives no match and no profile`() {
        assertEquals(HistoryList(emptyList(), emptyList()), read())
    }

    @Test
    fun `HS2 matches come most recent first with their players in throwing order`() {
        archiveFour()
        val list = read()
        assertEquals(4, list.matches.size)
        assertEquals(list.matches.map { it.endedAt }.sortedDescending(), list.matches.map { it.endedAt })
        val newest = list.matches.first()
        assertEquals(listOf("Bob"), newest.players.map { it.name })
        assertEquals(ArchivedMode.CRICKET, newest.settings.mode)
        val oldest = list.matches.last()
        assertEquals(listOf("Ana", "Bob"), oldest.players.map { it.name })
        assertEquals(listOf(PLAYER_1_ARGB, PLAYER_2_ARGB), oldest.players.map { it.colorArgb })
    }

    @Test
    fun `HS2 the stored winner, legs and sets are read back and the score shows for several legs only`() {
        archiveFour()
        val matches = read().matches
        val x01 = matches.last()
        assertEquals(listOf("Ana"), x01.winners.map { it.name })
        assertEquals(listOf(2, 0), x01.players.map { it.legsWon })
        assertEquals(ArchivedSettings.X01::class, x01.settings::class)
        assertTrue(x01.hasScore)
        val cricket = matches.first { it.settings is ArchivedSettings.Cricket }
        assertTrue(!cricket.hasScore)
    }

    @Test
    fun `HS3 the filters narrow the list by mode, by player and by both`() {
        archiveFour()
        val bob = profileId("Bob")
        assertEquals(2, read(HistoryFilters(mode = ArchivedMode.X01)).matches.size)
        assertEquals(2, read(HistoryFilters(mode = ArchivedMode.CRICKET)).matches.size)
        assertEquals(3, read(HistoryFilters(profileId = bob)).matches.size)
        assertEquals(1, read(HistoryFilters(ArchivedMode.CRICKET, bob)).matches.size)
        assertEquals(0, read(HistoryFilters(ArchivedMode.CRICKET, profileId("Ana") + 1000)).matches.size)
    }

    @Test
    fun `HS3 a player filter still lists every player of the match, not only the filtered one`() {
        archiveFour()
        val match = read(HistoryFilters(profileId = profileId("Ana"))).matches.last()
        assertEquals(listOf("Ana", "Bob"), match.players.map { it.name })
    }

    @Test
    fun `HS3 the filter offers every profile, whatever the filters`() {
        archiveFour()
        val names = read(HistoryFilters(mode = ArchivedMode.CRICKET)).profiles.map { it.name }
        assertEquals(setOf("Ana", "Bob", "Cleo"), names.toSet())
    }

    @Test
    fun `AR9 HS7 a closed database gives Failed, not an exception`() {
        db.close()
        assertEquals(DbResult.Failed, repository.list(HistoryFilters()))
        assertEquals(DbResult.Failed, repository.list(HistoryFilters(ArchivedMode.X01, 1L)))
    }

    @Test
    fun `HS5 a thousand matches are read in one go, the time is only reported`() {
        repeat(MATCH_COUNT) { index ->
            val names = if (index % 2 == 0) listOf("Ana", "Bob") else listOf("Cleo", "Dan")
            archive(x01Match(matchId = "match-$index", names = names, clock = clock))
        }
        val start = System.nanoTime()
        val list = read()
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI
        // Timing is only reported (plan T11 notes), never asserted.
        println("HistoryRepositoryTest: list of $MATCH_COUNT matches with players took $elapsedMs ms")
        assertEquals(MATCH_COUNT, list.matches.size)
        assertEquals(MATCH_COUNT, list.matches.map { it.sessionId }.toSet().size)
        assertTrue(list.matches.all { it.players.size == 2 })
    }

    private companion object {
        const val MATCH_COUNT = 1_000
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
