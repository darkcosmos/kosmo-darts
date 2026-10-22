package io.github.darkcosmos.kosmodarts.engine.x01

/**
 * What a match keeps of a confirmed X01 leg (docs/rules/match.md, M8, M9): its winner and each player's points and
 * darts. Every value is checked against [config], so that a summary read from a save is always a possible leg.
 *
 * @property winnerIndex the player who checked out.
 * @property pointsScored points scored by each player in the leg, in configuration order: the start score for the
 *   winner, from 0 to start score - 1 for the others (rule S1).
 * @property dartsThrown darts thrown by each player in the leg, bust darts included (rule A1); at most 60 points per
 *   dart.
 * @throws IllegalArgumentException if a size, an index or a value is impossible for an X01 leg.
 */
data class X01LegSummary(
    val config: X01Config,
    val winnerIndex: Int,
    val pointsScored: List<Int>,
    val dartsThrown: List<Int>,
) {
    init {
        val players = config.players.indices
        require(winnerIndex in players) { "Winner out of range" }
        require(pointsScored.size == config.players.size && dartsThrown.size == config.players.size) {
            "One value per player expected"
        }
        players.forEach { player ->
            val points = pointsScored[player]
            val darts = dartsThrown[player]
            if (player == winnerIndex) {
                require(points == config.startScore) { "The winner scores the start score" }
            } else {
                require(points in 0 until config.startScore) { "Points out of range" }
            }
            require(darts >= 0) { "Darts out of range" }
            require(points <= MAX_POINTS_PER_DART * darts) { "More points than the darts can score" }
        }
    }

    companion object {
        /** Highest value of a dart: triple 20. */
        private const val MAX_POINTS_PER_DART = 60

        /**
         * The summary of a finished [leg].
         *
         * @throws IllegalArgumentException if [leg] is not finished.
         */
        fun of(leg: X01Game): X01LegSummary {
            val state = leg.state
            val winner = requireNotNull(state.winnerIndex) { "The leg is not finished" }
            return X01LegSummary(
                config = leg.config,
                winnerIndex = winner,
                pointsScored = state.players.map { leg.config.startScore - it.remaining },
                dartsThrown = state.players.map(X01PlayerState::dartsThrown),
            )
        }
    }
}
