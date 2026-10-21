package io.github.darkcosmos.kosmodarts.engine.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** M10, M11 (docs/rules/match.md): the ranking only knows sets, legs and a tie-break value given by the mode. */
class MatchRankingTest {

    private fun ranks(vararg pairs: Pair<Int, Int>) = pairs.map { (rank, player) -> MatchRank(rank, player) }

    /** M10: winner first, then sets won, then legs won over the match, then the tie-break (lower is better). */
    @Test
    fun `M10 winner then sets then total legs then tie-break`() {
        // First to 2 legs, 2 sets, 4 players. Set 1 to B (B, B), set 2 to A (C, D, A, A), set 3 and the match to A
        // (A, C, A). C and D have no set: C is ahead on legs (2 to 1) despite a worse tie-break value.
        val score = MatchScore(MatchFormat(2, 2), 4, listOf(1, 1, 2, 3, 0, 0, 0, 2, 0))

        assertEquals(0, score.winnerIndex)
        assertEquals(listOf(2, 1, 0, 0), score.setsWon)
        assertEquals(listOf(4, 2, 2, 1), score.legsWonTotal)
        assertEquals(ranks(1 to 0, 2 to 1, 3 to 2, 4 to 3), MatchRanking.rank(score, listOf(0, 0, 400, 10)))
    }

    /** M10: players equal on every criterion share a rank and the next rank is skipped (1, 2, 2, 4). */
    @Test
    fun `M10 full ties share the rank`() {
        val score = MatchScore(MatchFormat(1, 1), 4, listOf(0))

        assertEquals(
            ranks(1 to 0, 2 to 1, 2 to 3, 4 to 2),
            MatchRanking.rank(score, listOf(0, 40, 100, 40)),
        )
    }

    /** M10: the tie-break needs one value per player. */
    @Test
    fun `M10 tie-break size is checked`() {
        val score = MatchScore(MatchFormat(), 2, listOf(0))

        assertThrows<IllegalArgumentException> { MatchRanking.rank(score, listOf(0)) }
    }
}
