package io.github.darkcosmos.kosmodarts.engine.x01

/** Whether a remaining score can be finished in one turn (docs/rules/announcements.md, rule AN3). */
object Checkouts {

    /**
     * AN3: whether at most [DARTS_PER_TURN] darts can score exactly [remaining], the last one finishing under [rule]
     * (a double or the bull in double out, rule D3; any scoring dart in single out). Uses the same search as the
     * checkout suggestion (docs/rules/checkout-suggestions.md, CS2).
     */
    fun isPossible(remaining: Int, rule: CheckoutRule): Boolean =
        CheckoutRoutes.suggest(remaining, DARTS_PER_TURN, rule) != null
}
