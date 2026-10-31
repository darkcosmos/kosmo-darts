package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.capture.CaptureActivity
import io.github.darkcosmos.kosmodarts.capture.ui.keypad.KeypadTags
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The capture screen at 915x412dp landscape, driven by the real view model or by fixed states. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w915dp-h412dp-land-xxhdpi")
class CaptureScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()

    // The readout clears its descendants' semantics for TalkBack, so its parts are read from the unmerged tree.
    private fun slot(index: Int) = composeRule.onNodeWithTag(CaptureTags.slot(index), useUnmergedTree = true)

    private fun hint() = composeRule.onNodeWithTag(CaptureTags.VOLLEY_FULL_HINT, useUnmergedTree = true)

    private fun runningWithTwoDarts() = CaptureUiState(
        permission = CameraPermission.GRANTED,
        cameraReady = true,
        phase = CapturePhase.RUNNING,
        slots = listOf(DartLabel.Text("T20"), DartLabel.Text("5"), null),
    )

    @Test
    fun `CP3 readout follows Start then T20, 5, Miss, and the volley full hint appears`() {
        composeRule.showViewModel(testViewModel(folder.newFile()))
        tap(CaptureTags.START_STOP)
        tap(KeypadTags.TRIPLE)
        tap(KeypadTags.number(20))
        tap(KeypadTags.number(5))
        hint().assertDoesNotExist()
        tap(KeypadTags.MISS)
        composeRule.onNodeWithContentDescription("Volley 1", substring = true).assertExists()
        slot(0).assertTextEquals("T20")
        slot(1).assertTextEquals("5")
        slot(2).assertTextEquals("Miss")
        hint().assertTextEquals("Volley full: press Next volley")
        composeRule.onNodeWithTag(KeypadTags.number(1)).assertIsNotEnabled()
    }

    @Test
    fun `CP3 Undo clears the third slot`() {
        composeRule.showViewModel(testViewModel(folder.newFile()))
        tap(CaptureTags.START_STOP)
        tap(KeypadTags.TRIPLE)
        tap(KeypadTags.number(20))
        tap(KeypadTags.number(5))
        tap(KeypadTags.MISS)
        tap(CaptureTags.UNDO)
        slot(2).assertTextEquals("-")
        hint().assertDoesNotExist()
        composeRule.onNodeWithTag(KeypadTags.number(1)).assertIsEnabled()
    }

    @Test
    fun `CP3 the readout content description includes the volley full hint`() {
        val full = runningWithTwoDarts().copy(
            slots = listOf(DartLabel.Text("T20"), DartLabel.Bull, DartLabel.Miss),
            volleyFull = true,
            volleyNumber = 3,
        )
        composeRule.showState(full)
        composeRule.onNodeWithTag(CaptureTags.READOUT).assertContentDescriptionEquals(
            "Volley 3: T20, Bull, Miss. Volley full: press Next volley",
        )
    }

    @Test
    fun `CP6 Stop asks for confirmation and Continue keeps the session`() {
        composeRule.showViewModel(testViewModel(folder.newFile()))
        tap(CaptureTags.START_STOP)
        tap(CaptureTags.START_STOP)
        composeRule.onNodeWithTag(CaptureTags.STOP_DIALOG).assertTextEquals("Stop the session? The current volley is saved.")
        tap(CaptureTags.STOP_CANCEL)
        composeRule.onNodeWithTag(CaptureTags.STOP_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(CaptureTags.REC).assertExists()
        composeRule.onNodeWithTag(CaptureTags.NEXT_VOLLEY).assertIsEnabled()
    }

    @Test
    fun `CP8 Delete all shows the dialog with the reinstall note`() {
        composeRule.showViewModel(testViewModel(folder.newFile()))
        tap(CaptureTags.DELETE_ALL)
        composeRule.onNodeWithTag(CaptureTags.DELETE_DIALOG).assertTextEquals(
            "Delete every session of this installation? Sessions from an earlier installation stay on the phone.",
        )
        tap(CaptureTags.DELETE_CANCEL)
        composeRule.onNodeWithTag(CaptureTags.DELETE_DIALOG).assertDoesNotExist()
    }

    @Test
    fun `CP2 icons, REC dot, preview and toggle have content descriptions`() {
        composeRule.showState(runningWithTwoDarts())
        composeRule.onNodeWithTag(CaptureTags.REC).assertContentDescriptionEquals("Recording")
        composeRule.onNodeWithTag(CaptureTags.PREVIEW).assertContentDescriptionEquals("Camera preview")
        composeRule.onNodeWithTag(CaptureTags.QUALITY).assertContentDescriptionEquals("Video quality: 4K")
        composeRule.onNodeWithTag(CaptureTags.DELETE_ALL).assertContentDescriptionEquals("Delete all sessions")
    }

    @Test
    fun `CP2 the quality toggle reports the other quality and is off while running`() {
        var chosen: VideoQuality? = null
        val idle = CaptureUiState(permission = CameraPermission.GRANTED, cameraReady = true)
        composeRule.showState(idle, CaptureActions(onQuality = { chosen = it }))
        tap(CaptureTags.QUALITY_FHD)
        assertEquals(VideoQuality.FHD, chosen)
        composeRule.showState(runningWithTwoDarts())
        composeRule.onNodeWithTag(CaptureTags.QUALITY_FHD).assertIsNotEnabled()
        composeRule.onNodeWithTag(CaptureTags.DELETE_ALL).assertIsNotEnabled()
    }

    @Test
    fun `CP13 the photo failure message is a snackbar shown at least 4 seconds with a dismiss action`() {
        var shown = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.showState(
            runningWithTwoDarts().copy(photoFailure = 2),
            CaptureActions(onPhotoMessageShown = { shown++ }),
        )
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(CaptureTags.PHOTO_SNACKBAR).assertExists()
        composeRule.onNodeWithText("Photo failed for this dart. The entry is kept.").assertExists()
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.onNodeWithTag(CaptureTags.PHOTO_SNACKBAR).assertExists()
        composeRule.onNodeWithTag(CaptureTags.PHOTO_SNACKBAR_DISMISS).assertHeightIsAtLeast(42.dp).assertWidthIsAtLeast(48.dp)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithTag(CaptureTags.PHOTO_SNACKBAR).assertDoesNotExist()
        assertEquals(1, shown)
    }

    @Test
    fun `CP13 the snackbar dismiss action stays at least 42dp tall at font scale 2`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.showState(runningWithTwoDarts().copy(photoFailure = 2), fontScale = 2f)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(CaptureTags.PHOTO_SNACKBAR_DISMISS).assertIsDisplayed().assertHeightIsAtLeast(42.dp)
    }

    @Test
    fun `CP2 buttons and keys are at least 42dp tall and 48dp wide at 412dp height and at font scale 2`() {
        listOf(1f, 2f).forEach { scale ->
            composeRule.showState(runningWithTwoDarts(), fontScale = scale)
            listOf(
                CaptureTags.START_STOP, CaptureTags.NEXT_VOLLEY, CaptureTags.UNDO, CaptureTags.DELETE_ALL,
                CaptureTags.QUALITY_UHD, CaptureTags.QUALITY_FHD, KeypadTags.number(7), KeypadTags.MISS,
            ).forEach {
                composeRule.onNodeWithTag(it).assertHeightIsAtLeast(42.dp).assertWidthIsAtLeast(48.dp)
            }
        }
    }

    @Test
    fun `CP2 the permission panel offers Allow camera, then Open settings after a permanent refusal`() {
        var allowed = 0
        var opened = 0
        val actions = CaptureActions(onAllowCamera = { allowed++ }, onOpenSettings = { opened++ })
        composeRule.showState(CaptureUiState(permission = CameraPermission.ASK), actions)
        composeRule.onNodeWithTag(CaptureTags.PERMISSION_BUTTON).assertTextEquals("Allow camera")
        tap(CaptureTags.PERMISSION_BUTTON)
        composeRule.showState(CaptureUiState(permission = CameraPermission.PERMANENTLY_DENIED), actions)
        composeRule.onNodeWithTag(CaptureTags.PERMISSION_BUTTON).assertTextEquals("Open settings")
        tap(CaptureTags.PERMISSION_BUTTON)
        assertEquals(1 to 1, allowed to opened)
        composeRule.onNodeWithTag(CaptureTags.START_STOP).assertIsNotEnabled()
    }
}
