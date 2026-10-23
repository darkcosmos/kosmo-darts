package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The X01 binding of docs/rules/match.md (M2 to M8, M12) and its invariants. */
class X01MatchTest {

    /** M5: with 1 and 1, the first win decides the match and the ranking is that of E1 and E2. */
    @Test
    fun `M5 a single game is one leg ranked by E1 and E2`() {
        val match = newMatch("Ann", "Bob", "Cid", "Dan").throwing(
            "T20", "T20", "T20", // Ann 121
            "20", "20", "20", // Bob 241
            "20", "T10", "D5", // Cid 241
            "1", "1", "1", // Dan 298
            "T20", "T20", "1", // Ann wins
        )

        assertEquals(LegOutcome.MATCH, match.pendingOutcome)
        assertTrue(match.isFinished)
        assertEquals(
            match.currentLeg.state.ranking.map { MatchRank(it.rank, it.playerIndex) },
            match.ranking,
        )
        assertEquals(listOf(MatchRank(1, 0), MatchRank(2, 1), MatchRank(2, 2), MatchRank(4, 3)), match.ranking)
    }

    /** M5: the current leg of a single game is a plain X01 game started by the first player. */
    @Test
    fun `M5 a single game starts as today`() {
        val match = newMatch("Ann", "Bob", startScore = 501, checkoutRule = CheckoutRule.DOUBLE_OUT)

        assertEquals(X01Game(X01Config(listOf("Ann", "Bob"))), match.currentLeg)
        assertEquals(MatchFormat(), match.format)
        assertTrue(match.completedLegs.isEmpty())
        assertNull(match.pendingOutcome)
        assertTrue(match.ranking.isEmpty())
    }

    /** M6, M8: confirming a leg keeps its summary and starts the next leg with the next starter. */
    @Test
    fun `M6 M8 confirmLeg appends the summary and starts the next leg`() {
        val won = newMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(1)
        assertEquals(LegOutcome.LEG, won.pendingOutcome)

        val next = won.confirmLeg()

        assertEquals(listOf(X01LegSummary.of(won.currentLeg)), next.completedLegs)
        assertEquals(X01Game(won.config, startingPlayerIndex = 1), next.currentLeg)
        assertEquals(listOf(0, 1), next.score.legsWonInSet)
        assertNull(next.pendingOutcome)
    }

    /** M4, M7: the leg that decides the match is not confirmed; a leg in progress neither. */
    @Test
    fun `M4 a match leg or a leg in progress cannot be confirmed`() {
        val match = newMatch("Ann", "Bob", legsPerSet = 2)

        assertThrows<IllegalStateException> { match.confirmLeg() }
        assertThrows<IllegalStateException> { match.throwing("T20").confirmLeg() }
        val decided = match.confirmLegsWonBy(0).playLegWonBy(0)
        assertTrue(decided.isFinished)
        assertThrows<IllegalStateException> { decided.confirmLeg() }
    }

    /** M8: undo never goes back into a confirmed leg. */
    @Test
    fun `M8 undo never crosses a leg`() {
        val next = newMatch("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)

        assertFalse(next.canUndo)
        assertSame(next, next.undo())
        assertEquals(1, next.completedLegs.size)
    }

    /** M8, U1: undoing the winning dart reopens the leg and clears the pending outcome. */
    @Test
    fun `M8 undoing the winning dart reopens the leg`() {
        val won = newMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0)

        val reopened = won.undo()

        assertNull(reopened.pendingOutcome)
        assertFalse(reopened.currentLeg.state.isFinished)
        assertEquals(LegOutcome.LEG, reopened.throwDart(won.currentLeg.darts.last()).pendingOutcome)
    }

    /** M7, single out: LEG, SET, then MATCH outcomes in turn, in 301. */
    @Test
    fun `M7 single out leg then set then match`() {
        var match = newMatch("Ann", "Bob", legsPerSet = 2, sets = 2)
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
    }

    /** S4, M7, double out in 501: a bust on the last dart of a turn (2 left, single 2) gives no pending outcome. */
    @Test
    fun `M7 double out bust on the last dart gives no outcome`() {
        val config = X01Config(listOf("Ann"), 501, CheckoutRule.DOUBLE_OUT)
        // 499 in 9 darts, then 2 left: single 2 is a bust in double out.
        val darts = List(8) { "T20" } + listOf("19") + listOf("MISS", "MISS", "2")
        val match = X01Match.start(X01MatchConfig(config, MatchFormat(2, 1))).throwing(*darts.toTypedArray())

        assertEquals(TurnOutcome.BUST, match.currentLeg.state.completedTurns.last().outcome)
        assertEquals(2, match.currentLeg.player(0).remaining)
        assertNull(match.pendingOutcome)
        assertEquals(LegOutcome.LEG, match.throwing("D1").pendingOutcome)
    }

