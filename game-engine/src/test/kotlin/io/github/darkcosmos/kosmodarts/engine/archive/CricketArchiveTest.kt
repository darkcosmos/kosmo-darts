package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.cricket.cricketMatch
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AR5, AR6, DJ3 for Cricket matches. */
class CricketArchiveTest {
    @Test
    fun `standard with the 25 and Bull, four players, legs won by two players (AR5, AR6)`() {
        val session = CricketSession(cricketMatch("A", "B", "C", "D", legsPerSet = 2))
            .playLegWonBy(0).confirmLeg()
            .playLegWonBy(1).confirmLeg()
            .playLegWonBy(0)

        val archived = session.archive()

        val score = checkNotNull(session.match.finalScore)
        assertEquals(ArchivedMode.CRICKET, archived.mode)
        assertEquals("STANDARD", archived.variant)
        assertEquals(ArchivedSettings.Cricket(CricketVariant.STANDARD, true, MatchFormat(2, 1)), archived.settings)
        assertEquals(score.legWinners, archived.legs.map { it.winnerPosition })
        assertEquals(listOf(0, 1, 2), archived.legs.map { it.starterPosition })
        assertEquals(listOf(1, 1, 1), archived.legs.map { it.setNumber })
        assertEquals(listOf(1, 2, 3), archived.legs.map { it.legNumberInSet })
        assertEquals(session.match.ranking.ranksByPosition(), archived.players.map { it.rank })
        assertEquals(score.legsWonTotal, archived.players.map { it.legsWon })
        assertEquals(score.setsWon, archived.players.map { it.setsWon })
        assertEquals(1, archived.players[0].rank)
        assertTrue(archived.darts.none { it.isBust })
        assertEquals(listOf(0, 1, 2), archived.darts.map { it.legOrdinal }.distinct())
    }

    @Test
    fun `cut-throat is named by its variant and archives like standard (AR5)`() {
        val match = cricketMatch("A", "B", "C", "D", variant = CricketVariant.CUT_THROAT, legsPerSet = 2)
        val session = CricketSession(match)
            .playLegWonBy(1).confirmLeg()
            .playLegWonBy(1)

        val archived = session.archive()

        val score = checkNotNull(session.match.finalScore)
        assertEquals("CUT_THROAT", archived.variant)
        assertEquals(1, archived.players[1].rank)
        assertEquals(session.match.ranking.ranksByPosition(), archived.players.map { it.rank })
        assertEquals(score.legsWonTotal, archived.players.map { it.legsWon })
        assertEquals(listOf(1, 1), archived.legs.map { it.setNumber })
        assertFalse(archived.darts.any { it.isVoided })
    }

    @Test
    fun `a Cricket match not won is refused`() {
        val session = CricketSession(cricketMatch("A", "B")).throwing("T20")

        assertThrows(IllegalArgumentException::class.java) { session.archive() }
    }
}
