package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The Cricket binding of docs/rules/match.md (M1 to M12, M19) with the Cricket tie-break (CR16). */
class CricketMatchTest {

    private fun ranks(vararg pairs: Pair<Int, Int>) = pairs.map { (rank, player) -> MatchRank(rank, player) }

    /** M5, CR12, CR16: with 1 and 1, the first win decides the match and the ranking is the leg ranking. */
    @Test
    fun `M5 a single game is one leg ranked like the leg`() {
        val match = cricketMatch("Ann", "Bob", "Cid", "Dan", isBullInPlay = false).turns(
            "T20 T20 T19", // Ann closes 20 and 19, 60 points
            "T18 MISS MISS", // Bob 3 marks
            "T19 19 MISS", // Cid 3 marks, 19 points
            "17 MISS MISS", // Dan 1 mark
            "T18 T17 T16",
            MISSES,
            MISSES,
            MISSES,
            "T15", // Ann wins
        )

        assertEquals(LegOutcome.MATCH, match.pendingOutcome)
        assertTrue(match.isFinished)
        assertEquals(match.currentLeg.state.ranking.map { MatchRank(it.rank, it.playerIndex) }, match.ranking)
        assertEquals(ranks(1 to 0, 2 to 2, 3 to 1, 4 to 3), match.ranking)
    }

    /** M5: the current leg of a single game is a plain Cricket leg started by the first player. */
    @Test
    fun `M5 a single game starts as a plain leg`() {
        val match = cricketMatch("Ann", "Bob")

        assertEquals(CricketGame(CricketConfig(listOf("Ann", "Bob"))), match.currentLeg)
        assertEquals(MatchFormat(), match.format)
        assertTrue(match.completedLegs.isEmpty())
        assertNull(match.pendingOutcome)
        assertNull(match.finalScore)
        assertTrue(match.ranking.isEmpty())
    }

    /** M6, M8, M19: confirming a leg keeps its summary and starts the next leg with the next starter. */
    @Test
    fun `M6 M8 confirmLeg appends the summary and starts the next leg`() {
        val won = cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(1)
        assertEquals(LegOutcome.LEG, won.pendingOutcome)

        val next = won.confirmLeg()

        assertEquals(listOf(CricketLegSummary.of(won.currentLeg)), next.completedLegs)
        assertEquals(CricketGame(won.config, startingPlayerIndex = 1), next.currentLeg)
        assertEquals(listOf(0, 1), next.score.legsWonInSet)
        assertEquals(1, next.score.currentSetNumber)
        assertEquals(2, next.score.currentLegNumber)
        assertNull(next.pendingOutcome)
    }

    /** M4, M7, LegOutcome.isConfirmable: the leg that decides the match is not confirmed; a leg in progress neither. */
    @Test
    fun `M4 a match leg or a leg in progress cannot be confirmed`() {
        val match = cricketMatch("Ann", "Bob", legsPerSet = 2)

        assertThrows<IllegalStateException> { match.confirmLeg() }
        assertThrows<IllegalStateException> { match.throwing("T20").confirmLeg() }
        val decided = match.confirmLegsWonBy(0).playLegWonBy(0)
        assertTrue(decided.isFinished)
        assertThrows<IllegalStateException> { decided.confirmLeg() }
    }

    /** M8, CR11: undo never goes back into a confirmed leg. */
    @Test
    fun `M8 undo never crosses a leg`() {
        val next = cricketMatch("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)

        assertFalse(next.canUndo)
        assertSame(next, next.undo())
        assertEquals(1, next.completedLegs.size)
    }

    /** M8, CR11: undoing the winning dart reopens the leg and clears the pending outcome. */
    @Test
    fun `M8 undoing the winning dart reopens the leg`() {
        val won = cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0)

        val reopened = won.undo()

        assertTrue(reopened.canUndo)
        assertNull(reopened.pendingOutcome)
        assertFalse(reopened.currentLeg.state.isFinished)
        assertEquals(LegOutcome.LEG, reopened.throwDart(won.currentLeg.darts.last()).pendingOutcome)
    }

