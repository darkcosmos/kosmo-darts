package io.github.darkcosmos.kosmodarts.engine.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** M2 to M4, M6, M7, M12 (docs/rules/match.md). */
class MatchScoreTest {

    private val a = 0
    private val b = 1
    private val c = 2

    private fun score(legsPerSet: Int, sets: Int, players: Int, vararg winners: Int) =
        MatchScore(MatchFormat(legsPerSet, sets), players, winners.toList())

    /** M2, M4: first to 3 legs, in one set: the third leg won decides the match. */
    @Test
    fun `M2 M4 first to 3 legs`() {
        val ongoing = score(3, 1, 2, a, b, a, b)

        assertEquals(listOf(2, 2), ongoing.legsWonInSet)
        assertFalse(ongoing.isFinished)
        assertNull(ongoing.winnerIndex)

        val won = ongoing.withLegWonBy(b)

        assertTrue(won.isFinished)
        assertEquals(b, won.winnerIndex)
        assertEquals(listOf(0, 1), won.setsWon)
        assertEquals(listOf(2, 3), won.legsWonTotal)
    }

    /** M3: winning a set puts every player's legs of the set back to 0; totals keep counting. */
    @Test
    fun `M3 legs go back to 0 after a set`() {
        val afterSet = score(2, 2, 3, a, b, b)

        assertEquals(listOf(0, 1, 0), afterSet.setsWon)
        assertEquals(listOf(0, 0, 0), afterSet.legsWonInSet)
        assertEquals(listOf(1, 2, 0), afterSet.legsWonTotal)
        assertFalse(afterSet.isFinished)

        val next = afterSet.withLegWonBy(a)

        assertEquals(listOf(1, 0, 0), next.legsWonInSet)
        assertEquals(listOf(2, 2, 0), next.legsWonTotal)
    }

    /** M4: the match ends on reaching S sets won. */
    @Test
    fun `M4 two sets of two legs`() {
        val won = score(2, 2, 2, a, a, b, a, a)

        assertEquals(a, won.winnerIndex)
        assertEquals(listOf(2, 0), won.setsWon)
        assertEquals(listOf(0, 0), won.legsWonInSet)
    }

    /** M7: LEG, SET and MATCH outcomes, including S = 1, where a leg reaching L decides the match. */
    @Test
    fun `M7 outcome of the next leg`() {
        val start = score(2, 2, 2)
        assertEquals(LegOutcome.LEG, start.outcomeIfWonBy(a))

        val aOneLeg = start.withLegWonBy(a)
        assertEquals(LegOutcome.SET, aOneLeg.outcomeIfWonBy(a))
        assertEquals(LegOutcome.LEG, aOneLeg.outcomeIfWonBy(b))

        val aOneSetOneLeg = aOneLeg.withLegWonBy(a).withLegWonBy(a)
        assertEquals(LegOutcome.MATCH, aOneSetOneLeg.outcomeIfWonBy(a))
        assertEquals(LegOutcome.LEG, aOneSetOneLeg.outcomeIfWonBy(b))

        val oneSet = score(2, 1, 2, a)
        assertEquals(LegOutcome.MATCH, oneSet.outcomeIfWonBy(a))
        assertEquals(LegOutcome.LEG, oneSet.outcomeIfWonBy(b))

        val oneLegPerSet = score(1, 3, 2)
        assertEquals(LegOutcome.SET, oneLegPerSet.outcomeIfWonBy(a))
    }

    /** M5, M7: a single game always decides the match. */
    @Test
    fun `M5 M7 a single game leg decides the match`() {
        val single = MatchScore(MatchFormat(), 4)

        (0 until 4).forEach { assertEquals(LegOutcome.MATCH, single.outcomeIfWonBy(it)) }
    }

    /** M4: no leg after the match is won. */
    @Test
    fun `M4 no leg after the match`() {
        val won = score(1, 1, 2, a)

        assertThrows<MatchFinishedException> { won.withLegWonBy(b) }
        assertThrows<MatchFinishedException> { won.outcomeIfWonBy(b) }
        assertThrows<IllegalArgumentException> { score(1, 1, 2, a, b) }
    }

    /** Winners must be player indices. */
    @Test
    fun `winners out of range are rejected`() {
        assertThrows<IllegalArgumentException> { score(3, 1, 2, 2) }
        assertThrows<IllegalArgumentException> { score(3, 1, 2, -1) }
        assertThrows<IllegalArgumentException> { score(3, 1, 2).withLegWonBy(2) }
        assertThrows<IllegalArgumentException> { score(3, 1, 2).outcomeIfWonBy(2) }
        assertThrows<IllegalArgumentException> { MatchScore(MatchFormat(), 0) }
    }

