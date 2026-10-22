package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** M9 (docs/rules/match.md): match-wide average and darts, per-leg figures restart at each leg. */
class X01MatchStatisticsTest {

    /** M9, A1: the average is total points / total darts x 3 over every leg, bust darts of both legs included. */
    @Test
    fun `M9 average over two legs counts bust darts`() {
        val firstLeg = newMatch("Ann", "Bob", legsPerSet = 3).throwing(
            "T20", "T20", "T20", // Ann 121
            "T20", "MISS", "MISS", // Bob 241
            "T20", "T20", "T5", // Ann busts (15 points short of -14): back to 121
            "20", "20", "20", // Bob 181
            "T20", "T20", "1", // Ann wins
        )
        // Ann: 301 points in 9 darts. Bob: 120 points in 6 darts.
        val secondLeg = firstLeg.confirmLeg().throwing(
            "T20", "T20", "T20", // Bob 121 (Bob starts, M6)
            "T20", "T20", "MISS", // Ann 181
            "T20", "T20", "T20", // Bob busts: back to 121
            "5", // Ann 176, turn in progress
        )
        // Ann: 301 + 125 = 426 points in 9 + 4 = 13 darts. Bob: 120 + 180 = 300 points in 6 + 6 = 12 darts.
        val stats = secondLeg.playerStats

        assertEquals(426 * 3.0 / 13, checkNotNull(stats[0].threeDartAverage), 1e-9)
        assertEquals(13, stats[0].dartsThrown)
        assertEquals(300 * 3.0 / 12, checkNotNull(stats[1].threeDartAverage), 1e-9)
        assertEquals(12, stats[1].dartsThrown)
    }

    /** M9: no dart thrown means no average. */
    @Test
    fun `M9 no dart means no average`() {
        val stats = newMatch("Ann", "Bob").playerStats

        assertNull(stats[0].threeDartAverage)
        assertEquals(0, stats[0].dartsThrown)
    }

    /** M9: a player who threw only in a previous leg keeps that leg's average at the start of the next one. */
    @Test
    fun `M9 the average carries over to the next leg`() {
        val next = newMatch("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)

        val finishedLeg = next.completedLegs.single()
        val expected = finishedLeg.pointsScored[0] * 3.0 / finishedLeg.dartsThrown[0]
        assertEquals(expected, next.playerStats[0].threeDartAverage)
        assertEquals(0, next.currentLeg.player(0).dartsThrown)
    }

    /** M9, VT5: at the start of leg 2, every visit box is "not played" but the starter's, which is in progress. */
    @Test
    fun `M9 visit totals restart at each leg`() {
        val next = newMatch("Ann", "Bob", "Cid", legsPerSet = 2).confirmLegsWonBy(0)

        assertEquals(
            listOf(VisitTotal.NotPlayed, VisitTotal.InProgress(0), VisitTotal.NotPlayed),
            VisitTotals.of(next.currentLeg.state),
        )
        (0..2).forEach { assertEquals(VisitTotal.NotPlayed, VisitTotals.lastCompleted(next.currentLeg.state, it)) }
        assertEquals(listOf(301, 301, 301), next.currentLeg.state.players.map(X01PlayerState::remaining))
    }
}
