package io.github.darkcosmos.kosmodarts.capture.camera

import io.github.darkcosmos.kosmodarts.capture.session.CaptureSetup
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget
import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.ZoomRecord
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureEffects
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureReports

/**
 * Turns the view model effects into camera calls and the camera events into view model reports (rules CP1, CP3, CP5,
 * CP9, CP10, CP13). It never decides a session rule: a failure is only reported, the view model ends the session.
 * [attach] must be called once the view model exists.
 */
class CaptureController(private val camera: CaptureCamera) : CaptureEffects, CaptureCameraListener {
    private var reports: CaptureReports? = null

    init {
        camera.listener = this
    }

    /** Connects the view model that receives the camera events. */
    fun attach(reports: CaptureReports) {
        this.reports = reports
    }

    /**
     * The session setup with the actual resolutions and the timestamp source (rule CP11) and the zoom ratio the camera
     * reports (rule CP17); `requestedRatio` stays null here, the view model fills it.
     */
    fun setup(app: AppInfo, device: DeviceInfo, quality: VideoQuality): CaptureSetup = CaptureSetup(
        app = app,
        device = device,
        video = VideoSettings(quality, checkNotNull(camera.videoResolution) { "Camera is not bound" }),
        photo = PhotoSettings(
            checkNotNull(camera.photoResolution) { "Camera is not bound" },
            camera.timestampSource,
        ),
        zoom = ZoomRecord(requestedRatio = null, ratio = camera.zoomRatio),
    )

    override fun startVideo(volley: Int, target: CaptureOutputTarget) {
        try {
            camera.startVideo(volley, target)
        } catch (_: RuntimeException) {
            // The recording could not even begin: end the session (CP9), then let the view model leave Saving.
            reports?.onCameraError()
            reports?.onVideoFinalized(volley)
        }
    }

    override fun stopVideo(volley: Int) = camera.stopVideo(volley)

    override fun takePhoto(dart: Int, target: CaptureOutputTarget) {
        try {
            camera.takePhoto(dart, target)
        } catch (_: RuntimeException) {
            reports?.onPhotoFailed(dart)
        }
    }

    override fun setZoomRatio(ratio: Float) {
        try {
            camera.setZoomRatio(ratio)
        } catch (_: RuntimeException) {
            // A zoom failure is not a camera error (CP9): no report, the session goes on.
        }
    }

    override fun onVideoStarted(volley: Int) {
        reports?.onVideoStarted(volley)
    }

    override fun onVideoFinalized(volley: Int, failed: Boolean) {
        // A recording error ends the session first, which asks to stop (and so finalize) the other open video.
        if (failed) reports?.onCameraError()
        reports?.onVideoFinalized(volley)
    }

    override fun onPhotoSaved(dart: Int) {
        reports?.onPhotoSaved(dart)
    }

    override fun onPhotoFailed(dart: Int) {
        reports?.onPhotoFailed(dart)
    }
}
