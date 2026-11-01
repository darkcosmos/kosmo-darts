package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.darkcosmos.kosmodarts.camera.zoom.ZoomRange
import io.github.darkcosmos.kosmodarts.camera.zoom.ZoomSelection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.darkcosmos.kosmodarts.capture.CaptureActivity
import io.github.darkcosmos.kosmodarts.capture.session.CaptureSetup
import io.github.darkcosmos.kosmodarts.capture.session.FakeClock
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureOutputTarget
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureSettings
import io.github.darkcosmos.kosmodarts.capture.storage.FakeCaptureStorage
import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.ZoomRecord
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoDartsTheme
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.io.File

/** Test double of the camera side: records nothing, the screen tests only need the view model to run. */
internal class NoopEffects : CaptureEffects {
    override fun startVideo(volley: Int, target: CaptureOutputTarget) = Unit
    override fun stopVideo(volley: Int) = Unit
    override fun takePhoto(dart: Int, target: CaptureOutputTarget) = Unit
    override fun setZoomRatio(ratio: Float) = Unit
}

internal fun testViewModel(settingsFile: File): CaptureViewModel {
    val setup = CaptureSetup(
        app = AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0"),
        device = DeviceInfo("Google", "Pixel 7", 37),
        video = VideoSettings(VideoQuality.UHD, Resolution(3840, 2160)),
        photo = PhotoSettings(Resolution(4080, 3072), TimestampSource.REALTIME),
        zoom = ZoomRecord(requestedRatio = 2.4f, ratio = 2.4f),
    )
    return CaptureViewModel(FakeClock(), FakeCaptureStorage(), CaptureSettings(settingsFile))
        .also {
            it.attachEffects(NoopEffects()) { setup }
            it.onPermission(CameraPermission.GRANTED)
            it.onCameraReady(true)
        }
}

private val PLACEHOLDER_PREVIEW: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("preview") }
}

/** Shows a fixed [state] at [fontScale]. */
internal fun AndroidComposeTestRule<ActivityScenarioRule<CaptureActivity>, CaptureActivity>.showState(
    state: CaptureUiState,
    actions: CaptureActions = CaptureActions(),
    fontScale: Float = 1f,
) {
    runOnUiThread {
        activity.setContent { Scaled(fontScale) { CaptureScreen(state, actions, PLACEHOLDER_PREVIEW) } }
    }
    waitForIdle()
}

/** Ready camera, idle, with a zoom of [ratio] inside the range [min] to [max] (the range is null when not adjustable). */
internal fun zoomState(ratio: Float, min: Float = 1f, max: Float = 10f): CaptureUiState = CaptureUiState(
    permission = CameraPermission.GRANTED,
    cameraReady = true,
    zoom = ZoomSelection(ratio, ZoomRange.of(min, max)),
)

/**
 * Shows the screen with a zoom applied the way the view model does it: a slider move or a step replaces the selection
 * in a state of the test, so the screen shows the result. [onFinished] counts released gestures.
 */
internal fun AndroidComposeTestRule<ActivityScenarioRule<CaptureActivity>, CaptureActivity>.showZoomHarness(
    initial: CaptureUiState,
    fontScale: Float = 1f,
    onChanged: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    runOnUiThread {
        activity.setContent {
            Scaled(fontScale) {
                var state by remember { mutableStateOf(initial) }
                val actions = CaptureActions(
                    onZoomChanged = { position -> onChanged(); state = state.copy(zoom = state.zoom.movedTo(position)) },
                    onZoomChangeFinished = onFinished,
                    onZoomStep = { steps -> state = state.copy(zoom = state.zoom.steppedBy(steps)) },
                )
                CaptureScreen(state, actions, PLACEHOLDER_PREVIEW)
            }
        }
    }
    waitForIdle()
}

/** Shows the screen driven by [viewModel], as the real activity will. */
internal fun AndroidComposeTestRule<ActivityScenarioRule<CaptureActivity>, CaptureActivity>.showViewModel(
    viewModel: CaptureViewModel,
    fontScale: Float = 1f,
) {
    runOnUiThread {
        activity.setContent {
            Scaled(fontScale) {
                val state by viewModel.state.collectAsState()
                CaptureScreen(state, CaptureActions.of(viewModel, {}, {}), PLACEHOLDER_PREVIEW)
            }
        }
    }
    waitForIdle()
}

@Composable
private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        KosmoDartsTheme(content = content)
    }
}
