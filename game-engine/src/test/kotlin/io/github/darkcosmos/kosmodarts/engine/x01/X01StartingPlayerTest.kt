package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** T1 as amended by docs/rules/match.md, M6: the leg's starting player throws first. */
class X01StartingPlayerTest {

    private val config = X01Config(listOf("Ann", "Bob", "Cid"))

    /** T1: by default, the first player starts. */
    @Test
    fun `T1 the first player starts by default`() {
        assertEquals(0, X01Game(config).startingPlayerIndex)
        assertEquals(0, X01Game(config).state.currentPlayerIndex)
    }

    /** T1, T2: a leg started at index 2 gives the first turn to player 2, then wraps to player 0. */
    @Test
    fun `T1 T2 a leg started by the last player wraps around`() {
        val leg = X01Game(config, startingPlayerIndex = 2)

        assertEquals(2, leg.state.currentPlayerIndex)
        val afterTurn = leg.throwing("20", "20", "20")
        assertEquals(0, afterTurn.state.currentPlayerIndex)
        assertEquals(441, afterTurn.player(2).remaining)
        assertEquals(1, afterTurn.throwing("1", "1", "1").state.currentPlayerIndex)
    }

    /** U1 to U3: undoing back to an empty leg keeps its starter. */
    @Test
    fun `U1 undo back to empty keeps the starter`() {
        val leg = X01Game(config, startingPlayerIndex = 1).throwing("T20", "T20", "T20", "5")

        val empty = (1..4).fold(leg) { game, _ -> game.undo() }

        assertEquals(X01Game(config, startingPlayerIndex = 1), empty)
        assertEquals(1, empty.state.currentPlayerIndex)
    }

    /** P1: the starter is part of the game's identity. */
    @Test
    fun `P1 equality includes the starter`() {
        assertNotEquals(X01Game(config, startingPlayerIndex = 1), X01Game(config))
        assertEquals(
            X01Game(config, listOf(dart("5")), startingPlayerIndex = 1),
            X01Game(config, startingPlayerIndex = 1).throwDart(dart("5")),
        )
    }

    /** An index outside the players is rejected. */
    @Test
    fun `invalid starting player is rejected`() {
        assertThrows<IllegalArgumentException> { X01Game(config, startingPlayerIndex = 3) }
        assertThrows<IllegalArgumentException> { X01Game(config, startingPlayerIndex = -1) }
    }
}
