package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.capture.CaptureActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Recorded screenshots of every state of the capture screen, in English and French, at three sizes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    private val granted = zoomState(2.4f)
    private val running = granted.copy(
        phase = CapturePhase.RUNNING,
        slots = listOf(DartLabel.Text("T20"), DartLabel.Text("5"), null),
        sessionCount = 2,
        totalCount = 14,
    )

    private val scenarios: Map<String, CaptureUiState> = mapOf(
        "idle" to granted.copy(totalCount = 12),
        "running" to running,
        "volley_full" to running.copy(slots = listOf(DartLabel.Text("T20"), DartLabel.Bull, DartLabel.Miss), volleyFull = true),
        "stop_confirm" to running.copy(stopConfirmVisible = true),
        "delete_confirm" to granted.copy(deleteAllConfirmVisible = true),
        "permission_missing" to CaptureUiState(permission = CameraPermission.ASK),
        "permission_denied" to CaptureUiState(permission = CameraPermission.PERMANENTLY_DENIED),
        "photo_failure" to running.copy(photoFailure = 2),
        "saving" to granted.copy(phase = CapturePhase.SAVING, totalCount = 12),
        "error" to granted.copy(phase = CapturePhase.ERROR, error = CaptureError.TRUTH_WRITE),
    )

    @Test
    @Config(qualifiers = "en-w915dp-h412dp-land-xxhdpi")
    fun `zoom at minimum english`() = captureOne("zoom_at_minimum", "915x412_en", zoomState(1f))

    @Test
    @Config(qualifiers = "en-w915dp-h412dp-land-xxhdpi")
    fun `zoom unavailable english`() = captureOne("zoom_unavailable", "915x412_en", zoomState(1f, max = 1f))

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `zoom unavailable french`() = captureOne("zoom_unavailable", "915x412_fr", zoomState(1f, max = 1f))

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `zoom unavailable font scale 2 french`() =
        captureOne("zoom_unavailable", "915x412_font2_fr", zoomState(1f, max = 1f), fontScale = 2f)

    private fun captureOne(name: String, suffix: String, state: CaptureUiState, fontScale: Float = 1f) {
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.showState(state, fontScale = fontScale)
        composeRule.onRoot().captureRoboImage("$directory/capture_${name}_$suffix.png")
    }

    @Test
    @Config(qualifiers = "en-w915dp-h412dp-land-xxhdpi")
    fun `landscape 915x412 english`()= captureAll("915x412_en", fontScale = 1f)

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `landscape 915x412 french`() = captureAll("915x412_fr", fontScale = 1f)

    @Test
    @Config(qualifiers = "en-w640dp-h360dp-land-xxhdpi")
    fun `landscape 640x360 english`() = captureAll("640x360_en", fontScale = 1f)

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xxhdpi")
    fun `landscape 640x360 french`() = captureAll("640x360_fr", fontScale = 1f)

    @Test
    @Config(qualifiers = "en-w915dp-h412dp-land-xxhdpi")
    fun `font scale 2 english`() = captureAll("915x412_font2_en", fontScale = 2f)

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `font scale 2 french`() = captureAll("915x412_font2_fr", fontScale = 2f)

    private fun captureAll(suffix: String, fontScale: Float) {
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        scenarios.forEach { (name, state) ->
            // The snackbar must stay on screen while the picture is taken.
            composeRule.mainClock.autoAdvance = state.photoFailure == null
            composeRule.showState(state, fontScale = fontScale)
            if (state.photoFailure != null) composeRule.mainClock.advanceTimeBy(SNACKBAR_SHOWN_MILLIS)
            composeRule.onRoot().captureRoboImage("$directory/capture_${name}_$suffix.png")
        }
    }

    private companion object {
        const val SNACKBAR_SHOWN_MILLIS = 500L
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
    }
}
