package io.github.darkcosmos.kosmodarts.engine.cricket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CricketStandardScoringTest {

    @Test
    fun `CR4 surplus marks score the target's points for the thrower`() {
        val game = cricketGame("Anna", "Bob").throwing("T20", "T20", "D15")

        assertEquals(listOf(60, 0), game.points())
    }

    @Test
    fun `CR4 a Bull surplus mark is worth 25`() {
        val game = cricketGame("Anna", "Bob").throwing("BULL", "BULL", "25")

        assertEquals(listOf(50, 0), game.points())
    }

    @Test
    fun `CR4 a dart that both closes and scores counts only its surplus marks`() {
        val game = cricketGame("Anna", "Bob").throwing("D16", "T16")

        assertEquals(listOf(32, 0), game.points())
    }

    @Test
    fun `CR4 surplus marks score nothing once every opponent has closed the target`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS MISS", "T20 MISS MISS").throwing("T20", "D20")

        assertEquals(listOf(0, 0), game.points())
    }

    @Test
    fun `CR4 with 3 players, a surplus scores for the thrower while one opponent is still open`() {
        val game = cricketGame("Anna", "Bob", "Cleo").turns(MISSES, "T20 MISS MISS", MISSES).throwing("T20", "T20")

        assertEquals(listOf(60, 0, 0), game.points())
    }

    @Test
    fun `CR4 with 4 players, a surplus scores while 1 opponent is open, then nothing once he closes`() {
        val threeClosed = cricketGame("Anna", "Bob", "Cleo", "Dan")
            .turns("T20 MISS MISS", "T20 MISS MISS", "T20 MISS MISS", MISSES)
            .turns("T20 MISS MISS")
        assertEquals(listOf(60, 0, 0, 0), threeClosed.points())

        val allClosed = threeClosed.turns(MISSES, MISSES, "T20 MISS MISS").throwing("T20")

        assertEquals(listOf(60, 0, 0, 0), allClosed.points())
    }

    @Test
    fun `CR6 a solo player never scores`() {
        val game = cricketGame().turns("T20 T20 T20", "BULL BULL BULL")

        assertEquals(listOf(0), game.points())
    }
}
