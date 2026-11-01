package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.capture.CaptureActivity
import io.github.darkcosmos.kosmodarts.capture.ui.keypad.breaksOnlyBetweenWords
import io.github.darkcosmos.kosmodarts.capture.ui.keypad.lineEnds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val EN_915 = "en-w915dp-h412dp-land-xxhdpi"
private const val FR_915 = "fr-w915dp-h412dp-land-xxhdpi"
private const val EN_640 = "en-w640dp-h360dp-land-xxhdpi"
private const val FR_640 = "fr-w640dp-h360dp-land-xxhdpi"

/** The zoom row (rules CP14, CP15): semantics, enabled states, and the width and height budgets of the layout. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoomRowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag)

    private fun valueText(text: String) = composeRule.onNode(
        hasAnyAncestor(hasTestTag(CaptureTags.ZOOM_VALUE)) and hasText(text),
        useUnmergedTree = true,
    )

    private fun stateDescription(): String? =
        tag(CaptureTags.ZOOM_SLIDER).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun assertInsideScreen(tag: String) {
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = tag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("$tag $bounds not inside $screen", bounds.left >= screen.left && bounds.right <= screen.right)
        assertTrue("$tag $bounds not inside $screen", bounds.top >= screen.top && bounds.bottom <= screen.bottom)
    }

    private fun assertZoomValueSemantics(value: String, spoken: String, out: String, `in`: String) {
        composeRule.showState(zoomState(2.4f))
        valueText(value).assertIsDisplayed()
        // The value text is cleared from semantics: it is not a separate node of the merged tree.
        composeRule.onNodeWithText(value).assertDoesNotExist()
        assertEquals(spoken, stateDescription())
        val slider = tag(CaptureTags.ZOOM_SLIDER).fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, slider[SemanticsProperties.LiveRegion])
        tag(CaptureTags.ZOOM_SLIDER).assertContentDescriptionEquals("Zoom")
        tag(CaptureTags.ZOOM_OUT).assertContentDescriptionEquals(out)
        tag(CaptureTags.ZOOM_IN).assertContentDescriptionEquals(`in`)
        listOf(CaptureTags.ZOOM_OUT, CaptureTags.ZOOM_IN).forEach {
            assertEquals(null, tag(it).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        }
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `CP14 english value, spoken state, live region and button descriptions`() =
        assertZoomValueSemantics("2.4×", "2.4 times", "Zoom out", "Zoom in")

    @Test
    @Config(qualifiers = FR_915)
    fun `CP14 french value with decimal comma, spoken state and button descriptions`() {
        composeRule.showState(zoomState(2.4f))
        valueText("2,4×").assertIsDisplayed()
        assertEquals("2,4 fois", stateDescription())
        tag(CaptureTags.ZOOM_SLIDER).assertContentDescriptionEquals("Zoom")
        tag(CaptureTags.ZOOM_OUT).assertContentDescriptionEquals("Réduire le zoom")
        tag(CaptureTags.ZOOM_IN).assertContentDescriptionEquals("Augmenter le zoom")
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `CP14 a swipe calls onZoomChanged then onZoomChangeFinished once, the buttons send one step each`() {
        var changed = 0
        var finished = 0
        val steps = mutableListOf<Int>()
        composeRule.showState(
            zoomState(2.4f),
            CaptureActions(
                onZoomChanged = { changed++ },
                onZoomChangeFinished = { finished++ },
                onZoomStep = { steps += it },
            ),
        )
        tag(CaptureTags.ZOOM_SLIDER).performTouchInput { swipe(Offset(width * 0.3f, centerY), Offset(width * 0.9f, centerY)) }
        assertTrue("onZoomChanged calls: $changed", changed >= 1)
        assertEquals(1, finished)
        tag(CaptureTags.ZOOM_OUT).performClick()
        assertEquals(listOf(-1), steps)
        tag(CaptureTags.ZOOM_IN).performClick()
        assertEquals(listOf(-1, 1), steps)
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `CP14 a plus click changes the slider state description to 2_5 times`() {
        composeRule.showZoomHarness(zoomState(2.4f))
        assertEquals("2.4 times", stateDescription())
        tag(CaptureTags.ZOOM_IN).performClick()
        assertEquals("2.5 times", stateDescription())
        valueText("2.5×").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `CP15 everything is enabled when idle with a ready camera`() {
        composeRule.showState(zoomState(2.4f))
        listOf(CaptureTags.ZOOM_SLIDER, CaptureTags.ZOOM_OUT, CaptureTags.ZOOM_IN).forEach { tag(it).assertIsEnabled() }
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `CP15 slider and buttons are disabled while running, saving, starting or without permission`() {
        val idle = zoomState(2.4f)
        listOf(
            idle.copy(phase = CapturePhase.RUNNING),
            idle.copy(phase = CapturePhase.SAVING),
            idle.copy(cameraReady = false),
            idle.copy(permission = CameraPermission.ASK),
        ).forEach { state ->
            composeRule.showState(state)
            listOf(CaptureTags.ZOOM_SLIDER, CaptureTags.ZOOM_OUT, CaptureTags.ZOOM_IN).forEach { tag(it).assertIsNotEnabled() }
            // The value stays readable while greyed.
            valueText("2.4×").assertIsDisplayed()
        }
    }

    /** Disabled style DZ16: a disabled button has no fill and a dimmed outline (`States.DISABLED_ALPHA`); see captures. */
    @Test
    @Config(qualifiers = EN_915)
    fun `CP15 minus is disabled at the range minimum and plus at the maximum, the other stays enabled`() {
        composeRule.showState(zoomState(1f))
        tag(CaptureTags.ZOOM_OUT).assertIsNotEnabled()
        tag(CaptureTags.ZOOM_IN).assertIsEnabled()
        composeRule.showState(zoomState(10f))
        tag(CaptureTags.ZOOM_OUT).assertIsEnabled()
        tag(CaptureTags.ZOOM_IN).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `CP15 a range without zoom shows Zoom not available in english and enables nothing`() {
        composeRule.showState(zoomState(1f, min = 1f, max = 1f))
        valueText("Zoom not available").assertIsDisplayed()
        assertEquals("Zoom not available", stateDescription())
        listOf(CaptureTags.ZOOM_SLIDER, CaptureTags.ZOOM_OUT, CaptureTags.ZOOM_IN).forEach { tag(it).assertIsNotEnabled() }
    }

    @Test
    @Config(qualifiers = FR_915)
    fun `CP15 a range without zoom shows Zoom non disponible in french and enables nothing`() {
        composeRule.showState(zoomState(1f, min = 1f, max = 1f))
        valueText("Zoom non disponible").assertIsDisplayed()
        assertEquals("Zoom non disponible", stateDescription())
        listOf(CaptureTags.ZOOM_SLIDER, CaptureTags.ZOOM_OUT, CaptureTags.ZOOM_IN).forEach { tag(it).assertIsNotEnabled() }
    }

    private fun assertButtonsAndRow(fontScale: Float) {
        composeRule.showState(zoomState(2.4f), fontScale = fontScale)
        tag(CaptureTags.ZOOM_ROW).assertHeightIsEqualTo(42.dp)
        listOf(CaptureTags.ZOOM_OUT, CaptureTags.ZOOM_IN).forEach {
            tag(it).assertWidthIsAtLeast(ZOOM_BUTTON_WIDTH).assertHeightIsAtLeast(ZOOM_BUTTON_HEIGHT)
            tag(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(42.dp)
            assertInsideScreen(it)
        }
        assertInsideScreen(CaptureTags.ZOOM_ROW)
        assertInsideScreen(CaptureTags.ZOOM_SLIDER)
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `layout zoom row is 42dp and buttons 48x42 at 915x412, font scales 1 and 2`() {
        assertButtonsAndRow(1f)
        assertButtonsAndRow(2f)
    }

    @Test
    @Config(qualifiers = EN_640)
    fun `layout zoom row is 42dp and buttons 48x42 at 640x360`() = assertButtonsAndRow(1f)

    @Test
    @Config(qualifiers = EN_915)
    fun `layout gaps are 4dp above and 8dp below the zoom row, preview and control row stay inside`() {
        composeRule.showState(zoomState(2.4f))
        val density = composeRule.density
        val row = tag(CaptureTags.ZOOM_ROW).fetchSemanticsNode().boundsInRoot
        val preview = tag(CaptureTags.PREVIEW).fetchSemanticsNode().boundsInRoot
        val start = tag(CaptureTags.CONTROL_ROW).fetchSemanticsNode().boundsInRoot
        with(density) {
            assertEquals(4.dp.toPx(), row.top - preview.bottom, 1f)
            assertEquals(8.dp.toPx(), start.top - row.bottom, 1f)
        }
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `layout the preview is at least 96dp tall at 915x412 and font scale 2`() {
        composeRule.showState(zoomState(2.4f), fontScale = 2f)
        tag(CaptureTags.PREVIEW).assertHeightIsAtLeast(96.dp)
        tag(CaptureTags.ZOOM_ROW).assertHeightIsEqualTo(42.dp)
        assertInsideScreen(CaptureTags.START_STOP)
    }

    @Test
    @Config(qualifiers = EN_640)
    fun `layout the preview is at least 160dp tall at 640x360 and the control row is inside the screen`() {
        composeRule.showState(zoomState(2.4f))
        tag(CaptureTags.PREVIEW).assertHeightIsAtLeast(160.dp)
        listOf(CaptureTags.DELETE_ALL, CaptureTags.QUALITY, CaptureTags.START_STOP).forEach(::assertInsideScreen)
    }

    private fun assertSliderWidthAndValueText(text: String, minSlider: Float, fontScale: Float = 1f) {
        val unavailable = zoomState(1f, min = 1f, max = 1f)
        // Fixed bounds use the density of the test: dp values compared in pixels.
        composeRule.showState(unavailable, fontScale = fontScale)
        val slider = tag(CaptureTags.ZOOM_SLIDER).fetchSemanticsNode().boundsInRoot.width
        with(composeRule.density) {
            assertTrue("slider width ${slider.toDp()} < $minSlider dp", slider >= minSlider.dp.toPx())
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val node = valueText(text).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        // The reported layout is the unshrunk one: fit is checked on the drawn bounds of the text node.
        val box = tag(CaptureTags.ZOOM_VALUE).fetchSemanticsNode().boundsInRoot
        val bounds = node.boundsInRoot
        assertTrue("text $bounds outside $box", bounds.left >= box.left && bounds.right <= box.right && bounds.top >= box.top && bounds.bottom <= box.bottom)
        assertTrue("break inside a word", breaksOnlyBetweenWords(text, layout.lineEnds()))
    }

    @Test
    @Config(qualifiers = EN_915)
    fun `U17 english not available keeps the slider at least 120dp at 915x412 and font scale 2`() {
        assertSliderWidthAndValueText("Zoom not available", 120f, fontScale = 2f)
    }

    @Test
    @Config(qualifiers = FR_915)
    fun `U17 french not available keeps the slider at least 120dp at 915x412 and font scale 2`() {
        assertSliderWidthAndValueText("Zoom non disponible", 120f, fontScale = 2f)
    }

    @Test
    @Config(qualifiers = EN_640)
    fun `U17 english not available keeps the slider at least 96dp at 640x360`() =
        assertSliderWidthAndValueText("Zoom not available", 96f)

    @Test
    @Config(qualifiers = FR_640)
    fun `U17 french not available keeps the slider at least 96dp at 640x360`() =
        assertSliderWidthAndValueText("Zoom non disponible", 96f)
}
