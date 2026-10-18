package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Loading, reading back and the 10,000-dart limit through [Persistence] (rules R8 to R10, R12). */
class PersistenceTest {

    private val gameStore = MemoryStore()
    private val setupStore = MemoryStore()
    private val codec = GameSaveCodec()

    /** Runs every task at once, on the calling thread: reads and writes are then sequential. */
    private val direct = Persistence(gameStore, setupStore, Runnable::run)

    @Test
    fun `R12 a saved game and last setup load back`() {
        val setup = SaveFixtures.config(players = 3, startScore = 301, rule = CheckoutRule.SINGLE_OUT)
        direct.saveGame(SaveFixtures.WAITING_BUST)
        direct.saveLastSetup(setup)

        assertEquals(LoadResult(GameLoad.Valid(SaveFixtures.WAITING_BUST), setup), direct.load())
    }

    @Test
    fun `R12 delete removes the saved game only`() {
        direct.saveGame(SaveFixtures.MID_TURN)
        direct.saveLastSetup(SaveFixtures.config())
        direct.deleteGame()

        assertEquals(LoadResult(GameLoad.Absent, SaveFixtures.config()), direct.load())
    }

    /** R12: over the limit the previous file stays; a later delete still deletes it; undo resumes saving. */
    @Test
    fun `R12 over 10,000 darts the previous file stays until undo or delete`() {
        val atLimit = SaveFixtures.LONGEST
        val overLimit = SavedGame(atLimit.game.throwDart(Dart.MISS), isAwaitingNextPlayer = false)
        direct.saveGame(atLimit)
        val atLimitBytes = gameStore.content

        direct.saveGame(overLimit)
        assertArrayEquals(atLimitBytes, gameStore.content)
        assertEquals(1, gameStore.writes)

        val undone = SavedGame(overLimit.game.undo(), isAwaitingNextPlayer = false)
        direct.saveGame(undone)
        assertEquals(2, gameStore.writes)
        assertArrayEquals(codec.encode(undone), gameStore.content)

        direct.saveGame(overLimit)
        direct.deleteGame()
        assertNull(gameStore.content)
    }

    /** ADR 0002, decision 3: a load waits behind a write still pending on the save thread. */
    @Test
    fun `R12 a load after a pending write sees that write`() {
        val saveThread = Executors.newSingleThreadExecutor()
        try {
            val persistence = Persistence(gameStore, setupStore, saveThread)
            // The save thread is held, so the write stays pending while the load is requested.
            val gate = CountDownLatch(1)
            saveThread.execute { gate.await() }
            persistence.saveGame(SaveFixtures.WAITING_NORMAL)
            var loaded: GameLoad? = null
            val loader = Thread { loaded = persistence.load().game }
            loader.start()
            // A load that bypassed the queue would read now, before the write, and find nothing.
            gate.countDown()
            loader.join(JOIN_TIMEOUT_MILLIS)

            assertEquals(GameLoad.Valid(SaveFixtures.WAITING_NORMAL), loaded)
        } finally {
            saveThread.shutdown()
            saveThread.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `R12 awaitIdle returns once every pending write is done`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val persistence = Persistence(gameStore, setupStore, executor)
            persistence.saveGame(SaveFixtures.MID_TURN)
            persistence.awaitIdle()

            assertArrayEquals(codec.encode(SaveFixtures.MID_TURN), gameStore.content)
        } finally {
            executor.shutdown()
        }
    }

    /** R11: an unforeseen loader failure deletes both files, so the app cannot fail the same way at every launch. */
    @Test
    fun `R11 an unexpected loader failure deletes both files instead of crashing`() {
        val failing = GameSaveCodec { _, _ -> throw IllegalStateException("unforeseen") }
        gameStore.content = codec.encode(SaveFixtures.MID_TURN)
        setupStore.content = LastSetupCodec().encode(SaveFixtures.config())
        val persistence = Persistence(gameStore, setupStore, Runnable::run, gameCodec = failing)

        assertEquals(LoadResult(GameLoad.Invalid(InvalidSaveReason.UNEXPECTED_ERROR), null), persistence.load())
        assertNull(gameStore.content)
        assertNull(setupStore.content)
    }

    private companion object {
        const val JOIN_TIMEOUT_MILLIS = 10_000L
    }
}
