package io.github.darkcosmos.kosmodarts.engine.cricket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CricketCutThroatScoringTest {

    private fun game(vararg players: String) = cricketGame(*players, variant = CricketVariant.CUT_THROAT)

    @Test
    fun `CR5 surplus marks go to the open opponent and never to the thrower`() {
        val result = game("Anna", "Bob").throwing("T20", "T20", "D15")

        assertEquals(listOf(0, 60), result.points())
    }

    @Test
    fun `CR5 surplus marks give nothing once every opponent has closed the target`() {
        val result = game("Anna", "Bob").turns("T20 MISS MISS", "T20 MISS MISS").throwing("T20")

        assertEquals(listOf(0, 0), result.points())
    }

    @Test
    fun `CR5 with 3 players, a D20 surplus of 40 goes to the open opponent only`() {
        val result = game("Anna", "Bob", "Cleo").turns("T20 MISS MISS", "T20 MISS MISS", MISSES).throwing("D20")

        assertEquals(listOf(0, 0, 40), result.points())
    }

    @Test
    fun `CR5 with 4 players, each open opponent receives the full surplus`() {
        val result = game("Anna", "Bob", "Cleo", "Dan").turns(MISSES, "T20 MISS MISS", MISSES, MISSES)
            .throwing("T20", "T20")

        assertEquals(listOf(0, 0, 60, 60), result.points())
    }

    @Test
    fun `CR5 with 4 players, a Bull surplus mark gives 25 to the only open opponent`() {
        val result = game("Anna", "Bob", "Cleo", "Dan")
            .turns("BULL MISS MISS", "BULL 25 MISS", "BULL 25 MISS", MISSES)
            .throwing("BULL")

        assertEquals(listOf(0, 0, 0, 25), result.points())
    }

    @Test
    fun `CR6 a solo player never scores in Cut-throat`() {
        val result = game("Anna").turns("T20 T20 T20")

        assertEquals(listOf(0), result.points())
    }
}
