package io.github.darkcosmos.kosmodarts.capture.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import io.github.darkcosmos.kosmodarts.capture.session.CaptureNaming
import java.io.IOException

private val FOLDER_PATTERN = Regex("""\d{8}-\d{6}(-\d+)?""")
private val FILE_PATTERN = Regex("""[a-z0-9][a-z0-9.\-]*""")

/**
 * [CaptureStorage] on `MediaStore.Downloads`: no storage permission, the app only sees and changes the entries it
 * created itself. Listing and deleting query only entries whose `RELATIVE_PATH` starts with [CAPTURE_ROOT], pending
 * ones included, and delete by the returned URIs only, never by a path built from input (rule CP8).
 */
class MediaStoreCaptureStorage(private val resolver: ContentResolver) : CaptureStorage {
    private val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    override fun createTruthFile(folder: String): CaptureEntry {
        val target = target(folder, CaptureNaming.TRUTH_FILE, EntryKind.TRUTH)
        val uri = resolver.insert(collection, contentValues(target))
            ?: throw CaptureStorageException("Cannot create ${target.displayName}")
        return CaptureEntry(uri.toString(), folder, target.displayName)
    }

    override fun writeTruth(entry: CaptureEntry, text: String) {
        try {
            val output = resolver.openOutputStream(Uri.parse(entry.id), "wt")
                ?: throw CaptureStorageException("Cannot open ${entry.name}")
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            throw CaptureStorageException("Cannot write ${entry.name}", e)
        } catch (e: SecurityException) {
            throw CaptureStorageException("Not allowed to write ${entry.name}", e)
        } catch (e: IllegalArgumentException) {
            throw CaptureStorageException("Bad entry ${entry.name}", e)
        }
    }

    override fun photoTarget(folder: String, file: String): CaptureOutputTarget = target(folder, file, EntryKind.PHOTO)

    override fun videoTarget(folder: String, file: String): CaptureOutputTarget = target(folder, file, EntryKind.VIDEO)

    /** Values to insert for [target]; the camera layer adds the pending flag for its own insert. */
    fun contentValues(target: CaptureOutputTarget): ContentValues =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, target.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
        }

    override fun deleteAllOwnSessions(): Int =
        queryOwn().sumOf { resolver.delete(it.uri, null, null) }

    override fun listOwnSessions(): Set<String> = queryOwn().mapTo(linkedSetOf()) { it.folder }

    private fun target(folder: String, file: String, kind: EntryKind): CaptureOutputTarget {
        require(FOLDER_PATTERN.matches(folder)) { "Bad session folder name" }
        require(FILE_PATTERN.matches(file)) { "Bad file name" }
        return CaptureOutputTarget(folder, file, "$CAPTURE_ROOT$folder/", kind.mimeType)
    }

    private fun queryOwn(): List<OwnEntry> {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("$CAPTURE_ROOT%"))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val entries = mutableListOf<OwnEntry>()
        try {
            resolver.query(collection, projection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val path = cursor.getString(1).orEmpty()
                    // Defence in depth: the LIKE filter is not trusted alone.
                    if (!path.startsWith(CAPTURE_ROOT)) continue
                    val folder = path.removePrefix(CAPTURE_ROOT).substringBefore('/')
                    entries += OwnEntry(Uri.withAppendedPath(collection, cursor.getLong(0).toString()), folder)
                }
            }
        } catch (e: SecurityException) {
            throw CaptureStorageException("Not allowed to read the capture entries", e)
        }
        return entries
    }

    private class OwnEntry(val uri: Uri, val folder: String)
}
