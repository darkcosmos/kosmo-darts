package io.github.darkcosmos.kosmodarts.persistence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** What loading finds and deletes (rules R8 to R10; ADR 0002, decision 5). */
class SaveLoaderTest {

    private val gameCodec = GameSaveCodec()
    private val setupCodec = LastSetupCodec()
    private val validGame = checkNotNull(gameCodec.encode(SaveFixtures.WAITING_CHECKOUT))
    private val validSetup = setupCodec.encode(SaveFixtures.config(players = 3))
    private val garbage = "not a save".toByteArray()

    private val gameStore = MemoryStore()
    private val setupStore = MemoryStore()
    private val loader = SaveLoader(gameStore, setupStore, gameCodec, setupCodec)

    @Test
    fun `R8 no files give no game and no last setup, and nothing is changed`() {
        assertEquals(LoadResult(GameLoad.Absent, null), loader.load())
        assertEquals(0, gameStore.writes + gameStore.deletes + setupStore.writes + setupStore.deletes)
    }

    @Test
    fun `R8 valid files load`() {
        gameStore.content = validGame
        setupStore.content = validSetup

        val expected = LoadResult(GameLoad.Valid(SaveFixtures.WAITING_CHECKOUT), SaveFixtures.config(players = 3))

        assertEquals(expected, loader.load())
        assertEquals(0, gameStore.deletes + setupStore.deletes)
    }

    @Test
    fun `R9 invalid game content is deleted, and the last setup is kept`() {
        gameStore.content = garbage
        setupStore.content = validSetup

        val result = loader.load()

        assertEquals(GameLoad.Invalid(InvalidSaveReason.MALFORMED), result.game)
        assertEquals(SaveFixtures.config(players = 3), result.lastSetup)
        assertNull(gameStore.content)
        assertArrayEquals(validSetup, setupStore.content)
    }

    @Test
    fun `R9 an oversized game file is invalid and deleted`() {
        gameStore.content = ByteArray(GameSaveCodec.MAX_FILE_BYTES + 1)

        assertEquals(GameLoad.Invalid(InvalidSaveReason.TOO_LARGE), loader.load().game)
        assertNull(gameStore.content)
    }

    /** R9 (owner decision Q9): a read error is handled like invalid content: the file is deleted. */
    @Test
    fun `R9 a game file that cannot be read is deleted`() {
        gameStore.content = validGame
        gameStore.failOnRead = true

        assertEquals(GameLoad.Invalid(InvalidSaveReason.READ_FAILED), loader.load().game)
        assertNull(gameStore.content)
    }

    /** R9: the message is shown once: the next launch finds nothing to resume. */
    @Test
    fun `R9 after an unreadable game, the next load finds nothing`() {
        gameStore.content = garbage
        loader.load()

        assertEquals(GameLoad.Absent, loader.load().game)
    }

    @Test
    fun `R10 an invalid last setup is ignored and deleted, and the saved game is kept`() {
        gameStore.content = validGame
        setupStore.content = garbage

        val result = loader.load()

        assertNull(result.lastSetup)
        assertNull(setupStore.content)
        assertEquals(GameLoad.Valid(SaveFixtures.WAITING_CHECKOUT), result.game)
        assertNotNull(gameStore.content)
    }

    @Test
    fun `R10 an oversized last setup is ignored and deleted`() {
        setupStore.content = ByteArray(LastSetupCodec.MAX_FILE_BYTES + 1)

        assertNull(loader.load().lastSetup)
        assertNull(setupStore.content)
    }

    @Test
    fun `R10 a last setup that cannot be read is ignored and deleted`() {
        setupStore.content = validSetup
        setupStore.failOnRead = true

        assertNull(loader.load().lastSetup)
        assertNull(setupStore.content)
    }
}
