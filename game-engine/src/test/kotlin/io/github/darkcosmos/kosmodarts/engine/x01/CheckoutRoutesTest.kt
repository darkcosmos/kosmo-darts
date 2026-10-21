package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * CS1 to CS3 (docs/rules/checkout-suggestions.md). Expected routes come from the published rules and the owner's
 * reference table, not from the code; the exhaustive test uses its own brute force and its own sort key.
 */
class CheckoutRoutesTest {

    private fun route(vararg codes: String): List<Dart> = codes.map(::dart)

    private fun suggest(remaining: Int, dartsLeft: Int, rule: CheckoutRule = CheckoutRule.DOUBLE_OUT) =
        CheckoutRoutes.suggest(remaining, dartsLeft, rule)

    /** CS3: the owner's reference table (issue #38, single written without "S"). */
    @Test
    fun `CS3 reference table`() {
        val double = CheckoutRule.DOUBLE_OUT
        val cases = listOf(
            Triple(170 to 3, double, route("T20", "T20", "BULL")),
            Triple(167 to 3, double, route("T20", "T19", "BULL")),
            Triple(160 to 3, double, route("T20", "T20", "D20")),
            Triple(110 to 2, double, route("T20", "BULL")),
            Triple(100 to 3, double, route("T20", "D20")),
            Triple(60 to 3, double, route("20", "D20")),
            Triple(50 to 3, double, route("BULL")),
            Triple(40 to 1, double, route("D20")),
            Triple(3 to 3, double, route("1", "D1")),
            Triple(101 to 1, double, null),
            Triple(159 to 3, double, null),
            Triple(180 to 3, CheckoutRule.SINGLE_OUT, route("T20", "T20", "T20")),
        )
        cases.forEach { (input, rule, expected) ->
            assertEquals(expected, suggest(input.first, input.second, rule), "$input $rule")
        }
    }

    /** CS3.1: the fewest darts. */
    @Test
    fun `CS3_1 fewest darts`() {
        assertEquals(route("T20", "D20"), suggest(100, 3))
        assertEquals(route("BULL"), suggest(50, 3))
        assertEquals(route("T20"), suggest(60, 3, CheckoutRule.SINGLE_OUT))
    }

    /** CS3.2: preferred finishing doubles, then the other doubles from highest to lowest, the bull last. */
    @Test
    fun `CS3_2 finishing double preference`() {
        assertEquals(route("D16"), suggest(32, 1))
        assertEquals(route("16", "D20"), suggest(56, 2))
        assertEquals(route("D19"), suggest(38, 1))
        assertEquals(route("T20", "BULL"), suggest(110, 2))
        // 36 in 2 darts: D18 alone is one dart; with 2 darts left the single dart still wins (CS3.1).
        assertEquals(route("D18"), suggest(36, 2))
        // 22 in 2 darts: D11 alone (one dart) beats any two-dart route.
        assertEquals(route("D11"), suggest(22, 2))
        // 41 in 2 darts: D20 preferred over D16 and the others: 1 then D20.
        assertEquals(route("1", "D20"), suggest(41, 2))
    }

    /** CS3.3: the preceding darts, the highest value first. */
    @Test
    fun `CS3_3 strongest preceding darts first`() {
        assertEquals(route("T20", "T19", "BULL"), suggest(167, 3))
        assertEquals(route("T20", "T20", "T20"), suggest(180, 3, CheckoutRule.SINGLE_OUT))
        assertEquals(route("T20", "20"), suggest(80, 2, CheckoutRule.SINGLE_OUT))
    }

    /** CS3.4: at equal value, single, then double, then triple. */
    @Test
    fun `CS3_4 easiest dart at equal value`() {
        assertEquals(route("20", "D20"), suggest(60, 3, CheckoutRule.DOUBLE_OUT))
        assertEquals(route("18"), suggest(18, 1, CheckoutRule.SINGLE_OUT))
        assertEquals(route("D15"), suggest(30, 1, CheckoutRule.SINGLE_OUT))
        assertEquals(route("D18"), suggest(36, 1, CheckoutRule.SINGLE_OUT))
    }

