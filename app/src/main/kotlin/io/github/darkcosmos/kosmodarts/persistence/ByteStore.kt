package io.github.darkcosmos.kosmodarts.persistence

import java.io.IOException

/** One stored file, read and written as a whole. Implementations need not be thread-safe: one thread uses them. */
interface ByteStore {

    /** Reads the whole content, without ever reading more than [maxBytes] + 1 bytes (rule R9). */
    fun read(maxBytes: Int): ReadResult

    /**
     * Replaces the content atomically: after a crash, the file holds either the previous or the new content (R12).
     *
     * @throws IOException if the content could not be written; the previous content is then kept.
     */
    @Throws(IOException::class)
    fun write(bytes: ByteArray)

    /** Removes the content, if any. */
    fun delete()
}

/** Outcome of [ByteStore.read]. */
sealed interface ReadResult {
    /** No file. */
    data object Absent : ReadResult

    /** The file is larger than the limit; it was not read. */
    data object TooLarge : ReadResult

    /** The file exists but storage failed while reading it. It is then deleted like invalid content (ADR 0002). */
    data object Failed : ReadResult

    class Bytes(val bytes: ByteArray) : ReadResult
}
