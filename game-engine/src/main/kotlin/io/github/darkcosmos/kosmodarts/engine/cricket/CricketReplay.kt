package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketPlayerState.Companion.CLOSING_MARKS
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Maximum number of darts in a Cricket turn (CR7). */
private const val DARTS_PER_TURN = 3

/**
 * Derives a [CricketState] from a configuration and an ordered list of darts (CR3 to CR12, CR15). The leg starts with
 * the player at [startingPlayerIndex] (CR7; docs/rules/match.md, M6).
 */
internal class CricketReplay(private val config: CricketConfig, private val startingPlayerIndex: Int) {

    fun replay(darts: List<Dart>): CricketState =
        darts.fold(Progress.start(config, startingPlayerIndex), ::record).toState()

    private fun record(progress: Progress, dart: Dart): Progress {
        val thrower = progress.currentPlayer ?: throw CricketLegFinishedException()
        val dartMarks = CricketMarks.of(dart, config)
        val scored = dartMarks.target?.let { progress.withMarks(thrower, it, dartMarks.count) } ?: progress
        val counted = scored.copy(
            dartsThrown = scored.dartsThrown.increment(thrower, 1),
            marksHit = scored.marksHit.increment(thrower, dartMarks.count),
            turnDarts = scored.turnDarts + dart,
        )
        val winner = winnerAfterDartOf(counted, thrower)
        return when {
            winner != null -> counted.endTurn(winner, marksOf(counted.turnDarts))
            counted.turnDarts.size == DARTS_PER_TURN -> counted.endTurn(winner = null, marksOf(counted.turnDarts))
            else -> counted
        }
    }

    /** CR3 to CR6: closes [target] for [thrower], then scores the surplus marks under the variant. */
    private fun Progress.withMarks(thrower: Int, target: CricketTarget, count: Int): Progress {
        val before = marks[thrower].getValue(target)
        val surplus = count - minOf(count, CLOSING_MARKS - before)
        val openOpponents = marks.indices.filter { it != thrower && marks[it].getValue(target) < CLOSING_MARKS }
        val gain = surplus * target.points
        val newPoints = when {
            gain == 0 || openOpponents.isEmpty() -> points
            config.variant == CricketVariant.STANDARD -> points.increment(thrower, gain)
            else -> points.mapIndexed { index, value -> if (index in openOpponents) value + gain else value }
        }
        val newMarks = marks.mapIndexed { index, playerMarks ->
            if (index == thrower) playerMarks + (target to minOf(CLOSING_MARKS, before + count)) else playerMarks
        }
        return copy(marks = newMarks, points = newPoints)
    }

    /** CR9: the thrower first, then the other players in play order after him; `null` when nobody meets CR8. */
    private fun winnerAfterDartOf(progress: Progress, thrower: Int): Int? {
        val playerCount = progress.points.size
        return (0 until playerCount)
            .map { offset -> (thrower + offset) % playerCount }
            .firstOrNull { progress.meetsWinCondition(it) }
    }

    /** CR8: every target in play closed, and points not behind any opponent's in the variant's direction. */
    private fun Progress.meetsWinCondition(player: Int): Boolean {
        val hasClosedAll = config.targets.all { marks[player].getValue(it) >= CLOSING_MARKS }
        return hasClosedAll && points.indices.filter { it != player }.all { opponent ->
            when (config.variant) {
                CricketVariant.STANDARD -> points[player] >= points[opponent]
                CricketVariant.CUT_THROAT -> points[player] <= points[opponent]
            }
        }
    }

    private fun Progress.toState(): CricketState {
        val players = config.players.mapIndexed { index, name ->
            val turnDarts = if (index == currentPlayer) turnDarts else emptyList()
            CricketPlayerState(
                name = name,
                marks = marks[index],
                points = points[index],
                dartsThrown = dartsThrown[index],
                marksHit = marksHit[index],
                currentTurnDarts = turnDarts,
                currentTurnMarks = marksOf(turnDarts),
            )
        }
        return CricketState(
            players = players,
            currentPlayerIndex = currentPlayer,
            winnerIndex = winner,
            ranking = winner?.let { ranking(players, it) }.orEmpty(),
            completedTurns = completedTurns,
            deadTargets = config.targets.filterTo(LinkedHashSet()) { target -> players.all { it.hasClosed(target) } },
        )
    }

    /**
     * CR12: the winner first, then by points in the variant's direction, then by board marks (more is better). Equal
     * players share a rank and the next ranks are skipped (1, 2, 2, 4).
     */
    private fun ranking(players: List<CricketPlayerState>, winner: Int): List<CricketRank> {
        val pointsOrder = when (config.variant) {
            CricketVariant.STANDARD -> compareByDescending<CricketPlayerState> { it.points }
            CricketVariant.CUT_THROAT -> compareBy { it.points }
        }
        val order = pointsOrder.thenByDescending { it.boardMarks }
        fun isAhead(other: Int, player: Int): Boolean = when {
            other == player || player == winner -> false
            other == winner -> true
            else -> order.compare(players[other], players[player]) < 0
        }
        return players.indices
            .map { player -> CricketRank(1 + players.indices.count { isAhead(it, player) }, player) }
            .sortedBy(CricketRank::rank)
    }

    private fun marksOf(darts: List<Dart>): Int = darts.sumOf { CricketMarks.of(it, config).count }

    /** Intermediate replay state. [currentPlayer] is `null` once the leg is won. */
    private data class Progress(
        val marks: List<Map<CricketTarget, Int>>,
        val points: List<Int>,
        val dartsThrown: List<Int>,
        val marksHit: List<Int>,
        val currentPlayer: Int?,
        val turnDarts: List<Dart>,
        val completedTurns: List<CricketTurn>,
        val winner: Int?,
    ) {
        /**
         * Records the turn in progress, with its [turnMarks], as completed, then passes to the next player, or ends
         * the leg when [winner] is set (CR7).
         */
        fun endTurn(winner: Int?, turnMarks: Int): Progress {
            val player = checkNotNull(currentPlayer)
            return copy(
                currentPlayer = if (winner != null) null else (player + 1) % points.size,
                turnDarts = emptyList(),
                completedTurns = completedTurns + CricketTurn(player, turnDarts, turnMarks),
                winner = winner,
            )
        }

        companion object {
            fun start(config: CricketConfig, startingPlayerIndex: Int): Progress = Progress(
                marks = config.players.map { config.targets.associateWith { 0 } },
                points = config.players.map { 0 },
                dartsThrown = config.players.map { 0 },
                marksHit = config.players.map { 0 },
                currentPlayer = startingPlayerIndex,
                turnDarts = emptyList(),
                completedTurns = emptyList(),
                winner = null,
            )
        }
    }

    private fun List<Int>.increment(index: Int, amount: Int): List<Int> =
        mapIndexed { i, value -> if (i == index) value + amount else value }
}
