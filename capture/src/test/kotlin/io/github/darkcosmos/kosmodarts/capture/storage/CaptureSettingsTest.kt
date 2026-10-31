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
    fun `CP7 a corrupt file is repaired by the next write and no temporary file is left`() {
        file().writeText("%%%")
        val settings = CaptureSettings(file())
        settings.saveTotal(3)
        assertEquals(StoredSettings(VideoQuality.UHD, 3), settings.read())
        assertFalse(File(folder.root, "settings.txt.tmp").exists())
    }
}
