package io.github.darkcosmos.kosmodarts.camera.zoom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule CP14 (buttons and slider state) and DZ2, DZ11. */
class ZoomSelectionTest {
    private fun range(min: Float, max: Float) = checkNotNull(ZoomRange.of(min, max))

    private fun at(ratio: Float, min: Float = 1f, max: Float = 10f) = ZoomSelection(ratio, range(min, max))

    @Test
    fun `CP16 the ratio defaults to 1 without a range`() {
        val selection = ZoomSelection()
        assertEquals(1.0f, selection.ratio, 0f)
        assertNull(selection.range)
    }

    @Test
    fun `CP16 the remembered ratio is kept when it is outside the range`() {
        val selection = ZoomSelection(12f, range(1f, 10f))
        assertEquals(10.0f, selection.ratio, 0f)
        assertEquals(12.0f, selection.remembered, 0f)
    }

    @Test
    fun `CP16 moving the slider updates the remembered ratio`() {
        assertEquals(3.2f, at(1f).movedTo(0.5f).remembered, 0f)
    }

    @Test
    fun `CP15 a move is ignored when the range is not adjustable`() {
        val fixed = at(1f, 1f, 1f)
        assertEquals(fixed, fixed.movedTo(0.5f))
    }

    @Test
    fun `CP16 losing the range keeps the remembered ratio`() {
        assertEquals(4.0f, at(4f).withRange(null).remembered, 0f)
    }

    @Test
    fun `CP15 unavailable only when the range is known and not adjustable`() {
        assertFalse(ZoomSelection().unavailable)
        assertFalse(at(1f).unavailable)
        assertTrue(at(1f, 1f, 1f).unavailable)
    }

    @Test
    fun `CP14 a step changes the ratio by one tenth`() {
        assertEquals(2.5f, at(2.4f).steppedBy(1).ratio, 0f)
        assertEquals(2.3f, at(2.4f).steppedBy(-1).ratio, 0f)
    }

    @Test
    fun `CP14 ten steps from 1 give exactly 2`() {
        var selection = at(1f)
        repeat(10) { selection = selection.steppedBy(1) }
        assertEquals(2.0f, selection.ratio, 0f)
    }

    @Test
    fun `CP14 steps stop at the range ends`() {
        assertEquals(10.0f, at(10f).steppedBy(1).ratio, 0f)
        assertEquals(1.0f, at(1f).steppedBy(-1).ratio, 0f)
        assertEquals(8.37f, at(8.37f, 0.6f, 8.37f).steppedBy(1).ratio, 0f)
        assertEquals(8.3f, at(8.37f, 0.6f, 8.37f).steppedBy(-1).ratio, 0f)
        assertEquals(0.6f, at(0.6f, 0.6f, 8.37f).steppedBy(-1).ratio, 0f)
        assertEquals(1.0f, at(1f, 1f, 1f).steppedBy(1).ratio, 0f)
    }

    @Test
    fun `CP15 minus and plus availability follows the range ends`() {
        assertFalse(at(1f).canStepDown)
        assertTrue(at(1f).canStepUp)
        assertFalse(at(10f).canStepUp)
        assertFalse(at(8.37f, 0.6f, 8.37f).canStepUp)
        assertTrue(at(2.4f).canStepDown)
        assertTrue(at(2.4f).canStepUp)
        assertFalse(at(1f, 1f, 1f).canStepUp)
        assertFalse(at(1f, 1f, 1f).canStepDown)
        assertFalse(ZoomSelection().canStepUp)
        assertFalse(ZoomSelection().canStepDown)
    }
}
