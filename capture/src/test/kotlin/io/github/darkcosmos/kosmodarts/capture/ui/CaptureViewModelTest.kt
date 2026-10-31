package io.github.darkcosmos.kosmodarts.capture.ui

import io.github.darkcosmos.kosmodarts.capture.session.CaptureSetup
import io.github.darkcosmos.kosmodarts.capture.session.FakeClock
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureSettings
import io.github.darkcosmos.kosmodarts.capture.storage.FakeCaptureStorage
import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.EndReason
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeEffects : CaptureEffects {
        val calls = mutableListOf<String>()

        override fun startVideo(volley: Int, target: CaptureOutputTarget) {
            calls += "start $volley ${target.displayName}"
        }

        override fun stopVideo(volley: Int) {
            calls += "stop $volley"
        }

        override fun takePhoto(dart: Int, target: CaptureOutputTarget) {
            calls += "photo $dart ${target.displayName}"
        }
    }

    private val clock = FakeClock()
    private val storage = FakeCaptureStorage()
    private val effects = FakeEffects()
    private val t20 = Dart(20, Multiplier.TRIPLE)
    private val setup = CaptureSetup(
        app = AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0"),
        device = DeviceInfo("Google", "Pixel 7", 37),
        video = VideoSettings(VideoQuality.UHD, Resolution(3840, 2160)),
        photo = PhotoSettings(Resolution(4080, 3072), TimestampSource.REALTIME),
    )

    private fun settings() = CaptureSettings(folder.root.resolve("settings.txt"))

    private fun viewModel(): CaptureViewModel = CaptureViewModel(clock, storage, settings(), effects) { setup }
        .also {
            it.onPermission(CameraPermission.GRANTED)
            it.onCameraReady(true)
        }

    private fun CaptureViewModel.ui(): CaptureUiState = state.value

    private fun CaptureViewModel.running(): CaptureViewModel = also { it.onStart() }

    @Test
    fun `readout shows the volley number and the labels, Undo empties a slot, Next volley restarts`() {
        val vm = viewModel().running()
        vm.onDart(t20)
        vm.onDart(Dart(5))
        vm.onDart(Dart.MISS)
        assertEquals(1, vm.ui().volleyNumber)
        assertEquals(listOf(DartLabel.Text("T20"), DartLabel.Text("5"), DartLabel.Miss), vm.ui().slots)
        vm.onUndo()
        assertEquals(listOf(DartLabel.Text("T20"), DartLabel.Text("5"), null), vm.ui().slots)
        vm.onNextVolley()
        assertEquals(2, vm.ui().volleyNumber)
        assertEquals(listOf(null, null, null), vm.ui().slots)
    }

    @Test
    fun `dart labels cover doubles, outer bull and bull`() {
        assertEquals(DartLabel.Text("D5"), DartLabel.of(Dart.double(5)))
        assertEquals(DartLabel.Text("25"), DartLabel.of(Dart.OUTER_BULL))
        assertEquals(DartLabel.Bull, DartLabel.of(Dart.BULL))
    }

    @Test
    fun `CP3 three darts fill the volley and disable the keypad, a fourth changes nothing`() {
        val vm = viewModel().running()
        repeat(3) { vm.onDart(t20) }
        assertTrue(vm.ui().volleyFull)
        assertFalse(vm.ui().keypadEnabled)
        val before = vm.ui()
        val calls = effects.calls.size
        vm.onDart(t20)
        assertEquals(before, vm.ui())
        assertEquals(calls, effects.calls.size)
    }

    @Test
    fun `CP2 enabled flags in idle`() {
        val ui = viewModel().ui()
        assertEquals(CapturePhase.IDLE, ui.phase)
        assertFalse(ui.keypadEnabled)
        assertFalse(ui.undoEnabled)
        assertFalse(ui.nextVolleyEnabled)
        assertTrue(ui.startStopEnabled)
        assertTrue(ui.qualityEnabled)
        assertTrue(ui.deleteAllEnabled)
        assertFalse(ui.keepScreenOn)
    }

    @Test
    fun `CP2 and CP12 enabled flags while running`() {
        val vm = viewModel().running()
        var ui = vm.ui()
        assertEquals(CapturePhase.RUNNING, ui.phase)
        assertTrue(ui.keypadEnabled)
        assertFalse(ui.undoEnabled)
        assertTrue(ui.nextVolleyEnabled)
        assertTrue(ui.startStopEnabled)
        assertFalse(ui.qualityEnabled)
        assertFalse(ui.deleteAllEnabled)
        assertTrue(ui.keepScreenOn)
        vm.onDart(t20)
        ui = vm.ui()
        assertTrue(ui.undoEnabled)
    }

    @Test
    fun `CP2 Start is disabled without a ready camera or without the permission`() {
        val vm = viewModel()
        vm.onCameraReady(false)
        assertFalse(vm.ui().startStopEnabled)
        vm.onStart()
        assertEquals(CapturePhase.IDLE, vm.ui().phase)
        vm.onCameraReady(true)
        vm.onPermission(CameraPermission.PERMANENTLY_DENIED)
        assertFalse(vm.ui().startStopEnabled)
        vm.onStart()
        assertEquals(CapturePhase.IDLE, vm.ui().phase)
        assertTrue(effects.calls.isEmpty())
    }

    @Test
    fun `CP2 and CP6 saving disables everything until the last video is finalized`() {
        val vm = viewModel().running()
        vm.onStopRequested()
        vm.onStopConfirmed()
        val ui = vm.ui()
        assertEquals(CapturePhase.SAVING, ui.phase)
        assertFalse(ui.startStopEnabled)
        assertFalse(ui.qualityEnabled)
        assertFalse(ui.deleteAllEnabled)
        assertFalse(ui.keepScreenOn)
        vm.onVideoFinalized(1)
        assertEquals(CapturePhase.IDLE, vm.ui().phase)
        assertTrue(vm.ui().startStopEnabled)
    }

    @Test
    fun `CP6 Stop opens the confirmation and Cancel leaves the session untouched`() {
        val vm = viewModel().running()
        vm.onDart(t20)
        vm.onStopRequested()
        assertTrue(vm.ui().stopConfirmVisible)
        vm.onStopCancelled()
        assertFalse(vm.ui().stopConfirmVisible)
        assertEquals(CapturePhase.RUNNING, vm.ui().phase)
        assertFalse(effects.calls.contains("stop 1"))
    }

    @Test
    fun `CP6 confirmed Stop ends with STOP and stops the video`() {
        val vm = viewModel().running()
        vm.onStopRequested()
        vm.onStopConfirmed()
        assertFalse(vm.ui().stopConfirmVisible)
        assertTrue(effects.calls.contains("stop 1"))
        assertTrue(storage.writes.last().contains(EndReason.STOP.name))
    }

    @Test
    fun `CP7 counters follow darts and undo and the total survives a restart`() {
        val vm = viewModel().running()
        repeat(3) { vm.onDart(t20) }
        vm.onUndo()
        assertEquals(2, vm.ui().sessionCount)
        assertEquals(2, vm.ui().totalCount)
        assertEquals(2, settings().read().total)
        vm.onStopRequested()
        vm.onStopConfirmed()
        vm.onVideoFinalized(1)
        assertEquals(2, viewModel().ui().totalCount)
        vm.onStart()
        assertEquals(0, vm.ui().sessionCount)
        assertEquals(2, vm.ui().totalCount)
    }

    @Test
    fun `CP8 Delete all asks, Cancel deletes nothing, confirm deletes and resets the total`() {
        val vm = viewModel().running()
        vm.onDart(t20)
        vm.onStopRequested()
        vm.onStopConfirmed()
        vm.onVideoFinalized(1)
        vm.onDeleteAllRequested()
        assertTrue(vm.ui().deleteAllConfirmVisible)
        vm.onDeleteAllCancelled()
        assertFalse(vm.ui().deleteAllConfirmVisible)
        assertEquals(1, storage.items.size)
        assertEquals(1, vm.ui().totalCount)
        vm.onDeleteAllRequested()
        vm.onDeleteAllConfirmed()
        assertTrue(storage.items.isEmpty())
        assertEquals(0, vm.ui().totalCount)
        assertEquals(0, settings().read().total)
        assertFalse(vm.ui().deleteAllConfirmVisible)
    }

    @Test
    fun `CP8 Delete all cannot be requested during a session`() {
        val vm = viewModel().running()
        vm.onDeleteAllRequested()
        assertFalse(vm.ui().deleteAllConfirmVisible)
    }

    @Test
    fun `CP9 a configuration change keeps the session running and emits no stop`() {
        val vm = viewModel().running()
        vm.onScreenStopped(true)
        assertEquals(CapturePhase.RUNNING, vm.ui().phase)
        assertFalse(effects.calls.any { it.startsWith("stop") })
    }

    @Test
    fun `CP9 really leaving the screen ends the session as INTERRUPTED and stops the video`() {
        val vm = viewModel().running()
        vm.onDart(t20)
        vm.onScreenStopped(false)
        assertTrue(effects.calls.contains("stop 1"))
        assertTrue(storage.writes.last().contains(EndReason.INTERRUPTED.name))
        assertEquals(CapturePhase.SAVING, vm.ui().phase)
    }

    @Test
    fun `CP9 a camera error ends the session as INTERRUPTED with the error state`() {
        val vm = viewModel().running()
        vm.onCameraError()
        vm.onVideoFinalized(1)
        assertEquals(CapturePhase.ERROR, vm.ui().phase)
        assertEquals(CaptureError.CAMERA, vm.ui().error)
        assertTrue(storage.writes.last().contains(EndReason.INTERRUPTED.name))
    }

    @Test
    fun `CP11 quality is persisted outside a session and refused while running`() {
        val vm = viewModel()
        vm.onQuality(VideoQuality.FHD)
        assertEquals(VideoQuality.FHD, vm.ui().quality)
        assertEquals(VideoQuality.FHD, settings().read().quality)
        assertEquals(VideoQuality.FHD, viewModel().ui().quality)
        vm.onStart()
        vm.onQuality(VideoQuality.UHD)
        assertEquals(VideoQuality.FHD, vm.ui().quality)
        assertEquals(VideoQuality.FHD, settings().read().quality)
    }

    @Test
    fun `CP13 photo failure shows the message once, the session keeps running and the dart stays counted`() {
        val vm = viewModel().running()
        vm.onDart(t20)
        vm.onPhotoFailed(1)
        assertEquals(1, vm.ui().photoFailure)
        assertEquals(CapturePhase.RUNNING, vm.ui().phase)
        assertEquals(1, vm.ui().sessionCount)
        vm.onPhotoMessageShown()
        assertNull(vm.ui().photoFailure)
        vm.onDart(t20)
        assertNull(vm.ui().photoFailure)
        assertEquals(2, vm.ui().sessionCount)
    }

    @Test
    fun `a failed truth write ends the session, stops the recording and shows the error`() {
        val vm = viewModel().running()
        storage.failWrites = true
        vm.onDart(t20)
        assertEquals(CaptureError.TRUTH_WRITE, vm.ui().error)
        assertTrue(effects.calls.contains("stop 1"))
        vm.onVideoFinalized(1)
        assertEquals(CapturePhase.ERROR, vm.ui().phase)
        assertFalse(vm.ui().keypadEnabled)
        storage.failWrites = false
        vm.onErrorDismissed()
        assertEquals(CapturePhase.IDLE, vm.ui().phase)
    }

    @Test
    fun `Start asks for video 1 in the new session folder and a dart asks for its photo`() {
        val vm = viewModel().running()
        vm.onDart(t20)
        assertEquals(listOf("start 1 volley-001.mp4", "photo 1 dart-001.jpg"), effects.calls)
    }
}
