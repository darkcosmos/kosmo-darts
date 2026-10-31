package io.github.darkcosmos.kosmodarts.capture.truth

/**
 * One volley of a session and its video (rules CP3 and CP5).
 *
 * [index] counts from 1; [video] is the file name `volley-NNN.mp4` in the session folder. [start] has no video
 * position. [end] is null while the volley runs, so it is null for the last volley of a session killed before Stop
 * (rule CP9). [darts] lists every entry in order, voided ones included.
 */
data class TruthVolley(
    val index: Int,
    val video: String,
    val start: Stamp,
    val end: Stamp?,
    val darts: List<TruthDart>,
) {

    init {
        require(index >= 1) { "Volley index $index is not positive" }
        require(video.isNotBlank()) { "Volley video file name is blank" }
        require(start.videoPositionMs == null) { "Volley start has a video position" }
    }

    /** Whether the volley has no non-voided dart (rule CP5). */
    val empty: Boolean
        get() = darts.none { !it.voided }
}
