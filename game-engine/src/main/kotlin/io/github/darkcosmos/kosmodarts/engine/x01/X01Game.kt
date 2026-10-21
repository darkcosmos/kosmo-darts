package io.github.darkcosmos.kosmodarts.engine.x01

/** Thrown when a dart is recorded after the leg has been won (rule S5). */
class LegFinishedException : IllegalStateException("The leg is finished: no further dart can be recorded")

/**
 * An X01 leg: a configuration plus the ordered list of darts thrown (rule P1). Immutable: every action returns a new
 * game. The [state] is derived by replaying [darts] from [config], so the same input always yields the same state.
 *
 * @throws LegFinishedException if [darts] contains a dart after the winning one.
 */
class X01Game(val config: X01Config, darts: List<Dart> = emptyList()) {

    /** All recorded darts, in throw order, bust darts and the winning dart included. */
    val darts: List<Dart> = darts.toList()

    /** Current scores, turn and result. */
    val state: X01State = X01Replay(config).replay(this.darts)

    /** Whether there is a dart to undo (rule U2). */
    val canUndo: Boolean
        get() = darts.isNotEmpty()

    /**
     * Suggested checkout route of the engine current player (docs/rules/checkout-suggestions.md, CS4): for their
     * remaining score, the darts left in their turn and the game's checkout rule. `null` when the leg is finished,
     * when no route exists, or when the darts left are outside 1 to 3 (never an exception).
     */
    val checkoutSuggestion: List<Dart>? by lazy {
        val player = state.currentPlayerIndex?.takeUnless { state.isFinished }?.let { state.players[it] }
        val dartsLeft = player?.let { DARTS_PER_TURN - it.currentTurnDarts.size }
        if (player == null || dartsLeft == null || dartsLeft !in 1..DARTS_PER_TURN) {
            null
        } else {
            CheckoutRoutes.suggest(player.remaining, dartsLeft, config.checkoutRule)
        }
    }

    /**
     * Records [dart] for the current player (rules S1 to S5).
     *
     * @throws LegFinishedException if the leg is already won.
     */
    fun throwDart(dart: Dart): X01Game {
        if (state.isFinished) throw LegFinishedException()
        return X01Game(config, darts + dart)
    }

    /** Removes the last recorded dart, whoever threw it (rules U1 to U3). Returns this game if there is none. */
    fun undo(): X01Game = if (canUndo) X01Game(config, darts.dropLast(1)) else this

    override fun equals(other: Any?): Boolean = other is X01Game && config == other.config && darts == other.darts

    override fun hashCode(): Int = config.hashCode() * HASH_PRIME + darts.hashCode()

    override fun toString(): String = "X01Game(config=$config, darts=$darts)"

    private companion object {
        const val HASH_PRIME = 31
    }
}
