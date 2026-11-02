package io.github.darkcosmos.kosmodarts.engine.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OklabTest {
    private fun lab(argb: Long) = Oklab.fromColor(PlayerColor(argb.toInt()))

    @Test
    fun `white is L 1 with no chroma`() {
        val white = lab(0xFFFFFFFF)
        assertEquals(1.0, white.l, 1e-4)
        assertEquals(0.0, white.a, 1e-4)
        assertEquals(0.0, white.b, 1e-4)
    }

    @Test
    fun `black is all zero`() {
        val black = lab(0xFF000000)
        assertEquals(0.0, black.l, 1e-9)
        assertEquals(0.0, black.a, 1e-9)
        assertEquals(0.0, black.b, 1e-9)
    }

    @Test
    fun `pure red matches the reference`() {
        val red = lab(0xFFFF0000)
        assertEquals(0.6280, red.l, 1e-3)
        assertEquals(0.2249, red.a, 1e-3)
        assertEquals(0.1258, red.b, 1e-3)
    }

    @Test
    fun `OKLCH round trips an in gamut color`() {
        val blue = PlayerColor.BLUE
        val lab = Oklab.fromColor(blue)
        val back = Oklab.toColorOrNull(lab.l, kotlin.math.hypot(lab.a, lab.b), kotlin.math.atan2(lab.b, lab.a))
        assertEquals(blue, back)
    }

    @Test
    fun `an out of gamut OKLCH value gives null`() {
        assertNull(Oklab.toColorOrNull(0.5, 0.4, 0.0))
        assertNotNull(Oklab.toColorOrNull(0.6, 0.05, 1.0))
    }
}
