package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.capture.CaptureActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Undo and Next volley row is pinned: fully inside the screen, without scrolling, on the smallest layouts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureActionRowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    private val running = CaptureUiState(
        permission = CameraPermission.GRANTED,
        cameraReady = true,
        phase = CapturePhase.RUNNING,
        slots = listOf(DartLabel.Text("T20"), DartLabel.Text("5"), null),
    )
    private val full = running.copy(
        slots = listOf(DartLabel.Text("T20"), DartLabel.Bull, DartLabel.Miss),
        volleyFull = true,
    )

    private fun assertInsideScreen(tag: String) {
        val node: SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)
        node.assertIsDisplayed().assertHeightIsAtLeast(42.dp)
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$tag $bounds not inside $screen", bounds.left >= screen.left && bounds.right <= screen.right)
        assertTrue("$tag $bounds not inside $screen", bounds.top >= screen.top && bounds.bottom <= screen.bottom)
    }

    private fun assertRowVisible(state: CaptureUiState, fontScale: Float) {
        composeRule.showState(state, fontScale = fontScale)
        listOf(CaptureTags.ACTION_ROW, CaptureTags.UNDO, CaptureTags.NEXT_VOLLEY).forEach(::assertInsideScreen)
    }

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xxhdpi")
    fun `U11 Undo and Next volley are fully visible at 640x360 in French, volley full included`() {
        assertRowVisible(running, 1f)
        assertRowVisible(full, 1f)
    }

    @Test
    @Config(qualifiers = "fr-w915dp-h412dp-land-xxhdpi")
    fun `U11 Undo and Next volley are fully visible at 915x412 and font scale 2 in French, volley full included`() {
        assertRowVisible(running, 2f)
        assertRowVisible(full, 2f)
    }
}
