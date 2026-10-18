package io.github.darkcosmos.kosmodarts.persistence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R12: background writes, one at a time, latest content first; a failed write keeps the previous file. */
class SaveWriterTest {

    private val executor = ManualExecutor()
    private val store = MemoryStore()
    private val writer = SaveWriter(store, executor)

    @Test
    fun `R12 writes run on the executor, never on the caller`() {
        writer.write(byteArrayOf(1))

        assertNull(store.content)
        executor.runAll()
        assertArrayEquals(byteArrayOf(1), store.content)
    }

    @Test
    fun `R12 only the latest pending content is written`() {
        writer.write(byteArrayOf(1))
        writer.write(byteArrayOf(2))
        writer.write(byteArrayOf(3))

        assertEquals(1, executor.pendingTasks)
        executor.runAll()
        assertEquals(1, store.writes)
        assertArrayEquals(byteArrayOf(3), store.content)
    }

    @Test
    fun `R12 save then delete ends deleted, delete then save ends saved`() {
        writer.write(byteArrayOf(1))
        writer.delete()
        executor.runAll()
        assertNull(store.content)

        writer.delete()
        writer.write(byteArrayOf(2))
        executor.runAll()
        assertArrayEquals(byteArrayOf(2), store.content)
    }

    @Test
    fun `R12 a request after a drain is written by a new drain`() {
        writer.write(byteArrayOf(1))
        executor.runAll()
        writer.write(byteArrayOf(2))
        executor.runAll()

        assertEquals(2, store.writes)
        assertArrayEquals(byteArrayOf(2), store.content)
    }

    @Test
    fun `R12 two stores do not overwrite each other`() {
        val other = MemoryStore()
        val otherWriter = SaveWriter(other, executor)

        writer.write(byteArrayOf(1))
        otherWriter.write(byteArrayOf(2))
        executor.runAll()

        assertArrayEquals(byteArrayOf(1), store.content)
        assertArrayEquals(byteArrayOf(2), other.content)
    }

    @Test
    fun `R12 a failed write keeps the previous content and the next write succeeds`() {
        writer.write(byteArrayOf(1))
        executor.runAll()

        store.failOnWrite = true
        writer.write(byteArrayOf(2))
        executor.runAll() // Must not throw.
        assertArrayEquals(byteArrayOf(1), store.content)

        store.failOnWrite = false
        writer.write(byteArrayOf(3))
        executor.runAll()
        assertArrayEquals(byteArrayOf(3), store.content)
    }
}
