package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.GameSaveCodec
import io.github.darkcosmos.kosmodarts.persistence.LastSetupCodec
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.toLastSetups
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Profile creation at Start and Rematch (PF1, PF2, PF5), failure contract (AR9) and the launch (PF4 withdrawn). */
@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private lateinit var db: HistoryDatabase
    private lateinit var repository: RoomProfileRepository

    @Before
    fun open() {
        db = inMemoryHistoryDatabase(context)
        repository = RoomProfileRepository(db, PlayerListRules::nameKey)
    }

    @After
    fun close() = db.close()

    private fun recent(): List<Profile> = (repository.recent() as DbResult.Ok).value

    @Test
    fun `PF1 PF2 a new name creates a profile with that spelling`() {
        val at = clock.nowMillis()
        assertEquals(DbResult.Ok(Unit), repository.touchOrCreate(listOf("Kevin", "Ana"), at))

        assertEquals(setOf("Kevin", "Ana"), recent().map(Profile::name).toSet())
        val stored = db.profileDao().findByNameKey("kevin")!!
        assertEquals(listOf(at, at, at), listOf(stored.createdAt, stored.lastUsedAt, stored.resetAt))
    }

    @Test
    fun `PF2 another spelling of a known name touches it and keeps the first spelling`() {
        repository.touchOrCreate(listOf("Kevin"), 100L)
        repository.touchOrCreate(listOf(" KEVIN "), 200L)

        val profiles = recent()
        assertEquals(listOf("Kevin"), profiles.map(Profile::name))
        val stored = db.profileDao().findByNameKey("kevin")!!
        assertEquals(listOf(100L, 200L, 100L), listOf(stored.createdAt, stored.lastUsedAt, stored.resetAt))
    }

    @Test
    fun `PF2 PF3 recent lists the most recently used profile first`() {
        repository.touchOrCreate(listOf("Ana", "Ben"), 100L)
        repository.touchOrCreate(listOf("Cléo"), 200L)
        repository.touchOrCreate(listOf("Ana"), 300L)

        assertEquals(listOf("Ana", "Cléo", "Ben"), recent().map(Profile::name))
    }

    @Test
    fun `PF1 a name of the maximum length is stored whole`() {
        val name = "é".repeat(PlayerListRules.MAX_NAME_LENGTH)
        repository.touchOrCreate(listOf(name), 100L)

        assertEquals(listOf(name), recent().map(Profile::name))
    }

    @Test
    fun `PF5 no profile is deleted by later starts`() {
        repository.touchOrCreate(listOf("Ana", "Ben"), 100L)
        repository.touchOrCreate(listOf("Ana"), 200L)

        assertEquals(2, recent().size)
    }

    @Test
    fun `AR9 a closed database gives Failed and no exception`() {
        db.close()

        assertEquals(DbResult.Failed, repository.touchOrCreate(listOf("Ana"), 100L))
        assertEquals(DbResult.Failed, repository.recent())
    }

    @Test
    fun `AR9 PF2 a repository returning Failed never blocks or crashes the queue`() {
        val calls = mutableListOf<List<String>>()
        val failing = object : ProfileRepository {
            override fun touchOrCreate(names: List<String>, at: Long): DbResult<Unit> {
                calls += names
                return DbResult.Failed
            }

            override fun recent(): DbResult<List<Profile>> = DbResult.Failed
        }
        val persistence = Persistence(MemoryStore(), MemoryStore(), Runnable::run, profiles = failing)

        persistence.touchProfiles(listOf("Ana"), 100L)
        persistence.touchProfiles(listOf("Ben"), 200L)

        assertEquals(listOf(listOf("Ana"), listOf("Ben")), calls)
    }

    @Test
    fun `AR9 Start with a closed database does not crash`() {
        db.close()
        val persistence = Persistence(MemoryStore(), MemoryStore(), Runnable::run, profiles = repository)

        persistence.touchProfiles(listOf("Ana"), 100L)
        persistence.awaitIdle()

        assertEquals(DbResult.Failed, repository.recent())
    }

    @Test
    fun `PF4 withdrawn - loading a last setup and a saved game creates no profile`() {
        val gameStore = MemoryStore(GameSaveCodec().encode(SaveFixtures.MID_TURN))
        val setupStore = MemoryStore(LastSetupCodec().encode(SaveFixtures.setup().toLastSetups()))
        val persistence = Persistence(gameStore, setupStore, Runnable::run, profiles = repository)

        assertTrue(persistence.load().game is GameLoad.Valid)
        persistence.awaitIdle()

        assertTrue(recent().isEmpty())
    }
}
