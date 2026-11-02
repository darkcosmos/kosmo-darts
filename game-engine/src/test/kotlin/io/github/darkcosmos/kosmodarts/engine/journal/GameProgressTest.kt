package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GameProgressTest {

    private val x01Config = X01Config(listOf("Alice", "Bob"), 301, CheckoutRule.SINGLE_OUT)
    private val x01Start = X01Match.start(X01MatchConfig(x01Config, MatchFormat(legsPerSet = 2)))
    private val cricketConfig = CricketConfig(listOf("Alice"), isBullInPlay = false)
    private val cricketStart = CricketMatch.start(CricketMatchConfig(cricketConfig, MatchFormat(legsPerSet = 2)))
    private val emptyJournal = MatchJournal("m1", 100L)

    /** Alice wins the first leg of 301 (single out) with her ninth dart; Bob scores 1 a dart in between. */
    private val winningDarts = listOf("T20", "T20", "T20", "S1", "S1", "S1", "T20", "T19", "S4")

    private fun wonLeg(): X01Run = winningDarts.foldIndexed(X01Run(x01Start, emptyJournal)) { i, run, code ->
        run.throwDart(code, 1L + i)
    }

    /** A progress step on an X01 match: the match, its journal, and DJ4 checked after every step. */
    private class X01Run(val match: X01Match, val journal: MatchJournal) {
        fun throwDart(code: String, at: Long): X01Run = step(match.throwDart(dart(code)), at)

        fun undo(at: Long): X01Run = step(match.undo(), at)

        fun confirm(at: Long): X01Run = step(match.confirmLeg(), at)

        private fun step(next: X01Match, at: Long): X01Run {
            val run = X01Run(next, GameProgress.follow(journal, match, next, at))
            run.assertDj4()
            return run
        }

        /** DJ4: the darts in play of the current leg journal are the darts of the current leg of the match. */
        fun assertDj4() {
            val starter = match.completedLegs.size % match.config.players.size
            val replayed = LegReplay.replay(journal.currentLeg, LegSetup.X01(match.config, starter))
            assertEquals(match.currentLeg.darts, replayed.dartsInPlay)
        }
    }

    private class CricketRun(val match: CricketMatch, val journal: MatchJournal) {
        fun throwDart(code: String, at: Long): CricketRun = step(match.throwDart(dart(code)), at)

        fun undo(at: Long): CricketRun = step(match.undo(), at)

        private fun step(next: CricketMatch, at: Long): CricketRun {
            val run = CricketRun(next, GameProgress.follow(journal, match, next, at))
            val replayed = LegReplay.replay(run.journal.currentLeg, LegSetup.Cricket(next.config, 0))
            assertEquals(next.currentLeg.darts, replayed.dartsInPlay)
            return run
        }
    }

    @Test
    fun `DJ1 a dart is journaled with the time of the caller`() {
        val run = X01Run(x01Start, emptyJournal).throwDart("T20", 500L).throwDart("S5", 510L)

        assertEquals(
            listOf(JournalEvent.DartEntered(dart("T20"), 500L), JournalEvent.DartEntered(dart("S5"), 510L)),
            run.journal.currentLeg.events,
        )
    }

    @Test
    fun `DJ2 an undo is journaled and a dart can follow it`() {
        val run = X01Run(x01Start, emptyJournal).throwDart("T20", 500L).undo(600L).throwDart("S1", 700L)

        assertEquals(
            listOf(
                JournalEvent.DartEntered(dart("T20"), 500L),
                JournalEvent.Undone,
                JournalEvent.DartEntered(dart("S1"), 700L),
            ),
            run.journal.currentLeg.events,
        )
    }

    @Test
    fun `DJ2 an undo with nothing to undo is not journaled`() {
        val run = X01Run(x01Start, emptyJournal).undo(500L)

        assertEquals(emptyJournal, run.journal)
    }

    @Test
    fun `DJ1 a confirmed leg opens the journal of the next leg and keeps the previous one`() {
        val won = wonLeg()

        val next = won.confirm(40L)

        assertEquals(2, next.journal.legs.size)
        assertEquals(won.journal.currentLeg, next.journal.legs.first())
        assertEquals(LegJournal(), next.journal.currentLeg)
    }

    @Test
    fun `DJ2 an undo on the first dart of a leg right after the confirmation journals nothing`() {
        val next = wonLeg().confirm(40L)

        val undone = next.undo(41L)

        assertEquals(next.journal, undone.journal)
    }

    @Test
    fun `DJ2 the undo of the winning dart is journaled and the win disappears`() {
        val undone = wonLeg().undo(41L)

        assertEquals(JournalEvent.Undone, undone.journal.currentLeg.events.last())
        assertEquals(winningDarts.size - 1, undone.match.currentLeg.darts.size)
    }

    @Test
    fun `DJ4 a scripted cricket leg keeps the journal in step, undo and confirmation included`() {
        val closing = listOf("T20", "T19", "T18", "T17", "T16", "T15")
        val run = closing.foldIndexed(CricketRun(cricketStart, emptyJournal)) { i, acc, code ->
            acc.throwDart(code, 10L + i)
        }.undo(20L).throwDart("T15", 21L)

        assertEquals(8, run.journal.currentLeg.events.size)
        val next = GameProgress.follow(run.journal, run.match, run.match.confirmLeg(), 30L)
        assertEquals(2, next.legs.size)
        assertEquals(run.journal.currentLeg, next.legs.first())
    }

    @Test
    fun `a journal of another length than the legs of the match is rejected`() {
        val twoLegs = emptyJournal.startNextLeg()

        assertThrows(IllegalStateException::class.java) {
            GameProgress.follow(twoLegs, x01Start, x01Start.throwDart(dart("S1")), 1L)
        }
    }

    @Test
    fun `a match that changed by two darts is rejected`() {
        val two = x01Start.throwDart(dart("S1")).throwDart(dart("S2"))

        assertThrows(IllegalStateException::class.java) {
            GameProgress.follow(emptyJournal, x01Start, two, 1L)
        }
    }
}
