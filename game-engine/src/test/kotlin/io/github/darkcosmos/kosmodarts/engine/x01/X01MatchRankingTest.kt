package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** M10 (docs/rules/match.md) with the X01 tie-break: remaining score in the last leg. */
class X01MatchRankingTest {

    private fun ranks(vararg pairs: Pair<Int, Int>) = pairs.map { (rank, player) -> MatchRank(rank, player) }

    /**
     * M10: sets first, then legs over the whole match, not legs of the last set, then the remaining score. D won two
     * legs in earlier sets, C one leg in the last set and a better remaining score: D is ahead of C.
     */
    @Test
    fun `M10 sets then total legs then remaining`() {
        val confirmed = newMatch("A", "B", "C", "D", legsPerSet = 2, sets = 2)
            .confirmLegsWonBy(3, 1, 1, 3, 0, 0, 2, 0)
        assertEquals(0, confirmed.currentLeg.startingPlayerIndex)
        val finished = confirmed.throwing(
            "T20", "T20", "T20", // A 121
            "MISS", "MISS", "MISS", // B 301
            "T20", "T20", "T20", // C 121
            "MISS", "MISS", "MISS", // D 301
            "T20", "T20", "1", // A wins the match
        )

        assertTrue(finished.isFinished)
        val score = checkNotNull(finished.finalScore)
        assertEquals(listOf(2, 1, 0, 0), score.setsWon)
        assertEquals(listOf(0, 0, 0, 0), score.legsWonInSet)
        assertEquals(listOf(4, 2, 1, 2), score.legsWonTotal)
        assertEquals(ranks(1 to 0, 2 to 1, 3 to 3, 4 to 2), finished.ranking)
    }

    /** M10: equal sets, legs and remaining score share the rank, and the next rank is skipped (1, 2, 2, 4). */
    @Test
    fun `M10 full tie shares the rank`() {
        val finished = newMatch("A", "B", "C", "D", legsPerSet = 2).confirmLegsWonBy(1, 2, 0).playLegWonBy(0)

        assertEquals(ranks(1 to 0, 2 to 1, 2 to 2, 4 to 3), finished.ranking)
    }

    /** M10: two players, the loser second whatever the remaining scores. */
    @Test
    fun `M10 two players`() {
        val finished = newMatch("A", "B", legsPerSet = 3).confirmLegsWonBy(0, 1, 1, 0).playLegWonBy(0)

        assertEquals(ranks(1 to 0, 2 to 1), finished.ranking)
        assertEquals(listOf(3, 2), finished.finalScore?.legsWonTotal)
    }

    /** M10: no ranking before the match is won, even when a leg is won. */
    @Test
    fun `M10 no ranking before the end`() {
        val legWon = newMatch("A", "B", legsPerSet = 3).playLegWonBy(0)

        assertTrue(legWon.ranking.isEmpty())
        assertEquals(null, legWon.finalScore)
    }
}
