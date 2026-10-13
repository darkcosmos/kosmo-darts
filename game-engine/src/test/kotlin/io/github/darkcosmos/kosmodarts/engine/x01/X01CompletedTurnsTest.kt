package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class X01CompletedTurnsTest {

    private fun turn(player: Int, codes: List<String>, outcome: TurnOutcome, before: Int, after: Int) =
        X01Turn(
            playerIndex = player,
            darts = codes.map(::dart),
            outcome = outcome,
            remainingBefore = before,
            remainingAfter = after,
        )

    /** Alice and Bob, 301, double out. */
    private val twoPlayers = newGame("Alice", "Bob", startScore = 301)

    /** A3: a new game has no completed turn. */
    @Test
    fun `A3 no completed turn in a new game`() {
        assertEquals(emptyList<X01Turn>(), twoPlayers.state.completedTurns)
    }

    /** A2, A3: the turn in progress is not a completed turn; its darts are only in the current turn. */
    @Test
    fun `A3 the turn in progress is not included`() {
        val game = twoPlayers.throwing("T20", "T20")
        assertEquals(emptyList<X01Turn>(), game.state.completedTurns)
        assertEquals(listOf(dart("T20"), dart("T20")), game.player(0).currentTurnDarts)
    }

    /** A3: a turn of three darts without bust or checkout is NORMAL, with the scores before and after. */
    @Test
    fun `A3 normal turn`() {
        val game = twoPlayers.throwing("T20", "D10", "MISS")
        assertEquals(
            listOf(turn(0, listOf("T20", "D10", "MISS"), TurnOutcome.NORMAL, 301, 221)),
            game.state.completedTurns,
        )
    }

    /** A3, S4: a bust turn keeps its darts (up to the bust dart) and its score after equals its score before. */
    @Test
    fun `A3 S4 bust turn`() {
        val game = twoPlayers.throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20")
        assertEquals(
            listOf(
                turn(0, listOf("T20", "T20", "T20"), TurnOutcome.NORMAL, 301, 121),
                turn(1, listOf("1", "1", "1"), TurnOutcome.NORMAL, 301, 298),
                turn(0, listOf("T20", "T20"), TurnOutcome.BUST, 121, 121),
            ),
            game.state.completedTurns,
        )
    }

    /** A3, S5: the winning turn is completed with outcome CHECKOUT and a score after of 0. */
    @Test
    fun `A3 S5 checkout turn`() {
        val game = twoPlayers.throwing("T20", "T20", "T20", "1", "1", "1", "T20", "11", "BULL")
        assertEquals(
            turn(0, listOf("T20", "11", "BULL"), TurnOutcome.CHECKOUT, 121, 0),
            game.state.completedTurns.last(),
        )
        assertEquals(3, game.state.completedTurns.size)
    }

    /** A3: turns are listed in throw order, across players and wrap-around, including a two-dart bust. */
    @Test
    fun `A3 completed turns are listed in order across players`() {
        val game = twoPlayers.throwing("20", "20", "20", "T20", "T20", "T20", "MISS", "MISS", "MISS", "T20", "T20")
        assertEquals(
            listOf(
                turn(0, listOf("20", "20", "20"), TurnOutcome.NORMAL, 301, 241),
                turn(1, listOf("T20", "T20", "T20"), TurnOutcome.NORMAL, 301, 121),
                turn(0, listOf("MISS", "MISS", "MISS"), TurnOutcome.NORMAL, 241, 241),
                turn(1, listOf("T20", "T20"), TurnOutcome.BUST, 121, 121),
            ),
            game.state.completedTurns,
        )
        assertEquals(listOf(0, 1, 0, 1), game.state.completedTurns.map(X01Turn::playerIndex))
    }

    /** A3, U1: undoing the dart that completed a normal turn removes that turn and reopens it. */
    @Test
    fun `A3 U1 undo of the last dart of a normal turn reopens it`() {
        val game = twoPlayers.throwing("T20", "T20", "T20").undo()
        assertEquals(emptyList<X01Turn>(), game.state.completedTurns)
        assertEquals(listOf(dart("T20"), dart("T20")), game.player(0).currentTurnDarts)
    }

    /** A3, U1: undoing the first dart of the next turn keeps the previous completed turns unchanged. */
    @Test
    fun `A3 U1 undo in the next turn keeps completed turns`() {
        val completed = twoPlayers.throwing("T20", "T20", "T20")
        assertEquals(completed.state.completedTurns, completed.throwing("5").undo().state.completedTurns)
        assertEquals(1, completed.state.completedTurns.size)
    }

    /** A3, U1, S4: undoing the bust dart removes the BUST turn and gives back the turn in progress. */
    @Test
    fun `A3 U1 undo of a bust dart removes the bust turn`() {
        val busted = twoPlayers.throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20")
        val undone = busted.undo()
        assertEquals(busted.state.completedTurns.dropLast(1), undone.state.completedTurns)
        assertEquals(listOf(dart("T20")), undone.player(0).currentTurnDarts)
        val replayed = undone.throwing("T20")
        assertEquals(busted.state.completedTurns, replayed.state.completedTurns)
    }

    /** A3, U1, U3: undoing the winning dart removes the CHECKOUT turn and reopens the leg. */
    @Test
    fun `A3 U1 U3 undo of the winning dart removes the checkout turn`() {
        val won = twoPlayers.throwing("T20", "T20", "T20", "1", "1", "1", "T20", "11", "BULL")
        val undone = won.undo()
        assertEquals(
            listOf(TurnOutcome.NORMAL, TurnOutcome.NORMAL),
            undone.state.completedTurns.map(X01Turn::outcome),
        )
        assertEquals(listOf(dart("T20"), dart("11")), undone.player(0).currentTurnDarts)
        assertEquals(0, undone.state.currentPlayerIndex)
    }

    /** A3, P1: completed turns are rebuilt identically by replaying the darts. */
    @Test
    fun `A3 P1 completed turns are rebuilt by replay`() {
        val game = twoPlayers.throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20", "5")
        assertEquals(game.state.completedTurns, X01Game(game.config, game.darts).state.completedTurns)
    }
}