    /** M6: 3 players over a set change (2 sets of 2 legs): starters A, B, C, A, B. */
    @Test
    fun `M6 rotation over a set change`() {
        val starters = startersOf(newMatch("A", "B", "C", legsPerSet = 2, sets = 2), 0, 0, 1, 2)

        assertEquals(listOf("A", "B", "C", "A", "B"), starters)
    }

    /** M6: 2 players alternate; 4 players wrap past n (T2). */
    @Test
    fun `M6 rotation with 2 and 4 players`() {
        assertEquals(listOf("A", "B", "A", "B"), startersOf(newMatch("A", "B", legsPerSet = 9), 0, 1, 0))
        assertEquals(
            listOf("A", "B", "C", "D", "A"),
            startersOf(newMatch("A", "B", "C", "D", legsPerSet = 9, startScore = 501), 0, 1, 2, 3),
        )
    }

    /** M6: a single player always starts. */
    @Test
    fun `M6 a single player always starts`() {
        assertEquals(listOf("Solo", "Solo", "Solo"), startersOf(newMatch("Solo", legsPerSet = 3, sets = 2), 0, 0))
    }

    /** M6: "Shuffle" only fixes the configuration order: setup order C, A, B gives starters C, A, B, C. */
    @Test
    fun `M6 the configuration order gives the first starter`() {
        assertEquals(listOf("C", "A", "B", "C"), startersOf(newMatch("C", "A", "B", legsPerSet = 9), 0, 0, 0))
    }

    /** A3 invariants: wrong starter, other configuration, or completed legs that decide the match. */
    @Test
    fun `constructor invariants`() {
        val config = X01Config(listOf("Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT)
        val matchConfig = X01MatchConfig(config, MatchFormat(2, 1))
        val firstLeg = newMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0).currentLeg
        val summary = X01LegSummary.of(firstLeg)

        assertThrows<IllegalArgumentException> { X01Match(matchConfig, listOf(summary), X01Game(config)) }
        val otherConfig = X01Config(listOf("Ann", "Bob"), 501, CheckoutRule.SINGLE_OUT)
        assertThrows<IllegalArgumentException> { X01Match(matchConfig, emptyList(), X01Game(otherConfig)) }
        val decided = listOf(summary, summary)
        assertThrows<IllegalArgumentException> { X01Match(matchConfig, decided, X01Game(config)) }
        assertThrows<IllegalArgumentException> {
            X01Match(matchConfig, decided + summary, X01Game(config, startingPlayerIndex = 1))
        }
        val otherSummary = X01LegSummary.of(firstLegIn(otherConfig))
        assertThrows<IllegalArgumentException> {
            X01Match(matchConfig, listOf(otherSummary), X01Game(config, startingPlayerIndex = 1))
        }
    }

    /** S1, A1: impossible leg summaries are rejected. */
    @Test
    fun `leg summary ranges`() {
        val config = X01Config(listOf("Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT)

        X01LegSummary(config, 0, listOf(301, 300), listOf(6, 5))
        X01LegSummary(config, 1, listOf(0, 301), listOf(0, 6))
        // A non-winner at the start score.
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(301, 301), listOf(6, 6)) }
        // A winner below the start score.
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(300, 0), listOf(6, 6)) }
        // More than 60 points per dart.
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(301, 61), listOf(6, 1)) }
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(301, 0), listOf(5, 0)) }
        // Negative values, wrong sizes, winner out of range.
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(301, -1), listOf(6, 0)) }
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(301, 0), listOf(6, -1)) }
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 0, listOf(301), listOf(6)) }
        assertThrows<IllegalArgumentException> { X01LegSummary(config, 2, listOf(301, 0), listOf(6, 0)) }
        // A leg in progress has no summary.
        assertThrows<IllegalArgumentException> { X01LegSummary.of(X01Game(config)) }
    }

    /** PC6 (docs/rules/players.md): the color slots never change when legs and sets are confirmed. */
    @Test
    fun `PC6 color slots are unchanged after legs and sets`() {
        val slots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1, ColorSlot.SLOT_4)
        val config = X01Config(listOf("Ann", "Bob", "Cid"), 301, CheckoutRule.SINGLE_OUT)
        val start = X01Match.start(X01MatchConfig(config, MatchFormat(2, 2), slots))

        // Ann wins a leg, Bob a leg, Ann the set; then Cid wins a leg of set 2.
        val later = start.confirmLegsWonBy(0, 1, 0, 2)

        assertEquals(slots, start.colorSlots)
        assertEquals(slots, later.colorSlots)
        assertEquals(slots, later.playLegWonBy(1).undo().colorSlots)
    }

    /** Names of the starters of the first leg and of the leg after each confirmed winner. */
    private fun startersOf(start: X01Match, vararg winners: Int): List<String> =
        winners.runningFold(start) { match, winner -> match.confirmLegsWonBy(winner) }.map { it.starterName }

    private fun firstLegIn(config: X01Config): X01Game {
        val match = X01Match.start(X01MatchConfig(config, MatchFormat(2, 1)))
        return match.playLegWonBy(0).currentLeg
    }
}
