package io.github.darkcosmos.kosmodarts.capture.storage

import io.github.darkcosmos.kosmodarts.camera.zoom.DEFAULT_ZOOM_RATIO
import io.github.darkcosmos.kosmodarts.camera.zoom.ZoomSettingFormat
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Values kept between launches: the video [quality] (rule CP11), the persisted [total] of darts (rule CP7) and the
 * camera [zoom] ratio (rule CP16).
 */
data class StoredSettings(
    val quality: VideoQuality = VideoQuality.UHD,
    val total: Int = 0,
    val zoom: Float = DEFAULT_ZOOM_RATIO,
)

/**
 * Private settings in one small text file ([file], under the app's files directory), `key=value` per line. Writes go
 * to a temporary file that atomically replaces the real one. A missing or corrupt file reads as the defaults.
 */
class CaptureSettings(private val file: File) {
    private val lock = Any()

    /** Current values; defaults for a missing or unreadable file, and for any line that cannot be understood. */
    fun read(): StoredSettings = synchronized(lock) { readLocked() }

    /** Keeps [quality] and the stored total. */
    fun saveQuality(quality: VideoQuality) = update { it.copy(quality = quality) }

    /** Keeps [total] and the stored quality. A negative total is stored as 0. */
    fun saveTotal(total: Int) = update { it.copy(total = total.coerceAtLeast(0)) }

    /** Keeps the stored quality and total. */
    fun saveZoom(ratio: Float) = update { it.copy(zoom = ratio) }

    private fun update(change: (StoredSettings) -> StoredSettings) = synchronized(lock) {
        val next = change(readLocked())
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText("quality=${next.quality.name}\ntotal=${next.total}\nzoom=${ZoomSettingFormat.format(next.zoom)}\n")
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temp.delete()
            throw CaptureStorageException("Cannot write ${file.name}", e)
        }
    }

    private fun readLocked(): StoredSettings {
        val lines = try {
            file.readLines()
        } catch (_: IOException) {
            return StoredSettings()
        }
        val values = lines.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key.trim() to value.trim() }
        val quality = VideoQuality.entries.firstOrNull { it.name == values["quality"] } ?: VideoQuality.UHD
        val total = values["total"]?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        return StoredSettings(quality, total, ZoomSettingFormat.parse(values["zoom"]))
    }
}
