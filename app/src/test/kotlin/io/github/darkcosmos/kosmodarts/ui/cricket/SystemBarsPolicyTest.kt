package io.github.darkcosmos.kosmodarts.ui.cricket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The system bars of a screen (docs/rules/cricket.md CR22, Kosmo's Q5 B, review U1 and A3). */
class SystemBarsPolicyTest {

    @Test
    fun `CR22 Q5 B the Cricket game in landscape hides the bars and pads the cutout only`() {
        val decision = SystemBarsPolicy.decide(isLandscape = true, isCricketGame = true)

        assertTrue(decision.isHidden)
        assertEquals(InsetsPadding.CUTOUT_ONLY, decision.padding)
    }

    @Test
    fun `CR22 the Cricket game in portrait shows the bars with the safe drawing padding`() {
        val decision = SystemBarsPolicy.decide(isLandscape = false, isCricketGame = true)

        assertFalse(decision.isHidden)
        assertEquals(InsetsPadding.SAFE_DRAWING, decision.padding)
    }

    @Test
    fun `CR22 every other screen shows the bars with the safe drawing padding, in both orientations`() {
        listOf(true, false).forEach { isLandscape ->
            val decision = SystemBarsPolicy.decide(isLandscape = isLandscape, isCricketGame = false)

            assertFalse("landscape $isLandscape", decision.isHidden)
            assertEquals("landscape $isLandscape", InsetsPadding.SAFE_DRAWING, decision.padding)
        }
    }

    @Test
    fun `CR22 hide never comes without the cutout only padding, show never without safe drawing`() {
        listOf(true, false).forEach { isLandscape ->
            listOf(true, false).forEach { isCricketGame ->
                val decision = SystemBarsPolicy.decide(isLandscape, isCricketGame)
                val expected = if (decision.isHidden) InsetsPadding.CUTOUT_ONLY else InsetsPadding.SAFE_DRAWING

                assertEquals("landscape $isLandscape, cricket $isCricketGame", expected, decision.padding)
            }
        }
    }
}
