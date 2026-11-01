package io.github.darkcosmos.kosmodarts.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ratio that fits a big number in its area (CR22 for Cricket, design system 6.3 for the X01 score). */
class FitScaleTest {

    @Test
    fun `an area larger than the reference never grows the number`() {
        assertEquals(1f, fitScale(1000, 1000, 200, 100), 0f)
    }

    @Test
    fun `the width limits`() {
        assertEquals(0.5f, fitScale(100, 1000, 200, 100), 0f)
    }

    @Test
    fun `the height limits with one pixel less`() {
        assertEquals(0.5f, fitScale(1000, 51, 100, 100), 0f)
    }

    @Test
    fun `a height equal to the reference leaves one pixel out`() {
        assertEquals(0.99f, fitScale(100, 100, 50, 100), 0f)
    }

    @Test
    fun `a zero reference side counts as one pixel`() {
        val scale = fitScale(100, 100, 0, 0)

        assertTrue(scale.isFinite())
        assertEquals(1f, scale, 0f)
    }

    @Test
    fun `a zero or one pixel high area uses one pixel`() {
        assertEquals(1f / 100, fitScale(1000, 0, 100, 100), 0f)
        assertEquals(1f / 100, fitScale(1000, 1, 100, 100), 0f)
    }

    @Test
    fun `a zero wide area gives zero`() {
        assertEquals(0f, fitScale(0, 1000, 100, 100), 0f)
    }
}