    /** M7: LEG, SET, then MATCH outcomes in turn. */
    @Test
    fun `M7 leg then set then match`() {
        var match = cricketMatch("Ann", "Bob", variant = CricketVariant.CUT_THROAT, legsPerSet = 2, sets = 2)
        val outcomes = mutableListOf<LegOutcome?>()
        repeat(3) {
            match = match.playLegWonBy(0)
            outcomes += match.pendingOutcome
            match = match.confirmLeg()
        }
        match = match.playLegWonBy(0)
        outcomes += match.pendingOutcome

        assertEquals(listOf(LegOutcome.LEG, LegOutcome.SET, LegOutcome.LEG, LegOutcome.MATCH), outcomes)
        assertTrue(match.isFinished)
        assertEquals(listOf(2, 0), match.finalScore?.setsWon)
        assertEquals(3, match.completedLegs.size)
    }

    /** M6: 3 players over a set change (2 sets of 2 legs): starters A, B, C, A, B. */
    @Test
    fun `M6 rotation over a set change`() {
        val starters = startersOf(cricketMatch("A", "B", "C", legsPerSet = 2, sets = 2), 0, 0, 1, 2)

        assertEquals(listOf("A", "B", "C", "A", "B"), starters)
    }

    /** M6: 4 players wrap past n; the configuration order gives the first starter. */
    @Test
    fun `M6 rotation with 4 players and the configuration order`() {
        assertEquals(
            listOf("A", "B", "C", "D", "A"),
            startersOf(cricketMatch("A", "B", "C", "D", legsPerSet = 9), 0, 1, 2, 3),
        )
        assertEquals(listOf("C", "A", "B", "C"), startersOf(cricketMatch("C", "A", "B", legsPerSet = 9), 0, 0, 0))
    }

    /** M6, CR6: a solo match, where the only player always starts and is ranked first. */
    @Test
    fun `solo match`() {
        val start = cricketMatch("Solo", legsPerSet = 2)
        assertEquals(listOf("Solo", "Solo"), startersOf(start, 0))

        val won = start.confirmLegsWonBy(0).playLegWonBy(0)

        assertTrue(won.isFinished)
        assertEquals(listOf(2), won.finalScore?.legsWonTotal)
        assertEquals(ranks(1 to 0), won.ranking)
        assertEquals(listOf(0), won.currentLeg.state.players.map(CricketPlayerState::points))
    }

    /**
     * M10, CR16, Standard: players level on sets and legs are ranked by points in the last leg (more is better), then
     * by board marks in the last leg (more is better).
     */
    @Test
    fun `CR16 Standard tie-break is points then board marks of the last leg`() {
        val match = cricketMatch("Ann", "Bob", "Cid", "Dan", isBullInPlay = false, legsPerSet = 2)
            .confirmLegsWonBy(0)
            .turns(
                "T18 MISS MISS", // Bob 3 marks, 0 points
                "T19 19 MISS", // Cid 3 marks, 19 points
                "17 MISS MISS", // Dan 1 mark, 0 points
                "T20 T20 T19", // Ann 60 points
                MISSES,
                MISSES,
                MISSES,
                "T18 T17 T16",
                MISSES,
                MISSES,
                MISSES,
                "T15", // Ann wins the leg and the match
            )

        assertTrue(match.isFinished)
        assertEquals(listOf(0, 0, 0), match.finalScore?.legsWonTotal?.drop(1))
        assertEquals(ranks(1 to 0, 2 to 2, 3 to 1, 4 to 3), match.ranking)
    }

