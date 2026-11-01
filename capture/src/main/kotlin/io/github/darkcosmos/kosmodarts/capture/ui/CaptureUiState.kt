package io.github.darkcosmos.kosmodarts.capture.ui

import io.github.darkcosmos.kosmodarts.camera.zoom.ZoomSelection
import io.github.darkcosmos.kosmodarts.capture.session.DARTS_PER_VOLLEY
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality

/** State of the camera permission, as reported by the screen. */
enum class CameraPermission { GRANTED, ASK, PERMANENTLY_DENIED }

/** What the screen is doing (rules CP2 and CP6). */
enum class CapturePhase { IDLE, RUNNING, SAVING, ERROR }

/** Why the screen is in [CapturePhase.ERROR]; the message says that files up to that point are kept. */
enum class CaptureError { START, TRUTH_WRITE, CAMERA, DELETE_ALL }

/** Everything the capture screen shows, computed by the view model. */
data class CaptureUiState(
    val permission: CameraPermission = CameraPermission.ASK,
    val cameraReady: Boolean = false,
    val phase: CapturePhase = CapturePhase.IDLE,
    val error: CaptureError? = null,
    val volleyNumber: Int = 1,
    /** Always [DARTS_PER_VOLLEY] slots, null when empty. */
    val slots: List<DartLabel?> = List(DARTS_PER_VOLLEY) { null },
    val volleyFull: Boolean = false,
    val sessionCount: Int = 0,
    val totalCount: Int = 0,
    val quality: VideoQuality = VideoQuality.UHD,
    val stopConfirmVisible: Boolean = false,
    val deleteAllConfirmVisible: Boolean = false,
    /** Dart number whose photo failed, until the screen reports the message as shown (rule CP13). */
    val photoFailure: Int? = null,
    val zoom: ZoomSelection = ZoomSelection(),
) {
    private val running: Boolean get() = phase == CapturePhase.RUNNING
    private val idle: Boolean get() = phase == CapturePhase.IDLE || phase == CapturePhase.ERROR

    /** Rule CP3: the keypad is off outside a session and when the volley is full. */
    val keypadEnabled: Boolean get() = running && !volleyFull

    /** Rule CP4: Undo needs a non-voided dart in the current volley. */
    val undoEnabled: Boolean get() = running && slots.any { it != null }

    /** Rule CP5. */
    val nextVolleyEnabled: Boolean get() = running

    /** Rule CP2: Start needs the camera bound and a granted permission; Stop is always possible while running. */
    val startStopEnabled: Boolean
        get() = running || (idle && cameraReady && permission == CameraPermission.GRANTED)

    /** Rule CP2: quality and Delete all only outside a session, and not while saving. */
    val qualityEnabled: Boolean get() = idle

    val deleteAllEnabled: Boolean get() = idle

    /** Rule CP15: the zoom changes only outside a session, with a ready camera, a granted permission and a range. */
    val zoomEnabled: Boolean
        get() = idle && cameraReady && permission == CameraPermission.GRANTED && zoom.adjustable

    /** Rule CP14. */
    val zoomOutEnabled: Boolean get() = zoomEnabled && zoom.canStepDown

    val zoomInEnabled: Boolean get() = zoomEnabled && zoom.canStepUp

    /** Rule CP12. */
    val keepScreenOn: Boolean get() = running
}
