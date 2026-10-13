package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class X01UndoTest {

    /** U1: undo removes the last dart and gives back exactly the previous state. */
    @Test
    fun `U1 undo restores the exact previous state`() {
        val before = newGame("Alice", "Bob").throwing("T20", "5")
        val after = before.throwing("D7").undo()
        assertEquals(before, after)
        assertEquals(before.state, after.state)
        assertEquals(before.darts, after.darts)
    }

    /** U1: undoing the first dart of a turn goes back to the previous player's turn. */
    @Test
    fun `U1 undo across players`() {
        val endOfAliceTurn = newGame("Alice", "Bob").throwing("T20", "T20", "T20")
        val bobFirstDart = endOfAliceTurn.throwing("19")
        assertEquals(1, bobFirstDart.state.currentPlayerIndex)

        val undoneOnce = bobFirstDart.undo()
        assertEquals(endOfAliceTurn.state, undoneOnce.state)
        assertEquals(1, undoneOnce.state.currentPlayerIndex)

        val undoneTwice = undoneOnce.undo()
        assertEquals(0, undoneTwice.state.currentPlayerIndex)
        assertEquals(381, undoneTwice.player(0).remaining)
        assertEquals(listOf(dart("T20"), dart("T20")), undoneTwice.player(0).currentTurnDarts)
    }

    /** U1, S4: undoing a bust dart restores the in-turn score and keeps the turn open. */
    @Test
    fun `U1 S4 undo across a bust`() {
        val inTurn = newGame("Alice", "Bob", startScore = 301).throwing(
            "T20", "T20", "T20", "1", "1", "1",
            "T20", "T20", // Alice: 121, then 61, then the second T20 would leave 1: bust.
        )
        assertEquals(1, inTurn.state.currentPlayerIndex)
        assertEquals(121, inTurn.player(0).remaining)

        val beforeBust = newGame("Alice", "Bob", startScore = 301)
            .throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20")
            .undo()
        assertEquals(0, beforeBust.state.currentPlayerIndex)
        assertEquals(61, beforeBust.player(0).remaining)
        assertEquals(listOf(dart("T20")), beforeBust.player(0).currentTurnDarts)
    }

    /** U1, U3: undoing the winning dart reopens the leg with the previous state. */
    @Test
    fun `U1 U3 undo of the winning dart reopens the leg`() {
        val beforeWin = newGame("Alice", "Bob", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20")
        val won = beforeWin.throwing("1")
        assertTrue(won.state.isFinished)

        val reopened = won.undo()
        assertFalse(reopened.state.isFinished)
        assertNull(reopened.state.winnerIndex)
        assertEquals(emptyList<X01Rank>(), reopened.state.ranking)
        assertEquals(0, reopened.state.currentPlayerIndex)
        assertEquals(1, reopened.player(0).remaining)
        assertEquals(beforeWin.state, reopened.state)
        assertFalse(reopened.throwing("MISS").state.isFinished)
    }

    /** U2: undo can be repeated down to an empty game, which equals a new game. */
    @Test
    fun `U2 repeated undo goes back to an empty game`() {
        val fresh = newGame("Alice", "Bob")
        var game = fresh.throwing("T20", "T20", "T20", "1", "1", "1", "20")
        repeat(7) {
            assertTrue(game.canUndo)
            game = game.undo()
        }
        assertEquals(fresh, game)
        assertEquals(fresh.state, game.state)
        assertFalse(game.canUndo)
    }

    /** U2: undo on an empty game has no effect, and the engine says undo is not possible. */
    @Test
    fun `U2 undo on an empty game has no effect`() {
        val fresh = newGame("Alice")
        assertFalse(fresh.canUndo)
        val undone = fresh.undo()
        assertSame(fresh, undone)
        assertEquals(fresh.state, undone.state)
    }

    /** U2: undo is possible as soon as one dart has been recorded, including after a checkout. */
    @Test
    fun `U2 undo is possible after any dart`() {
        assertTrue(newGame("Alice").throwing("MISS").canUndo)
        val won = newGame("Alice", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "T20", "T20", "1")
        assertTrue(won.state.isFinished)
        assertTrue(won.canUndo)
    }
}
