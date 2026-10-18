package io.github.darkcosmos.kosmodarts.persistence

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * A [ByteStore] over one file of the app's private storage, written with [AtomicFile] (rules R9, R12): a write goes
 * to a `.new` file that replaces the file only once complete.
 */
class AtomicFileStore(file: File) : ByteStore {

    private val atomicFile = AtomicFile(file)

    /** Older Android versions kept the previous content in a `.bak` file; [AtomicFile.openRead] restores it. */
    private val legacyBackup = File(file.path + ".bak")

    override fun read(maxBytes: Int): ReadResult {
        val base = atomicFile.baseFile
        if (!base.exists() && !legacyBackup.exists()) return ReadResult.Absent
        return try {
            atomicFile.openRead().use { input ->
                if (base.length() > maxBytes) ReadResult.TooLarge else readAtMost(input, maxBytes)
            }
        } catch (ignored: IOException) {
            // The caller deletes the file, as for invalid content (ADR 0002, decision 5).
            ReadResult.Failed
        }
    }

    override fun write(bytes: ByteArray) {
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (e: IOException) {
            atomicFile.failWrite(output)
            throw e
        }
    }

    override fun delete() = atomicFile.delete()
}

/**
 * Reads [input] to its end, but never more than [maxBytes] + 1 bytes, in case the file grew after its length was
 * checked (rule R9).
 */
internal fun readAtMost(input: InputStream, maxBytes: Int): ReadResult {
    val buffer = ByteArray(maxBytes + 1)
    var size = 0
    while (size < buffer.size) {
        val read = input.read(buffer, size, buffer.size - size)
        if (read < 0) break
        size += read
    }
    return if (size > maxBytes) ReadResult.TooLarge else ReadResult.Bytes(buffer.copyOf(size))
}
