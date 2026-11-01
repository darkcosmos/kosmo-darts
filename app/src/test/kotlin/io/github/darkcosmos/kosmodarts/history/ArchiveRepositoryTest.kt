package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import android.database.sqlite.SQLiteFullException
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.SessionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Archive writes: AR2, AR4, AR5, AR6, AR10, PF2, D31 to D33 (rules in `docs/rules/history.md`). */
@RunWith(RobolectricTestRunner::class)
class ArchiveRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = inMemoryHistoryDatabase(context)
    private val repository = ArchiveRepository(db, testNameKey)
    private val history = db.historyDao()

    @After
    fun closeDatabase() = db.close()

    private fun archive(match: ArchivedMatch, repo: ArchiveRepository = repository) =
        repo.archive(match, colorsOf(match), APP_VERSION)

    private fun countOf(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private val tables = listOf("profile", "session", "match_record", "match_player", "leg", "dart")

    @Test
    fun x01MatchIsReadBackFieldByField() {
        val match = x01Match()

        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), archive(match))

        val summary = history.matchSummaries(mode = null, profileId = null).single()
        assertEquals(match.startedAt, summary.startedAt)
        assertEquals(match.endedAt, summary.endedAt)
        assertEquals(match.rulesVersion, summary.rulesVersion)
        assertEquals(APP_VERSION, summary.appVersion)
        assertEquals(ArchivedMode.X01, summary.mode)
        assertEquals("501", summary.variant)
        assertEquals("v=1;start=501;out=double;legs=2;sets=1", summary.settings)
        assertEquals(SettingsText.VERSION, summary.settingsVersion)
        assertEquals(match.settings, SettingsText.decode(summary.settings, summary.mode))
        assertSessionType(summary.sessionId, SessionType.MATCH)
        assertPlayers(match, summary.sessionId)
        assertLegsAndDarts(match, summary.sessionId)
    }

    @Test
    fun cricketMatchIsReadBackFieldByField() {
        val match = cricketMatch()

        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), archive(match))

        val summary = history.matchSummaries(mode = null, profileId = null).single()
        assertEquals(ArchivedMode.CRICKET, summary.mode)
        assertEquals("CUT_THROAT", summary.variant)
        assertEquals("v=1;variant=cut-throat;bull=0;legs=1;sets=1", summary.settings)
        assertEquals(match.settings, SettingsText.decode(summary.settings, summary.mode))
        assertEquals(match.startedAt, summary.startedAt)
        assertEquals(APP_VERSION, summary.appVersion)
        assertPlayers(match, summary.sessionId)
        assertLegsAndDarts(match, summary.sessionId)
    }

    private fun assertSessionType(sessionId: Long, expected: SessionType) {
        val type = db.openHelper.readableDatabase.query("SELECT type FROM session WHERE id = $sessionId")
            .use { it.moveToFirst(); it.getString(0) }
        assertEquals(expected.name, type)
    }

    private fun assertPlayers(match: ArchivedMatch, sessionId: Long) {
        val players = history.playersOf(sessionId)
        assertEquals(match.players.map { it.name }, players.map { it.name })
        assertEquals(match.players.map { it.position }, players.map { it.position })
        assertEquals(colorsOf(match), players.map { it.colorArgb })
        assertEquals(match.players.map { it.rank }, players.map { it.finalRank })
        assertEquals(match.players.map { it.legsWon }, players.map { it.legsWon })
        assertEquals(match.players.map { it.setsWon }, players.map { it.setsWon })
        val profiles = history.profilesByRecency().associateBy { it.id }
        players.forEach { assertEquals(it.name, profiles.getValue(it.profileId).name) }
    }

    private fun assertLegsAndDarts(match: ArchivedMatch, sessionId: Long) {
        val legs = history.legsOf(sessionId)
        assertEquals(match.legs.map { it.ordinal }, legs.map { it.ordinal })
        assertEquals(match.legs.map { it.setNumber }, legs.map { it.setNumber })
        assertEquals(match.legs.map { it.legNumberInSet }, legs.map { it.legNumber })
        assertEquals(match.legs.map { it.starterPosition }, legs.map { it.starterPosition })
        assertEquals(match.legs.map { it.winnerPosition }, legs.map { it.winnerPosition })
        // Every dart value, including entry time, voided, bust, source and position (AR5, AR10, D33).
        assertEquals(match.darts, history.dartsOf(sessionId).map(ArchiveMapper::archivedDart))
    }

    @Test
    fun sameMatchTwiceIsArchivedOnce() {
        val match = x01Match()

        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), archive(match))
        val countsAfterFirst = tables.associateWith(::countOf)
        assertEquals(DbResult.Ok(ArchiveOutcome.ALREADY_ARCHIVED), archive(match))

        assertEquals(countsAfterFirst, tables.associateWith(::countOf))
        assertEquals(1, countOf("session"))
    }

    @Test
    fun archiveCreatesMissingProfilesAtTheMatchStartAndKeepsExistingSpellings() {
        val profiles = db.profileDao()
        profiles.upsertUsed("ANA", testNameKey("ANA"), at = 10L)
        val match = x01Match(names = listOf("Ana", "Bob"))

        archive(match)

        val ana = checkNotNull(profiles.findByNameKey("ana"))
        assertEquals("ANA", ana.name)
        assertEquals(10L, ana.createdAt)
        assertEquals(match.startedAt, ana.lastUsedAt)
        val bob = checkNotNull(profiles.findByNameKey("bob"))
        assertEquals("Bob", bob.name)
        assertEquals(match.startedAt, bob.createdAt)
        assertEquals(match.startedAt, bob.resetAt)
    }

    @Test
    fun failureWhileInsertingDartsRollsEverythingBack() {
        val profiles = db.profileDao()
        profiles.upsertUsed("Ana", testNameKey("Ana"), at = 10L)
        val failingDao = DartInsertFailingArchiveDao(db.archiveDao())
        val failing = ArchiveRepository(db, testNameKey, failingDao)

        val result = archive(x01Match(names = listOf("Ana", "Bob")), failing)

        assertEquals(DbResult.Failed, result)
        val inserted = mapOf("profile" to 2, "session" to 1, "match_record" to 1, "match_player" to 2, "leg" to 2)
        assertEquals(inserted + ("dart" to 0), failingDao.countsBeforeFailure)
        assertEquals(listOf(0, 0, 0, 0, 0), tables.drop(1).map(::countOf))
        assertEquals(1, countOf("profile"))
        assertEquals(10L, checkNotNull(profiles.findByNameKey("ana")).lastUsedAt)
        assertEquals(null, profiles.findByNameKey("bob"))
        // The database is still usable and the same match can then be archived (AR3 retry).
        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), archive(x01Match(names = listOf("Ana", "Bob"))))
    }

    @Test
    fun wrongNumberOfColorsFailsWithoutWriting() {
        val match = x01Match()

        assertEquals(DbResult.Failed, repository.archive(match, listOf(PLAYER_1_ARGB), APP_VERSION))

        assertTrue(tables.all { countOf(it) == 0 })
    }

    /**
     * Test-only archive DAO: the dart insert throws after the session, match, players and legs were inserted; it
     * records the row counts seen inside the transaction at that moment.
     */
    private inner class DartInsertFailingArchiveDao(real: ArchiveDao) : ArchiveDao by real {
        var countsBeforeFailure: Map<String, Int> = emptyMap()

        override fun insertDarts(darts: List<DartEntity>) {
            countsBeforeFailure = tables.associateWith(::countOf)
            throw SQLiteFullException("injected")
        }
    }
}
