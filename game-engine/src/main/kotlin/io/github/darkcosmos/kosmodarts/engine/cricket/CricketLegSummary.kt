package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketPlayerState.Companion.CLOSING_MARKS

/**
 * What a match keeps of a confirmed Cricket leg (docs/rules/match.md, M8, M9; docs/rules/cricket.md, CR13): its winner
 * and each player's marks hit, darts, points and board marks. Every value is checked against [config], so that a
 * summary read from a save is always a possible leg.
 *
 * @property winnerIndex the player who won the leg (CR8, CR9).
 * @property marksHit the CR2 marks of each player's darts in the leg, in configuration order: at most 3 per dart.
 * @property dartsThrown darts thrown by each player in the leg, misses included.
 * @property points points of each player at the end of the leg (CR4 to CR6), never negative.
 * @property boardMarks board marks of each player at the end of the leg (CR12): from 0 to 3 per target in play, all
 *   of them for the winner (CR8), and never more than [marksHit].
 * @throws IllegalArgumentException if a size, an index or a value is impossible for a Cricket leg.
 */
data class CricketLegSummary(
    val config: CricketConfig,
    val winnerIndex: Int,
    val marksHit: List<Int>,
    val dartsThrown: List<Int>,
    val points: List<Int>,
    val boardMarks: List<Int>,
) {
    init {
        val players = config.players.indices
        val count = config.players.size
        require(winnerIndex in players) { "Winner out of range" }
        require(listOf(marksHit, dartsThrown, points, boardMarks).all { it.size == count }) {
            "One value per player expected"
        }
        val fullBoard = CLOSING_MARKS * config.targets.size
        players.forEach { player ->
            val darts = dartsThrown[player]
            require(darts >= 0) { "Darts out of range" }
            require(marksHit[player] in 0..MAX_MARKS_PER_DART * darts) { "Marks out of range" }
            require(points[player] >= 0) { "Points out of range" }
            require(boardMarks[player] in 0..minOf(fullBoard, marksHit[player])) { "Board marks out of range" }
        }
        require(boardMarks[winnerIndex] == fullBoard) { "The winner has closed every target" }
    }

    companion object {
        /** Most marks a dart can give: a triple (CR2). */
        private const val MAX_MARKS_PER_DART = 3

        /**
         * The summary of a finished [leg].
         *
         * @throws IllegalArgumentException if [leg] is not finished.
         */
        fun of(leg: CricketGame): CricketLegSummary {
            val state = leg.state
            val winner = requireNotNull(state.winnerIndex) { "The leg is not finished" }
            return CricketLegSummary(
                config = leg.config,
                winnerIndex = winner,
                marksHit = state.players.map(CricketPlayerState::marksHit),
                dartsThrown = state.players.map(CricketPlayerState::dartsThrown),
                points = state.players.map(CricketPlayerState::points),
                boardMarks = state.players.map(CricketPlayerState::boardMarks),
            )
        }
    }
}
