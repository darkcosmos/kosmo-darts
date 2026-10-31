package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CricketUndoTest {

    @Test
    fun `CR11 undo inside a turn removes the last dart and its marks`() {
        val game = cricketGame("Anna", "Bob").throwing("20", "T19")

        val undone = game.undo()

        assertEquals(cricketGame("Anna", "Bob").throwing("20"), undone)
        assertEquals(0, undone.player(0).marksOn(CricketTarget.NINETEEN))
        assertEquals(listOf(dart("20")), undone.player(0).currentTurnDarts)
        assertEquals(0, undone.state.currentPlayerIndex)
    }

    @Test
    fun `CR11 undo across turns gives the previous player his turn back`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS 18")
        assertEquals(1, game.state.currentPlayerIndex)

        val undone = game.undo()

        assertEquals(0, undone.state.currentPlayerIndex)
        assertEquals(listOf(dart("T20"), dart("MISS")), undone.player(0).currentTurnDarts)
        assertTrue(undone.state.completedTurns.isEmpty())
        assertEquals(0, undone.player(0).marksOn(CricketTarget.EIGHTEEN))
    }

    @Test
    fun `CR11 undo of a scoring dart restores the points, in both variants`() {
        val standard = cricketGame("Anna", "Bob").throwing("T20", "D20")
        val cutThroat = cricketGame("Anna", "Bob", variant = CricketVariant.CUT_THROAT).throwing("T20", "D20")
        assertEquals(listOf(40, 0), standard.points())
        assertEquals(listOf(0, 40), cutThroat.points())

        assertEquals(listOf(0, 0), standard.undo().points())
        assertEquals(listOf(0, 0), cutThroat.undo().points())
        assertEquals(3, standard.undo().player(0).marksHit)
    }

    @Test
    fun `CR11 undo of the winning dart reopens the leg`() {
        val won = cricketGame("Anna", "Bob")
            .turns(CLOSE_20_19_18, MISSES, CLOSE_17_16_15, MISSES)
            .throwing("BULL", "25")
        assertEquals(0, won.state.winnerIndex)

        val reopened = won.undo()

        assertNull(reopened.state.winnerIndex)
        assertFalse(reopened.state.isFinished)
        assertTrue(reopened.state.ranking.isEmpty())
        assertEquals(0, reopened.state.currentPlayerIndex)
        assertEquals(listOf(dart("BULL")), reopened.player(0).currentTurnDarts)
        assertEquals(dart("MISS"), reopened.throwDart(dart("MISS")).darts.last())
    }

    @Test
    fun `CR11 undo can be repeated down to an empty leg, where it changes nothing`() {
        val empty = cricketGame("Anna", "Bob")
        assertFalse(empty.canUndo)
        assertSame(empty, empty.undo())

        val game = empty.throwing("T20", "BULL")
        assertTrue(game.canUndo)

        assertEquals(empty, game.undo().undo())
    }

    @Test
    fun `CR11 undo keeps the configuration and the starting player`() {
        val game = cricketGame(
            "Anna",
            "Bob",
            variant = CricketVariant.CUT_THROAT,
            isBullInPlay = false,
            startingPlayerIndex = 1,
        ).throwing("T20")

        val undone = game.undo()

        assertEquals(game.config, undone.config)
        assertEquals(1, undone.startingPlayerIndex)
        assertEquals(1, undone.state.currentPlayerIndex)
    }
}
