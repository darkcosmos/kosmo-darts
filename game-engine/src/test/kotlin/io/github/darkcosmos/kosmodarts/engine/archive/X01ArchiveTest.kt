package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.board.nominalPoint
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.newMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AR5, AR6, BC2, DJ3, DJ4 for X01 matches. */
class X01ArchiveTest {
    @Test
    fun `single leg of two players keeps the session, settings, results and darts (AR5, AR6)`() {
        val session = X01Session(newMatch("Alice", "Bob")).playLegWonBy(0)

        val archived = session.archive()

        assertEquals(MATCH_ID, archived.matchId)
        assertEquals(SessionType.MATCH, archived.sessionType)
        assertEquals(STARTED_AT, archived.startedAt)
        assertEquals(ENDED_AT, archived.endedAt)
        assertEquals(ENGINE_RULES_VERSION, archived.rulesVersion)
        assertEquals(ArchivedMode.X01, archived.mode)
        assertEquals("301", archived.variant)
        assertEquals(ArchivedSettings.X01(301, CheckoutRule.SINGLE_OUT, MatchFormat()), archived.settings)
        assertEquals(
            listOf(
                ArchivedPlayer(0, "Alice", ColorSlot.SLOT_1, rank = 1, legsWon = 1, setsWon = 1),
                ArchivedPlayer(1, "Bob", ColorSlot.SLOT_2, rank = 2, legsWon = 0, setsWon = 0),
            ),
            archived.players,
        )
        assertEquals(listOf(ArchivedLeg(0, 1, 1, starterPosition = 0, winnerPosition = 0)), archived.legs)
        assertEquals(session.match.currentLeg.darts, archived.darts.map { it.dart() })
        assertEquals(session.match.currentLeg.darts.size, archived.darts.size)
        assertEquals(List(archived.darts.size) { 2_000L + it }, archived.darts.map { it.enteredAt })
        assertTrue(archived.darts.all { it.source == DartSource.MANUAL && !it.isVoided })
    }

