package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** AN1 to AN6 (docs/rules/announcements.md). */
class X01CuesTest {

    private fun cuesOf(game: X01Game, code: String): List<X01Cue> = X01Cues.afterDart(game, game.throwing(code))

    /** AN1: a scoring dart is a hit, whatever it is. */
    @Test
    fun `AN1 scoring darts are hits`() {
        listOf("S1", "T20", "D5", "25", "BULL").forEach {
            assertEquals(listOf(X01Cue.DartHit(dart(it))), cuesOf(newGame(), it), it)
        }
    }

    /** AN1: a miss is a miss only, never also a hit. */
    @Test
    fun `AN1 a miss is only a miss`() {
        assertEquals(listOf(X01Cue.DartMiss), cuesOf(newGame(), "MISS"))
    }

    /** AN2: the first and second darts of a turn have no end-of-turn cue. */
    @Test
    fun `AN2 darts before the end of a turn give the dart cue only`() {
        val game = newGame("A", "B")
        assertEquals(listOf(X01Cue.DartHit(dart("20"))), cuesOf(game, "20"))
        assertEquals(listOf(X01Cue.DartMiss), cuesOf(game.throwing("20"), "MISS"))
    }

    /** AN2: a third dart gives the turn score after its dart cue. 177 is the highest score under 180. */
    @Test
    fun `AN2 turn scores from 1 to 177`() {
        assertEquals(
            listOf(X01Cue.DartHit(dart("1")), X01Cue.TurnScore(1)),
            cuesOf(newGame().throwing("MISS", "MISS"), "1"),
        )
        assertEquals(
            listOf(X01Cue.DartHit(dart("20")), X01Cue.TurnScore(60)),
            cuesOf(newGame().throwing("20", "20"), "20"),
        )
        assertEquals(
            listOf(X01Cue.DartHit(dart("T19")), X01Cue.TurnScore(177)),
            cuesOf(newGame().throwing("T20", "T20"), "T19"),
        )
    }

    /** AN2: 180 has its own cue. */
    @Test
    fun `AN2 one hundred and eighty`() {
        assertEquals(
            listOf(X01Cue.DartHit(dart("T20")), X01Cue.OneEighty),
            cuesOf(newGame().throwing("T20", "T20"), "T20"),
        )
    }

    /** AN2: three misses are "no score". */
    @Test
    fun `AN2 no score`() {
        assertEquals(listOf(X01Cue.DartMiss, X01Cue.NoScore), cuesOf(newGame().throwing("MISS", "MISS"), "MISS"))
    }

    /** AN2: a bust on the first, second or third dart is a bust. */
    @Test
    fun `AN2 bust on any dart`() {
        val at40 = gameAtRemaining(40)
        assertEquals(listOf(X01Cue.DartHit(dart("T20")), X01Cue.Bust), cuesOf(at40, "T20"))
        assertEquals(listOf(X01Cue.DartHit(dart("T20")), X01Cue.Bust), cuesOf(at40.throwing("MISS"), "T20"))
        assertEquals(listOf(X01Cue.DartHit(dart("T20")), X01Cue.Bust), cuesOf(at40.throwing("MISS", "MISS"), "T20"))
    }

    /** AN2: a bust stays a bust, even after two misses (a single 1 that leaves 1 in double out). */
    @Test
    fun `AN2 bust after two misses`() {
        assertEquals(
            listOf(X01Cue.DartHit(dart("1")), X01Cue.Bust),
            cuesOf(gameAtRemaining(2).throwing("MISS", "MISS"), "1"),
        )
    }

    /** AN2, AN5 (E3): the winning dart has no score cue: its dart cue, then the win cue. */
    @Test
    fun `AN5 the checkout dart gives its dart cue then the win`() {
        assertEquals(
            listOf(X01Cue.DartHit(dart("D20")), X01Cue.GameShot(0, "Alice")),
            cuesOf(gameAtRemaining(40), "D20"),
        )
    }

    /** AN4: at 501, no checkout is possible, so only the name. */
    @Test
    fun `AN4 turn start at 501 gives the name only`() {
        assertEquals(X01Cue.TurnStart(0, "Alice", null, isGameStart = true), X01Cues.turnStart(newGame("Alice", "Bob")))
    }

    /** AN4: at 301, no checkout is possible either. */
    @Test
    fun `AN4 turn start at 301 gives the name only`() {
        assertEquals(
            X01Cue.TurnStart(0, "Alice", null, isGameStart = true),
            X01Cues.turnStart(newGame("Alice", startScore = 301)),
        )
    }

