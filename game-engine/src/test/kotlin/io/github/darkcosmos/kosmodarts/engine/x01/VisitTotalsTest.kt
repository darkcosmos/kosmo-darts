package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotal.Bust
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotal.InProgress
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotal.LastVisit
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotal.NotPlayed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Visit totals of docs/rules/visit-total.md (VT1 to VT7). */
class VisitTotalsTest {

    /** Ann, Bob and Cid, 501, double out. */
    private val threePlayers = newGame("Ann", "Bob", "Cid")

    private fun totals(game: X01Game): List<VisitTotal> = VisitTotals.of(game.state)

    private fun lastCompleted(game: X01Game, player: Int): VisitTotal = VisitTotals.lastCompleted(game.state, player)

    /** 301 double out, Ann and Bob: Ann leaves 121, Bob 298, then Ann leaves 61. */
    private fun annAtSixtyOne(): X01Game =
        newGame("Ann", "Bob", startScore = 301).throwing("T20", "T20", "T20", "1", "1", "1", "T20", "MISS", "MISS")
            .throwing("1", "1", "1")

    @Test
    fun `VT2 VT5 game start`() {
        assertEquals(listOf(InProgress(0), NotPlayed, NotPlayed), totals(threePlayers))
    }

    @Test
    fun `VT2 VT3 running total after one, two and three darts`() {
        val first = threePlayers.throwing("20")
        val second = first.throwing("15")
        val third = second.throwing("15")
        assertEquals(listOf(InProgress(20), NotPlayed, NotPlayed), totals(first))
        assertEquals(listOf(InProgress(35), NotPlayed, NotPlayed), totals(second))
        assertEquals(listOf(LastVisit(50), InProgress(0), NotPlayed), totals(third))
    }

    @Test
    fun `VT1 a miss counts 0`() {
        assertEquals(InProgress(20), totals(threePlayers.throwing("20", "MISS"))[0])
        assertEquals(LastVisit(0), totals(threePlayers.throwing("MISS", "MISS", "MISS"))[0])
    }

    @Test
    fun `VT4 a bust shows bust whatever was scored before the bust dart`() {
        // Ann has 61 left: T20 leaves 1, a bust in double out (S2).
        val game = annAtSixtyOne().throwing("T20")
        assertEquals(TurnOutcome.BUST, game.state.completedTurns.last().outcome)
        assertEquals(listOf(Bust, InProgress(0)), totals(game))
    }

    @Test
    fun `VT4 a bust after a scoring dart shows bust`() {
        // 41 then T20: below zero.
        val game = annAtSixtyOne().throwing("D10", "T20")
        assertEquals(listOf(Bust, InProgress(0)), totals(game))
    }

    @Test
    fun `VT3 the winning turn shows its total and there is no current player`() {
        val game = annAtSixtyOne().throwing("T19", "D2")
        assertNull(game.state.currentPlayerIndex)
        assertEquals(listOf(LastVisit(61), LastVisit(3)), totals(game))
    }

    @Test
    fun `VT3 single out checkout on the first dart of a turn`() {
        // Ann: 121 left, then 1 left, then S1 on the first dart of the next turn.
        val game = newGame("Ann", "Bob", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "1", "1", "1", "T20", "T20", "MISS", "1", "1", "1", "S1")
        assertEquals(TurnOutcome.CHECKOUT, game.state.completedTurns.last().outcome)
        assertEquals(listOf(LastVisit(1), LastVisit(3)), totals(game))
    }

