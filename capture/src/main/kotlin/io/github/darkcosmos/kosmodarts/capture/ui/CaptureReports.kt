package io.github.darkcosmos.kosmodarts.capture.ui

/** What the camera layer reports to the view model; the view model implements it. */
interface CaptureReports {
    /** Rule CP10: the video of [volley] reported its `Start` event. */
    fun onVideoStarted(volley: Int)

    /** The video of [volley] is finalized (written, or failed). */
    fun onVideoFinalized(volley: Int)

    /** Rule CP13: the photo of dart [number] was saved. */
    fun onPhotoSaved(number: Int)

    /** Rule CP13: the photo of dart [number] failed. */
    fun onPhotoFailed(number: Int)

    /** Rule CP9: the camera or the recording failed. */
    fun onCameraError()
}
