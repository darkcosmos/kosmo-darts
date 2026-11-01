package io.github.darkcosmos.kosmodarts.capture.storage

import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file(): File = File(folder.root, "settings.txt")

    @Test
    fun `CP11 first launch reads UHD and a total of 0`() {
        assertEquals(StoredSettings(VideoQuality.UHD, 0), CaptureSettings(file()).read())
    }

    @Test
    fun `CP11 and CP7 quality and total survive a new instance`() {
        val settings = CaptureSettings(file())
        settings.saveQuality(VideoQuality.FHD)
        settings.saveTotal(42)
        assertEquals(StoredSettings(VideoQuality.FHD, 42), CaptureSettings(file()).read())
    }

    @Test
    fun `CP7 saving the total keeps the quality and saving the quality keeps the total`() {
        val settings = CaptureSettings(file())
        settings.saveQuality(VideoQuality.FHD)
        settings.saveTotal(5)
        settings.saveQuality(VideoQuality.UHD)
        assertEquals(StoredSettings(VideoQuality.UHD, 5), settings.read())
    }

    @Test
    fun `CP7 a corrupt file reads as the defaults without crashing`() {
        file().writeText("\u0000garbage\nquality=8K\ntotal=-3\n=\n")
        assertEquals(StoredSettings(), CaptureSettings(file()).read())
    }

    @Test
    fun `CP7 a corrupt total keeps a valid quality`() {
        file().writeText("quality=FHD\ntotal=abc\n")
        assertEquals(StoredSettings(VideoQuality.FHD, 0), CaptureSettings(file()).read())
    }

    @Test
    fun `CP16 an old file without zoom reads 1_0 and keeps quality and total`() {
        file().writeText("quality=FHD\ntotal=7\n")
        assertEquals(StoredSettings(VideoQuality.FHD, 7, 1.0f), CaptureSettings(file()).read())
    }

    @Test
    fun `CP16 an invalid zoom reads 1_0`() {
        listOf("abc", "NaN", "0", "-1").forEach { bad ->
            file().writeText("quality=FHD\ntotal=7\nzoom=$bad\n")
            assertEquals(bad, StoredSettings(VideoQuality.FHD, 7, 1.0f), CaptureSettings(file()).read())
        }
    }

    @Test
    fun `CP16 zoom, quality and total keep each other`() {
        val settings = CaptureSettings(file())
        settings.saveQuality(VideoQuality.FHD)
        settings.saveTotal(5)
        settings.saveZoom(2.4f)
        assertEquals(StoredSettings(VideoQuality.FHD, 5, 2.4f), CaptureSettings(file()).read())
        settings.saveQuality(VideoQuality.UHD)
        settings.saveTotal(6)
        assertEquals(StoredSettings(VideoQuality.UHD, 6, 2.4f), CaptureSettings(file()).read())
    }

    @Test
    fun `S10 an unknown line is ignored on read and dropped by the next write`() {
        file().writeText("quality=FHD\nfoo=bar\nzoom=3.0\n")
        val settings = CaptureSettings(file())
        assertEquals(StoredSettings(VideoQuality.FHD, 0, 3.0f), settings.read())
        settings.saveTotal(1)
        assertFalse(file().readText().contains("foo"))
        assertEquals(StoredSettings(VideoQuality.FHD, 1, 3.0f), settings.read())
    }

    @Test
    fun `CP7 a corrupt file is repaired by the next write and no temporary file is left`() {
        file().writeText("%%%")
        val settings = CaptureSettings(file())
        settings.saveTotal(3)
        assertEquals(StoredSettings(VideoQuality.UHD, 3), settings.read())
        assertFalse(File(folder.root, "settings.txt.tmp").exists())
    }
}
