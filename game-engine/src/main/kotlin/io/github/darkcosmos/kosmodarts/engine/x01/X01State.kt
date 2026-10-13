package io.github.darkcosmos.kosmodarts.engine.x01

/**
 * Snapshot of an X01 leg, derived from an [X01Game] (rule P1).
 *
 * @property players one entry per player, in configuration order.
 * @property currentPlayerIndex index of the player who throws next, or `null` once the leg is finished.
 * @property winnerIndex index of the player who checked out, or `null` while the leg is in progress.
 * @property ranking final ranking once the leg is finished (rules E1, E2), empty while it is in progress.
 * @property completedTurns every completed turn, in throw order (rule A3). The turn in progress is not included.
 */
data class X01State(
    val players: List<X01PlayerState>,
    val currentPlayerIndex: Int?,
    val winnerIndex: Int?,
    val ranking: List<X01Rank>,
    val completedTurns: List<X01Turn>,
) {
    /** Whether a player has checked out (rule S5). */
    val isFinished: Boolean
        get() = winnerIndex != null
}

/**
 * Per-player figures of an X01 leg (rule A2).
 *
 * @property remaining score still to be scored; includes the darts of the turn in progress.
 * @property dartsThrown all darts thrown by the player, including bust darts.
 * @property threeDartAverage points scored / darts thrown x 3 (rule A1), or `null` if no dart was thrown.
 * @property currentTurnDarts darts of the player's turn in progress; empty when the player is not throwing.
 */
data class X01PlayerState(
    val name: String,
    val remaining: Int,
    val dartsThrown: Int,
    val threeDartAverage: Double?,
    val currentTurnDarts: List<Dart>,
)

/**
 * Place of a player at the end of a leg (rules E1, E2). Tied players share a [rank] and the next rank is skipped.
 *
 * @property playerIndex index of the player in the configuration.
 */
data class X01Rank(val rank: Int, val playerIndex: Int)

/** How a completed turn ended (rule A3). */
enum class TurnOutcome {
    /** Three darts, without bust or checkout. */
    NORMAL,

    /** The last dart of the turn was a bust (rule S4): the score went back to [X01Turn.remainingBefore]. */
    BUST,

    /** The last dart of the turn won the leg (rule S5). */
    CHECKOUT,
}

/**
 * A completed turn (rule A3).
 *
 * @property playerIndex index of the player in the configuration.
 * @property darts darts of the turn, in throw order, the bust or winning dart included.
 * @property remainingBefore player's remaining score at the start of the turn.
 * @property remainingAfter player's remaining score at the end of the turn: equal to [remainingBefore] on a bust,
 *   0 on a checkout.
 */
data class X01Turn(
    val playerIndex: Int,
    val darts: List<Dart>,
    val outcome: TurnOutcome,
    val remainingBefore: Int,
    val remainingAfter: Int,
)
