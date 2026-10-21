package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AN3 (docs/rules/announcements.md). The oracles below come from the published rule, not from the code. */
class CheckoutsTest {

    /** AN3: well-known double-out checkouts. */
    @Test
    fun `AN3 double out possible checkouts`() {
        listOf(170, 167, 164, 161, 160, 100, 50, 40, 2).forEach {
            assertTrue(Checkouts.isPossible(it, CheckoutRule.DOUBLE_OUT), "$it should be possible")
        }
    }

    /** AN3: the "bogey numbers" and 1 cannot be finished in double out. */
    @Test
    fun `AN3 double out impossible checkouts`() {
        listOf(169, 168, 166, 165, 163, 162, 159, 1).forEach {
            assertFalse(Checkouts.isPossible(it, CheckoutRule.DOUBLE_OUT), "$it should not be possible")
        }
    }

    /** AN3: in double out, exactly 2 to 170 minus the seven bogey numbers can be finished. */
    @Test
    fun `AN3 double out possible set is exact`() {
        val expected = (2..170).toSet() - setOf(169, 168, 166, 165, 163, 162, 159)
        val actual = (-5..600).filter { Checkouts.isPossible(it, CheckoutRule.DOUBLE_OUT) }.toSet()
        assertEquals(expected, actual)
    }

    /** AN3: single out, possible scores. */
    @Test
    fun `AN3 single out possible checkouts`() {
        listOf(180, 177, 171, 2, 1).forEach {
            assertTrue(Checkouts.isPossible(it, CheckoutRule.SINGLE_OUT), "$it should be possible")
        }
    }

    /** AN3: single out, scores that no three darts can make. */
    @Test
    fun `AN3 single out impossible checkouts`() {
        listOf(179, 178, 176, 175, 173, 172, 169, 166, 163).forEach {
            assertFalse(Checkouts.isPossible(it, CheckoutRule.SINGLE_OUT), "$it should not be possible")
        }
    }

    /** AN3 and CS2: a checkout is possible exactly when the suggestion search finds a route with three darts. */
    @Test
    fun `AN3 matches the checkout suggestion search`() {
        CheckoutRule.entries.forEach { rule ->
            (-5..600).forEach {
                assertEquals(CheckoutRoutes.suggest(it, 3, rule) != null, Checkouts.isPossible(it, rule), "$it $rule")
            }
        }
    }

    /** AN3: scores under 1 or over 180, and the start scores, are never possible. */
    @Test
    fun `AN3 out of range scores are never possible`() {
        CheckoutRule.entries.forEach { rule ->
            listOf(0, -1, 181, 301, 501, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
                assertFalse(Checkouts.isPossible(it, rule), "$it should not be possible in $rule")
            }
        }
    }
}
