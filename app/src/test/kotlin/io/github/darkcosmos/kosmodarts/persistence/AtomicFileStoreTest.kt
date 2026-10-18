package io.github.darkcosmos.kosmodarts.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** [AtomicFileStore] on real files of the app's private storage (rules R9, R12). */
@RunWith(AndroidJUnit4::class)
class AtomicFileStoreTest {

    private val filesDir: File = ApplicationProvider.getApplicationContext<Context>().filesDir
    private val file = File(filesDir, "store_test")
    private val newFile = File(filesDir, "store_test.new")
    private val store = AtomicFileStore(file)

    private fun ReadResult.bytes(): ByteArray = (this as ReadResult.Bytes).bytes

    @Test
    fun `R12 a written content reads back`() {
        store.write(byteArrayOf(1, 2, 3))

        assertArrayEquals(byteArrayOf(1, 2, 3), store.read(10).bytes())
    }

    @Test
    fun `R12 a write replaces the previous content`() {
        store.write(byteArrayOf(1, 2, 3))
        store.write(byteArrayOf(4))

        assertArrayEquals(byteArrayOf(4), store.read(10).bytes())
    }

    @Test
    fun `R12 delete removes the file and any leftover new file`() {
        store.write(byteArrayOf(1))
        newFile.writeBytes(byteArrayOf(9))

        store.delete()

        assertFalse(file.exists())
        assertFalse(newFile.exists())
        assertEquals(ReadResult.Absent, store.read(10))
    }

    /** R9: the read stops after the limit + 1 bytes, even on content that never ends (a file still growing). */
    @Test
    fun `R9 a bounded read never reads past the limit`() {
        var served = 0
        val endless = object : java.io.InputStream() {
            override fun read(): Int = 0.also { served++ }
        }

        assertEquals(ReadResult.TooLarge, readAtMost(endless, 10))
        assertEquals(11, served)
    }

    @Test
    fun `R9 no file reads as absent`() {
        assertEquals(ReadResult.Absent, store.read(10))
    }

    @Test
    fun `R9 a file over the limit is too large`() {
        store.write(ByteArray(11))

        assertEquals(ReadResult.TooLarge, store.read(10))
        assertEquals(11, store.read(11).bytes().size)
    }

    /** R12: a write interrupted before completion leaves a `.new` file that never replaces the last content. */
    @Test
    fun `R12 an interrupted write keeps the last complete content`() {
        store.write(byteArrayOf(1, 2))
        newFile.writeBytes(byteArrayOf(7, 7, 7)) // As left by a crash in the middle of a write.

        assertArrayEquals(byteArrayOf(1, 2), store.read(10).bytes())
    }

    @Test
    fun `R12 the file is in the app's private storage`() {
        assertTrue(file.canonicalPath.startsWith(filesDir.canonicalPath + File.separator))
    }

    /** R12: a real input/output error on write keeps the previous content, and a later write succeeds. */
    @Test
    fun `R12 a failed write keeps the previous content`() {
        store.write(byteArrayOf(1))
        // A directory where AtomicFile writes its .new file: opening it for writing fails.
        assertTrue(newFile.mkdir())

        val failure = runCatching { store.write(byteArrayOf(2)) }.exceptionOrNull()

        assertTrue("got $failure", failure is IOException)
        assertArrayEquals(byteArrayOf(1), store.read(10).bytes())
        newFile.delete() // AtomicFile may already have removed it.
        store.write(byteArrayOf(3))
        assertArrayEquals(byteArrayOf(3), store.read(10).bytes())
    }
}
