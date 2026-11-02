package io.github.darkcosmos.kosmodarts.engine.journal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegReplayUndoTest {

    private val two = x01Setup("Alice", "Bob")

    /** DJ2, DJ3: a, b, undo, undo, c: b was entered at rank 2 and keeps it, c takes the rank of a. */
    @Test
    fun `DJ2 two undos in a row keep the entry values of the voided darts`() {
        val tokens = arrayOf("1", "2", UNDO, UNDO, "3")
        val leg = replay(two, *tokens)

        assertEquals(listOf(Triple(0, 1, 1), Triple(0, 1, 2), Triple(0, 1, 1)), leg.positions())
        assertEquals(listOf(true, true, false), leg.voids())
        assertDartsInPlayMatchEngine(two, *tokens)
    }

    /** DJ2, DJ3: a, undo, b, undo, c: b was entered at rank 1. */
    @Test
    fun `DJ2 undo then enter gives rank 1 to the second dart`() {
        val tokens = arrayOf("1", UNDO, "2", UNDO, "3")
        val leg = replay(two, *tokens)

        assertEquals(listOf(Triple(0, 1, 1), Triple(0, 1, 1), Triple(0, 1, 1)), leg.positions())
        assertEquals(listOf(true, true, false), leg.voids())
        assertDartsInPlayMatchEngine(two, *tokens)
    }

    /** DJ2: undo across a turn boundary voids the other player's dart, then the previous player's last dart. */
    @Test
    fun `DJ2 undo across a turn boundary`() {
        val tokens = arrayOf("1", "2", "3", "4", UNDO, UNDO, "5")
        val leg = replay(two, *tokens)

        assertEquals(
            listOf(Triple(0, 1, 1), Triple(0, 1, 2), Triple(0, 1, 3), Triple(1, 2, 1), Triple(0, 1, 3)),
            leg.positions(),
        )
        assertEquals(listOf(false, false, true, true, false), leg.voids())
        assertDartsInPlayMatchEngine(two, *tokens)
    }

    /** DJ2: undoing the winning dart of an X01 leg voids it and the leg is no longer won. */
    @Test
    fun `DJ2 undo of the winning X01 dart`() {
        val tokens = arrayOf(*WIN_301_ONE_DART, UNDO)
        val leg = replay(x01Setup("Alice"), *tokens)

        assertFalse(leg.isFinished)
        assertTrue(leg.darts.last().isVoided)
        assertEquals(Triple(0, 3, 1), leg.positions().last())
        assertDartsInPlayMatchEngine(x01Setup("Alice"), *tokens)
    }

    /** DJ2: undoing the winning dart of a Cricket leg voids it and the leg is no longer won. */
    @Test
    fun `DJ2 undo of the winning Cricket dart`() {
        val setup = cricketSetup("Alice")
        val won = replay(setup, *WIN_CRICKET)
        assertTrue(won.isFinished)

        val tokens = arrayOf(*WIN_CRICKET, UNDO)
        val leg = replay(setup, *tokens)

        assertFalse(leg.isFinished)
        assertTrue(leg.darts.last().isVoided)
        assertDartsInPlayMatchEngine(setup, *tokens)
    }

    /** DJ2: the leg can be won again after the undo of the winning dart. */
    @Test
    fun `DJ2 enter a winning dart again after undo`() {
        val tokens = arrayOf(*WIN_301_ONE_DART, UNDO, "D20")
        val leg = replay(x01Setup("Alice"), *tokens)

        assertTrue(leg.isFinished)
        assertEquals(listOf(true, false), leg.voids().takeLast(2))
        assertDartsInPlayMatchEngine(x01Setup("Alice"), *tokens)
    }

    /** DJ2, DJ3: undo right after a bust voids the bust dart; the rest of the visit is no longer bust. */
    @Test
    fun `DJ2 undo right after a bust`() {
        val setup = x01Setup("Alice", "Bob")
        val bust = arrayOf("T20", "T20", "T20", "1", "1", "1", "T20", "T20")
        assertEquals(listOf(true, true), replay(setup, *bust).busts().takeLast(2))

        val tokens = arrayOf(*bust, UNDO)
        val leg = replay(setup, *tokens)

        assertEquals(listOf(false, true), leg.busts().takeLast(2))
        assertEquals(listOf(false, true), leg.voids().takeLast(2))
        assertDartsInPlayMatchEngine(setup, *tokens)
    }

    /** DJ2: undo of the only dart of a leg journal leaves an empty leg. */
    @Test
    fun `DJ2 undo of the first dart of a leg`() {
        val tokens = arrayOf("1", UNDO)
        val leg = replay(two, *tokens)

        assertEquals(listOf(true), leg.voids())
        assertTrue(leg.dartsInPlay.isEmpty())
        assertDartsInPlayMatchEngine(two, *tokens)
    }
}
