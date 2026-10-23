package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CricketDeadTargetTest {

    @Test
    fun `CR15 a target is dead only once every player has closed it`() {
        val oneClosed = cricketGame("Anna", "Bob").turns("T20 MISS MISS")
        assertEquals(emptySet<CricketTarget>(), oneClosed.state.deadTargets)

        val bothClosed = oneClosed.throwing("T20")

        assertEquals(setOf(CricketTarget.TWENTY), bothClosed.state.deadTargets)
    }

    @Test
    fun `CR15 owner example, the dart that completes the closure is a normal hit and every later dart on 20 is not`() {
        val p3BeforeClosing = cricketGame("P1", "P2", "P3").turns("20 20 20", "T20 D20 MISS").throwing("D20")
        assertEquals(emptySet<CricketTarget>(), p3BeforeClosing.state.deadTargets)
        assertFalse(p3BeforeClosing.isClosedSectorHit(dart("20")))

        val dead = p3BeforeClosing.throwing("20")

        assertEquals(setOf(CricketTarget.TWENTY), dead.state.deadTargets)
        assertEquals(2, dead.state.currentPlayerIndex)
        assertTrue(dead.isClosedSectorHit(dart("T20")), "the rest of P3's visit")
        val p1Turn = dead.throwing("T20")
        assertEquals(0, p1Turn.state.currentPlayerIndex)
        assertTrue(p1Turn.isClosedSectorHit(dart("20")), "a later dart by P1")
        val p2Turn = p1Turn.turns(MISSES)
        assertEquals(1, p2Turn.state.currentPlayerIndex)
        assertTrue(p2Turn.isClosedSectorHit(dart("D20")), "a later dart by P2")
        assertFalse(p2Turn.isClosedSectorHit(dart("19")), "19 is still live")
        assertFalse(p2Turn.isClosedSectorHit(Dart.MISS), "a miss gives no mark")
    }

    @Test
    fun `CR15 a dart that closes for the thrower while an opponent is open is a normal hit`() {
        val game = cricketGame("Anna", "Bob")

        assertFalse(game.isClosedSectorHit(dart("T20")))
        val closedByAnna = game.throwing("T20")
        assertEquals(emptySet<CricketTarget>(), closedByAnna.state.deadTargets)
        assertFalse(closedByAnna.isClosedSectorHit(dart("T20")), "a scoring surplus dart is a normal hit")
    }

    @Test
    fun `CR15 CR11 undoing the dart that made a target dead makes it live again`() {
        val dead = cricketGame("Anna", "Bob").turns("T20 MISS MISS").throwing("T20")
        assertTrue(dead.isClosedSectorHit(dart("20")))

        val undone = dead.undo()

        assertEquals(emptySet<CricketTarget>(), undone.state.deadTargets)
        assertFalse(undone.isClosedSectorHit(dart("20")))
        assertEquals(setOf(CricketTarget.TWENTY), undone.throwing("T20").state.deadTargets)
    }

    @Test
    fun `CR15 in a solo game, a target is dead once the only player has closed it`() {
        val game = cricketGame().throwing("D19")
        assertFalse(game.isClosedSectorHit(dart("19")))

        val dead = game.throwing("19")

        assertEquals(setOf(CricketTarget.NINETEEN), dead.state.deadTargets)
        assertTrue(dead.isClosedSectorHit(dart("19")))
    }

    @Test
    fun `CR15 dead targets follow board order and the Bull can be dead`() {
        val game = cricketGame().throwing("BULL", "25", "T15")

        assertEquals(listOf(CricketTarget.FIFTEEN, CricketTarget.BULL), game.state.deadTargets.toList())
        assertTrue(game.isClosedSectorHit(Dart.OUTER_BULL))
    }

    @Test
    fun `CR15 with 25-Bull off, 25 and Bull are never closed-sector hits`() {
        val allNumbersDead = cricketGame("Anna", "Bob", isBullInPlay = false)
            .turns("T20 T19 MISS", "T20 T19 MISS", "T18 T17 MISS", "T18 T17 MISS", "T16 MISS MISS", "T16 MISS MISS")
        assertEquals(CricketTarget.NUMBERS.take(5).toSet(), allNumbersDead.state.deadTargets)

        assertFalse(CricketTarget.BULL in allNumbersDead.state.deadTargets)
        assertFalse(allNumbersDead.isClosedSectorHit(Dart.OUTER_BULL))
        assertFalse(allNumbersDead.isClosedSectorHit(Dart.BULL))
    }
}