    /** M10, CR16, Cut-throat: fewer points in the last leg is better, then more board marks. */
    @Test
    fun `CR16 Cut-throat tie-break is fewer points then board marks of the last leg`() {
        val match = cricketMatch(
            "Ann", "Bob", "Cid", "Dan",
            variant = CricketVariant.CUT_THROAT,
            isBullInPlay = false,
            legsPerSet = 2,
        )
            .confirmLegsWonBy(0)
            .turns(
                "T18 MISS MISS", // Bob 3 marks
                "T19 MISS MISS", // Cid 3 marks
                "17 MISS MISS", // Dan 1 mark
                "T20 20 MISS", // Ann: 20 points to Bob, Cid and Dan
                "18 MISS MISS", // Bob: 18 points to Ann, Cid and Dan
                MISSES,
                MISSES,
                "T19 T18 T17",
                MISSES,
                MISSES,
                MISSES,
                "T16 T15", // Ann wins with the fewest points
            )

        assertTrue(match.isFinished)
        assertEquals(listOf(18, 20, 38, 38), match.currentLeg.state.players.map(CricketPlayerState::points))
        assertEquals(ranks(1 to 0, 2 to 1, 3 to 2, 4 to 3), match.ranking)
    }

    /** M10, M20 (review T8): sets and legs come before the last leg tie-break, which would order them otherwise. */
    @Test
    fun `M20 sets and legs win over the last leg tie-break`() {
        val match = cricketMatch("Ann", "Bob", "Cid", isBullInPlay = false, legsPerSet = 2)
            .confirmLegsWonBy(2, 0) // Cid then Ann win a leg; Cid starts the deciding leg
            .turns(
                "T20 MISS MISS", // Cid 0 points
                "T19 T19 MISS", // Ann 57 points
                "T18 T18 MISS", // Bob 54 points
                MISSES,
                "T20 T18 T17",
                MISSES,
                MISSES,
                "T16 T15", // Ann wins the match
            )

        assertTrue(match.isFinished)
        assertEquals(listOf(2, 0, 1), match.finalScore?.legsWonTotal)
        assertEquals(listOf(CricketRank(1, 0), CricketRank(2, 1), CricketRank(3, 2)), match.currentLeg.state.ranking)
        assertEquals(ranks(1 to 0, 2 to 2, 3 to 1), match.ranking)
    }

    /** M10, CR16 (review T8): equal sets, legs, points and board marks share a rank and the next rank is skipped. */
    @Test
    fun `CR16 a full tie shares the rank`() {
        val match = cricketMatch("Ann", "Bob", "Cid", "Dan", isBullInPlay = false, legsPerSet = 2)
            .confirmLegsWonBy(0)
            .turns(
                "T18 MISS MISS", // Bob 3 marks, 0 points
                "T19 MISS MISS", // Cid 3 marks, 0 points
                MISSES, // Dan 0 marks
                "T20 T19 T18",
                MISSES,
                MISSES,
                MISSES,
                "T17 T16 T15", // Ann wins the match
            )

        assertTrue(match.isFinished)
        assertEquals(ranks(1 to 0, 2 to 1, 2 to 2, 4 to 3), match.ranking)
    }

    /** M12: for 2 players, first to 2 legs and 2 sets, the longest match ends on its maxLegs-th leg. */
    @Test
    fun `M12 the longest match reaches maxLegs and no further`() {
        val format = MatchFormat(2, 2)
        val match = cricketMatch("Ann", "Bob", legsPerSet = 2, sets = 2)
            .confirmLegsWonBy(0, 1, 0, 1, 0, 1, 0, 1)
            .playLegWonBy(0)

        assertTrue(match.isFinished)
        assertEquals(format.maxLegs(2), match.completedLegs.size + 1)
        assertEquals(listOf(2, 1), match.finalScore?.setsWon)
        assertThrows<IllegalStateException> { match.confirmLeg() }
    }

    /** M6, M11 invariants: wrong starter, other configuration, or completed legs that decide the match. */
    @Test
    fun `constructor invariants`() {
        val config = CricketConfig(listOf("Ann", "Bob"))
        val matchConfig = CricketMatchConfig(config, MatchFormat(2, 1))
        val summary = CricketLegSummary.of(cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0).currentLeg)