    /** CS2: no suggestion when no route exists, for scores under 1, and with no dart left. */
    @Test
    fun `CS2 no suggestion`() {
        CheckoutRule.entries.forEach { rule ->
            listOf(0, -1, 181, 501, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
                assertNull(suggest(it, 3, rule), "$it $rule")
            }
            assertNull(suggest(40, 0, rule))
        }
        assertNull(suggest(1, 3, CheckoutRule.DOUBLE_OUT))
        assertNull(suggest(90, 1, CheckoutRule.DOUBLE_OUT))
        assertNull(suggest(61, 1, CheckoutRule.SINGLE_OUT))
    }

    /** CS1: the number of darts left must be 0 to 3. */
    @Test
    fun `CS1 darts left out of range is rejected`() {
        assertThrows<IllegalArgumentException> { suggest(40, -1) }
        assertThrows<IllegalArgumentException> { suggest(40, 4) }
    }

    /**
     * CS1 to CS3, exhaustive (issue #38): for every remaining 1 to 180, 1 to 3 darts left and both rules, compared
     * with a brute force over every sequence of darts of the board, misses included.
     */
    @Test
    fun `CS1 to CS3 exhaustive brute force`() {
        val sequencesBySum = allSequences().groupBy { it.sumOf(Dart::value) }
        CheckoutRule.entries.forEach { rule ->
            (1..DARTS_PER_TURN).forEach { dartsLeft ->
                (1..MAX_SCORE).forEach { remaining ->
                    checkAgainstBruteForce(sequencesBySum[remaining].orEmpty(), remaining, dartsLeft, rule)
                }
            }
        }
    }

    /** [sequences] are all the sequences of 1 to 3 darts that total [remaining]. */
    private fun checkAgainstBruteForce(sequences: List<List<Dart>>, remaining: Int, dartsLeft: Int, rule: CheckoutRule) {
        val label = "$remaining with $dartsLeft darts, $rule"
        val valid = sequences.filter { it.size <= dartsLeft && finishes(it, rule) }
        val suggestion = suggest(remaining, dartsLeft, rule)
        assertEquals(valid.isNotEmpty(), suggestion != null, label)
        if (suggestion == null) return
        assertEquals(remaining, suggestion.sumOf(Dart::value), label)
        assertTrue(finishes(suggestion, rule), label)
        assertTrue(suggestion.size <= dartsLeft, label)
        assertTrue(suggestion.none { it == Dart.MISS }, label)
        assertEquals(valid.minOf { it.size }, suggestion.size, label)
        val expected = valid
            .filter { route -> route.none { it == Dart.MISS } }
            .minWith { a, b -> compareKeys(sortKey(a, rule), sortKey(b, rule)) }
        assertEquals(expected, suggestion, label)
    }

    private fun finishes(route: List<Dart>, rule: CheckoutRule): Boolean {
        val last = route.last()
        return last != Dart.MISS && (rule == CheckoutRule.SINGLE_OUT || last.countsAsDouble)
    }

    /**
     * Sort key written from the CS3 text: number of darts; in double out the rank of the last dart in "D20, D16, D8,
     * D18, D12, D10, the other doubles from highest to lowest, the bull last"; then each compared dart as (minus its
     * value, then 1 for a single, 2 for a double, 3 for a triple).
     */
    private fun sortKey(route: List<Dart>, rule: CheckoutRule): List<Int> {
        val compared = if (rule == CheckoutRule.DOUBLE_OUT) route.dropLast(1) else route
        val lastRank = if (rule == CheckoutRule.DOUBLE_OUT) listOf(DOUBLE_ORDER.indexOf(route.last())) else emptyList()
        return listOf(route.size) + lastRank + compared.flatMap { listOf(-it.value, it.multiplier.factor) }
    }

    private fun compareKeys(a: List<Int>, b: List<Int>): Int =
        a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: a.size.compareTo(b.size)

    private fun allSequences(): List<List<Dart>> {
        val board = Dart.NUMBERS.flatMap { listOf(Dart.single(it), Dart.double(it), Dart.triple(it)) } +
            Dart.OUTER_BULL + Dart.BULL + Dart.MISS
        return generateSequence(board.map(::listOf)) { previous -> previous.flatMap { seq -> board.map(seq::plus) } }
            .take(DARTS_PER_TURN)
            .flatten()
            .toList()
    }

    private companion object {
        const val MAX_SCORE = 180

        val DOUBLE_ORDER: List<Dart> =
            listOf(20, 16, 8, 18, 12, 10, 19, 17, 15, 14, 13, 11, 9, 7, 6, 5, 4, 3, 2, 1).map(Dart::double) + Dart.BULL
    }
}
