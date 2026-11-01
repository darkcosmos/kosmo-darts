package io.github.darkcosmos.kosmodarts.capture.camera

import io.github.darkcosmos.kosmodarts.capture.session.FakeClock
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureSettings
import io.github.darkcosmos.kosmodarts.capture.storage.FakeCaptureStorage
import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.EndReason
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.TruthDecodeResult
import io.github.darkcosmos.kosmodarts.capture.truth.TruthJson
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.ZoomRecord
import io.github.darkcosmos.kosmodarts.capture.ui.CameraPermission
import io.github.darkcosmos.kosmodarts.capture.ui.CapturePhase
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureViewModel
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The controller wired to the real view model, a fake camera and a fake storage (rules CP1 to CP13). */
class CaptureControllerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock()
    private val storage = FakeCaptureStorage()
    private val camera = FakeCaptureCamera()
    private val controller = CaptureController(camera)
    private val t20 = Dart(20, Multiplier.TRIPLE)
    private lateinit var vm: CaptureViewModel

    private fun viewModel(): CaptureViewModel {
        val settings = CaptureSettings(folder.root.resolve("settings.txt"))
        vm = CaptureViewModel(clock, storage, settings)
        vm.attachEffects(controller) {
            controller.setup(AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0"), DeviceInfo("Google", "Pixel 7", 37), VideoQuality.UHD)
        }
        controller.attach(vm)
        vm.onPermission(CameraPermission.GRANTED)
        vm.onCameraReady(true)
        return vm
    }

    private fun lastTruth(): String = storage.writes.last()

    private fun setupFor(c: CaptureController) = {
        c.setup(AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0"), DeviceInfo("Google", "Pixel 7", 37), VideoQuality.UHD)
    }

    @Test
    fun `CP9 a second attach routes the effects to the new controller only and a real stop closes the recording once`() {
        val vm = viewModel()
        vm.onStart()
        vm.onDart(t20)
        // Rotation by 180 degrees: configuration change stop, then the new activity registers its own camera side.
        vm.onScreenStopped(true)
        val newCamera = FakeCaptureCamera()
        val newController = CaptureController(newCamera)
        newController.attach(vm)
        vm.attachEffects(newController, setupFor(newController))
        val oldCalls = camera.calls.toList()
        assertEquals(CapturePhase.RUNNING, vm.state.value.phase)
        assertTrue(camera.isRecording(1))
        vm.onDart(t20)
        vm.onNextVolley()
        assertEquals(oldCalls, camera.calls)
        assertEquals(listOf("photo dart-002.jpg", "stop 1", "start volley-002.mp4"), newCamera.calls)
        vm.onScreenStopped(false)
        assertEquals(CapturePhase.IDLE, vm.state.value.phase)
        assertEquals(listOf("photo dart-002.jpg", "stop 1", "start volley-002.mp4", "stop 2"), newCamera.calls)
        assertEquals(oldCalls, camera.calls)
        assertTrue(lastTruth().contains(EndReason.INTERRUPTED.name))
    }

    @Test
    fun `CP9 detaching the effects of a destroyed activity ends the session and closes the recording`() {
        val vm = viewModel()
        vm.onStart()
        vm.onDart(t20)
        vm.detachEffects(controller)
        assertFalse(camera.isRecording(1))
        assertEquals(CapturePhase.IDLE, vm.state.value.phase)
        assertTrue(lastTruth().contains(EndReason.INTERRUPTED.name))
        // Nothing reaches the old camera any more, and no session starts without a camera side.
        val calls = camera.calls.toList()
        vm.onCameraReady(true)
        vm.onStart()
        assertEquals(calls, camera.calls)
        assertEquals(CapturePhase.IDLE, vm.state.value.phase)
    }

    @Test
    fun `CP9 a late detach of the old controller does not unregister the new one`() {
        val vm = viewModel()
        val newCamera = FakeCaptureCamera()
        val newController = CaptureController(newCamera)
        newController.attach(vm)
        vm.attachEffects(newController, setupFor(newController))
        vm.detachEffects(controller)
        vm.onStart()
        assertEquals(listOf("start volley-001.mp4"), newCamera.calls)
        assertTrue(camera.calls.isEmpty())
    }


    @Test
    fun `CP1 Start opens volley 001 and each dart takes its photo`() {
        val vm = viewModel()
        vm.onStart()
        vm.onDart(t20)
        vm.onDart(t20)
        assertEquals(listOf("start volley-001.mp4", "photo dart-001.jpg", "photo dart-002.jpg"), camera.calls)
    }

    @Test
    fun `CP5 Next volley stops then starts the next video, CP6 Stop stops without restart`() {
        val vm = viewModel()
        vm.onStart()
        vm.onNextVolley()
        assertEquals(listOf("start volley-001.mp4", "stop 1", "start volley-002.mp4"), camera.calls)
        vm.onStopConfirmed()
        assertEquals("stop 2", camera.calls.last())
        assertEquals(CapturePhase.IDLE, vm.state.value.phase)
        assertFalse(camera.isRecording(2))
    }

    @Test
    fun `CP9 a recording error stops the open recording, ends the session INTERRUPTED and writes the truth`() {
        val vm = viewModel()
        vm.onStart()
        vm.onDart(t20)
        camera.failRecording(1)
        assertFalse(camera.isRecording(1))
        assertEquals(CapturePhase.ERROR, vm.state.value.phase)
        assertTrue(lastTruth().contains(EndReason.INTERRUPTED.name))
    }

    @Test
    fun `CP9 leaving the screen stops the open recording and ends INTERRUPTED`() {
        val vm = viewModel()
        vm.onStart()
        vm.onScreenStopped(false)
        assertFalse(camera.isRecording(1))
        assertTrue(lastTruth().contains(EndReason.INTERRUPTED.name))
        assertEquals(CapturePhase.IDLE, vm.state.value.phase)
    }

    @Test
    fun `CP9 a configuration change keeps the recording open, a real stop closes it`() {
        val vm = viewModel()
        vm.onStart()
        vm.onScreenStopped(true)
        assertTrue(camera.isRecording(1))
        assertEquals(CapturePhase.RUNNING, vm.state.value.phase)
        vm.onScreenStopped(false)
        assertFalse(camera.isRecording(1))
    }

    @Test
    fun `CP11 and CP10 actual resolutions and timestamp source are copied into the session`() {
        camera.videoResolution = Resolution(1920, 1080)
        camera.photoResolution = Resolution(4000, 3000)
        camera.timestampSource = TimestampSource.UNKNOWN
        val vm = viewModel()
        vm.onStart()
        val decoded = TruthJson.decode(lastTruth()) as TruthDecodeResult.Success
        assertEquals(Resolution(1920, 1080), decoded.session.video.resolution)
        assertEquals(Resolution(4000, 3000), decoded.session.photo.resolution)
        assertEquals(TimestampSource.UNKNOWN, decoded.session.photo.timestampSource)
    }

    @Test
    fun `CP10 a dart entered before the Start event has no video position, a later one has`() {
        camera.autoStart = false
        val vm = viewModel()
        vm.onStart()
        vm.onDart(t20)
        clock.advance(500)
        camera.emitStart(1)
        clock.advance(1_000)
        vm.onDart(t20)
        val truth = lastTruth()
        // Dart 1: entry and photo stamps, both before the Start event.
        assertEquals(2, Regex("\"videoPositionMs\"\\s*:\\s*null").findAll(truth).count())
        assertTrue(Regex("\"videoPositionMs\"\\s*:\\s*1000").containsMatchIn(truth))
    }

    @Test
    fun `CP17 setup copies the camera zoom ratio, with no requested ratio`() {
        val app = AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0")
        val device = DeviceInfo("Google", "Pixel 7", 37)
        fun setupZoom() = controller.setup(app, device, VideoQuality.UHD).zoom

        camera.zoomRatio = 2.4f
        assertEquals(ZoomRecord(requestedRatio = null, ratio = 2.4f), setupZoom())
        camera.zoomRatio = null
        assertEquals(ZoomRecord(requestedRatio = null, ratio = null), setupZoom())
    }

    @Test
    fun `CP17 the zoom at Start is written to the truth file`() {
        camera.zoomRatio = 2.4f
        val vm = viewModel()
        vm.onStart()
        val decoded = TruthJson.decode(lastTruth()) as TruthDecodeResult.Success
        assertEquals(ZoomRecord(requestedRatio = null, ratio = 2.4f), decoded.session.zoom)
    }

    @Test
    fun `CP16 setZoomRatio calls the camera once with the ratio`() {
        controller.setZoomRatio(2.4f)
        assertEquals(listOf(2.4f), camera.zoomCalls)
        assertEquals(2.4f, camera.zoomRatio)
    }

    @Test
    fun `CP9 a camera that refuses the zoom reports no camera error and keeps the recording`() {
        val vm = viewModel()
        vm.onStart()
        camera.failZoom = true
        controller.setZoomRatio(3.0f)
        assertEquals(listOf(3.0f), camera.zoomCalls)
        assertEquals(CapturePhase.RUNNING, vm.state.value.phase)
        assertTrue(camera.isRecording(1))
        assertFalse(camera.calls.any { it.startsWith("error") || it.startsWith("stop") })
    }

    @Test
    fun `CP13 a failed photo keeps the dart, reports the failure once and the next dart is still photographed`() {
        camera.failPhotos += 2
        val vm = viewModel()
        vm.onStart()
        vm.onDart(t20)
        vm.onDart(t20)
        assertEquals(2, vm.state.value.photoFailure)
        assertTrue(Regex("\"photo\"\\s*:\\s*null").containsMatchIn(lastTruth()))
        vm.onDart(t20)
        assertEquals(3, vm.state.value.sessionCount)
        assertTrue(camera.calls.contains("photo dart-003.jpg"))
        assertTrue(camera.isRecording(1))
        assertEquals(CapturePhase.RUNNING, vm.state.value.phase)
    }
}
