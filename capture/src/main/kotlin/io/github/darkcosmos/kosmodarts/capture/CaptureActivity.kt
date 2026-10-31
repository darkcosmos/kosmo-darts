package io.github.darkcosmos.kosmodarts.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureActions
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureScreen
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureUiState
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoDartsTheme

/**
 * Single activity of Kosmo Capture. It hosts the capture screen edge to edge; the camera preview and the view model are
 * wired by the camera task, until then the screen shows its idle state with an empty preview.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KosmoDartsTheme {
                CaptureScreen(
                    state = CaptureUiState(),
                    actions = CaptureActions(),
                    preview = { Box(Modifier.fillMaxSize()) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}
