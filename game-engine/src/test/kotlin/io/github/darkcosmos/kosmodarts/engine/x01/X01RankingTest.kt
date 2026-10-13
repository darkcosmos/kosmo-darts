package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class X01RankingTest {

    /** E1: a leg in progress has no ranking yet. */
    @Test
    fun `E1 no ranking while the leg is in progress`() {
        assertEquals(emptyList<X01Rank>(), newGame("Alice", "Bob").throwing("T20").state.ranking)
    }

    /** E1: the winner is first, the others follow by remaining score, lowest first. */
    @Test
    fun `E1 winner first then others by remaining score ascending`() {
        val game = newGame("Alice", "Bob", "Carol", "Dave", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing(
                "1", "1", "1", // Alice 298
                "T20", "T20", "T20", // Bob 121
                "20", "20", "20", // Carol 241
                "MISS", "MISS", "MISS", // Dave 301
                "1", "1", "1", // Alice 295
                "T20", "T20", "1", // Bob wins
            )
        assertEquals(1, game.state.winnerIndex)
        assertEquals(
            listOf(X01Rank(1, 1), X01Rank(2, 2), X01Rank(3, 0), X01Rank(4, 3)),
            game.state.ranking,
        )
    }

    /** E1: in a solo game, the winner is ranked first. */
    @Test
    fun `E1 solo winner is ranked first`() {
        val game = newGame("Solo", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "T20", "T20", "1")
        assertEquals(listOf(X01Rank(1, 0)), game.state.ranking)
    }

    /** E1, E2: players with the same remaining score share a rank and the next rank is skipped (1, 2, 2, 4). */
    @Test
    fun `E1 E2 ranking with ties skips the next rank`() {
        val game = newGame("Alice", "Bob", "Carol", "Dave", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing(
                "T20", "T20", "T20", // Alice 121
                "20", "20", "20", // Bob 241
                "20", "T10", "D5", // Carol 241
                "1", "1", "1", // Dave 298
                "T20", "T20", "1", // Alice wins
            )
        assertEquals(
            listOf(X01Rank(1, 0), X01Rank(2, 1), X01Rank(2, 2), X01Rank(4, 3)),
            game.state.ranking,
        )
    }

    /** E2: several ties: all non-winners equal share rank 2. */
    @Test
    fun `E2 all losers tied share rank 2`() {
        val game = newGame("Alice", "Bob", "Carol", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing(
                "T20", "T20", "T20", "MISS", "MISS", "MISS", "MISS", "MISS", "MISS",
                "T20", "T20", "1",
            )
        assertEquals(listOf(X01Rank(1, 0), X01Rank(2, 1), X01Rank(2, 2)), game.state.ranking)
    }

    /** E1: players listed after the winner do not get an extra turn; they are ranked on their current score. */
    @Test
    fun `E1 players without a turn after the checkout are not compensated`() {
        val game = newGame("Alice", "Bob", "Carol", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing(
                "T20", "T20", "T20", // Alice 121
                "20", "20", "20", // Bob 241
                "1", "1", "1", // Carol 298
                "T20", "T20", "1", // Alice wins: Bob and Carol had one turn fewer
            )
        assertEquals(null, game.state.currentPlayerIndex)
        assertEquals(listOf(6, 3, 3), game.state.players.map(X01PlayerState::dartsThrown))
        assertEquals(listOf(241, 298), listOf(game.player(1).remaining, game.player(2).remaining))
        assertEquals(listOf(X01Rank(1, 0), X01Rank(2, 1), X01Rank(3, 2)), game.state.ranking)
    }
}
