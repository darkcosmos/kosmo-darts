package io.github.darkcosmos.kosmodarts.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Display of the marks per round (docs/rules/cricket.md, CR13; review T4). */
class MprFormatTest {

    private fun fr(mpr: Double?) = formatMpr(mpr, Locale.FRENCH, NONE)

    private fun en(mpr: Double?) = formatMpr(mpr, Locale.ENGLISH, NONE)

    @Test
    fun `CR13 one decimal with the separator of the language`() {
        assertEquals("2,4", fr(2.4))
        assertEquals("2.4", en(2.4))
    }

    @Test
    fun `CR13 no dart thrown shows the dash, not zero`() {
        assertEquals(NONE, fr(null))
        assertEquals(NONE, en(null))
    }

    @Test
    fun `CR13 rounding is half up`() {
        assertEquals("2,5", fr(2.45))
        assertEquals("2,4", fr(2.449))
        assertEquals("2.5", en(2.45))
    }

    @Test
    fun `CR13 zero and the highest value`() {
        assertEquals("0,0", fr(0.0))
        assertEquals("9,0", fr(9.0))
    }

    private companion object {
        const val NONE = "–"
    }
}
