package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegReplayVisitsTest {

    /** DJ3: player, visit and rank of every dart, here with a non-zero starter. */
    @Test
    fun `DJ3 three players with the second player starting`() {
        val leg = replay(x01Setup("Alice", "Bob", "Cara", starter = 1), "1", "2", "3", "4", "5", "6", "7")

        assertEquals(
            listOf(
                Triple(1, 1, 1), Triple(1, 1, 2), Triple(1, 1, 3),
                Triple(2, 2, 1), Triple(2, 2, 2), Triple(2, 2, 3),
                Triple(0, 3, 1),
            ),
            leg.positions(),
        )
    }

    /** DJ3: visits count every player's visits, over four players and several rounds. */
    @Test
    fun `DJ3 four players over two rounds`() {
        val tokens = Array(25) { "1" }
        val leg = replay(x01Setup("A", "B", "C", "D"), *tokens)

        leg.darts.forEachIndexed { n, record ->
            assertEquals(n / 3 % 4, record.playerIndex, "player of dart $n")
            assertEquals(n / 3 + 1, record.visit, "visit of dart $n")
            assertEquals(n % 3 + 1, record.rank, "rank of dart $n")
        }
    }

    /** DJ3: Cricket gets the same visits and ranks. */
    @Test
    fun `DJ3 cricket visits and ranks`() {
        val leg = replay(cricketSetup("Alice", "Bob"), "20", "20", "20", "19", "19", "19", "18")

        assertEquals(
            listOf(
                Triple(0, 1, 1), Triple(0, 1, 2), Triple(0, 1, 3),
                Triple(1, 2, 1), Triple(1, 2, 2), Triple(1, 2, 3),
                Triple(0, 3, 1),
            ),
            leg.positions(),
        )
    }

    /** DJ1: entry time and source are kept on every record. */
    @Test
    fun `DJ1 keeps the entry time and the source`() {
        val journal = LegJournal()
            .with(JournalEvent.DartEntered(Dart.BULL, 42L, DartSource.CAMERA))
            .with(JournalEvent.DartEntered(Dart.MISS, 43L))

        val leg = LegReplay.replay(journal, x01Setup("Alice"))

        assertEquals(listOf(42L, 43L), leg.darts.map { it.enteredAt })
        assertEquals(listOf(DartSource.CAMERA, DartSource.MANUAL), leg.darts.map { it.source })
        assertEquals(listOf(Dart.BULL, Dart.MISS), leg.darts.map { it.dart })
    }

    /** Winning with one dart in the last visit: visit 3, rank 1. */
    @Test
    fun `a leg won with one dart in the last visit`() {
        val leg = replay(x01Setup("Alice"), *WIN_301_ONE_DART)

        assertTrue(leg.isFinished)
        assertEquals(Triple(0, 3, 1), leg.positions().last())
        assertFalse(leg.darts.last().isBust)
    }

    /** Winning with two darts in the last visit: visit 3, rank 2. */
    @Test
    fun `a leg won with two darts in the last visit`() {
        val leg = replay(x01Setup("Alice"), *WIN_301_TWO_DARTS)

        assertTrue(leg.isFinished)
        assertEquals(listOf(Triple(0, 3, 1), Triple(0, 3, 2)), leg.positions().takeLast(2))
        assertFalse(leg.darts.last().isBust)
    }

    /** DJ4 on plain journals, X01 and Cricket. */
    @Test
    fun `DJ4 darts in play equal the engine darts without undo`() {
        assertDartsInPlayMatchEngine(x01Setup("Alice", "Bob"), "T20", "5", "1", "D3")
        assertDartsInPlayMatchEngine(cricketSetup("Alice", "Bob"), "20", "20", "19", "MISS")
    }
}
