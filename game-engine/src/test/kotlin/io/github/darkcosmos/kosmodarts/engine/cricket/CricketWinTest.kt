package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CricketWinTest {

    @Test
    fun `CR8 Standard, closing every target with more points wins on that dart`() {
        val beforeLastDart = cricketGame("Anna", "Bob")
            .turns("T20 T20 MISS", MISSES, "T19 T18 T17", MISSES, "T16 T15 BULL", MISSES)
        assertFalse(beforeLastDart.state.isFinished)

        val won = beforeLastDart.throwing("25")

        assertEquals(0, won.state.winnerIndex)
        assertTrue(won.state.isFinished)
        assertNull(won.state.currentPlayerIndex)
        assertEquals(listOf(60, 0), won.points())
    }

    @Test
    fun `CR8 Standard, closing every target with equal points wins`() {
        val won = cricketGame("Anna", "Bob")
            .turns(CLOSE_20_19_18, MISSES, CLOSE_17_16_15, MISSES)
            .throwing("BULL", "25")

        assertEquals(0, won.state.winnerIndex)
        assertEquals(listOf(0, 0), won.points())
    }

    @Test
    fun `CR8 CR10 Standard, a closed player behind on points keeps playing, then wins once he catches up`() {
        val closedBehind = cricketGame("Anna", "Bob")
            .turns(MISSES, "T20 T20 MISS", CLOSE_20_19_18, MISSES, CLOSE_17_16_15, MISSES, CLOSE_BULL)
        assertNull(closedBehind.state.winnerIndex)
        assertTrue(CricketTarget.entries.all(closedBehind.player(0)::hasClosed))
        assertEquals(listOf(0, 60), closedBehind.points())
        assertEquals(1, closedBehind.state.currentPlayerIndex)

        val stillBehind = closedBehind.turns(MISSES).throwing("T19")
        assertNull(stillBehind.state.winnerIndex)
        assertEquals(listOf(57, 60), stillBehind.points())

        val won = stillBehind.throwing("19")

        assertEquals(0, won.state.winnerIndex)
        assertEquals(listOf(76, 60), won.points())
    }

    @Test
    fun `CR8 Cut-throat, closing every target with fewer points wins`() {
        val won = cricketGame("Anna", "Bob", variant = CricketVariant.CUT_THROAT)
            .turns("T20 T20 MISS", MISSES, "T19 T18 T17", MISSES, "T16 T15 BULL", MISSES)
            .throwing("25")

        assertEquals(0, won.state.winnerIndex)
        assertEquals(listOf(0, 60), won.points())
    }

    @Test
    fun `CR8 Cut-throat, closing every target with equal points wins`() {
        val won = cricketGame("Anna", "Bob", variant = CricketVariant.CUT_THROAT)
            .turns(CLOSE_20_19_18, MISSES, CLOSE_17_16_15, MISSES)
            .throwing("BULL", "25")

        assertEquals(0, won.state.winnerIndex)
    }

    @Test
    fun `CR8 CR10 Cut-throat, closing every target with more points does not win`() {
        val game = cricketGame("Anna", "Bob", variant = CricketVariant.CUT_THROAT)
            .turns(MISSES, "T20 T20 MISS", CLOSE_20_19_18, MISSES, CLOSE_17_16_15, MISSES, CLOSE_BULL)

        assertNull(game.state.winnerIndex)
        assertEquals(listOf(60, 0), game.points())
        assertEquals(1, game.state.currentPlayerIndex)
    }

    @Test
    fun `CR9 Cut-throat, another player's dart can make a closed player win`() {
        val beforeWinningDart = cutThroatNonThrowerWin.dropLast(1).toTypedArray()
        val game = cricketGame("Anna", "Bob", "Cleo", variant = CricketVariant.CUT_THROAT).turns(*beforeWinningDart)
        assertNull(game.state.winnerIndex)
        assertEquals(listOf(60, 57, 60), game.points())

        val won = game.turns(cutThroatNonThrowerWin.last())

        assertEquals(0, won.state.winnerIndex)
        assertEquals(listOf(60, 114, 60), won.points())
        assertEquals(CricketTurn(playerIndex = 2, listOf(dart("T19")), marks = 3), won.state.completedTurns.last())
    }

    @Test
    fun `CR9 when the thrower and another player meet the condition on the same dart, the thrower wins`() {
        val game = cricketGame("Anna", "Bob", "Cleo", variant = CricketVariant.CUT_THROAT).turns(*bothMeetAtOnce)

        assertEquals(0, game.state.winnerIndex)
        assertEquals(listOf(20, 20, 30), game.points())
        assertTrue(CricketTarget.entries.all(game.player(1)::hasClosed))
    }

    @Test
    fun `CR9 when only other players meet the condition, the first after the thrower in play order wins`() {
        val game = cricketGame("Anna", "Bob", "Cleo", "Dan", variant = CricketVariant.CUT_THROAT)
            .turns(*twoOthersMeetAtOnce)

        assertEquals(3, game.state.winnerIndex)
        assertEquals(listOf(20, 38, 20, 20), game.points())
        assertTrue(CricketTarget.entries.all(game.player(0)::hasClosed))
        assertEquals(2, game.state.completedTurns.last().playerIndex)
    }

    @Test
    fun `CR9 any dart after the win is rejected with an explicit error`() {
        val won = cricketGame().turns(CLOSE_20_19_18, CLOSE_17_16_15).throwing("BULL", "25")

        assertThrows<CricketLegFinishedException> { won.throwDart(Dart.MISS) }
        assertThrows<CricketLegFinishedException> { CricketGame(won.config, won.darts + Dart.MISS) }
    }

    @Test
    fun `CR8 a solo player wins once every target is closed`() {
        val beforeLastDart = cricketGame().turns(CLOSE_20_19_18, CLOSE_17_16_15).throwing("BULL")
        assertNull(beforeLastDart.state.winnerIndex)

        val won = beforeLastDart.throwing("25")

        assertEquals(0, won.state.winnerIndex)
    }

    @Test
    fun `CR1 CR8 with 25-Bull off, closing 20 to 15 wins without the Bull`() {
        val solo = cricketGame(isBullInPlay = false).turns(CLOSE_20_19_18, CLOSE_17_16_15)
        val duo = cricketGame("Anna", "Bob", isBullInPlay = false).turns(CLOSE_20_19_18, MISSES, CLOSE_17_16_15)

        assertEquals(0, solo.state.winnerIndex)
        assertEquals(0, duo.state.winnerIndex)
        assertFalse(duo.player(0).hasClosed(CricketTarget.BULL))
    }

    companion object {
        /**
         * Cut-throat, Anna, Bob and Cleo. Anna closes everything with 60 points while Bob has 57; Cleo's last T19
         * gives Bob 57 more, so Anna is no longer behind anyone and wins on Cleo's dart.
         */
        val cutThroatNonThrowerWin = arrayOf(
            MISSES, "T20 T20 MISS", MISSES,
            CLOSE_20_19_18, MISSES, "T19 T19 MISS",
            CLOSE_17_16_15, MISSES, MISSES,
            CLOSE_BULL, MISSES, "T19",
        )

        /**
         * Cut-throat, Anna, Bob and Cleo. Cleo gives Anna and Bob 20 each; both then close everything but Anna's
         * last mark on 15. Anna's T15 closes it and gives Cleo 30: Anna and Bob both meet the condition.
         */
        val bothMeetAtOnce = arrayOf(
            MISSES, MISSES, "T20 20 MISS",
            CLOSE_20_19_18, CLOSE_20_19_18, MISSES,
            "T17 T16 D15", CLOSE_17_16_15, MISSES,
            CLOSE_BULL, CLOSE_BULL, MISSES,
            "T15",
        )

        /**
         * Cut-throat, Anna, Bob, Cleo and Dan. Bob gives Anna, Cleo and Dan 20 each; Anna and Dan close everything;
         * Cleo's D19 on her closed 19 gives Bob 38, so Anna and Dan both meet the condition: Dan comes first after
         * Cleo in play order.
         */
        val twoOthersMeetAtOnce = arrayOf(
            MISSES, "T20 20 MISS", "T19 MISS MISS", MISSES,
            CLOSE_20_19_18, MISSES, MISSES, CLOSE_20_19_18,
            CLOSE_17_16_15, MISSES, MISSES, CLOSE_17_16_15,
            CLOSE_BULL, MISSES, MISSES, CLOSE_BULL,
            MISSES, MISSES, "D19",
        )
    }
}
