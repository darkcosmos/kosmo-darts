package io.github.darkcosmos.kosmodarts.engine.cricket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CricketRankingTest {

    @Test
    fun `CR12 the ranking is empty while the leg is in progress`() {
        val game = cricketGame("Anna", "Bob", "Cleo").turns(CLOSE_20_19_18, "T20 MISS MISS")

        assertTrue(game.state.ranking.isEmpty())
    }

    @Test
    fun `CR12 Standard, 3 players, the winner then more points first`() {
        val won = cricketGame("Anna", "Bob", "Cleo")
            .turns("T20 T20 T20", "T20 D20 MISS", MISSES)
            .turns("T19 T18 T17", MISSES, MISSES)
            .turns("T16 T15 BULL", MISSES, MISSES)
            .throwing("25")

        assertEquals(listOf(120, 40, 0), won.points())
        assertEquals(listOf(1 to 0, 2 to 1, 3 to 2), won.ranks())
    }

    @Test
    fun `CR12 Standard, equal points are ranked by board marks`() {
        val won = cricketGame("Anna", "Bob", "Cleo")
            .turns(CLOSE_20_19_18, "20 MISS MISS", "T20 MISS MISS")
            .turns(CLOSE_17_16_15, MISSES, MISSES)
            .throwing("BULL", "25")

        assertEquals(listOf(0, 0, 0), won.points())
        assertEquals(listOf(1 to 0, 2 to 2, 3 to 1), won.ranks())
    }

    @Test
    fun `CR12 Standard, 4 players, players equal on points and board marks share a rank and the next is skipped`() {
        val won = cricketGame("Anna", "Bob", "Cleo", "Dan")
            .turns(CLOSE_20_19_18, "20 MISS MISS", "20 MISS MISS", MISSES)
            .turns(CLOSE_17_16_15, MISSES, MISSES, MISSES)
            .throwing("BULL", "25")

        assertEquals(listOf(1 to 0, 2 to 1, 2 to 2, 4 to 3), won.ranks())
    }

    @Test
    fun `CR12 Cut-throat, 3 players, fewer points first`() {
        val won = cricketGame("Anna", "Bob", "Cleo", variant = CricketVariant.CUT_THROAT)
            .turns("T20 T20 MISS", "T20 MISS MISS", MISSES)
            .turns("T20 T19 T18", MISSES, MISSES)
            .turns(CLOSE_17_16_15, MISSES, MISSES)
            .throwing("BULL", "25")

        assertEquals(listOf(0, 60, 120), won.points())
        assertEquals(listOf(1 to 0, 2 to 1, 3 to 2), won.ranks())
    }

    @Test
    fun `CR12 Cut-throat, 4 players, fewer points first, then more board marks`() {
        val won = cricketGame("Anna", "Bob", "Cleo", "Dan", variant = CricketVariant.CUT_THROAT)
            .turns("T20 T20 MISS", "T20 MISS MISS", "19 MISS MISS", MISSES)
            .turns("T20 T19 T18", MISSES, MISSES, MISSES)
            .turns(CLOSE_17_16_15, MISSES, MISSES, MISSES)
            .throwing("BULL", "25")

        assertEquals(listOf(0, 60, 120, 120), won.points())
        assertEquals(listOf(1 to 0, 2 to 1, 3 to 2, 4 to 3), won.ranks())
    }

    @Test
    fun `CR12 the winner is ranked first alone, even when another player equals him on points and board marks`() {
        val won = cricketGame("Anna", "Bob", "Cleo", variant = CricketVariant.CUT_THROAT)
            .turns(*CricketWinTest.bothMeetAtOnce)

        assertEquals(won.player(0).boardMarks, won.player(1).boardMarks)
        assertEquals(won.player(0).points, won.player(1).points)
        assertEquals(listOf(1 to 0, 2 to 1, 3 to 2), won.ranks())
    }
}