    /** AN4: 40 left in double out is announced. */
    @Test
    fun `AN4 turn start with a possible checkout gives the remaining score`() {
        assertEquals(X01Cue.TurnStart(0, "Alice", 40, isGameStart = false), X01Cues.turnStart(gameAtRemaining(40)))
    }

    /** AN4: 169 left in double out is not announced. */
    @Test
    fun `AN4 turn start with an impossible checkout gives the name only`() {
        assertEquals(X01Cue.TurnStart(0, "Alice", null, isGameStart = false), X01Cues.turnStart(gameAtRemaining(169)))
    }

    /** AN4: 1 left in single out is announced. */
    @Test
    fun `AN4 turn start with 1 left in single out`() {
        val game = gameAtRemaining(1, checkoutRule = CheckoutRule.SINGLE_OUT)
        assertEquals(X01Cue.TurnStart(0, "Alice", 1, isGameStart = false), X01Cues.turnStart(game))
    }

    /** AN4: with one player, the same name starts every turn. */
    @Test
    fun `AN4 turn start with one player`() {
        val game = newGame("Solo").throwing("20", "20", "20")
        assertEquals(X01Cue.TurnStart(0, "Solo", null, isGameStart = false), X01Cues.turnStart(game))
    }

    /** AN4: with four players, the first player follows the fourth. */
    @Test
    fun `AN4 turn start wraps around with four players`() {
        var game = newGame("A", "B", "C", "D")
        val names = mutableListOf<String?>()
        repeat(5) {
            names += X01Cues.turnStart(game)?.name
            game = game.throwing("1", "1", "1")
        }
        assertEquals(listOf("A", "B", "C", "D", "A"), names)
    }

    /** AN4: a finished leg has no next player. */
    @Test
    fun `AN4 no turn start once the leg is finished`() {
        assertNull(X01Cues.turnStart(finishedFrom170()))
    }

    /** AN5: a win whose turn started at 170 is a big fish. */
    @Test
    fun `AN5 win from 170 is a big fish`() {
        assertEquals(X01Cue.BigFish(0, "Alice"), cuesOf(gameAtRemaining(170).throwing("T20", "T20"), "BULL").last())
    }

    /** AN5: any other win is a game shot. */
    @Test
    fun `AN5 win from 100 is a game shot`() {
        assertEquals(X01Cue.GameShot(0, "Alice"), cuesOf(gameAtRemaining(100).throwing("T20"), "D20").last())
    }

    /** AN5: 170 left, then a later turn finishes from a lower score: a game shot. */
    @Test
    fun `AN5 win in a later turn after 170 left is a game shot`() {
        val game = gameAtRemaining(170).throwing("T20", "T20", "MISS", "D20")
        assertEquals(X01Cue.GameShot(0, "Alice"), cuesOf(game, "D5").last())
    }

    /** AN5: the win names the player who won, not the next one. */
    @Test
    fun `AN5 the win names the winner`() {
        val bobAt170 = newGame("Ann", "Bob", startScore = 301).throwing("1", "1", "1", "T20", "T20", "11")
        assertEquals(170, bobAt170.player(1).remaining)
        val cues = cuesOf(bobAt170.throwing("1", "1", "1", "T20", "T20"), "BULL")
        assertEquals(X01Cue.BigFish(1, "Bob"), cues.last())
    }

    /** AN6. */
    @Test
    fun `AN6 undo cue`() {
        assertEquals(X01Cue.DartUndone, X01Cues.dartUndone)
    }

    /** afterDart only accepts a game with exactly one more dart. */
    @Test
    fun `afterDart rejects pairs that are not one dart apart`() {
        val game = newGame()
        assertThrows<IllegalArgumentException> { X01Cues.afterDart(game, game) }
        assertThrows<IllegalArgumentException> { X01Cues.afterDart(game, game.throwing("1", "2")) }
        assertThrows<IllegalArgumentException> { X01Cues.afterDart(game.throwing("1"), game.throwing("2", "3")) }
    }

    /** The fixture really reaches the scores the tests rely on. */
    @Test
    fun `gameAtRemaining reaches the requested score at the start of a turn`() {
        listOf(1, 2, 40, 100, 169, 170, 300).forEach {
            val game = gameAtRemaining(it, checkoutRule = CheckoutRule.SINGLE_OUT)
            assertEquals(it, game.player(0).remaining)
            assertEquals(0, game.darts.size % 3)
        }
    }

    private fun finishedFrom170(): X01Game = gameAtRemaining(170).throwing("T20", "T20", "BULL")
}
