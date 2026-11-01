package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Failure contract (AR9, ADR 0012 "Failure contract", review S1): `Failed`, never an exception, no file lost. */
@RunWith(RobolectricTestRunner::class)
class HistoryDatabaseFailureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Every History read of T11 and T12, each wrapped as the repositories do. */
    private fun readsOf(db: HistoryDatabase): Map<String, () -> DbResult<Any?>> {
        val history = db.historyDao()
        return mapOf(
            "matchSummaries" to { dbResult { history.matchSummaries(ArchivedMode.X01, profileId = 1L) } },
            "matchPlayers" to { dbResult { history.matchPlayers(mode = null, profileId = null) } },
            "profilesByRecency" to { dbResult { history.profilesByRecency() } },
            "matchSummary" to { dbResult { history.matchSummary(1L) } },
            "playersOf" to { dbResult { history.playersOf(1L) } },
            "legsOf" to { dbResult { history.legsOf(1L) } },
            "dartsOf" to { dbResult { history.dartsOf(1L).map(ArchiveMapper::archivedDart) } },
            "isArchived" to { dbResult { db.archiveDao().isArchived("m1") } },
            "findByNameKey" to { dbResult { db.profileDao().findByNameKey("ana") } },
        )
    }

    private fun archive(db: HistoryDatabase): DbResult<ArchiveOutcome> {
        val match = x01Match()
        return ArchiveRepository(db, testNameKey).archive(match, colorsOf(match), APP_VERSION)
    }

    @Test
    fun closedDatabaseGivesFailedForTheArchiveAndEveryRead() {
        val db = inMemoryHistoryDatabase(context)
        db.close()

        assertEquals(DbResult.Failed, archive(db))
        readsOf(db).forEach { (name, read) -> assertEquals(name, DbResult.Failed, read()) }
    }

    @Test
    fun corruptFileGivesFailedAndKeepsItsBytes() {
        val file = context.getDatabasePath(HistoryDatabase.FILE_NAME)
        file.parentFile?.mkdirs()
        val garbage = ByteArray(GARBAGE_SIZE) { (it * 37 + 11).toByte() }
        file.writeBytes(garbage)
        val db = HistoryDatabase.builder(context).onCallingThread().build()
        try {
            assertEquals(DbResult.Failed, archive(db))
            readsOf(db).forEach { (name, read) -> assertEquals(name, DbResult.Failed, read()) }
            assertEquals(DbResult.Failed, archive(db))

            // Not deleted, not recreated empty, no destructive migration: the same bytes stay.
            assertTrue(file.exists())
            assertArrayEquals(garbage, file.readBytes())
        } finally {
            db.close()
        }
    }

    @Test
    fun aReadOfAnUnreadableStoredValueGivesFailed() {
        val db = inMemoryHistoryDatabase(context)
        try {
            archive(db)
            db.openHelper.writableDatabase.execSQL("UPDATE dart SET multiplier = 7")
            db.openHelper.writableDatabase.execSQL("UPDATE match_record SET settings = 'v=9'")
            val summary = db.historyDao().matchSummaries(mode = null, profileId = null).single()

            assertEquals(DbResult.Failed, readsOf(db).getValue("dartsOf")())
            assertEquals(DbResult.Failed, dbResult { SettingsText.decode(summary.settings, summary.mode) })
        } finally {
            db.close()
        }
    }

    private companion object {
        const val GARBAGE_SIZE = 4096
    }
}
