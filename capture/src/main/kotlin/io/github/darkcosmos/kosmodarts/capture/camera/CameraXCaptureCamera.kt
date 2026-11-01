package io.github.darkcosmos.kosmodarts.capture.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import io.github.darkcosmos.kosmodarts.camera.zoom.ZoomRange
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget
import io.github.darkcosmos.kosmodarts.capture.storage.MediaStoreCaptureStorage
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality

/**
 * CameraX implementation of [CaptureCamera] (rules CP1, CP3, CP5, CP10, CP11, CP13): a preview, one video use case
 * (UHD or FHD with lower fallback, no audio) and one photo use case at the highest resolution, on the back camera.
 * Files are created by CameraX in the shared storage from the values of [storage] (CameraX sets the pending flag).
 * Every call and every callback runs on the main thread.
 */
class CameraXCaptureCamera(
    private val context: Context,
    private val storage: MediaStoreCaptureStorage,
) : CaptureCamera {
    override var listener: CaptureCameraListener? = null
    override var videoResolution: Resolution? = null
        private set
    override var photoResolution: Resolution? = null
        private set
    override var timestampSource: TimestampSource = TimestampSource.UNKNOWN
        private set

    private var camera: Camera? = null

    override val zoomRange: ZoomRange?
        get() = camera?.cameraInfo?.zoomState?.value?.let { ZoomRange.of(it.minZoomRatio, it.maxZoomRatio) }

    override val zoomRatio: Float?
        get() = camera?.cameraInfo?.zoomState?.value?.zoomRatio

    override fun setZoomRatio(ratio: Float) {
        // The returned future is ignored on purpose: a refused ratio leaves the zoom as it was (CP16).
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    private val executor =context.mainExecutor
    private val recordings = mutableMapOf<Int, Recording>()
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    /**
     * Binds the preview and both use cases to [owner] and calls [onBound] with the outcome on the main thread. Any
     * earlier binding is replaced (a quality change). Resolutions are known once [onBound] got true.
     */
    fun bind(owner: LifecycleOwner, view: PreviewView, quality: VideoQuality, rotation: Int, onBound: (Boolean) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val bound = try {
                    bindUseCases(future.get(), owner, view, quality, rotation)
                } catch (_: Exception) {
                    // Provider failure, missing camera or an unsupported use case combination: the screen stays not ready.
                    false
                }
                onBound(bound)
            },
            executor,
        )
    }

    /** Display rotation changed: the use cases follow without rebinding (rule CP9). */
    fun setRotation(rotation: Int) {
        preview?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindUseCases(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        view: PreviewView,
        quality: VideoQuality,
        rotation: Int,
    ): Boolean {
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val qualities = when (quality) {
            VideoQuality.UHD -> listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            VideoQuality.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(qualities, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            )
            .build()
        val newVideo = VideoCapture.Builder(recorder).setTargetRotation(rotation).build()
        val newPhoto = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build(),
            )
            .setTargetRotation(rotation)
            .build()
        val newPreview = Preview.Builder().setTargetRotation(rotation).build()
            .also { it.surfaceProvider = view.surfaceProvider }

        provider.unbindAll()
        val camera = provider.bindToLifecycle(owner, selector, newPreview, newPhoto, newVideo)
        this.camera = camera
        val info = camera.cameraInfo

        val size = qualities.firstNotNullOfOrNull { QualitySelector.getResolution(info, it) }
        val photoSize = newPhoto.resolutionInfo?.resolution
        if (size == null || photoSize == null) return false
        videoResolution = Resolution(size.width, size.height)
        photoResolution = Resolution(photoSize.width, photoSize.height)
        val source = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        timestampSource =
            if (source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) TimestampSource.REALTIME
            else TimestampSource.UNKNOWN
        preview = newPreview
        imageCapture = newPhoto
        videoCapture = newVideo
        return true
    }

    override fun startVideo(volley: Int, target: CaptureOutputTarget) {
        val capture = checkNotNull(videoCapture) { "Camera is not bound" }
        val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            .setContentValues(storage.contentValues(target))
            .build()
        // No withAudioEnabled(): the videos have no sound track.
        recordings[volley] = capture.output.prepareRecording(context, options).start(executor) { event ->
            onEvent(volley, event)
        }
    }

    private fun onEvent(volley: Int, event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> listener?.onVideoStarted(volley)
            is VideoRecordEvent.Finalize -> {
                recordings.remove(volley)
                listener?.onVideoFinalized(volley, event.hasError())
            }
            else -> Unit
        }
    }

    override fun stopVideo(volley: Int) {
        val recording = recordings[volley]
        if (recording == null) listener?.onVideoFinalized(volley, false) else recording.stop()
    }

    override fun takePhoto(dart: Int, target: CaptureOutputTarget) {
        val capture = imageCapture
        if (capture == null) {
            listener?.onPhotoFailed(dart)
            return
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            storage.contentValues(target),
        ).build()
        capture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    listener?.onPhotoSaved(dart)
                }

                override fun onError(exception: ImageCaptureException) {
                    listener?.onPhotoFailed(dart)
                }
            },
        )
    }
}
