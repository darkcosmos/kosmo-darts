package io.github.darkcosmos.kosmodarts.capture.storage

import io.github.darkcosmos.kosmodarts.capture.session.CaptureCounters
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeleteAllTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val storage = FakeCaptureStorage()

    @Test
    fun `CP8 Delete all removes every own entry, pending ones included, and keeps foreign files`() {
        val session = "20261031-140322"
        storage.createTruthFile(session)
        storage.addPendingOwn(storage.videoTarget(session, "volley-001.mp4"))
        storage.addPendingOwn(storage.photoTarget(session, "dart-001.jpg"))
        val foreign = storage.addForeign(session, "dart-001.jpg")
        val foreignTruth = storage.addForeign(session, "truth.json")

        assertEquals(setOf(session), storage.listOwnSessions())
        assertEquals(3, storage.deleteAllOwnSessions())

        assertEquals(listOf(foreign, foreignTruth), storage.items)
        assertTrue(storage.listOwnSessions().isEmpty())
    }

    @Test
    fun `CP8 Delete all resets the total and keeps the quality`() {
        val settings = CaptureSettings(File(folder.root, "settings.txt"))
        settings.saveQuality(VideoQuality.FHD)
        settings.saveTotal(17)
        storage.createTruthFile("20261031-140322")

        storage.deleteAllOwnSessions()
        settings.saveTotal(CaptureCounters(session = 3, total = 17).cleared().total)

        assertEquals(StoredSettings(VideoQuality.FHD, 0), settings.read())
    }

    @Test
    fun `CP1 the collision check sees only own folders`() {
        storage.createTruthFile("20261031-140322")
        storage.addForeign("20261031-140323", "other.txt")
        assertEquals(setOf("20261031-140322"), storage.listOwnSessions())
    }
}
