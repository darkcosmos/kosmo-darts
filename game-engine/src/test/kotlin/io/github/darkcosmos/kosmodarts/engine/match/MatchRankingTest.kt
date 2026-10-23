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

    /** M20: the mode tie-break is a comparator, applied only after the winner, sets and total legs. */
    @Test
    fun `M20 a comparator tie-break comes after sets and legs`() {
        // Same match as above: A wins, B has a set, C is ahead of D on legs. The tie-break prefers higher values.
        val score = MatchScore(MatchFormat(2, 2), 4, listOf(1, 1, 2, 3, 0, 0, 0, 2, 0))
        val values = listOf(0, 0, 10, 400)

        assertEquals(
            ranks(1 to 0, 2 to 1, 3 to 2, 4 to 3),
            MatchRanking.rank(score, compareByDescending<Int> { values[it] }),
        )
    }

    /** M20: criteria with their own directions; players equal on every criterion share a rank (1, 2, 2, 4). */
    @Test
    fun `M20 a tie-break with several criteria and directions`() {
        val score = MatchScore(MatchFormat(1, 1), 4, listOf(0))
        val points = listOf(0, 50, 50, 50)
        val marks = listOf(21, 6, 4, 6)
        val tieBreak = compareByDescending<Int> { points[it] }.thenByDescending { marks[it] }

        assertEquals(ranks(1 to 0, 2 to 1, 2 to 3, 4 to 2), MatchRanking.rank(score, tieBreak))
        // The first criterion decides before the second one: fewer points first, whatever the marks.
        val fewerPoints = listOf(0, 40, 50, 60)
        assertEquals(
            ranks(1 to 0, 2 to 1, 3 to 2, 4 to 3),
            MatchRanking.rank(score, compareBy<Int> { fewerPoints[it] }.thenByDescending { marks[it] }),
        )
    }

    /** M10, M20: the list tie-break is the comparator one with "lower is better". */
    @Test
    fun `M20 the list tie-break is a lower is better comparator`() {
        val score = MatchScore(MatchFormat(1, 1), 4, listOf(2))
        val remaining = listOf(40, 100, 0, 40)

        assertEquals(
            MatchRanking.rank(score, compareBy<Int> { remaining[it] }),
            MatchRanking.rank(score, remaining),
        )
        assertEquals(ranks(1 to 2, 2 to 0, 2 to 3, 4 to 1), MatchRanking.rank(score, remaining))
    }
}
