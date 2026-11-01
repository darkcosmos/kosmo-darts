package io.github.darkcosmos.kosmodarts.capture.storage

import java.io.IOException

/** Kind of file a session writes; decides the MIME type. */
enum class EntryKind(val mimeType: String) {
    TRUTH("application/json"),
    VIDEO("video/mp4"),
    PHOTO("image/jpeg"),
}

/** A file created in the shared storage. [id] is opaque to callers (a content URI string on the phone). */
data class CaptureEntry(val id: String, val folder: String, val name: String)

/**
 * Where the camera writes a video or a photo: [relativePath] and [displayName] go into the platform entry, which the
 * camera layer (T8) creates through the pending flag.
 */
data class CaptureOutputTarget(
    val folder: String,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
)

/** A storage operation failed; the cause is kept. */
class CaptureStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Root folder of every capture session, relative to the shared storage root. */
const val CAPTURE_ROOT = "Download/KosmoCapture/"

/**
 * Shared storage of the Kosmo Capture sessions (rules CP7 to CP9): `Download/KosmoCapture/<session>/`. Every function
 * can throw [CaptureStorageException].
 */
interface CaptureStorage {
    /** Creates the truth file entry of session [folder]. */
    fun createTruthFile(folder: String): CaptureEntry

    /** Replaces the whole text of the truth file [entry]. */
    fun writeTruth(entry: CaptureEntry, text: String)

    /** Target of the photo [file] of session [folder]. */
    fun photoTarget(folder: String, file: String): CaptureOutputTarget

    /** Target of the video [file] of session [folder]. */
    fun videoTarget(folder: String, file: String): CaptureOutputTarget

    /** Deletes every entry created by this installation, pending ones included; returns how many (rule CP8). */
    fun deleteAllOwnSessions(): Int

    /** Names of the session folders holding entries of this installation (rule CP1 collision check). */
    fun listOwnSessions(): Set<String>
}
