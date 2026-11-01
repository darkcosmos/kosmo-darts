package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.GameSaveCodec
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/**
 * The two steps of a confirmed win (AR2) and the launch check (AR4) through [Persistence], with a same-thread executor
 * (every task runs at once, in call order: no sleep, no idling wait) and a fixed clock. The failures are those of a
 * fake gateway and of a closed database (AR3, AR9, R2).
 */
@RunWith(RobolectricTestRunner::class)
class ArchiveFlowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sameThread = Executor { it.run() }
    private val saved = SaveFixtures.WAITING_CHECKOUT
    private val gameStore = MemoryStore(checkNotNull(GameSaveCodec().encode(saved)))
    private val gateway = FakeArchiveGateway()
    private val match = x01Match(matchId = saved.journal.matchId, clock = FakeClock())
    private val colors = colorsOf(match)
    private var database: HistoryDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    private fun persistenceOver(archive: ArchiveGateway) = Persistence(
        gameStore,
        MemoryStore(),
        sameThread,
        archiveRequest = MatchArchiveRequest(archive, APP_VERSION),
        archiveCheck = ArchiveCheck(archive),
    )

    private fun archiveWith(persistence: Persistence): Boolean {
        var answer: Boolean? = null
        persistence.archiveMatch(match, colors) { answer = it }
        return checkNotNull(answer)
    }

    private fun openDatabase(): ArchiveRepository {
        val opened = inMemoryHistoryDatabase(context)
        database = opened
        return ArchiveRepository(opened, testNameKey)
    }

    @Test
    fun `AR2 the match is archived first, then the saved game is deleted`() {
        val persistence = persistenceOver(gateway)

        assertTrue(archiveWith(persistence))

        assertEquals(listOf(match to colors), gateway.archived)
        assertEquals(listOf(APP_VERSION), gateway.versions)
        assertNull(gameStore.content)
        assertEquals(1, gameStore.deletes)
    }

    @Test
    fun `AR2 a write queued before the win never recreates the saved game after it`() {
        val persistence = persistenceOver(gateway)
        persistence.saveGame(saved)

        assertTrue(archiveWith(persistence))

        assertNull(gameStore.content)
    }

    @Test
    fun `AR3 a failing archive keeps the saved game and answers false`() {
        gateway.archiveAnswer = DbResult.Failed
        val persistence = persistenceOver(gateway)

        assertEquals(false, archiveWith(persistence))

        assertNotNull(gameStore.content)
        assertEquals(0, gameStore.deletes)
    }

    @Test
    fun `AR3 AR9 a closed database fails cleanly and keeps the saved game`() {
        val repository = openDatabase()
        checkNotNull(database).close()
        val persistence = persistenceOver(repository)

        assertEquals(false, archiveWith(persistence))

        assertNotNull(gameStore.content)
    }

    @Test
    fun `AR3 retryAfterFailureDeletesOnlyOnSuccess`() {
        val persistence = persistenceOver(gateway)
        gateway.archiveAnswer = DbResult.Failed
        assertEquals(false, archiveWith(persistence))
        assertNotNull(gameStore.content)

        gateway.archiveAnswer = DbResult.Ok(ArchiveOutcome.ARCHIVED)
        assertTrue(archiveWith(persistence))

        assertNull(gameStore.content)
        assertEquals(2, gateway.archived.size)
        assertEquals(1, gameStore.deletes)
    }

    @Test
    fun `AR4 retryAfterFailureAlreadyArchivedDeletesSave`() {
        val persistence = persistenceOver(gateway)
        gateway.archiveAnswer = DbResult.Failed
        assertEquals(false, archiveWith(persistence))

        // The first try was written after all (the answer was lost): the retry finds the match id archived.
        gateway.archiveAnswer = DbResult.Ok(ArchiveOutcome.ALREADY_ARCHIVED)
        assertTrue(archiveWith(persistence))

        assertNull(gameStore.content)
    }

    @Test
    fun `AR2 without an archive the answer is false and the saved game stays`() {
        val persistence = Persistence(gameStore, MemoryStore(), sameThread)

        assertEquals(false, archiveWith(persistence))

        assertNotNull(gameStore.content)
    }

    @Test
    fun `AR4 a saved game whose match is archived is deleted at launch and not offered`() {
        gateway.archivedIds = DbResult.Ok(setOf(saved.journal.matchId))
        val persistence = persistenceOver(gateway)

        val loaded = persistence.load()

        assertEquals(GameLoad.Absent, loaded.game)
        assertEquals(listOf(saved.journal.matchId), gateway.checked)
        assertNull(gameStore.content)
    }

    @Test
    fun `AR4 R2 a saved game that is not archived is offered, and kept`() {
        val persistence = persistenceOver(gateway)

        val loaded = persistence.load()

        assertEquals(GameLoad.Valid(saved), loaded.game)
        assertNotNull(gameStore.content)
    }

    @Test
    fun `AR4 AR9 R2 a failing database keeps the saved game and offers it`() {
        gateway.archivedIds = DbResult.Failed
        val persistence = persistenceOver(gateway)

        val loaded = persistence.load()

        assertEquals(GameLoad.Valid(saved), loaded.game)
        assertNotNull(gameStore.content)
        assertEquals(0, gameStore.deletes)
    }

    @Test
    fun `AR4 AR9 R2 a closed database keeps the saved game and offers it`() {
        val repository = openDatabase()
        checkNotNull(database).close()
        val persistence = persistenceOver(repository)

        val loaded = persistence.load()

        assertEquals(GameLoad.Valid(saved), loaded.game)
        assertNotNull(gameStore.content)
    }

    @Test
    fun `AR4 a real database that holds the match deletes the saved game at launch`() {
        val repository = openDatabase()
        assertEquals(DbResult.Ok(ArchiveOutcome.ARCHIVED), repository.archive(match, colors, APP_VERSION))
        val persistence = persistenceOver(repository)

        assertEquals(GameLoad.Absent, persistence.load().game)

        assertNull(gameStore.content)
    }

    @Test
    fun `AR2 a real database writes the match and deletes the saved game`() {
        val repository = openDatabase()
        val persistence = persistenceOver(repository)

        assertTrue(archiveWith(persistence))

        assertEquals(DbResult.Ok(true), repository.isArchived(match.matchId))
        assertNull(gameStore.content)
    }
}