    @Test
    fun `VT3 single out checkout on the second dart of a turn`() {
        // Ann: 121 left, then 61 left, then T20 S1.
        val game = newGame("Ann", "Bob", startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
            .throwing("T20", "T20", "T20", "1", "1", "1", "T20", "MISS", "MISS", "1", "1", "1", "T20", "S1")
        assertEquals(TurnOutcome.CHECKOUT, game.state.completedTurns.last().outcome)
        assertEquals(listOf(LastVisit(61), LastVisit(3)), totals(game))
    }

    @Test
    fun `VT3 VT2 a player keeps his last visit until his next turn starts`() {
        val annDone = threePlayers.throwing("20", "15", "15")
        val bobDone = annDone.throwing("10", "10", "10")
        val cidDone = bobDone.throwing("1", "1", "1")
        assertEquals(listOf(LastVisit(50), LastVisit(30), InProgress(0)), totals(bobDone))
        assertEquals(listOf(InProgress(0), LastVisit(30), LastVisit(3)), totals(cidDone))
        assertEquals(listOf(InProgress(5), LastVisit(30), LastVisit(3)), totals(cidDone.throwing("5")))
    }

    @Test
    fun `VT6 last completed visit in each case`() {
        val annInProgress = threePlayers.throwing("20", "15", "15", "10", "10", "10", "1", "1", "1", "T20")
        assertEquals(NotPlayed, lastCompleted(threePlayers, 0))
        assertEquals(LastVisit(50), lastCompleted(annInProgress, 0))
        assertEquals(LastVisit(30), lastCompleted(annInProgress, 1))
        assertEquals(Bust, lastCompleted(annAtSixtyOne().throwing("T20"), 0))
        assertEquals(LastVisit(61), lastCompleted(annAtSixtyOne().throwing("T19", "D2"), 0))
        // The turn in progress is ignored.
        assertEquals(InProgress(60), totals(annInProgress)[0])
    }

    @Test
    fun `VT7 undo in the turn in progress`() {
        val game = threePlayers.throwing("20", "15").undo()
        assertEquals(listOf(InProgress(20), NotPlayed, NotPlayed), totals(game))
    }

    @Test
    fun `VT7 undo that reopens the previous player's turn in the first round`() {
        val game = threePlayers.throwing("20", "15", "15").undo()
        assertEquals(listOf(InProgress(35), NotPlayed, NotPlayed), totals(game))
    }

    @Test
    fun `VT7 undo that reopens the previous player's turn in a later round`() {
        val bobDone = threePlayers.throwing("20", "15", "15", "10", "10", "10", "1", "1", "1", "5", "5", "5")
            .throwing("20", "20", "20")
        assertEquals(listOf(LastVisit(15), LastVisit(60), InProgress(0)), totals(bobDone))
        assertEquals(listOf(LastVisit(15), InProgress(40), LastVisit(3)), totals(bobDone.undo()))
    }

    @Test
    fun `VT7 undo of a bust dart reopens the turn`() {
        val game = annAtSixtyOne().throwing("D10", "T20").undo()
        assertEquals(listOf(InProgress(20), LastVisit(3)), totals(game))
    }

    @Test
    fun `VT7 a game restored from its darts gives the same totals`() {
        val played = threePlayers.throwing("20", "15", "15", "10", "10")
        val restored = X01Game(played.config, played.darts.toList())
        assertEquals(totals(played), totals(restored))
        assertEquals(listOf(LastVisit(50), InProgress(20), NotPlayed), totals(restored))
    }

    @Test
    fun `VT1 VT2 VT4 one player who busts throws again at once`() {
        // 41 left, T20 busts below zero; the only player is the next thrower.
        val game = gameAtRemaining(41).throwing("T20")
        assertEquals(TurnOutcome.BUST, game.state.completedTurns.last().outcome)
        assertEquals(listOf(InProgress(0)), totals(game))
        assertEquals(Bust, lastCompleted(game, 0))
    }

    @Test
    fun `VT1 VT3 VT4 VT5 four players with a bust`() {
        val start = newGame("Ann", "Bob", "Cid", "Dan", startScore = 301)
        assertEquals(
            listOf(LastVisit(60), InProgress(0), NotPlayed, NotPlayed),
            totals(start.throwing("20", "20", "20")),
        )
        // One round: Cid scores 180 (121 left), the others miss. Then Cid's T20 T20 leaves 1: a bust in double out.
        val round = arrayOf("MISS", "MISS", "MISS", "MISS", "MISS", "MISS", "T20", "T20", "T20", "MISS", "MISS", "MISS")
        val cidAt121 = start.throwing(*round)
        val game = cidAt121.throwing("20", "20", "20", "5", "5", "5", "T20", "T20")
        assertEquals(listOf(LastVisit(60), LastVisit(15), Bust, InProgress(0)), totals(game))
    }
}
