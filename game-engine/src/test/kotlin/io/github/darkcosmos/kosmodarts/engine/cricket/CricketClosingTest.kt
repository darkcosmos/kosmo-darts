package io.github.darkcosmos.kosmodarts.engine.cricket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CricketClosingTest {

    @Test
    fun `CR3 every target in play starts with no mark`() {
        val game = cricketGame("Anna", "Bob")

        game.state.players.forEach { player ->
            assertEquals(CricketTarget.entries.associateWith { 0 }, player.marks)
        }
        assertEquals(CricketTarget.NUMBERS.associateWith { 0 }, cricketGame(isBullInPlay = false).player(0).marks)
    }

    @Test
    fun `CR3 three singles close a target`() {
        val game = cricketGame("Anna", "Bob").throwing("20", "20")
        assertEquals(2, game.player(0).marksOn(CricketTarget.TWENTY))
        assertFalse(game.player(0).hasClosed(CricketTarget.TWENTY))

        val closed = game.throwing("20")

        assertEquals(3, closed.player(0).marksOn(CricketTarget.TWENTY))
        assertTrue(closed.player(0).hasClosed(CricketTarget.TWENTY))
        assertEquals(0, closed.player(0).points)
    }

    @Test
    fun `CR3 a single then a triple closes with 2 marks and leaves 1 surplus mark`() {
        val game = cricketGame("Anna", "Bob").throwing("20", "T20")

        assertEquals(3, game.player(0).marksOn(CricketTarget.TWENTY))
        assertEquals(20, game.player(0).points)
    }

    @Test
    fun `CR3 two singles then a triple close with 1 mark and leave 2 surplus marks`() {
        val game = cricketGame("Anna", "Bob").throwing("19", "19", "T19")

        assertEquals(3, game.player(0).marksOn(CricketTarget.NINETEEN))
        assertEquals(38, game.player(0).points)
    }

    @Test
    fun `CR3 a triple closes at once without surplus`() {
        val game = cricketGame("Anna", "Bob").throwing("T18")

        assertTrue(game.player(0).hasClosed(CricketTarget.EIGHTEEN))
        assertEquals(0, game.player(0).points)
    }

    @Test
    fun `CR3 every mark on a target already closed is surplus and marks stay capped at 3`() {
        val game = cricketGame("Anna", "Bob").throwing("T17", "D17", "17")

        assertEquals(3, game.player(0).marksOn(CricketTarget.SEVENTEEN))
        assertEquals(51, game.player(0).points)
        assertEquals(3, game.player(0).boardMarks)
        assertEquals(6, game.player(0).marksHit)
    }

    @Test
    fun `CR3 25 and Bull close the Bull together`() {
        val game = cricketGame("Anna", "Bob").throwing("BULL", "25")

        assertTrue(game.player(0).hasClosed(CricketTarget.BULL))
        assertEquals(0, game.player(0).points)
    }

    @Test
    fun `CR3 a mark on one target never changes another target or another player`() {
        val game = cricketGame("Anna", "Bob").throwing("T16")

        assertEquals(3, game.player(0).boardMarks)
        assertEquals(0, game.player(1).boardMarks)
        assertEquals(0, game.player(0).marksOn(CricketTarget.FIFTEEN))
    }

    @Test
    fun `CR2 a miss, 1 to 14, and 25 or Bull with 25-Bull off change no mark`() {
        val game = cricketGame("Anna", "Bob", isBullInPlay = false).throwing("MISS", "T14", "D1", "25", "BULL")

        assertEquals(0, game.player(0).boardMarks)
        assertEquals(0, game.player(1).boardMarks)
        assertEquals(0, game.player(0).marksOn(CricketTarget.BULL))
        assertFalse(game.player(0).hasClosed(CricketTarget.BULL))
    }

    @Test
    fun `CR13 marks hit count every CR2 mark, surplus marks included, and darts thrown count misses`() {
        val game = cricketGame("Anna", "Bob").throwing("T20", "T20", "MISS")

        assertEquals(6, game.player(0).marksHit)
        assertEquals(3, game.player(0).boardMarks)
        assertEquals(3, game.player(0).dartsThrown)
        assertEquals(0, game.player(1).marksHit)
        assertEquals(0, game.player(1).dartsThrown)
    }
}
