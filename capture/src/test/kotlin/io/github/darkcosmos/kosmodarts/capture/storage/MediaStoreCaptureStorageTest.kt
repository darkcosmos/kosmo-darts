package io.github.darkcosmos.kosmodarts.capture.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Ownership and the real pending behavior of MediaStore are not emulated by Robolectric: [FakeMediaProvider] only
 * stands in for them, and the real behavior is proven by the manual check on the phone (root plan, step 7).
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreCaptureStorageTest {
    private lateinit var provider: FakeMediaProvider
    private lateinit var storage: MediaStoreCaptureStorage
    private val session = "20261031-140322"

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java).create("media").get()
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = MediaStoreCaptureStorage(context.contentResolver)
    }

    @Test
    fun `CP9 the truth entry has the right display name and relative path`() {
        val entry = storage.createTruthFile(session)
        val row = provider.rows.single()
        assertEquals("truth.json", row.name)
        assertEquals("Download/KosmoCapture/$session/", row.path)
        assertEquals(session, entry.folder)
        assertEquals("truth.json", entry.name)
    }

    @Test
    fun `CP9 writing the truth text replaces the previous text`() {
        val entry = storage.createTruthFile(session)
        storage.writeTruth(entry, "a much longer first text")
        storage.writeTruth(entry, "short")
        assertEquals("short", provider.rows.single().file.readText())
    }

    @Test
    fun `targets carry the folder, the file name, the MIME type and the relative path`() {
        val video = storage.videoTarget(session, "volley-001.mp4")
        val photo = storage.photoTarget(session, "dart-001.jpg")
        assertEquals("video/mp4", video.mimeType)
        assertEquals("image/jpeg", photo.mimeType)
        assertEquals("Download/KosmoCapture/$session/", video.relativePath)
        assertEquals("dart-001.jpg", photo.displayName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a folder name that is not a session name is refused`() {
        storage.createTruthFile("../../DCIM")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file name with a path is refused`() {
        storage.videoTarget(session, "../x.mp4")
    }

    @Test
    fun `CP8 Delete all removes own entries, pending included, and leaves foreign rows`() {
        storage.createTruthFile(session)
        provider.addPendingOwnRow("volley-001.mp4", "Download/KosmoCapture/$session/")
        provider.addForeignRow("truth.json", "Download/KosmoCapture/$session/")
        provider.addForeignRow("photo.jpg", "Pictures/")

        assertEquals(2, storage.deleteAllOwnSessions())

        assertTrue(provider.lastQueryIncludedPending)
        assertEquals(listOf(false, false), provider.rows.map { it.own })
    }

    @Test
    fun `CP1 listing returns the own session folders only`() {
        storage.createTruthFile(session)
        provider.addPendingOwnRow("volley-001.mp4", "Download/KosmoCapture/20261031-140400/")
        provider.addPendingOwnRow("other.mp4", "Download/Elsewhere/20990101-000000/")
        provider.addForeignRow("truth.json", "Download/KosmoCapture/20261031-150000/")
        assertEquals(setOf(session, "20261031-140400"), storage.listOwnSessions())
    }
}
