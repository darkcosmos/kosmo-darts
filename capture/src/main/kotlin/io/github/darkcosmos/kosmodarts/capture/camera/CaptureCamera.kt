package io.github.darkcosmos.kosmodarts.capture.camera

import io.github.darkcosmos.kosmodarts.camera.zoom.ZoomRange
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource

/** What the camera reports back, always on the main thread. */
interface CaptureCameraListener {
    /** The `Start` event of the video of [volley] arrived (rule CP10). */
    fun onVideoStarted(volley: Int)

    /** The video of [volley] is written, or failed when [failed] is true. */
    fun onVideoFinalized(volley: Int, failed: Boolean)

    /** The photo of dart entry [dart] is saved. */
    fun onPhotoSaved(dart: Int)

    /** The photo of dart entry [dart] could not be taken or saved (rule CP13). */
    fun onPhotoFailed(dart: Int)
}

/**
 * The camera as the capture controller sees it: no Android type, so the controller is tested with a fake. The real
 * implementation also binds the preview to a lifecycle (see `CameraXCaptureCamera`).
 */
interface CaptureCamera {
    /** Receiver of the camera events. */
    var listener: CaptureCameraListener?

    /** Actual resolution of the videos once the camera is bound (rule CP11). */
    val videoResolution: Resolution?

    /** Actual resolution of the photos once the camera is bound (rule CP11). */
    val photoResolution: Resolution?

    /** Clock of the camera sensor timestamps (rule CP10). */
    val timestampSource: TimestampSource

    /** Zoom ratios the bound camera accepts; null when unbound or when the camera gave no zoom state (rule CP16). */
    val zoomRange: ZoomRange?

    /** Current zoom ratio of the bound camera; null when unbound (rule CP16). */
    val zoomRatio: Float?

    /** Applies [ratio] to the bound camera; does nothing when unbound (rule CP16). */
    fun setZoomRatio(ratio: Float)

    /** Starts recording the video of [volley] into [target], without audio. */
    fun startVideo(volley: Int, target: CaptureOutputTarget)

    /** Stops the video of [volley]; [CaptureCameraListener.onVideoFinalized] follows, also when nothing was recording. */
    fun stopVideo(volley: Int)

    /** Takes one full resolution photo of dart entry [dart] into [target]. */
    fun takePhoto(dart: Int, target: CaptureOutputTarget)
}
