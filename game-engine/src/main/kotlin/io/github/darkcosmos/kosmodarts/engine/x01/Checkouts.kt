package io.github.darkcosmos.kosmodarts.engine.x01

/** Whether a remaining score can be finished in one turn (docs/rules/announcements.md, rule AN3). */
object Checkouts {

    /** Every dart that scores: singles, doubles and triples of 1 to 20, the outer bull and the bull (rule D1). */
    private val scoringDarts: List<Dart> =
        Dart.NUMBERS.flatMap { number -> Multiplier.entries.map { Dart(number, it) } } + Dart.OUTER_BULL + Dart.BULL

    private val possibleScores: Map<CheckoutRule, Set<Int>> =
        CheckoutRule.entries.associateWith { rule -> computePossibleScores(rule) }

    /**
     * AN3: whether at most [DARTS_PER_TURN] darts can score exactly [remaining], the last one finishing under [rule]
     * (a double or the bull in double out, rule D3; any scoring dart in single out). Computed by enumeration.
     */
    fun isPossible(remaining: Int, rule: CheckoutRule): Boolean = remaining in possibleScores.getValue(rule)

    private fun computePossibleScores(rule: CheckoutRule): Set<Int> {
        val finishingDarts = scoringDarts.filter { rule == CheckoutRule.SINGLE_OUT || it.countsAsDouble }
        // Sums of 0 to DARTS_PER_TURN - 1 setup darts. Misses are not enumerated: they never help to finish.
        var setupSums = setOf(0)
        var allSetupSums = setupSums
        repeat(DARTS_PER_TURN - 1) {
            setupSums = setupSums.flatMap { sum -> scoringDarts.map { sum + it.value } }.toSet()
            allSetupSums = allSetupSums + setupSums
        }
        return allSetupSums.flatMap { sum -> finishingDarts.map { sum + it.value } }.toSet()
    }
}
