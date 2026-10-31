package io.github.darkcosmos.kosmodarts.capture.ui

import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget

/**
 * What the view model asks the camera layer to do. The camera reports back with the matching view model functions
 * (`onVideoStarted`, `onVideoFinalized`, `onPhotoSaved`, `onPhotoFailed`, `onCameraError`).
 */
interface CaptureEffects {
    /** Start recording the video of [volley] into [target]. */
    fun startVideo(volley: Int, target: CaptureOutputTarget)

    /** Stop and finalize the video of [volley]. */
    fun stopVideo(volley: Int)

    /** Take the photo of dart entry [dart] into [target]. */
    fun takePhoto(dart: Int, target: CaptureOutputTarget)
}
