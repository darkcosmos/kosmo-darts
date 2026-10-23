package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CricketTurnTest {

    @Test
    fun `CR7 the first player starts by default`() {
        val game = cricketGame("Anna", "Bob")

        assertEquals(0, game.state.currentPlayerIndex)
        assertNull(game.state.winnerIndex)
        assertTrue(game.state.completedTurns.isEmpty())
        assertTrue(game.state.ranking.isEmpty())
    }

    @Test
    fun `CR7 a turn has 3 darts, then the next player throws`() {
        val twoDarts = cricketGame("Anna", "Bob").throwing("20", "MISS")
        assertEquals(0, twoDarts.state.currentPlayerIndex)
        assertEquals(listOf(dart("20"), dart("MISS")), twoDarts.player(0).currentTurnDarts)

        val turnDone = twoDarts.throwing("T19")

        assertEquals(1, turnDone.state.currentPlayerIndex)
        assertEquals(emptyList<Any>(), turnDone.player(0).currentTurnDarts)
        assertEquals(
            listOf(CricketTurn(playerIndex = 0, darts = listOf(dart("20"), dart("MISS"), dart("T19")), marks = 4)),
            turnDone.state.completedTurns,
        )
    }

    @Test
    fun `CR7 players rotate in configuration order and back to the first`() {
        val game = cricketGame("Anna", "Bob", "Cleo")
        val order = (0..3).map { turns -> game.turns(*Array(turns) { MISSES }).state.currentPlayerIndex }

        assertEquals(listOf(0, 1, 2, 0), order)
    }

    @Test
    fun `CR7 a leg can start with another player, who throws first, then rotation continues`() {
        val game = cricketGame("Anna", "Bob", "Cleo", startingPlayerIndex = 2)
        assertEquals(2, game.state.currentPlayerIndex)

        val afterOneTurn = game.turns("T20 MISS MISS")

        assertEquals(0, afterOneTurn.state.currentPlayerIndex)
        assertEquals(3, afterOneTurn.player(2).marksOn(CricketTarget.TWENTY))
        assertEquals(2, afterOneTurn.state.completedTurns.single().playerIndex)
    }

    @Test
    fun `CR7 an invalid starting player is rejected`() {
        val config = CricketConfig(listOf("Anna", "Bob"))

        assertThrows<IllegalArgumentException> { CricketGame(config, startingPlayerIndex = 2) }
        assertThrows<IllegalArgumentException> { CricketGame(config, startingPlayerIndex = -1) }
    }

    @Test
    fun `CR7 there is no bust, darts that give no mark never end a turn early`() {
        val game = cricketGame("Anna", "Bob").throwing("MISS", "T14")

        assertEquals(0, game.state.currentPlayerIndex)
        assertEquals(2, game.player(0).currentTurnDarts.size)
        assertEquals(2, game.player(0).dartsThrown)
    }

    @Test
    fun `CR7 a winning dart ends the turn before its third dart`() {
        val won = cricketGame().turns(CLOSE_20_19_18, CLOSE_17_16_15).throwing("BULL", "25")

        assertEquals(CricketTurn(0, listOf(dart("BULL"), dart("25")), marks = 3), won.state.completedTurns.last())
        assertEquals(3, won.state.completedTurns.size)
        assertNull(won.state.currentPlayerIndex)
    }

    @Test
    fun `CR14 the current player's turn marks count the CR2 marks of his darts`() {
        val game = cricketGame("Anna", "Bob").throwing("T20", "BULL")

        assertEquals(5, game.player(0).currentTurnMarks)
        assertEquals(0, game.player(1).currentTurnMarks)
    }
}
