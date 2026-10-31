package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.lifecycle.ViewModel
import io.github.darkcosmos.kosmodarts.capture.session.CaptureClock
import io.github.darkcosmos.kosmodarts.capture.session.CaptureCounters
import io.github.darkcosmos.kosmodarts.capture.session.CaptureEffect
import io.github.darkcosmos.kosmodarts.capture.session.CaptureOutcome
import io.github.darkcosmos.kosmodarts.capture.session.CaptureSession
import io.github.darkcosmos.kosmodarts.capture.session.CaptureSetup
import io.github.darkcosmos.kosmodarts.capture.session.DARTS_PER_VOLLEY
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureEntry
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureSettings
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureStorage
import io.github.darkcosmos.kosmodarts.capture.storage.TruthWriter
import io.github.darkcosmos.kosmodarts.capture.truth.Gravity
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder of the capture screen (rules CP2 to CP13). It owns the [CaptureSession] of the running session, writes
 * the truth file after every event, sends camera work to [effects] and exposes everything the screen shows as
 * [state]. Calls come from the main thread only; [setupProvider] gives the session setup (with the actual resolutions)
 * at Start.
 */
class CaptureViewModel(
    private val clock: CaptureClock,
    private val storage: CaptureStorage,
    private val settings: CaptureSettings,
    private val effects: CaptureEffects,
    private val setupProvider: () -> CaptureSetup,
) : ViewModel(), CaptureReports {
    private val writer = TruthWriter(storage, settings)
    private var session = CaptureSession()
    private var entry: CaptureEntry? = null
    private var counters: CaptureCounters
    private var permission = CameraPermission.ASK
    private var cameraReady = false
    private var error: CaptureError? = null
    private var photoFailure: Int? = null
    private var stopConfirmVisible = false
    private var deleteAllConfirmVisible = false

    /** Videos asked to stop and not yet reported finalized: the screen shows "Saving..." while any is left. */
    private val finalizing = mutableSetOf<Int>()

    private var quality: VideoQuality
    private val mutableState: MutableStateFlow<CaptureUiState>

    /** What the screen shows. */
    val state: StateFlow<CaptureUiState>

    init {
        val stored = settings.read()
        quality = stored.quality
        counters = CaptureCounters(total = stored.total)
        mutableState = MutableStateFlow(CaptureUiState(quality = quality, totalCount = stored.total))
        state = mutableState.asStateFlow()
    }

    /** Camera permission changed. */
    fun onPermission(value: CameraPermission) = update { permission = value }

    /** The camera is bound (or no longer is). */
    fun onCameraReady(ready: Boolean) = update { cameraReady = ready }

    /** Rule CP1: Start session. */
    fun onStart() = update {
        if (!isIdle() || !cameraReady || permission != CameraPermission.GRANTED) return@update
        error = null
        counters = counters.forNewSession()
        val outcome = session.start(clock, setupProvider(), ownFolders())
        val folder = outcome.session.truth?.session ?: return@update
        val created = try {
            storage.createTruthFile(folder)
        } catch (_: IOException) {
            error = CaptureError.START
            return@update
        }
        entry = created
        session = outcome.session
        persist()
        dispatch(outcome.effects)
    }

    /** Rule CP3: a dart from the keypad. [gravity] is the phone attitude when the sensor gave one. */
    fun onDart(dart: Dart, gravity: Gravity? = null) = update { apply(session.dart(clock, dart, gravity)) }

    /** Rule CP4. */
    fun onUndo() = update { apply(session.undo(clock)) }

    /** Rule CP5. */
    fun onNextVolley() = update { apply(session.nextVolley(clock)) }

    /** Rule CP6: Stop opens the confirmation. */
    fun onStopRequested() = update { stopConfirmVisible = session.running }

    /** Rule CP6: Cancel leaves the session untouched. */
    fun onStopCancelled() = update { stopConfirmVisible = false }

    /** Rule CP6: confirmed Stop. */
    fun onStopConfirmed() = update {
        stopConfirmVisible = false
        apply(session.stop(clock))
    }

    /** Rule CP8: Delete all opens the confirmation (outside a session only). */
    fun onDeleteAllRequested() = update { deleteAllConfirmVisible = isIdle() }

    /** Rule CP8: Cancel deletes nothing. */
    fun onDeleteAllCancelled() = update { deleteAllConfirmVisible = false }

    /** Rule CP8: confirmed Delete all: own sessions are deleted and the total resets. */
    fun onDeleteAllConfirmed() = update {
        deleteAllConfirmVisible = false
        if (!isIdle()) return@update
        try {
            storage.deleteAllOwnSessions()
            counters = counters.cleared()
            settings.saveTotal(counters.total)
            error = null
        } catch (_: IOException) {
            error = CaptureError.DELETE_ALL
        }
    }

    /** Rule CP11: the quality toggle, refused during a session and while saving. */
    fun onQuality(value: VideoQuality) = update {
        if (!isIdle()) return@update
        try {
            settings.saveQuality(value)
            quality = value
        } catch (_: IOException) {
            error = CaptureError.START
        }
    }

    /** The error message was dismissed. */
    fun onErrorDismissed() = update { error = null }

    /** Rule CP13: the photo message was shown. */
    fun onPhotoMessageShown() = update { photoFailure = null }

    /** Rule CP10: the video of [volley] reported its `Start` event. */
    override fun onVideoStarted(volley: Int) = update { apply(session.videoStarted(clock, volley)) }

    /** The video of [volley] is finalized (written, or failed): the saving state ends with the last one. */
    override fun onVideoFinalized(volley: Int) = update { finalizing -= volley }

    /** Rule CP13: the photo of dart [number] was saved. */
    override fun onPhotoSaved(number: Int) = update { apply(session.photoSaved(clock, number)) }

    /** Rule CP13: the photo of dart [number] failed; the dart stays counted and the session goes on. */
    override fun onPhotoFailed(number: Int) = update { apply(session.photoFailed(number)) }

    /** Rule CP9: the camera or the recording failed; the session ends without confirmation. */
    override fun onCameraError() = update {
        if (session.running) {
            error = CaptureError.CAMERA
            apply(session.interrupt(clock))
        }
    }

    /** Rule CP9: the activity stopped; a configuration change never interrupts the session. */
    fun onScreenStopped(changingConfigurations: Boolean) = update {
        if (!changingConfigurations && session.running) {
            stopConfirmVisible = false
            apply(session.interrupt(clock))
        }
    }

    private fun isIdle(): Boolean = !session.running && finalizing.isEmpty()

    private fun ownFolders(): Set<String> = try {
        storage.listOwnSessions()
    } catch (_: IOException) {
        emptySet()
    }

    /** Takes the outcome of a session event: new session, counters, truth file, then camera effects. */
    private fun apply(outcome: CaptureOutcome) {
        session = outcome.session
        outcome.failedPhoto?.let { photoFailure = it }
        persist()
        dispatch(outcome.effects)
    }

    /** Rules CP7 and CP9: counters follow the truth; the truth file is rewritten after every event. */
    private fun persist() {
        val truth = session.truth ?: return
        val target = entry ?: return
        counters = counters.after(truth)
        if (writer.write(target, truth, counters).succeeded) return
        error = CaptureError.TRUTH_WRITE
        if (session.running) {
            session = session.interrupt(clock).also { dispatch(it.effects) }.session
            session.truth?.let { runCatching { writer.write(target, it, counters) } }
        }
    }

    private fun dispatch(list: List<CaptureEffect>) {
        val folder = session.truth?.session ?: return
        list.forEach { effect ->
            when (effect) {
                is CaptureEffect.StartVideo -> effects.startVideo(effect.volley, storage.videoTarget(folder, effect.file))
                is CaptureEffect.StopVideo -> {
                    finalizing += effect.volley
                    effects.stopVideo(effect.volley)
                }
                is CaptureEffect.TakePhoto -> effects.takePhoto(effect.dart, storage.photoTarget(folder, effect.file))
            }
        }
    }

    private fun update(change: () -> Unit) {
        change()
        mutableState.value = render()
    }

    private fun render(): CaptureUiState {
        val truth = session.truth
        val running = session.running
        val volley = truth?.volleys?.lastOrNull()
        val darts = if (running) volley?.darts.orEmpty().filter { !it.voided } else emptyList()
        val labels = darts.map { DartLabel.of(it.dart) }
        return CaptureUiState(
            permission = permission,
            cameraReady = cameraReady,
            phase = when {
                running -> CapturePhase.RUNNING
                finalizing.isNotEmpty() -> CapturePhase.SAVING
                error != null -> CapturePhase.ERROR
                else -> CapturePhase.IDLE
            },
            error = error,
            volleyNumber = if (running) volley?.index ?: 1 else 1,
            slots = List(DARTS_PER_VOLLEY) { labels.getOrNull(it) },
            volleyFull = session.volleyFull,
            sessionCount = counters.session,
            totalCount = counters.total,
            quality = quality,
            stopConfirmVisible = stopConfirmVisible && running,
            deleteAllConfirmVisible = deleteAllConfirmVisible,
            photoFailure = photoFailure,
        )
    }
}
