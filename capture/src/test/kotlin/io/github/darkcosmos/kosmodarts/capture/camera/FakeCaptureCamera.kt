package io.github.darkcosmos.kosmodarts.capture.camera

import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource

/** Camera double: records the calls, answers like the real one unless told otherwise. */
internal class FakeCaptureCamera : CaptureCamera {
    override var listener: CaptureCameraListener? = null
    override var videoResolution: Resolution? = Resolution(3840, 2160)
    override var photoResolution: Resolution? = Resolution(4080, 3072)
    override var timestampSource: TimestampSource = TimestampSource.REALTIME

    val calls = mutableListOf<String>()
    var autoStart = true
    var failPhotos = mutableSetOf<Int>()
    private val open = mutableSetOf<Int>()

    override fun startVideo(volley: Int, target: CaptureOutputTarget) {
        calls += "start ${target.displayName}"
        open += volley
        if (autoStart) listener?.onVideoStarted(volley)
    }

    override fun stopVideo(volley: Int) {
        calls += "stop $volley"
        open -= volley
        listener?.onVideoFinalized(volley, false)
    }

    override fun takePhoto(dart: Int, target: CaptureOutputTarget) {
        calls += "photo ${target.displayName}"
        if (dart in failPhotos) listener?.onPhotoFailed(dart) else listener?.onPhotoSaved(dart)
    }

    /** The `Start` event arrives (when [autoStart] is off). */
    fun emitStart(volley: Int) = listener?.onVideoStarted(volley)

    /** The recording of [volley] fails by itself. */
    fun failRecording(volley: Int) {
        calls += "error $volley"
        open -= volley
        listener?.onVideoFinalized(volley, true)
    }

    fun isRecording(volley: Int): Boolean = volley in open
}
