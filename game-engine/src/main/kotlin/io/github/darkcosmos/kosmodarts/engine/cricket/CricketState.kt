package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * Snapshot of a Cricket leg, derived from a [CricketGame] (docs/rules/cricket.md; X01 rule P1).
 *
 * @property players one entry per player, in configuration order.
 * @property currentPlayerIndex index of the player who throws next, or `null` once the leg is finished.
 * @property winnerIndex index of the player who won the leg (CR8, CR9), or `null` while the leg is in progress.
 * @property ranking final ranking once the leg is finished (CR12), empty while it is in progress.
 * @property completedTurns every completed turn, in throw order, the winning turn included. The turn in progress is
 *   not included.
 * @property deadTargets the targets in play closed by every player (CR15).
 */
data class CricketState(
    val players: List<CricketPlayerState>,
    val currentPlayerIndex: Int?,
    val winnerIndex: Int?,
    val ranking: List<CricketRank>,
    val completedTurns: List<CricketTurn>,
    val deadTargets: Set<CricketTarget>,
) {
    /** Whether a player has won the leg (CR9). */
    val isFinished: Boolean
        get() = winnerIndex != null
}

/**
 * Per-player figures of a Cricket leg.
 *
 * @property marks marks per target in play, capped at [CLOSING_MARKS] (CR3); every target in play has an entry.
 * @property points points of the player (CR4 to CR6); includes the darts of the turn in progress.
 * @property dartsThrown all darts thrown by the player, misses included.
 * @property marksHit the CR2 marks of all the player's darts, surplus marks and marks on dead targets included (CR13).
 * @property currentTurnDarts darts of the player's turn in progress; empty when the player is not throwing.
 * @property currentTurnMarks the CR2 marks of [currentTurnDarts] (CR14); 0 when the player is not throwing.
 */
data class CricketPlayerState(
    val name: String,
    val marks: Map<CricketTarget, Int>,
    val points: Int,
    val dartsThrown: Int,
    val marksHit: Int,
    val currentTurnDarts: List<Dart>,
    val currentTurnMarks: Int,
) {
    /** The player's marks on [target], 0 for a target not in play. */
    fun marksOn(target: CricketTarget): Int = marks[target] ?: 0

    /** Whether the player has closed [target] (CR3). Always false for a target not in play. */
    fun hasClosed(target: CricketTarget): Boolean = marksOn(target) >= CLOSING_MARKS

    /** Board marks: the sum over the targets of the marks, each capped at [CLOSING_MARKS] (CR12). */
    val boardMarks: Int
        get() = marks.values.sum()

    /**
     * Number of targets in play the player has closed (CR3), from 0 to `CricketConfig.targets.size` (6 or 7, CR1).
     * The app shows it against that size and never counts marks itself.
     */
    val closedTargetCount: Int
        get() = marks.values.count { it >= CLOSING_MARKS }

    companion object {
        /** Marks that close a target (CR3). */
        const val CLOSING_MARKS: Int = 3
    }
}

/**
 * Place of a player at the end of a leg (CR12). Tied players share a [rank] and the next rank is skipped.
 *
 * @property playerIndex index of the player in the configuration.
 */
data class CricketRank(val rank: Int, val playerIndex: Int)

/**
 * A completed turn: three darts, or fewer when it ended on a win (CR7).
 *
 * @property playerIndex index of the player who threw it.
 * @property darts darts of the turn, in throw order.
 * @property marks the CR2 marks of [darts] (CR14).
 */
data class CricketTurn(val playerIndex: Int, val darts: List<Dart>, val marks: Int)
