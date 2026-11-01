package io.github.darkcosmos.kosmodarts.camera.zoom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule CP14 (slider mapping, rounding) and DZ10. */
class ZoomRangeTest {
    private val range = checkNotNull(ZoomRange.of(1f, 10f))

    @Test
    fun `CP14 positions map logarithmically`() {
        assertEquals(1.0f, range.ratioAt(0f), 0f)
        assertEquals(10.0f, range.ratioAt(1f), 0f)
        assertEquals(3.2f, range.ratioAt(0.5f), 0f)
    }

    @Test
    fun `CP14 out of range positions and NaN are clamped`() {
        assertEquals(1.0f, range.ratioAt(-1f), 0f)
        assertEquals(10.0f, range.ratioAt(2f), 0f)
        assertEquals(1.0f, range.ratioAt(Float.NaN), 0f)
    }

    @Test
    fun `CP14 the position of every tenth round-trips to the same ratio`() {
        for (tenth in 10..100) {
            val ratio = tenth / 10f
            assertEquals("ratio $ratio", ratio, range.ratioAt(range.positionOf(ratio)), 0f)
        }
    }

    @Test
    fun `CP14 rounding uses the shortest decimal form, half up`() {
        assertEquals(2.4f, quantizeZoom(2.44f), 0f)
        assertEquals(2.5f, quantizeZoom(2.45f), 0f)
        assertEquals(2.5f, quantizeZoom(2.46f), 0f)
        assertEquals(1.1f, quantizeZoom(1.05f), 0f)
        assertEquals(2.4f, quantizeZoom(2.35f), 0f)
        assertEquals(1.0f, quantizeZoom(1.04999f), 0f)
    }

    @Test
    fun `CP14 the rounded ratio stays inside the camera range`() {
        val odd = checkNotNull(ZoomRange.of(0.6f, 8.37f))
        assertEquals(8.37f, odd.ratioAt(1f), 0f)
        assertEquals(0.6f, odd.ratioAt(0f), 0f)
    }

    @Test
    fun `CP14 the factory refuses invalid bounds`() {
        assertNull(ZoomRange.of(Float.NaN, 2f))
        assertNull(ZoomRange.of(1f, Float.POSITIVE_INFINITY))
        assertNull(ZoomRange.of(0f, 2f))
        assertNull(ZoomRange.of(-1f, 2f))
        assertNull(ZoomRange.of(3f, 2f))
        assertNotNull(ZoomRange.of(1f, 1f))
    }

    @Test
    fun `CP15 the range is adjustable only when it is at least one step wide`() {
        assertFalse(checkNotNull(ZoomRange.of(1f, 1f)).adjustable)
        assertFalse(checkNotNull(ZoomRange.of(1f, 1.05f)).adjustable)
        assertTrue(checkNotNull(ZoomRange.of(1f, 1.1f)).adjustable)
    }

    @Test
    fun `CP14 the position is zero when the range is not adjustable`() {
        assertEquals(0f, checkNotNull(ZoomRange.of(1f, 1f)).positionOf(1f), 0f)
    }
}
