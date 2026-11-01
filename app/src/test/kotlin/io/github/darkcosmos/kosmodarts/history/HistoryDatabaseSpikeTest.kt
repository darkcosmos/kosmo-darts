package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryDatabaseSpikeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun insertsThenReadsOneRowInMemory() {
        val db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.profileDao().upsertUsed("Ana", "ana", at = 1L)
            assertEquals(listOf("Ana"), db.historyDao().profilesByRecency().map { it.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun corruptFileIsKeptWithItsBytesWhenOpeningFails() {
        val file = context.getDatabasePath(HistoryDatabase.FILE_NAME)
        file.parentFile?.mkdirs()
        val garbage = ByteArray(4096) { (it * 31 + 7).toByte() }
        file.writeBytes(garbage)
        val db = Room.databaseBuilder(context, HistoryDatabase::class.java, HistoryDatabase.FILE_NAME)
            .openHelperFactory(KeepCorruptFileOpenHelperFactory())
            .allowMainThreadQueries()
            .build()
        try {
            repeat(2) {
                try {
                    db.historyDao().profilesByRecency()
                    fail("opening a corrupt database must throw")
                } catch (expected: Exception) {
                    assertTrue(expected.toString(), expected is RuntimeException)
                }
                assertTrue("file must still exist", file.exists())
                assertArrayEquals(garbage, file.readBytes())
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun defaultFrameworkBehaviorReplacesTheCorruptFile() {
        // Control: proves the test above is meaningful (the default handler removes the file).
        val name = "control.db"
        val file: File = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(4096) { (it * 31 + 7).toByte() })
        val db = Room.databaseBuilder(context, HistoryDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            try {
                db.historyDao().profilesByRecency()
            } catch (ignored: Exception) {
                // The framework may recreate the file and succeed, or throw: only the file content matters here.
            }
            val kept = file.exists() && file.readBytes().contentEquals(ByteArray(4096) { (it * 31 + 7).toByte() })
            assertTrue("default framework handler replaces the corrupt file", !kept)
        } finally {
            db.close()
        }
    }
}
