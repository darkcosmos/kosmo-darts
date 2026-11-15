package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.stats.CricketMatchMpr
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CR13 (MPR on useful marks) and CR14 over a match (docs/rules/cricket.md; docs/rules/match.md, M9; STC1), CR3. */
class CricketStatisticsTest {

    /**
     * CR13 (STC1): MPR = useful marks / darts thrown x 3 over the completed legs, read from the journal, and the
     * current one; a mark on a dead target is not useful, darts are counted over the match.
     */
    @Test
    fun `CR13 MPR counts useful marks over completed legs and the current leg`() {
        val firstLeg = cricketMatch("Ann", "Bob", isBullInPlay = false, legsPerSet = 2).turns(
            "T20 T20 MISS", // Ann 6 marks, the surplus ones score on an open target: useful
            MISSES,
            "T19 T18 T17",
            MISSES,
            "T16 T15", // Ann wins: 21 useful marks in 8 darts
        )
        val secondLeg = firstLeg.confirmLeg().turns(
            "T20 MISS MISS", // Bob closes 20
            "T20 20 MISS", // Ann closes 20, now dead, then 1 mark on the dead 20: 3 useful marks, 4 marks hit
        )
        val journal = journalOf(firstLeg.currentLeg.darts, secondLeg.currentLeg.darts)

        assertEquals(listOf(11, 9), secondLeg.playerStats.map(CricketMatchPlayerStats::dartsThrown))
        assertEquals(listOf(24 * 3.0 / 11, 3 * 3.0 / 9), CricketMatchMpr.of(secondLeg, journal))
        assertEquals(listOf(4, 3), secondLeg.currentLeg.state.players.map(CricketPlayerState::marksHit))
    }

    /** CR13: no dart thrown means no MPR, while a dart without a mark gives an MPR of 0. */
    @Test
    fun `CR13 no MPR before the first dart`() {
        val start = cricketMatch("Ann", "Bob")
        assertEquals(listOf(null, null), CricketMatchMpr.of(start, journalOf(emptyList())))

        assertEquals(
            listOf(0.0, null),
            CricketMatchMpr.of(start.throwing("MISS"), journalOf(listOf(Dart.MISS))),
        )
    }

    /** CR13: surplus on a target every player has closed lowers the MPR (all marks would give 3.0). */
    @Test
    fun `CR13 a mark on a dead target lowers the MPR`() {
        val match = cricketMatch("Ann", "Bob").turns("T20 MISS MISS", "T20 MISS MISS", "T20 MISS MISS")

        assertEquals(listOf(3 * 3.0 / 6, 3 * 3.0 / 3), CricketMatchMpr.of(match, journalOf(match.currentLeg.darts)))
        assertEquals(6, match.currentLeg.state.players[0].marksHit)
    }

    /** CR13, STC1: alone, only the closing marks count (a surplus mark scores nothing in a solo game). */
    @Test
    fun `CR13 solo MPR counts only closing marks`() {
        val match = cricketMatch("Ann").turns("T20 T20 T20")

        assertEquals(listOf(3.0), CricketMatchMpr.of(match, journalOf(match.currentLeg.darts)))
        assertEquals(9, match.currentLeg.state.players[0].marksHit)
    }

    /** CR13 with 3 players: a surplus mark is useful while one opponent is still open, not once all have closed. */
    @Test
    fun `CR13 three players count the surplus against every opponent`() {
        val match = cricketMatch("Ann", "Bob", "Cid").turns(
            "T20 T20 MISS", // Ann: Bob and Cid are open, 6 useful
            "T20 MISS MISS", // Bob closes 20: Cid is still open
            "T20 T20 MISS", // Cid closes 20 (3), the second triple is on a dead target (0)
        )

        assertEquals(
            listOf(6 * 3.0 / 3, 3 * 3.0 / 3, 3 * 3.0 / 3),
            CricketMatchMpr.of(match, journalOf(match.currentLeg.darts)),
        )
    }

    /** CR13, CR11, DJ2: a voided dart of a confirmed leg never counts, and undoing in the current leg removes it. */
    @Test
    fun `CR13 undone darts are excluded`() {
        val firstLeg = cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0)
        val darts = firstLeg.currentLeg.darts
        val voided = LegJournal(
            listOf<JournalEvent>(JournalEvent.DartEntered(Dart.triple(20), 0L), JournalEvent.Undone) +
                darts.mapIndexed { i, dart -> JournalEvent.DartEntered(dart, i + 1L) },
        )
        val confirmed = firstLeg.confirmLeg()
        val journal = MatchJournal("match", 0L, listOf(voided, LegJournal()))
        val expected = CricketMatchMpr.of(confirmed, journalOf(darts, emptyList()))

        assertEquals(expected, CricketMatchMpr.of(confirmed, journal))

        // Current leg: a closing dart, a scoring dart, then undone one by one.
        val start = cricketMatch("Ann", "Bob")
        val empty = journalOf(emptyList())
        val closing = start.throwing("T20")
        val scoring = closing.throwing("T20")
        assertEquals(listOf(9.0, null), CricketMatchMpr.of(closing, empty))
        assertEquals(listOf(6 * 3.0 / 2, null), CricketMatchMpr.of(scoring, empty))
        assertEquals(listOf(9.0, null), CricketMatchMpr.of(scoring.undo(), empty))
        assertEquals(listOf(null, null), CricketMatchMpr.of(scoring.undo().undo(), empty))
    }

    /** CR13, DJ2: a saved game reloaded (rebuilt from its summaries, darts and journal) gives the same MPR. */
    @Test
    fun `CR13 a reloaded game gives the same MPR`() {
        val firstLeg = cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0)
        val confirmed = firstLeg.confirmLeg().turns("T20 T20 MISS", "T20 MISS MISS")
        val journal = journalOf(firstLeg.currentLeg.darts, confirmed.currentLeg.darts)
        val reloaded = CricketMatch(
            confirmed.matchConfig,
            confirmed.completedLegs,
            CricketGame(confirmed.config, confirmed.currentLeg.darts, confirmed.currentLeg.startingPlayerIndex),
        )

        assertEquals(CricketMatchMpr.of(confirmed, journal), CricketMatchMpr.of(reloaded, journal))
    }

    /** CR13: a journal that does not match the confirmed legs falls back to the marks of the leg summaries. */
    @Test
    fun `CR13 a journal out of step falls back to the summaries`() {
        val firstLeg = cricketMatch("Ann", "Bob", legsPerSet = 2).playLegWonBy(0)
        val confirmed = firstLeg.confirmLeg()

        val mprs = CricketMatchMpr.of(confirmed, journalOf(listOf(Dart.MISS)))

        val dartsAnn = confirmed.playerStats[0].dartsThrown
        assertEquals(firstLeg.currentLeg.state.players[0].marksHit * 3.0 / dartsAnn, mprs[0])
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
