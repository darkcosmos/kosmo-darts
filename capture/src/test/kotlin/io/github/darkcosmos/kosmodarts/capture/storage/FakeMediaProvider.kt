package io.github.darkcosmos.kosmodarts.capture.storage

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

/**
 * Minimal stand-in for the `media` provider under Robolectric: rows with display name and relative path, a LIKE prefix
 * filter, a pending flag that hides rows unless the query includes them, and files opened with truncation.
 * Rows marked foreign (see [addForeignRow]) stand for files of other apps, which the real provider hides from us.
 */
internal class FakeMediaProvider : ContentProvider() {
    class Row(val id: Long, val name: String, val path: String, val pending: Boolean, val own: Boolean, val file: File)

    val rows = mutableListOf<Row>()
    var lastQueryIncludedPending = false
    private var nextId = 1L

    fun addForeignRow(name: String, path: String) = addRow(name, path, pending = false, own = false)

    fun addPendingOwnRow(name: String, path: String) = addRow(name, path, pending = true, own = true)

    private fun addRow(name: String, path: String, pending: Boolean, own: Boolean): Row {
        val file = File.createTempFile("media", ".bin").also { it.deleteOnExit() }
        return Row(nextId++, name, path, pending, own, file).also { rows += it }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val data = values ?: return null
        val row = addRow(
            data.getAsString(MediaStore.MediaColumns.DISPLAY_NAME),
            data.getAsString(MediaStore.MediaColumns.RELATIVE_PATH),
            pending = false,
            own = true,
        )
        return ContentUris.withAppendedId(uri, row.id)
    }

    override fun query(uri: Uri, projection: Array<String>?, queryArgs: Bundle?, signal: CancellationSignal?): Cursor {
        val args = queryArgs ?: Bundle()
        val prefix = args.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)?.firstOrNull().orEmpty()
            .removeSuffix("%")
        lastQueryIncludedPending = args.getInt(MediaStore.QUERY_ARG_MATCH_PENDING) == MediaStore.MATCH_INCLUDE
        val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH))
        rows.filter { it.own && it.path.startsWith(prefix) && (!it.pending || lastQueryIncludedPending) }
            .forEach { cursor.addRow(arrayOf<Any>(it.id, it.path)) }
        return cursor
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = query(uri, projection, Bundle(), null)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val row = rows.firstOrNull { it.id == ContentUris.parseId(uri) && it.own } ?: return 0
        rows.remove(row)
        row.file.delete()
        return 1
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = rows.first { it.id == ContentUris.parseId(uri) }
        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
    }
}
