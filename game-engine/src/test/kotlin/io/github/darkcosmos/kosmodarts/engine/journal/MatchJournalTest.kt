package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MatchJournalTest {

    private val setup = x01Setup("Alice", "Bob")
    private val empty = MatchJournal("m1", startedAt = 500L)

    /** DJ1: a match journal starts with one empty leg and keeps its id and start time. */
    @Test
    fun `DJ1 new match journal has one empty leg`() {
        assertEquals(1, empty.legs.size)
        assertTrue(empty.currentLeg.events.isEmpty())
        assertEquals("m1", empty.matchId)
        assertEquals(500L, empty.startedAt)
    }

    /** DJ1: the clock value passed in is the entry time that the replay gives back. */
    @Test
    fun `DJ1 entry time comes from the injected clock`() {
        val clock = Clock { 777L }
        val journal = empty.enter(Dart.BULL, clock.nowMillis())

        val leg = LegReplay.replay(journal.currentLeg, setup)

        assertEquals(listOf(777L), leg.darts.map { it.enteredAt })
        assertEquals(DartSource.MANUAL, leg.darts.single().source)
    }

    /** DJ2: undo of the first dart of a new leg changes only that leg. */
    @Test
    fun `DJ2 undo in a new leg leaves the previous legs untouched`() {
        val first = empty.enter(Dart.single(20), 1L).enter(Dart.single(5), 2L).startNextLeg()
        val second = first.enter(Dart.single(1), 3L).undo()

        assertEquals(first.legs[0], second.legs[0])
        assertEquals(2, second.legs.size)
        val leg = LegReplay.replay(second.currentLeg, setup)
        assertEquals(listOf(true), leg.voids())
        assertEquals(listOf(Triple(0, 1, 1)), leg.positions())
        assertTrue(leg.dartsInPlay.isEmpty())
    }

    /** DJ2: an undo with nothing to undo is rejected by the replay (the caller does not journal it). */
    @Test
    fun `DJ2 undo with nothing to undo is rejected in a new leg`() {
        val journal = empty.enter(Dart.single(20), 1L).startNextLeg().undo()

        assertThrows(JournalReplayException::class.java) { LegReplay.replay(journal.currentLeg, setup) }
    }

    @Test
    fun `a journal needs at least one leg`() {
        assertThrows(IllegalArgumentException::class.java) { MatchJournal("m1", 0L, emptyList()) }
    }

    @Test
    fun `dartsInPlay counts entered darts minus undos`() {
        val journal = empty.enter(Dart.single(1), 1L).enter(Dart.single(2), 2L).undo()

        assertEquals(1, journal.currentLeg.dartsInPlay)
    }

    /** Rejections: undo on an empty leg. */
    @Test
    fun `undo on an empty leg is rejected`() {
        assertThrows(JournalReplayException::class.java) { replay(setup, UNDO) }
        assertThrows(JournalReplayException::class.java) { replay(setup, "1", UNDO, UNDO) }
    }

    /** Rejections: a dart after the win, X01 and Cricket. */
    @Test
    fun `a dart after the win is rejected`() {
        assertThrows(JournalReplayException::class.java) { replay(x01Setup("Alice"), *WIN_301_ONE_DART, "1") }
        assertThrows(JournalReplayException::class.java) { replay(cricketSetup("Alice"), *WIN_CRICKET, "1") }
    }
}
