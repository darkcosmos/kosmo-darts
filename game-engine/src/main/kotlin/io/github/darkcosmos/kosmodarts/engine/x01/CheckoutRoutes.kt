package io.github.darkcosmos.kosmodarts.engine.x01

/**
 * Checkout routes (docs/rules/checkout-suggestions.md, rules CS1 to CS3): the darts that finish a remaining score
 * with the darts left in a turn. Computed by enumeration on every call; there is no table of routes in the code.
 */
object CheckoutRoutes {

    /** Every scoring dart: singles, doubles and triples of 1 to 20, the outer bull and the bull (CS1, rule D1). */
    private val scoringDarts: List<Dart> =
        Dart.NUMBERS.flatMap { number -> Multiplier.entries.map { Dart(number, it) } } + Dart.OUTER_BULL + Dart.BULL

    private val highestDartValue: Int = scoringDarts.maxOf { it.value }

    /** Finishing darts of each rule, grouped by value (CS1). */
    private val finishingDartsByValue: Map<CheckoutRule, Map<Int, List<Dart>>> =
        CheckoutRule.entries.associateWith { rule ->
            scoringDarts.filter { rule == CheckoutRule.SINGLE_OUT || it.countsAsDouble }.groupBy { it.value }
        }

    /** Setup darts placed before the finishing dart: index n holds every sequence of n scoring darts. */
    private val setupsBySize: List<List<List<Dart>>> =
        generateSequence(listOf(emptyList<Dart>())) { setups ->
            setups.flatMap { setup -> scoringDarts.map(setup::plus) }
        }
            .take(DARTS_PER_TURN)
            .toList()

    /** CS3.2: preferred finishing doubles, in order; the other doubles follow from highest to lowest, the bull last. */
    private val preferredDoubles: List<Int> = listOf(20, 16, 8, 18, 12, 10)

    /**
     * The suggested route for [remaining] with [dartsLeft] darts left under [rule] (CS1 to CS3), or `null` when no
     * route exists (CS2), including when [remaining] is under 1 or [dartsLeft] is 0.
     *
     * @throws IllegalArgumentException if [dartsLeft] is outside 0 to 3.
     */
    fun suggest(remaining: Int, dartsLeft: Int, rule: CheckoutRule): List<Dart>? {
        require(dartsLeft in 0..DARTS_PER_TURN) { "Darts left must be in 0..$DARTS_PER_TURN, was $dartsLeft" }
        if (remaining < 1 || remaining > dartsLeft * highestDartValue) return null
        val finishingDarts = finishingDartsByValue.getValue(rule)
        val order = routeOrder(rule)
        // CS3.1: the first length that has a route gives the fewest darts.
        return (1..dartsLeft).firstNotNullOfOrNull { length ->
            setupsBySize[length - 1]
                .flatMap { setup ->
                    finishingDarts[remaining - setup.sumOf(Dart::value)].orEmpty().map(setup::plus)
                }
                .minWithOrNull(order)
        }
    }

    /** CS3.2 to CS3.4 for routes of the same length. */
    private fun routeOrder(rule: CheckoutRule): Comparator<List<Dart>> = when (rule) {
        CheckoutRule.DOUBLE_OUT ->
            compareBy<List<Dart>> { finishingPreference(it.last()) }
                .then { a, b -> compareDarts(a.dropLast(1), b.dropLast(1)) }
        CheckoutRule.SINGLE_OUT -> Comparator(::compareDarts)
    }

    /** CS3.2: rank of a finishing dart in double out, lower first. */
    private fun finishingPreference(dart: Dart): Int = when {
        dart == Dart.BULL -> Int.MAX_VALUE
        dart.segment in preferredDoubles -> preferredDoubles.indexOf(dart.segment)
        else -> preferredDoubles.size + Dart.NUMBERS.last - dart.segment
    }

    /** CS3.3, CS3.4: position by position, the highest value first, then the easiest dart (single, double, triple). */
    private fun compareDarts(first: List<Dart>, second: List<Dart>): Int =
        first.zip(second).firstNotNullOfOrNull { (a, b) -> dartOrder.compare(a, b).takeIf { it != 0 } }
            ?: first.size.compareTo(second.size)

    private val dartOrder: Comparator<Dart> = compareByDescending<Dart> { it.value }.thenBy { it.multiplier.factor }
}
