package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class X01ScoringTest {

    /** Brings a single player from 501 down to [target] in three full turns, without any bust. */
    private fun soloAt(target: Int, checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT): X01Game {
        val thirdTurn = when (target) {
            100 -> arrayOf("D20", "1", "MISS")
            60 -> arrayOf("T20", "20", "1")
            50 -> arrayOf("T20", "T10", "1")
            40 -> arrayOf("T20", "T13", "2")
            25 -> arrayOf("T20", "T18", "2")
            20 -> arrayOf("T20", "T20", "1")
            else -> error("No fixture for $target")
        }
        val game = newGame("Solo", checkoutRule = checkoutRule)
            .throwing("T20", "T20", "T20", "T20", "T20", "T20")
            .throwing(*thirdTurn)
        check(game.player(0).remaining == target && game.state.currentPlayerIndex == 0)
        return game
    }

    /** C3, S1: entry is free, the very first dart scores and every dart subtracts its value. */
    @Test
    fun `C3 S1 every dart subtracts its value from the first one`() {
        val game = newGame("Alice")
        assertEquals(501, game.player(0).remaining)
        assertEquals(441, game.throwing("T20").player(0).remaining)
        assertEquals(431, game.throwing("T20", "D5").player(0).remaining)
        assertEquals(406, game.throwing("T20", "D5", "25").player(0).remaining)
    }

    /** S1: a miss scores nothing but is a thrown dart. */
    @Test
    fun `S1 a miss scores nothing but counts as a dart`() {
        val game = newGame("Alice").throwing("MISS")
        assertEquals(501, game.player(0).remaining)
        assertEquals(1, game.player(0).dartsThrown)
    }

    /** S2, S4: overshooting below zero is a bust and restores the score of the start of the turn. */
    @Test
    fun `S2 S4 overshoot is a bust in double out`() {
        val game = soloAt(40).throwing("10", "T20")
        assertEquals(40, game.player(0).remaining)
        assertFalse(game.state.isFinished)
    }

    /** S2: leaving exactly 1 is a bust in DOUBLE_OUT. */
    @Test
    fun `S2 leaving 1 is a bust in double out`() {
        val game = soloAt(40).throwing("19")
        assertEquals(21, game.player(0).remaining)
        val busted = game.throwing("20")
        assertEquals(40, busted.player(0).remaining)
        assertEquals(emptyList<Dart>(), busted.player(0).currentTurnDarts)
    }

    /** S2: reaching 0 with a single is a bust in DOUBLE_OUT. */
    @Test
    fun `S2 finishing on a single is a bust in double out`() {
        val game = soloAt(40).throwing("20", "20")
        assertEquals(40, game.player(0).remaining)
        assertFalse(game.state.isFinished)
        assertNull(game.state.winnerIndex)
    }

    /** S2: reaching 0 with a triple or the outer bull is a bust in DOUBLE_OUT. */
    @Test
    fun `S2 finishing on a triple or outer bull is a bust in double out`() {
        assertEquals(60, soloAt(60).throwing("T20").player(0).remaining)
        assertEquals(25, soloAt(25).throwing("25").player(0).remaining)
        assertFalse(soloAt(25).throwing("25").state.isFinished)
    }

    /** S2: reaching 0 with a double is a checkout in DOUBLE_OUT. */
    @Test
    fun `S2 finishing on a double is a checkout`() {
        val game = soloAt(40).throwing("D20")
        assertEquals(0, game.player(0).remaining)
        assertTrue(game.state.isFinished)
        assertEquals(0, game.state.winnerIndex)
    }

    /** D3, S2: reaching 0 with the bull is a checkout in DOUBLE_OUT. */
    @Test
    fun `D3 S2 checkout on bull`() {
        val game = soloAt(50).throwing("BULL")
        assertTrue(game.state.isFinished)
        assertEquals(0, game.player(0).remaining)
    }

    /** S3: reaching 0 with a single is a checkout in SINGLE_OUT. */
    @Test
    fun `S3 checkout with a single in single out`() {
        val game = soloAt(20, CheckoutRule.SINGLE_OUT).throwing("20")
        assertTrue(game.state.isFinished)
        assertEquals(0, game.state.winnerIndex)
    }

    /** S3: reaching 0 with a triple or the outer bull is a checkout in SINGLE_OUT. */
    @Test
    fun `S3 checkout with a triple or outer bull in single out`() {
        assertTrue(soloAt(60, CheckoutRule.SINGLE_OUT).throwing("T20").state.isFinished)
        assertTrue(soloAt(25, CheckoutRule.SINGLE_OUT).throwing("25").state.isFinished)
    }

    /** S3: leaving 1 is allowed in SINGLE_OUT, and can then be finished with a single 1. */
    @Test
    fun `S3 leaving 1 is not a bust in single out`() {
        val game = soloAt(40, CheckoutRule.SINGLE_OUT).throwing("19", "20")
        assertEquals(1, game.player(0).remaining)
        assertTrue(game.throwing("1").state.isFinished)
    }

    /** S3, S4: overshooting below zero is a bust in SINGLE_OUT. */
    @Test
    fun `S3 S4 overshoot is a bust in single out`() {
        val game = soloAt(40, CheckoutRule.SINGLE_OUT).throwing("20", "T20")
        assertEquals(40, game.player(0).remaining)
        assertFalse(game.state.isFinished)
    }

    /** S4: the bust dart is recorded in the game. */
    @Test
    fun `S4 bust dart is recorded`() {
        val before = soloAt(40)
        val game = before.throwing("T20")
        assertEquals(before.darts + dart("T20"), game.darts)
        assertEquals(before.player(0).dartsThrown + 1, game.player(0).dartsThrown)
    }

    /** S4: the score is restored to the start of the turn, not only the bust dart is cancelled. */
    @Test
    fun `S4 bust restores the score of the start of the turn`() {
        val game = soloAt(100).throwing("T20", "T20")
        assertEquals(100, game.player(0).remaining)
    }

    /** S5: the checkout makes the player the winner. */
    @Test
    fun `S5 checkout makes the player win`() {
        val game = newGame("Alice", "Bob", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20", "1")
        assertTrue(game.state.isFinished)
        assertEquals(0, game.state.winnerIndex)
    }

    /** S5: once the leg is finished, any further dart is rejected with an explicit error. */
    @Test
    fun `S5 a dart after the checkout is rejected`() {
        val finished = soloAt(40).throwing("D20")
        assertThrows<LegFinishedException> { finished.throwDart(Dart.MISS) }
        assertThrows<LegFinishedException> { X01Game(finished.config, finished.darts + Dart.MISS) }
    }
}