        assertThrows<IllegalArgumentException> { CricketMatch(matchConfig, listOf(summary), CricketGame(config)) }
        val otherConfig = CricketConfig(listOf("Ann", "Bob"), CricketVariant.CUT_THROAT)
        assertThrows<IllegalArgumentException> { CricketMatch(matchConfig, emptyList(), CricketGame(otherConfig)) }
        val decided = listOf(summary, summary)
        assertThrows<IllegalArgumentException> { CricketMatch(matchConfig, decided, CricketGame(config)) }
        val otherSummary = CricketLegSummary.of(
            cricketMatch("Ann", "Bob", variant = CricketVariant.CUT_THROAT, legsPerSet = 2).playLegWonBy(0).currentLeg,
        )
        assertThrows<IllegalArgumentException> {
            CricketMatch(matchConfig, listOf(otherSummary), CricketGame(config, startingPlayerIndex = 1))
        }
        CricketMatch(matchConfig, listOf(summary), CricketGame(config, startingPlayerIndex = 1))
    }

    /** CR2, CR8, CR12, CR13: impossible leg summaries are rejected. */
    @Test
    fun `leg summary ranges`() {
        val config = CricketConfig(listOf("Ann", "Bob"), isBullInPlay = false)
        fun summary(
            winner: Int = 0,
            marks: List<Int> = listOf(18, 3),
            darts: List<Int> = listOf(6, 3),
            points: List<Int> = listOf(0, 0),
            board: List<Int> = listOf(18, 3),
        ) = CricketLegSummary(config, winner, marks, darts, points, board)

        summary()
        summary(marks = listOf(20, 9), points = listOf(40, 0), darts = listOf(7, 3))
        // More than 3 marks per dart.
        assertThrows<IllegalArgumentException> { summary(marks = listOf(18, 10)) }
        assertThrows<IllegalArgumentException> { summary(marks = listOf(19, 3)) }
        // Board marks above the marks hit, or above 3 per target in play.
        assertThrows<IllegalArgumentException> { summary(board = listOf(18, 4)) }
        assertThrows<IllegalArgumentException> { summary(marks = listOf(18, 9), board = listOf(18, 19)) }
        // A winner who has not closed every target (CR8).
        assertThrows<IllegalArgumentException> { summary(board = listOf(17, 3)) }
        // Negative values, wrong sizes, winner out of range.
        assertThrows<IllegalArgumentException> { summary(points = listOf(0, -1)) }
        assertThrows<IllegalArgumentException> { summary(darts = listOf(6, -1)) }
        assertThrows<IllegalArgumentException> { summary(marks = listOf(18, -1)) }
        assertThrows<IllegalArgumentException> { summary(points = listOf(0)) }
        assertThrows<IllegalArgumentException> { summary(winner = 2) }
        // A leg in progress has no summary.
        assertThrows<IllegalArgumentException> { CricketLegSummary.of(CricketGame(config)) }
    }

    /** M8: the summary keeps the winner and each player's figures of the leg. */
    @Test
    fun `leg summary of a won leg`() {
        val leg = cricketMatch("Ann", "Bob", isBullInPlay = false, legsPerSet = 2).turns(
            "T20 T20 MISS", // Ann 60 points
            "19 MISS MISS",
            "T19 T18 T17",
            MISSES,
            "T16 T15",
        ).currentLeg

        assertEquals(
            CricketLegSummary(leg.config, 0, listOf(21, 1), listOf(8, 6), listOf(60, 0), listOf(18, 1)),
            CricketLegSummary.of(leg),
        )
    }

    /** PC6 (docs/rules/players.md): the color slots never change when legs and sets are confirmed. */
    @Test
    fun `PC6 color slots are unchanged after legs and sets`() {
        val slots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1, ColorSlot.SLOT_4)
        val config = CricketConfig(listOf("Ann", "Bob", "Cid"))
        val start = CricketMatch.start(CricketMatchConfig(config, MatchFormat(2, 2), slots))

        val later = start.confirmLegsWonBy(0, 1, 0, 2)

        assertEquals(slots, start.colorSlots)
        assertEquals(slots, later.colorSlots)
        assertEquals(slots, later.playLegWonBy(1).undo().colorSlots)
        assertNotEquals(start, later)
    }

    /** Names of the starters of the first leg and of the leg after each confirmed winner. */
    private fun startersOf(start: CricketMatch, vararg winners: Int): List<String> =
        winners.runningFold(start) { match, winner -> match.confirmLegsWonBy(winner) }.map { it.starterName }
}
