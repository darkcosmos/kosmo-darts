package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** DJ3: bust flags of the X01 darts, per finish mode, on 501 and on 301. */
class LegReplayBustTest {

    /** Two visits of 180: 501 leaves 141. */
    private val to141 = Array(6) { "T20" }

    /** One visit of 180: 301 leaves 121. */
    private val to121 = Array(3) { "T20" }

    private fun setup(startScore: Int, rule: CheckoutRule) =
        x01Setup("Alice", startScore = startScore, checkoutRule = rule)

    private fun lastVisitBusts(startScore: Int, rule: CheckoutRule, prefix: Array<String>, visit: Array<String>) =
        replay(setup(startScore, rule), *prefix, *visit).busts().takeLast(visit.size)

    @Test
    fun `501 over-score is a bust of the whole visit in both finish modes`() {
        val visit = arrayOf("T20", "T20", "T20")
        CheckoutRule.entries.forEach { rule ->
            assertEquals(listOf(true, true, true), lastVisitBusts(501, rule, to141, visit), "$rule")
        }
    }

    @Test
    fun `501 remainder 1 is a bust in double-out and not in single-out`() {
        val visit = arrayOf("T20", "T20", "20")
        assertEquals(listOf(true, true, true), lastVisitBusts(501, CheckoutRule.DOUBLE_OUT, to141, visit))
        assertEquals(listOf(false, false, false), lastVisitBusts(501, CheckoutRule.SINGLE_OUT, to141, visit))
    }

    @Test
    fun `501 finish on a non-double is a bust in double-out and a win in single-out`() {
        val visit = arrayOf("T20", "T19", "T8")
        assertEquals(listOf(true, true, true), lastVisitBusts(501, CheckoutRule.DOUBLE_OUT, to141, visit))

        val won = replay(setup(501, CheckoutRule.SINGLE_OUT), *to141, *visit)
        assertTrue(won.isFinished)
        assertEquals(listOf(false, false, false), won.busts().takeLast(3))
    }

    @Test
    fun `501 finish on a double wins and is not a bust`() {
        val won = replay(setup(501, CheckoutRule.DOUBLE_OUT), *to141, "T20", "T19", "D12")

        assertTrue(won.isFinished)
        assertFalse(won.busts().any { it })
    }

    @Test
    fun `301 over-score is a bust of the whole visit in both finish modes`() {
        val visit = arrayOf("T20", "T19", "T20")
        CheckoutRule.entries.forEach { rule ->
            assertEquals(listOf(true, true, true), lastVisitBusts(301, rule, to121, visit), "$rule")
        }
    }

    @Test
    fun `301 remainder 1 ends the visit in double-out only`() {
        val visit = arrayOf("T20", "T20")
        assertEquals(listOf(true, true), lastVisitBusts(301, CheckoutRule.DOUBLE_OUT, to121, visit))
        assertEquals(listOf(false, false), lastVisitBusts(301, CheckoutRule.SINGLE_OUT, to121, visit))
    }

    @Test
    fun `301 finish on a non-double is a bust in double-out and a win in single-out`() {
        val visit = arrayOf("T20", "T19", "4")
        assertEquals(listOf(true, true, true), lastVisitBusts(301, CheckoutRule.DOUBLE_OUT, to121, visit))

        val won = replay(setup(301, CheckoutRule.SINGLE_OUT), *to121, *visit)
        assertTrue(won.isFinished)
        assertEquals(listOf(false, false, false), won.busts().takeLast(3))
    }

    @Test
    fun `only the busted visit is flagged`() {
        val leg = replay(setup(301, CheckoutRule.DOUBLE_OUT), *to121, "T20", "T20", "5")

        assertEquals(List(3) { false }, leg.busts().take(3))
        assertEquals(List(2) { true }, leg.busts().slice(3..4))
        assertFalse(leg.busts().last())
        assertEquals(Triple(0, 3, 1), leg.positions().last())
    }

    @Test
    fun `the winning dart is never a bust`() {
        listOf(WIN_301_ONE_DART, WIN_301_TWO_DARTS).forEach { tokens ->
            val leg = replay(setup(301, CheckoutRule.DOUBLE_OUT), *tokens)
            assertTrue(leg.isFinished)
            assertFalse(leg.darts.last().isBust)
        }
    }

    @Test
    fun `Cricket is never a bust`() {
        val leg = replay(cricketSetup("Alice", "Bob"), "20", "MISS", "T19", "25", "BULL", "1", "20")

        assertFalse(leg.busts().any { it })
        assertFalse(replay(cricketSetup("Alice"), *WIN_CRICKET).busts().any { it })
    }
}
