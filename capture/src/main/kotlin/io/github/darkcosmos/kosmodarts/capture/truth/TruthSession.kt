package io.github.darkcosmos.kosmodarts.capture.truth

/**
 * Content of one `truth.json` file: a capture session as recorded so far (rule CP9 rewrites it after every event).
 * The format is described in `docs/camera/capture-format.md`; [TruthJson] reads and writes it.
 *
 * [session] is the session folder name (rule CP1). [endedAt] and [endReason] are both null while the session runs,
 * so both are null in the file of a session killed before Stop; [endedAt] has no video position. [volleys] is never
 * empty (Start creates volley 1), indexed 1, 2, 3... in order; every volley but the last has an end, and the last
 * has one too once the session ended (rule CP6). Dart numbers run 1, 2, 3... across the volleys, in order.
 */
data class TruthSession(
    val session: String,
    val app: AppInfo,
    val device: DeviceInfo,
    val startedAt: Stamp,
    val endedAt: Stamp?,
    val endReason: EndReason?,
    val video: VideoSettings,
    val photo: PhotoSettings,
    val zoom: ZoomRecord,
    val volleys: List<TruthVolley>,
) {

    init {
        require(session.isNotBlank()) { "Session name is blank" }
        require(startedAt.videoPositionMs == null) { "Session start has a video position" }
        require(endedAt?.videoPositionMs == null) { "Session end has a video position" }
        require((endedAt == null) == (endReason == null)) { "endedAt and endReason must be both set or both null" }
        require(volleys.isNotEmpty()) { "A session has at least one volley" }
        require(volleys.withIndex().all { (position, volley) -> volley.index == position + 1 }) {
            "Volley indexes must be 1, 2, 3... in order"
        }
        require(volleys.dropLast(1).all { it.end != null }) { "Only the last volley may have no end" }
        require(endedAt == null || volleys.last().end != null) { "The last volley of an ended session has no end" }
        require(volleys.flatMap { it.darts }.withIndex().all { (position, dart) -> dart.number == position + 1 }) {
            "Dart numbers must be 1, 2, 3... in order across the volleys"
        }
    }
}

/** The capture app that wrote the file: its application [id] and its version name. */
data class AppInfo(val id: String, val version: String)

/** The phone that recorded the session: `Build.MANUFACTURER`, `Build.MODEL` and `Build.VERSION.SDK_INT`. */
data class DeviceInfo(val manufacturer: String, val model: String, val sdk: Int)

/** Why a session ended: Stop confirmed (rule CP6) or the screen really left or a camera error (rule CP9). */
enum class EndReason { STOP, INTERRUPTED }

/** Video quality asked for by the quality setting (rule CP11): 4K (UHD) or 1080p (FHD). */
enum class VideoQuality { UHD, FHD }

/** Clock of the camera sensor timestamps (`SENSOR_INFO_TIMESTAMP_SOURCE`): unknown or `elapsedRealtimeNanos`. */
enum class TimestampSource { UNKNOWN, REALTIME }

/** The [requestedQuality] and the actual [resolution] of the volley videos (rule CP11). */
data class VideoSettings(val requestedQuality: VideoQuality, val resolution: Resolution)

/** The actual [resolution] of the photos (rule CP11) and the camera's sensor [timestampSource]. */
data class PhotoSettings(val resolution: Resolution, val timestampSource: TimestampSource)

/**
 * Zoom of the session, recorded once at Start (rule CP17): [requestedRatio] is the ratio the app last asked the camera
 * for (null when it asked none) and [ratio] the ratio the camera reported (null when it reported none). Each value is
 * null or finite and greater than 0; no upper bound is enforced.
 */
data class ZoomRecord(val requestedRatio: Float?, val ratio: Float?) {

    init {
        require(requestedRatio.isValidRatio()) { "requestedRatio must be null or finite and greater than 0" }
        require(ratio.isValidRatio()) { "ratio must be null or finite and greater than 0" }
    }

    private fun Float?.isValidRatio(): Boolean = this == null || (isFinite() && this > 0f)
}