    @Test
    fun `two sets of three legs for three players with undos match score and ranking (AR6, M19, M6)`() {
        val session = X01Session(newMatch("Ann", "Ben", "Cat", legsPerSet = 3, sets = 2))
            .throwing("T20", "T19")
            .undo()
            .undo()
            .playLegWonBy(0).confirmLeg()
            .playLegWonBy(1).confirmLeg()
            .playLegWonBy(0).confirmLeg()
            .playLegWonBy(0).confirmLeg()
            .playLegWonBy(1).confirmLeg()
            .playLegWonBy(1).confirmLeg()
            .playLegWonBy(0).confirmLeg()
            .playLegWonBy(0).confirmLeg()
            .playLegWonBy(0)
            .undo()
            .playLegWonBy(0)

        val archived = session.archive()

        val score = checkNotNull(session.match.finalScore)
        assertEquals(score.legWinners, archived.legs.map { it.winnerPosition })
        assertEquals(session.match.ranking.ranksByPosition(), archived.players.map { it.rank })
        assertEquals(score.legsWonTotal, archived.players.map { it.legsWon })
        assertEquals(score.setsWon, archived.players.map { it.setsWon })
        assertEquals(listOf(1, 2, 3), archived.players.map { it.rank })
        assertEquals(List(9) { it }, archived.legs.map { it.ordinal })
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 2), archived.legs.map { it.setNumber })
        assertEquals(listOf(1, 2, 3, 4, 1, 2, 3, 4, 5), archived.legs.map { it.legNumberInSet })
        assertEquals(List(9) { it % 3 }, archived.legs.map { it.starterPosition })
        assertEquals(2, archived.darts.count { it.isVoided && it.legOrdinal == 0 })
        assertEquals(listOf(true, false), archived.darts.inLeg(8).map { it.isVoided }.takeLast(2))
        assertEquals(session.journal.legs.size, archived.darts.map { it.legOrdinal }.distinct().size)
    }

    @Test
    fun `solo match gets rank 1 and one player (AR6)`() {
        val archived = X01Session(newMatch("Solo")).playLegWonBy(0).archive()

        assertEquals(listOf(1), archived.players.map { it.rank })
        assertEquals(listOf(0), archived.legs.map { it.winnerPosition })
    }

    @Test
    fun `double-out archive keeps the bust visit and the winning double not bust (DJ3)`() {
        val session = X01Session(newMatch("Alice", checkoutRule = CheckoutRule.DOUBLE_OUT))
            .throwing("T20", "T20", "T20")
            .throwing("T20", "T20")
            .throwing("T20", "S11", "BULL")

        val darts = session.archive().darts

        assertEquals(listOf(1, 1, 1, 2, 2, 3, 3, 3), darts.map { it.visit })
        assertEquals(listOf(1, 2, 3, 1, 2, 1, 2, 3), darts.map { it.rank })
        assertEquals(listOf(false, false, false, true, true, false, false, false), darts.map { it.isBust })
        assertEquals(Dart.BULL, darts.last().dart())
        assertFalse(darts.last().isBust)
    }

    @Test
    fun `single-out archive keeps a single finish as the winning dart, not bust (DJ3)`() {
        val session = X01Session(newMatch("Alice", checkoutRule = CheckoutRule.SINGLE_OUT))
            .throwing("T20", "T20", "T20")
            .throwing("T19", "T20", "S4")

        val darts = session.archive().darts

        assertEquals(Dart.single(4), darts.last().dart())
        assertTrue(darts.none { it.isBust })
        assertEquals(listOf(1, 1, 1, 2, 2, 2), darts.map { it.visit })
    }

    @Test
    fun `dart point is the nominal point of a hit and absent for a miss (BC2, D33)`() {
        val session = X01Session(newMatch("Alice", "Bob"))
            .throwing("T20", "MISS", "BULL")
            .playLegWonBy(0)

        val darts = session.archive().darts

        assertEquals(nominalPoint(Dart.triple(20)), darts[0].point)
        assertNotNull(darts[0].point)
        assertNull(darts[1].point)
        assertEquals(nominalPoint(Dart.BULL), darts[2].point)
        assertEquals(Multiplier.TRIPLE, darts[0].multiplier)
        assertEquals(20, darts[0].segment)
    }

    @Test
    fun `a voided dart is archived as voided with its entry values (DJ3)`() {
        val darts = X01Session(newMatch("Alice", "Bob"))
            .throwing("T20", "T19")
            .undo()
            .playLegWonBy(0)
            .archive()
            .darts

        assertEquals(listOf(false, true), darts.take(2).map { it.isVoided })
        assertEquals(listOf(1, 2), darts.take(2).map { it.rank })
        assertEquals(Dart.triple(19), darts[1].dart())
    }

    @Test
    fun `a match not won is refused`() {
        val session = X01Session(newMatch("Alice", "Bob")).throwing("T20")

        assertThrows(IllegalArgumentException::class.java) { session.archive() }
    }

    @Test
    fun `a journal with a missing leg is refused`() {
        val session = X01Session(newMatch("Alice", "Bob", legsPerSet = 2)).playLegWonBy(0).confirmLeg().playLegWonBy(0)
        val missing = session.journal.copy(legs = session.journal.legs.drop(1))

        assertThrows(IllegalArgumentException::class.java) {
            MatchArchiver.archive(session.match, missing, ENDED_AT)
        }
    }

    @Test
    fun `a current leg journal that differs from the match leg is refused (DJ4)`() {
        val session = X01Session(newMatch("Alice", "Bob")).playLegWonBy(0)
        val journal = session.journal.undo().enter(Dart.MISS, 5_000L)

        assertThrows(IllegalArgumentException::class.java) {
            MatchArchiver.archive(session.match, journal, ENDED_AT)
        }
    }
}