    /** M6: leg k starts with player k mod n, across set changes. */
    @Test
    fun `M6 starter of the next leg`() {
        val starters = mutableListOf<Int>()
        var current = score(2, 2, 3)
        listOf(a, a, b, c).forEach { winner ->
            starters += current.starterOfNextLeg
            current = current.withLegWonBy(winner)
        }
        starters += current.starterOfNextLeg

        assertEquals(listOf(a, b, c, a, b), starters)
        assertEquals(4, current.legsPlayed)
    }

    /** M12: for 2 players, first to 2 legs and 2 sets, the longest sequence takes exactly maxLegs legs. */
    @Test
    fun `M12 the longest sequence reaches maxLegs and no further`() {
        val format = MatchFormat(2, 2)
        val longest = listOf(a, b, a, b, a, b, a, b, a)
        val won = MatchScore(format, 2, longest)

        assertEquals(format.maxLegs(2), longest.size)
        assertTrue(won.isFinished)
        assertThrows<MatchFinishedException> { won.withLegWonBy(b) }
        assertThrows<IllegalArgumentException> { MatchScore(format, 2, longest + b) }
    }

    /** M12: over every small format, no winner sequence can be longer than maxLegs, and the bound is reached. */
    @Test
    fun `M12 maxLegs is the exact upper bound`() {
        for (players in 1..3) {
            for (legs in 1..3) {
                for (sets in 1..3) {
                    val format = MatchFormat(legs, sets)
                    val longest = longestMatch(MatchScore(format, players), mutableMapOf())
                    assertEquals(format.maxLegs(players), longest, "players=$players format=$format")
                }
            }
        }
    }

    /** M19: at the start of a match, set 1 leg 1, and no last leg. */
    @Test
    fun `M19 position at the start`() {
        val start = score(3, 2, 2)

        assertEquals(1, start.currentSetNumber)
        assertEquals(1, start.currentLegNumber)
        assertNull(start.lastLegSetNumber)
        assertNull(start.lastLegNumber)
    }

    /** M19: mid-set, the leg number counts the legs played in the set; the last leg is the one before. */
    @Test
    fun `M19 position mid-set`() {
        val midSet = score(3, 2, 2, a, b)

        assertEquals(1, midSet.currentSetNumber)
        assertEquals(3, midSet.currentLegNumber)
        assertEquals(1, midSet.lastLegSetNumber)
        assertEquals(2, midSet.lastLegNumber)
    }

    /** M19: after a set is won, the next leg is leg 1 of the next set; the last leg stays in the set it closed. */
    @Test
    fun `M19 position after a set change`() {
        val afterSet = score(2, 3, 2, a, b, a)

        assertEquals(2, afterSet.currentSetNumber)
        assertEquals(1, afterSet.currentLegNumber)
        assertEquals(1, afterSet.lastLegSetNumber)
        assertEquals(3, afterSet.lastLegNumber)

        val next = afterSet.withLegWonBy(b)

        assertEquals(2, next.currentSetNumber)
        assertEquals(2, next.currentLegNumber)
        assertEquals(2, next.lastLegSetNumber)
        assertEquals(1, next.lastLegNumber)
    }

    /** M19: with one set, the set number stays 1 and the leg number counts every leg. */
    @Test
    fun `M19 position with legs only`() {
        val legsOnly = score(5, 1, 3, a, b, c)

        assertEquals(1, legsOnly.currentSetNumber)
        assertEquals(4, legsOnly.currentLegNumber)
        assertEquals(1, legsOnly.lastLegSetNumber)
        assertEquals(3, legsOnly.lastLegNumber)
    }

    /** M19: a single game is set 1 leg 1 throughout. */
    @Test
    fun `M19 position in a single game`() {
        val single = score(1, 1, 2)

        assertEquals(1, single.currentSetNumber)
        assertEquals(1, single.currentLegNumber)
        assertNull(single.lastLegNumber)
    }

    /** Longest number of legs from [score] to the end of the match, memoized on the sets and legs of the set. */
    private fun longestMatch(score: MatchScore, memo: MutableMap<Pair<List<Int>, List<Int>>, Int>): Int {
        if (score.isFinished) return 0
        val key = score.setsWon to score.legsWonInSet
        return memo.getOrPut(key) {
            1 + (0 until score.playerCount).maxOf { longestMatch(score.withLegWonBy(it), memo) }
        }
    }
}
