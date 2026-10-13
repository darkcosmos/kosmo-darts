package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class X01TurnTest {

    /** T1: the first player in configuration order starts. */
    @Test
    fun `T1 first player starts`() {
        assertEquals(0, newGame("Alice", "Bob", "Carol").state.currentPlayerIndex)
    }

    /** T1, T2: a turn has 3 darts, then play passes to the next player. */
    @Test
    fun `T1 T2 play passes to next player after three darts`() {
        val game = newGame("Alice", "Bob").throwing("20", "20")
        assertEquals(0, game.state.currentPlayerIndex)
        assertEquals(1, game.throwing("20").state.currentPlayerIndex)
    }

    /** T2: with 4 players, play wraps around from the last player to the first. */
    @Test
    fun `T2 turn wraps around with four players`() {
        val players = arrayOf("A", "B", "C", "D")
        var game = newGame(*players)
        val order = mutableListOf<Int?>()
        repeat(5) {
            order += game.state.currentPlayerIndex
            game = game.throwing("1", "1", "1")
        }
        assertEquals(listOf(0, 1, 2, 3, 0), order)
        assertEquals(498 - 3, game.player(0).remaining)
    }

    /** T2: with 1 player, play wraps around to the same player after each turn. */
    @Test
    fun `T2 turn wraps around with one player`() {
        val game = newGame("Solo").throwing("20", "20", "20", "19")
        assertEquals(0, game.state.currentPlayerIndex)
        assertEquals(listOf(dart("19")), game.player(0).currentTurnDarts)
        assertEquals(501 - 79, game.player(0).remaining)
    }

    /** T2, S4: a bust on the second dart ends the turn immediately; the third dart belongs to the next player. */
    @Test
    fun `T2 S4 bust in the middle of a turn ends that turn`() {
        val aliceAt20 = newGame("Alice", "Bob").throwing(
            "T20", "T20", "T20", "1", "1", "1",
            "T20", "T20", "T20", "1", "1", "1",
            "T20", "T20", "1", "1", "1", "1",
        )
        assertEquals(20, aliceAt20.player(0).remaining)
        val inTurn = aliceAt20.throwing("10")
        assertEquals(10, inTurn.player(0).remaining)
        assertEquals(0, inTurn.state.currentPlayerIndex)

        val afterBust = inTurn.throwing("T20")
        assertEquals(20, afterBust.player(0).remaining)
        assertEquals(11, afterBust.player(0).dartsThrown)
        assertEquals(emptyList<Dart>(), afterBust.player(0).currentTurnDarts)
        assertEquals(1, afterBust.state.currentPlayerIndex)

        val bobThrows = afterBust.throwing("5")
        assertEquals(501 - 9 - 5, bobThrows.player(1).remaining)
        assertEquals(listOf(dart("5")), bobThrows.player(1).currentTurnDarts)
    }

    /** T2, S5: a checkout ends the turn and the leg: nobody is left to throw. */
    @Test
    fun `T2 S5 checkout ends the turn and the leg`() {
        val game = newGame("Alice", "Bob", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20", "1")
        assertEquals(null, game.state.currentPlayerIndex)
        assertEquals(0, game.state.winnerIndex)
    }
}
