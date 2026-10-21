package io.github.darkcosmos.kosmodarts.engine.x01

/** Maximum number of darts in a turn (rule T1). */
internal const val DARTS_PER_TURN = 3

/** Result of a single dart under rules S2 and S3. */
private enum class DartOutcome { SCORED, BUST, CHECKOUT }

/**
 * Derives an [X01State] from a configuration and an ordered list of darts (rule P1). The leg starts with the player at
 * [startingPlayerIndex] (rule T1; docs/rules/match.md, M6).
 */
internal class X01Replay(private val config: X01Config, private val startingPlayerIndex: Int = 0) {

    fun replay(darts: List<Dart>): X01State =
        darts.fold(Progress.start(config, startingPlayerIndex), ::record).toState()

    private fun record(progress: Progress, dart: Dart): Progress {
        val player = progress.currentPlayer ?: throw LegFinishedException()
        val turnStartScore = progress.turnStartScore
        val scoreAfter = progress.remaining[player] - dart.value
        val counted = progress.copy(
            dartsThrown = progress.dartsThrown.increment(player),
            turnDarts = progress.turnDarts + dart,
        )
        return when (outcomeOf(scoreAfter, dart)) {
            DartOutcome.BUST -> counted.endTurn(TurnOutcome.BUST, turnStartScore, turnStartScore)
            DartOutcome.CHECKOUT -> counted.endTurn(TurnOutcome.CHECKOUT, turnStartScore, 0)
            DartOutcome.SCORED -> if (counted.turnDarts.size == DARTS_PER_TURN) {
                counted.endTurn(TurnOutcome.NORMAL, turnStartScore, scoreAfter)
            } else {
                counted.withRemaining(player, scoreAfter)
            }
        }
    }

    private fun outcomeOf(scoreAfter: Int, dart: Dart): DartOutcome = when (config.checkoutRule) {
        CheckoutRule.DOUBLE_OUT -> when {
            scoreAfter < 0 || scoreAfter == 1 -> DartOutcome.BUST
            scoreAfter == 0 -> if (dart.countsAsDouble) DartOutcome.CHECKOUT else DartOutcome.BUST
            else -> DartOutcome.SCORED
        }
        CheckoutRule.SINGLE_OUT -> when {
            scoreAfter < 0 -> DartOutcome.BUST
            scoreAfter == 0 -> DartOutcome.CHECKOUT
            else -> DartOutcome.SCORED
        }
    }

    private fun Progress.toState(): X01State = X01State(
        players = config.players.mapIndexed { index, name ->
            X01PlayerState(
                name = name,
                remaining = remaining[index],
                dartsThrown = dartsThrown[index],
                threeDartAverage = threeDartAverage(remaining[index], dartsThrown[index]),
                currentTurnDarts = if (index == currentPlayer) turnDarts else emptyList(),
            )
        },
        currentPlayerIndex = currentPlayer,
        winnerIndex = winner,
        ranking = winner?.let { ranking(remaining, it) }.orEmpty(),
        completedTurns = completedTurns,
    )

    /** Exact value, not rounded (rule A1). */
    private fun threeDartAverage(remaining: Int, dartsThrown: Int): Double? =
        if (dartsThrown == 0) null else (config.startScore - remaining) * DARTS_PER_TURN.toDouble() / dartsThrown

    /** Winner first, then by remaining score ascending; ties share a rank and skip the next ones (1, 2, 2, 4). */
    private fun ranking(remaining: List<Int>, winner: Int): List<X01Rank> {
        val sortKey = remaining.mapIndexed { index, score -> if (index == winner) -1 else score }
        return sortKey.indices
            .map { player -> X01Rank(rank = 1 + sortKey.count { it < sortKey[player] }, playerIndex = player) }
            .sortedBy(X01Rank::rank)
    }

    /** Intermediate replay state. [currentPlayer] is `null` once the leg is won. */
    private data class Progress(
        val remaining: List<Int>,
        val dartsThrown: List<Int>,
        val currentPlayer: Int?,
        val turnDarts: List<Dart>,
        val completedTurns: List<X01Turn>,
        val winner: Int?,
    ) {
        /** A bust ends the turn, so the score at the start of the turn is the score plus the darts of the turn. */
        val turnStartScore: Int
            get() = remaining[checkNotNull(currentPlayer)] + turnDarts.sumOf(Dart::value)

        fun withRemaining(player: Int, score: Int): Progress =
            copy(remaining = remaining.mapIndexed { index, old -> if (index == player) score else old })

        /** Records the turn in progress as completed (rule A3), then passes to the next player (rule T2). */
        fun endTurn(outcome: TurnOutcome, remainingBefore: Int, remainingAfter: Int): Progress {
            val player = checkNotNull(currentPlayer)
            val turn = X01Turn(player, turnDarts, outcome, remainingBefore, remainingAfter)
            val isCheckout = outcome == TurnOutcome.CHECKOUT
            return withRemaining(player, remainingAfter).copy(
                currentPlayer = if (isCheckout) null else (player + 1) % remaining.size,
                turnDarts = emptyList(),
                completedTurns = completedTurns + turn,
                winner = if (isCheckout) player else winner,
            )
        }

        companion object {
            fun start(config: X01Config, startingPlayerIndex: Int): Progress = Progress(
                remaining = config.players.map { config.startScore },
                dartsThrown = config.players.map { 0 },
                currentPlayer = startingPlayerIndex,
                turnDarts = emptyList(),
                completedTurns = emptyList(),
                winner = null,
            )
        }
    }

    private fun List<Int>.increment(index: Int): List<Int> =
        mapIndexed { i, value -> if (i == index) value + 1 else value }
}
