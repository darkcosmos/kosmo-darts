package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** History reads (HS2, HS3, HS5) and profile reads (PF1, PF3, D32). */
@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = inMemoryHistoryDatabase(context)
    private val repository = ArchiveRepository(db, testNameKey)
    private val history = db.historyDao()
    private val clock = FakeClock()

    @After
    fun closeDatabase() = db.close()

    private fun archive(match: ArchivedMatch) {
        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), repository.archive(match, colorsOf(match), APP_VERSION))
    }

    /** Four matches, archived from the oldest to the most recent end. */
    private fun archiveFourMatches(): Map<String, ArchivedMatch> {
        val matches = linkedMapOf(
            "x01 Ana Bob" to x01Match(matchId = "m1", names = listOf("Ana", "Bob"), clock = clock),
            "cricket Ana Cleo" to cricketMatch(matchId = "m2", names = listOf("Ana", "Cleo"), clock = clock),
            "x01 Bob Cleo" to x01Match(matchId = "m3", names = listOf("Bob", "Cleo"), clock = clock),
            "cricket Bob" to cricketMatch(matchId = "m4", names = listOf("Bob"), clock = clock),
        )
        matches.values.forEach(::archive)
        return matches
    }

    private fun profileId(name: String): Long = checkNotNull(db.profileDao().findByNameKey(testNameKey(name))).id

    private fun endsOf(mode: ArchivedMode?, profileId: Long?): List<Long> =
        history.matchSummaries(mode, profileId).map { it.endedAt }

    @Test
    fun listIsOrderedFromTheMostRecentEnd() {
        val matches = archiveFourMatches().values.toList()

        assertEquals(matches.map { it.endedAt }.reversed(), endsOf(mode = null, profileId = null))
    }

    @Test
    fun modeFilterKeepsOnlyThatMode() {
        val m = archiveFourMatches()

        assertEquals(
            listOf(m.getValue("x01 Bob Cleo").endedAt, m.getValue("x01 Ana Bob").endedAt),
            endsOf(ArchivedMode.X01, profileId = null),
        )
        assertEquals(
            listOf(m.getValue("cricket Bob").endedAt, m.getValue("cricket Ana Cleo").endedAt),
            endsOf(ArchivedMode.CRICKET, profileId = null),
        )
    }

    @Test
    fun profileFilterKeepsTheMatchesOfThatPlayer() {
        val m = archiveFourMatches()

        assertEquals(
            listOf(m.getValue("cricket Ana Cleo").endedAt, m.getValue("x01 Ana Bob").endedAt),
            endsOf(mode = null, profileId = profileId("Ana")),
        )
    }

    @Test
    fun modeAndProfileFiltersCombine() {
        val m = archiveFourMatches()

        assertEquals(listOf(m.getValue("cricket Bob").endedAt), endsOf(ArchivedMode.CRICKET, profileId("Bob")))
        assertEquals(listOf(m.getValue("x01 Bob Cleo").endedAt), endsOf(ArchivedMode.X01, profileId("Cleo")))
        assertEquals(emptyList<Long>(), endsOf(ArchivedMode.X01, profileId = Long.MAX_VALUE))
    }

    @Test
    fun playersOfTheListedMatchesFollowTheSameFilters() {
        archiveFourMatches()
        val ana = profileId("Ana")

        val sessions = history.matchSummaries(ArchivedMode.X01, ana).map { it.sessionId }.toSet()
        val players = history.matchPlayers(ArchivedMode.X01, ana)

        assertEquals(sessions, players.map { it.sessionId }.toSet())
        assertEquals(listOf("Ana" to 0, "Bob" to 1), players.map { it.name to it.position })
        assertEquals(7, history.matchPlayers(mode = null, profileId = null).size)
    }

    @Test
    fun profilesAreListedMostRecentlyUsedFirst() {
        val profiles = db.profileDao()
        profiles.upsertUsed("Ana", "ana", at = 100L)
        profiles.upsertUsed("Bob", "bob", at = 200L)
        profiles.upsertUsed("Cleo", "cleo", at = 150L)
        profiles.upsertUsed("ana", "ana", at = 300L)

        assertEquals(listOf("Ana", "Bob", "Cleo"), history.profilesByRecency().map { it.name })
    }

    @Test
    fun newProfileIsResetAtItsCreationAndALaterUseChangesOnlyItsLastUse() {
        val profiles = db.profileDao()

        val id = profiles.upsertUsed("Ana", "ana", at = 100L)
        val created = checkNotNull(profiles.findByNameKey("ana"))
        assertEquals(ProfileEntity(id, "Ana", "ana", createdAt = 100L, lastUsedAt = 100L, resetAt = 100L), created)

        assertEquals(id, profiles.upsertUsed(" ANA ", "ana", at = 500L))
        assertEquals(created.copy(lastUsedAt = 500L), profiles.findByNameKey("ana"))
    }

    @Test
    fun anOlderUseNeverMovesTheLastUseBack() {
        val profiles = db.profileDao()
        profiles.upsertUsed("Ana", "ana", at = 500L)

        profiles.upsertUsed("Ana", "ana", at = 100L)

        assertEquals(500L, checkNotNull(profiles.findByNameKey("ana")).lastUsedAt)
    }

    @Test
    fun oneMatchIsReadWithItsLegsAndDarts() {
        val match = x01Match(clock = clock)
        archive(match)
        val sessionId = history.matchSummaries(mode = null, profileId = null).single().sessionId

        assertEquals(match.endedAt, checkNotNull(history.matchSummary(sessionId)).endedAt)
        assertEquals(2, history.playersOf(sessionId).size)
        assertEquals(listOf(0, 1), history.legsOf(sessionId).map { it.ordinal })
        assertEquals(match.darts.map { it.enteredAt }, history.dartsOf(sessionId).map { it.dart.enteredAt })
        assertNull(history.matchSummary(sessionId + 1))
    }

    @Test
    fun aThousandArchivedMatchesAreListed() {
        repeat(MATCH_COUNT) { index ->
            val names = if (index % 2 == 0) listOf("Ana", "Bob") else listOf("Cleo", "Dan")
            archive(x01Match(matchId = "match-$index", names = names, clock = clock))
        }

        val start = System.nanoTime()
        val summaries = history.matchSummaries(mode = null, profileId = null)
        val players = history.matchPlayers(mode = null, profileId = null)
        val filtered = history.matchSummaries(ArchivedMode.X01, profileId("Ana"))
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI

        // Timing is only reported (plan T7 notes), never asserted.
        println("HistoryDaoTest: list queries over $MATCH_COUNT matches took $elapsedMs ms")
        assertEquals(MATCH_COUNT, summaries.size)
        assertEquals(MATCH_COUNT * 2, players.size)
        assertEquals(MATCH_COUNT / 2, filtered.size)
        assertEquals(summaries.map { it.endedAt }.sortedDescending(), summaries.map { it.endedAt })
    }

    private companion object {
        const val MATCH_COUNT = 1_000
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
