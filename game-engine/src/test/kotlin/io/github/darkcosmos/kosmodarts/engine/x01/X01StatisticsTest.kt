package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class X01StatisticsTest {

    private val tolerance = 1e-9

    /** A1: a player who has not thrown yet has no average (not zero). */
    @Test
    fun `A1 no average without any dart thrown`() {
        val game = newGame("Alice", "Bob").throwing("T20")
        assertNull(game.player(1).threeDartAverage)
        assertNull(newGame("Alice").player(0).threeDartAverage)
    }

    /** A1: the average is points scored divided by darts thrown, times 3. */
    @Test
    fun `A1 three dart average from points and darts`() {
        val game = newGame("Alice").throwing("T20", "T20", "T20", "20", "1")
        // 201 points in 5 darts.
        assertEquals(201.0 / 5 * 3, game.player(0).threeDartAverage!!, tolerance)
    }

    /** A1: a player who only missed has an average of zero, not an absent average. */
    @Test
    fun `A1 only misses give a zero average`() {
        assertEquals(0.0, newGame("Alice").throwing("MISS").player(0).threeDartAverage!!, tolerance)
    }

    /** A1, S4: bust darts count as thrown and the busted points are not scored. */
    @Test
    fun `A1 S4 bust darts count as thrown in the average`() {
        val game = newGame("Alice", startScore = 301)
            .throwing("T20", "T20", "T20", "T20", "T20") // 121, 61, then 1: bust back to 121.
        assertEquals(121, game.player(0).remaining)
        assertEquals(5, game.player(0).dartsThrown)
        assertEquals(180.0 / 5 * 3, game.player(0).threeDartAverage!!, tolerance)
    }

    /** A1: the average uses the current remaining score, including darts of the turn in progress. */
    @Test
    fun `A1 average includes the turn in progress`() {
        val game = newGame("Alice").throwing("T20")
        assertEquals(180.0, game.player(0).threeDartAverage!!, tolerance)
    }

    /** A2: each player exposes name, remaining score, darts thrown, average and current turn darts. */
    @Test
    fun `A2 per player statistics are exposed`() {
        val game = newGame("Alice", "Bob").throwing("T20", "T20", "T20", "20", "D10")
        val alice = game.player(0)
        val bob = game.player(1)
        assertEquals("Alice", alice.name)
        assertEquals(321, alice.remaining)
        assertEquals(3, alice.dartsThrown)
        assertEquals(180.0, alice.threeDartAverage!!, tolerance)
        assertEquals(emptyList<Dart>(), alice.currentTurnDarts)

        assertEquals("Bob", bob.name)
        assertEquals(461, bob.remaining)
        assertEquals(2, bob.dartsThrown)
        assertEquals(60.0, bob.threeDartAverage!!, tolerance)
        assertEquals(listOf(dart("20"), dart("D10")), bob.currentTurnDarts)
    }

    /** A2: after a checkout, no turn is in progress any more. */
    @Test
    fun `A2 no current turn darts once the leg is finished`() {
        val game = newGame("Solo", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "T20", "T20", "1")
        assertEquals(emptyList<Dart>(), game.player(0).currentTurnDarts)
        assertEquals(6, game.player(0).dartsThrown)
        assertEquals(301.0 / 6 * 3, game.player(0).threeDartAverage!!, tolerance)
    }
}
