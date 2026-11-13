package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Players screen reads and writes (PM2 to PM5, PM10, PF1, AR9). */
@RunWith(RobolectricTestRunner::class)
class PlayersRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: HistoryDatabase
    private lateinit var repository: RoomPlayersRepository
    private lateinit var archive: ArchiveRepository
    private var matchNumber = 0

    @Before
    fun open() {
        db = inMemoryHistoryDatabase(context)
        repository = RoomPlayersRepository(db)
        archive = ArchiveRepository(db, testNameKey)
    }

    @After
    fun close() = db.close()

    private fun play(vararg names: String): Long {
        matchNumber += 1
        val id = "00000000-0000-4000-8000-%012d".format(matchNumber)
        val match = x01Match(matchId = id, names = names.toList())
        archive.archive(match, colorsOf(match), APP_VERSION)
        return match.endedAt
    }

    private fun list(): List<PlayerSummary> = (repository.list() as DbResult.Ok).value

    private fun idOf(name: String): Long = list().first { it.name == name }.id

    private fun counts(): List<Int> = listOf(
        "session", "match_record", "match_player", "leg", "dart", "profile",
    ).map { table ->
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    }

    private fun participants(session: Long): List<Long> = db.openHelper.readableDatabase
        .query("SELECT profile_id FROM match_player WHERE session_id = $session ORDER BY position").use {
            generateSequence { if (it.moveToNext()) it.getLong(0) else null }.toList()
        }

    @Test
    fun `PM2 lists by last use with match counts and last end date, a profile with no match last`() {
        val firstEnd = play("Ana", "Bob")
        val secondEnd = play("Cleo", "Bob")
        db.profileDao().upsertUsed("Zed", "zed", at = 1L)

        val rows = list()

        // Bob and Cleo share the latest use: the newest profile comes first.
        assertEquals(listOf("Cleo", "Bob", "Ana", "Zed"), rows.map(PlayerSummary::name))
        assertEquals(listOf(1, 2, 1, 0), rows.map(PlayerSummary::matchCount))
        assertEquals(listOf(secondEnd, secondEnd, firstEnd, null), rows.map(PlayerSummary::lastEndedAt))
    }

    @Test
    fun `PM2 PM7 the anonymous profile is never listed`() {
        db.profileDao().upsertUsed("Deleted player", "", at = 5L)
        db.profileDao().upsertUsed("Ana", "ana", at = 1L)

        assertEquals(listOf("Ana"), list().map(PlayerSummary::name))
    }

    @Test
    fun `PM3 rename gives the profile its new spelling and History shows it`() {
        val end = play("Ana", "Bob")
        val id = idOf("Ana")

        val result = repository.rename(id, "  Anaïs ")

        assertEquals(DbResult.Ok(RenameResult.Renamed("Anaïs", "anaïs")), result)
        assertEquals("Anaïs", db.playersDao().find(id)!!.name)
        val shown = db.historyDao().playersOf(db.historyDao().matchSummaries(null, null).first().sessionId)
        assertTrue(shown.any { it.name == "Anaïs" } && shown.none { it.name == "Ana" })
        assertEquals(end, list().first { it.id == id }.lastEndedAt)
    }

    @Test
    fun `PM3 a case-only rename changes the spelling`() {
        play("Ana", "Bob")
        val id = idOf("Ana")

        assertEquals(DbResult.Ok(RenameResult.Renamed("ANA", "ana")), repository.rename(id, "ANA"))
        assertEquals("ANA", db.playersDao().find(id)!!.name)
    }

    @Test
    fun `PM3 a name key of another profile returns its id and changes nothing`() {
        play("Ana", "Bob")
        val before = counts()
        val ana = idOf("Ana")

        assertEquals(DbResult.Ok(RenameResult.Clash(idOf("Bob"))), repository.rename(ana, " BOB "))

        assertEquals("Ana", db.playersDao().find(ana)!!.name)
        assertEquals("bob", db.playersDao().find(idOf("Bob"))!!.nameKey)
        assertEquals(before, counts())
    }

    @Test
    fun `PM3 an invalid name is refused and nothing changes`() {
        play("Ana", "Bob")
        val id = idOf("Ana")

        val blank = repository.rename(id, "   ") as DbResult.Ok
        val long = repository.rename(id, "x".repeat(21)) as DbResult.Ok

        assertEquals(RenameResult.Invalid(PlayerListError.BLANK_NAME), blank.value)
        assertEquals(RenameResult.Invalid(PlayerListError.NAME_TOO_LONG), long.value)
        assertEquals("Ana", db.playersDao().find(id)!!.name)
    }

    @Test
    fun `PM3 PM7 an unknown or anonymous profile is not renamed`() {
        val anonymous = db.profileDao().upsertUsed("Deleted player", "", at = 5L)

        assertEquals(DbResult.Ok(RenameResult.Unknown), repository.rename(anonymous, "Ana"))
        assertEquals(DbResult.Ok(RenameResult.Unknown), repository.rename(999L, "Ana"))
        assertEquals("Deleted player", db.playersDao().find(anonymous)!!.name)
    }

    @Test
    fun `PM5 reset moves only the reset date`() {
        play("Ana", "Bob")
        val id = idOf("Ana")
        val before = counts()
        val profile = db.playersDao().find(id)!!

        assertEquals(DbResult.Ok(Unit), repository.reset(id, at = 9_999_999_999_999L))

        assertEquals(profile.copy(resetAt = 9_999_999_999_999L), db.playersDao().find(id))
        assertEquals(before, counts())
        assertEquals(1, list().first { it.id == id }.matchCount)
    }

    @Test
    fun `PM4 merge moves participations, deletes the source and combines the dates`() {
        val firstEnd = play("Ana", "Bob")
        play("Cleo", "Dan")
        val ana = db.playersDao().find(idOf("Ana"))!!
        val cleo = db.playersDao().find(idOf("Cleo"))!!
        db.playersDao().updateResetAt(ana.id, 777L)

        assertEquals(DbResult.Ok(MergeResult.MERGED), repository.merge(sourceId = ana.id, targetId = cleo.id))

        assertEquals(null, db.playersDao().find(ana.id))
        val target = db.playersDao().find(cleo.id)!!
        assertEquals("Cleo", target.name)
        assertEquals(maxOf(ana.lastUsedAt, cleo.lastUsedAt), target.lastUsedAt)
        assertEquals(minOf(ana.createdAt, cleo.createdAt), target.createdAt)
        assertEquals(777L.coerceAtLeast(cleo.resetAt), target.resetAt)
        assertEquals(2, list().first { it.id == cleo.id }.matchCount)
        val firstSession = db.historyDao().matchSummaries(null, null).first { it.endedAt == firstEnd }.sessionId
        assertEquals(cleo.id, participants(firstSession).first())
    }

    @Test
    fun `PM4 a merge is refused on a shared match and nothing changes`() {
        play("Ana", "Bob")
        val before = counts()
        val ana = db.playersDao().find(idOf("Ana"))!!
        val bob = db.playersDao().find(idOf("Bob"))!!

        assertEquals(DbResult.Ok(MergeResult.SHARED_MATCH), repository.merge(ana.id, bob.id))

        assertEquals(before, counts())
        assertEquals(ana, db.playersDao().find(ana.id))
        assertEquals(bob, db.playersDao().find(bob.id))
    }

    @Test
    fun `PM4 PM7 merge with itself, an unknown or the anonymous profile does nothing`() {
        play("Ana", "Bob")
        val anonymous = db.profileDao().upsertUsed("Deleted player", "", at = 5L)
        val ana = idOf("Ana")
        val before = counts()

        assertEquals(DbResult.Ok(MergeResult.UNKNOWN), repository.merge(ana, ana))
        assertEquals(DbResult.Ok(MergeResult.UNKNOWN), repository.merge(ana, 999L))
        assertEquals(DbResult.Ok(MergeResult.UNKNOWN), repository.merge(ana, anonymous))
        assertEquals(DbResult.Ok(MergeResult.UNKNOWN), repository.merge(anonymous, ana))
        assertEquals(before, counts())
    }

    @Test
    fun `PM10 a failure after the move rolls the whole merge back`() {
        play("Ana", "Bob")
        play("Cleo", "Dan")
        val ana = idOf("Ana")
        val cleo = idOf("Cleo")
        val sessions = db.playersDao().sessionIdsOf(ana)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delete BEFORE DELETE ON profile BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val before = counts()
        val profile = db.playersDao().find(cleo)

        assertEquals(DbResult.Failed, repository.merge(ana, cleo))

        assertEquals(sessions, db.playersDao().sessionIdsOf(ana))
        assertEquals(profile, db.playersDao().find(cleo))
        assertEquals(before, counts())
    }

    @Test
    fun `AR9 a closed database gives Failed for every call`() {
        db.close()

        assertEquals(DbResult.Failed, repository.list())
        assertEquals(DbResult.Failed, repository.rename(1L, "Ana"))
        assertEquals(DbResult.Failed, repository.reset(1L, 1L))
        assertEquals(DbResult.Failed, repository.merge(1L, 2L))
    }
}
