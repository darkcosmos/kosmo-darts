package io.github.darkcosmos.kosmodarts.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #69, M15, design system 6.3: the text scale of the name row and the checkout line in a low zone. */
class CheckoutFitTest {

    /** Heights as measured on the 640 x 360 landscape screen: not linear in the font scale. */
    private val measured: (Float) -> Float = { scale -> 72f + (scale - 1f) * 62f }

    private fun scale(available: Float, fontScale: Float, heightAt: (Float) -> Float = measured): Float =
        checkoutTextScale(available, FIXED, fontScale, heightAt)

    @Test
    fun `M15 enough room keeps the font scale of the user`() {
        assertEquals(2f, scale(available = 400f, fontScale = 2f), 0f)
    }

    @Test
    fun `M15 no room falls back to 1_0 and never below`() {
        assertEquals(1f, scale(available = FIXED + 10f, fontScale = 2f), 0f)
    }

    @Test
    fun `M15 negative room returns 1_0`() {
        assertEquals(1f, scale(available = -50f, fontScale = 1.5f), 0f)
    }

    @Test
    fun `M15 exact fit at the user's scale is accepted`() {
        assertEquals(1.5f, scale(available = FIXED + measured(1.5f), fontScale = 1.5f), 0f)
    }

    @Test
    fun `M15 one pixel short at the user's scale takes the next lower scale that fits`() {
        val result = scale(available = FIXED + measured(1.5f) - 1f, fontScale = 1.5f)
        assertTrue("scale $result", result < 1.5f && result >= 1.4f)
        assertTrue(FIXED + measured(result) <= FIXED + measured(1.5f) - 1f)
    }

    @Test
    fun `M15 the result is the largest candidate that fits`() {
        val available = FIXED + measured(1.6f)
        val result = scale(available, fontScale = 2f)
        assertEquals(1.6f, result, 1e-4f)
        assertTrue(FIXED + measured(result + 0.05f) > available)
    }

    @Test
    fun `M15 a font scale of 1_0 always returns 1_0`() {
        assertEquals(1f, scale(available = 10f, fontScale = 1f), 0f)
        assertEquals(1f, scale(available = 1000f, fontScale = 1f), 0f)
    }

    @Test
    fun `M15 a font scale under 1_0 is kept`() {
        assertEquals(0.85f, scale(available = 10f, fontScale = 0.85f), 0f)
    }

    @Test
    fun `M15 the result grows with the room`() {
        val results = (0..400 step 10).map { scale(available = it.toFloat(), fontScale = 2f) }
        assertEquals(results.sorted(), results)
        assertEquals(1f, results.first(), 0f)
        assertEquals(2f, results.last(), 0f)
    }

    @Test
    fun `M15 a wrap at some scale is respected`() {
        // The line wraps from 1.7 on: its height jumps by 40.
        val wrapping: (Float) -> Float = { s -> measured(s) + if (s >= 1.7f) 40f else 0f }
        val result = scale(available = FIXED + measured(2f), fontScale = 2f, heightAt = wrapping)
        assertTrue("scale $result", result < 1.7f)
        assertTrue(FIXED + wrapping(result) <= FIXED + measured(2f))
    }

    private companion object {
        const val FIXED = 134f
    }
}
