package io.github.darkcosmos.kosmodarts.engine.cricket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CR13 and CR14 over a match (docs/rules/cricket.md; docs/rules/match.md, M9), and the closed target count (CR3). */
class CricketStatisticsTest {

    /**
     * CR13: MPR = marks hit / darts thrown x 3 over the completed legs and the current one, surplus marks and marks on
     * a dead target included; darts are counted over the match.
     */
    @Test
    fun `CR13 MPR over completed legs and the current leg`() {
        val firstLeg = cricketMatch("Ann", "Bob", isBullInPlay = false, legsPerSet = 2).turns(
            "T20 T20 MISS", // Ann 6 marks, 3 of them surplus
            MISSES,
            "T19 T18 T17",
            MISSES,
            "T16 T15", // Ann wins: 21 marks in 8 darts
        )
        val secondLeg = firstLeg.confirmLeg().turns(
            "T20 MISS MISS", // Bob closes 20
            "T20 20 MISS", // Ann closes 20, now dead, then 1 mark on the dead 20: 4 marks
        )

        assertEquals(
            listOf(
                CricketMatchPlayerStats(marksPerRound = 25 * 3.0 / 11, dartsThrown = 11),
                CricketMatchPlayerStats(marksPerRound = 3 * 3.0 / 9, dartsThrown = 9),
            ),
            secondLeg.playerStats,
        )
        assertEquals(listOf(4, 3), secondLeg.currentLeg.state.players.map(CricketPlayerState::marksHit))
    }

    /** CR13: no dart thrown means no MPR, while a dart without a mark gives an MPR of 0. */
    @Test
    fun `CR13 no MPR before the first dart`() {
        val start = cricketMatch("Ann", "Bob")
        assertEquals(List(2) { CricketMatchPlayerStats(null, 0) }, start.playerStats)

        assertEquals(
            listOf(CricketMatchPlayerStats(0.0, 1), CricketMatchPlayerStats(null, 0)),
            start.throwing("MISS").playerStats,
        )
    }

    /** CR14, M9: the visit marks restart at a new leg: the new starter is throwing, the other player has not played. */
    @Test
    fun `CR14 visit marks reset at a new leg`() {
        val won = cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0)
        assertEquals(
            listOf(CricketVisit.LastVisit(3), CricketVisit.LastVisit(0)),
            CricketVisits.of(won.currentLeg.state),
        )

        val next = won.confirmLeg()

        assertEquals(
            listOf(CricketVisit.NotPlayed, CricketVisit.InProgress(0)),
            CricketVisits.of(next.currentLeg.state),
        )
    }

    /** CR1, CR3 (review A2): the closed target count goes up to the number of targets in play, 7 with 25-Bull. */
    @Test
    fun `closed target count with 25-Bull`() {
        val game = cricketGame("Ann", "Bob")
        assertEquals(7, game.config.targets.size)

        val partly = game.turns("T20 T19 BULL")
        assertEquals(2, partly.player(0).closedTargetCount)
        assertEquals(0, partly.player(1).closedTargetCount)

        val won = partly.turns(MISSES, "T18 T17 T16", MISSES, "T15 25")
        assertTrue(won.state.isFinished)
        assertEquals(won.config.targets.size, won.player(0).closedTargetCount)
    }

    /** CR1, CR3 (review A2): with 25-Bull off, 6 targets are in play and Bull marks never count. */
    @Test
    fun `closed target count without 25-Bull`() {
        val game = cricketGame("Solo", isBullInPlay = false)
        assertEquals(6, game.config.targets.size)

        val partly = game.turns("BULL BULL T20")
        assertEquals(1, partly.player(0).closedTargetCount)

        val won = partly.turns(CLOSE_17_16_15, "T19 T18")
        assertTrue(won.state.isFinished)
        assertEquals(won.config.targets.size, won.player(0).closedTargetCount)
    }
}
