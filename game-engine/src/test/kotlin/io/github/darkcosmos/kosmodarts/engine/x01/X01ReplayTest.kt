package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class X01ReplayTest {

    private val config = X01Config(listOf("Alice", "Bob", "Carol"), startScore = 301)

    private val codes = listOf(
        "T20", "T20", "T20", "D20", "25", "MISS", "BULL", "T19", "S3",
        "T20", "T20", "T20", // Alice: 61, then 1 is a bust back to 121; the third T20 is Bob's.
        "1", "2", "3",
    )

    /** P1: a game rebuilt from its configuration and dart list has the same state as the game played live. */
    @Test
    fun `P1 replaying the dart list gives the same state as live play`() {
        val live = codes.fold(X01Game(config)) { game, code -> game.throwDart(dart(code)) }
        val replayed = X01Game(config, live.darts)
        assertEquals(live, replayed)
        assertEquals(live.state, replayed.state)
    }

    /** P1: the same input always yields the same state. */
    @Test
    fun `P1 replay is deterministic`() {
        val darts = codes.map(::dart)
        val states = List(5) { X01Game(config, darts).state }
        states.forEach { assertEquals(states.first(), it) }
        assertEquals(X01Game(config, darts).state.hashCode(), X01Game(config, darts).state.hashCode())
    }

    /** P1: the game exposes its configuration and its darts in throw order. */
    @Test
    fun `P1 game is its configuration plus its ordered darts`() {
        val darts = codes.map(::dart)
        val game = X01Game(config, darts)
        assertEquals(config, game.config)
        assertEquals(darts, game.darts)
        assertEquals(X01Config(listOf(" Alice", "Bob ", "Carol"), startScore = 301), game.config)
    }

    /** P1: the game state does not change when the caller later modifies the list it passed in. */
    @Test
    fun `P1 game is not affected by later changes to the input list`() {
        val darts = codes.map(::dart).toMutableList()
        val game = X01Game(config, darts)
        val stateBefore = game.state
        darts.clear()
        assertEquals(codes.size, game.darts.size)
        assertEquals(stateBefore, X01Game(config, game.darts).state)
    }
}
