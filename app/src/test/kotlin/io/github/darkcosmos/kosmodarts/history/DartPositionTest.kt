package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.board.BoardPoint
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Dart positions and values through the entity mapper (BC1, BC2, D33, review A11): both coordinates or neither,
 * never (0, 0) for an unknown position; the bull stored as segment 25, multiplier 2 (ADR 0012).
 */
@RunWith(RobolectricTestRunner::class)
class DartPositionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = inMemoryHistoryDatabase(context)

    @After
    fun closeDatabase() = db.close()

    /** Stored columns of every dart, in entry order: segment, multiplier, x_mm, y_mm (`null` for SQL NULL). */
    private fun storedColumns(): List<List<Any?>> =
        db.openHelper.readableDatabase.query("SELECT segment, multiplier, x_mm, y_mm FROM dart ORDER BY id").use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        listOf(
                            it.getInt(0),
                            it.getInt(1),
                            if (it.isNull(2)) null else it.getDouble(2),
                            if (it.isNull(3)) null else it.getDouble(3),
                        ),
                    )
                }
            }
        }

    private fun archiveAndReadBack(): List<ArchivedDart> {
        val match = x01Match()
        ArchiveRepository(db, testNameKey).archive(match, colorsOf(match), APP_VERSION)
        val sessionId = db.historyDao().matchSummaries(mode = null, profileId = null).single().sessionId
        return db.historyDao().dartsOf(sessionId).map(ArchiveMapper::archivedDart)
    }

    @Test
    fun hitIsStoredWithBothCoordinatesAndReadBackAsTheSamePoint() {
        val darts = archiveAndReadBack()

        assertEquals(listOf<Any?>(20, 3, T20_POINT.xMm, T20_POINT.yMm), storedColumns()[0])
        assertEquals(T20_POINT, darts[0].point)
        assertEquals(D16_POINT, darts[4].point)
    }

    @Test
    fun missIsStoredWithNeitherCoordinateAndReadBackWithoutPoint() {
        val darts = archiveAndReadBack()

        assertEquals(listOf<Any?>(0, 1, null, null), storedColumns()[1])
        assertNull(darts[1].point)
        assertEquals(Dart.MISS_SEGMENT, darts[1].segment)
    }

    @Test
    fun hitWithoutKnownPositionBehavesAsTheMiss() {
        val darts = archiveAndReadBack()

        // Voided single 5 and busted outer bull of the fixture were archived without a point.
        assertEquals(listOf<Any?>(5, 1, null, null), storedColumns()[3])
        assertEquals(listOf<Any?>(25, 1, null, null), storedColumns()[5])
        assertNull(darts[3].point)
        assertNull(darts[5].point)
    }

    @Test
    fun bullIsStoredAsSegment25Multiplier2AtTheCentreAndReadBackAsTheBull() {
        val darts = archiveAndReadBack()

        assertEquals(listOf<Any?>(25, 2, 0.0, 0.0), storedColumns()[2])
        assertEquals(Dart.BULL_SEGMENT, darts[2].segment)
        assertEquals(Multiplier.SINGLE, darts[2].multiplier)
        assertEquals(BoardPoint(0.0, 0.0), darts[2].point)
    }

    @Test
    fun aRowWithOnlyOneCoordinateIsReadWithoutPoint() {
        val row = DartWithLegRow(
            dart = DartEntity(
                id = 1, legId = 1, playerPosition = 0, visit = 1, rank = 1, segment = 20, multiplier = 1,
                bust = false, voided = false, source = DartSource.MANUAL,
                xMm = 1.0, yMm = null, enteredAt = 5L,
            ),
            legOrdinal = 0,
        )

        assertNull(ArchiveMapper.archivedDart(row).point)
    }

    @Test(expected = IllegalStateException::class)
    fun aStoredValueThatIsNoDartIsAReadFault() {
        val row = DartWithLegRow(
            dart = DartEntity(
                id = 1, legId = 1, playerPosition = 0, visit = 1, rank = 1, segment = 25, multiplier = 3,
                bust = false, voided = false, source = DartSource.MANUAL,
                xMm = null, yMm = null, enteredAt = 5L,
            ),
            legOrdinal = 0,
        )

        ArchiveMapper.archivedDart(row)
    }
}
