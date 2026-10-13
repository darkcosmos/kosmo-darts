package io.github.darkcosmos.kosmodarts.engine.x01

private const val DARTS_PER_TURN = 3

/** Result of a single dart under rules S2 and S3. */
private enum class DartOutcome { SCORED, BUST, CHECKOUT }

/** Derives an [X01State] from a configuration and an ordered list of darts (rule P1). */
internal class X01Replay(private val config: X01Config) {

    fun replay(darts: List<Dart>): X01State = darts.fold(Progress.start(config), ::record).toState()

    private fun record(progress: Progress, dart: Dart): Progress {
        val player = progress.currentPlayer ?: throw LegFinishedException()
        val counted = progress.copy(dartsThrown = progress.dartsThrown.increment(player))
        val scoreAfter = progress.remaining[player] - dart.value
        return when (outcomeOf(scoreAfter, dart)) {
            DartOutcome.BUST -> counted.withRemaining(player, progress.turnStartScore).nextTurn()
            DartOutcome.CHECKOUT -> counted.withRemaining(player, 0)
                .copy(currentPlayer = null, turnDarts = emptyList(), winner = player)
            DartOutcome.SCORED -> counted.withRemaining(player, scoreAfter).copy(turnDarts = progress.turnDarts + dart)
                .let { if (it.turnDarts.size == DARTS_PER_TURN) it.nextTurn() else it }
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
    )

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
        val winner: Int?,
    ) {
        /** A bust ends the turn, so the score at the start of the turn is the score plus the darts of the turn. */
        val turnStartScore: Int
            get() = remaining[checkNotNull(currentPlayer)] + turnDarts.sumOf(Dart::value)

        fun withRemaining(player: Int, score: Int): Progress =
            copy(remaining = remaining.mapIndexed { index, old -> if (index == player) score else old })

        fun nextTurn(): Progress =
            copy(currentPlayer = (checkNotNull(currentPlayer) + 1) % remaining.size, turnDarts = emptyList())

        companion object {
            fun start(config: X01Config): Progress = Progress(
                remaining = config.players.map { config.startScore },
                dartsThrown = config.players.map { 0 },
                currentPlayer = 0,
                turnDarts = emptyList(),
                winner = null,
            )
        }
    }

    private fun List<Int>.increment(index: Int): List<Int> =
        mapIndexed { i, value -> if (i == index) value + 1 else value }
}
