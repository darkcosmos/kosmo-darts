package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisit
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Cricket game screen state (docs/rules/cricket.md, CR7, CR9, CR11, CR13, CR14, CR22 to CR25). */
class CricketGameScreenStateTest {

    private fun screen(match: CricketMatch = CricketSaveFixtures.match("Ann", "Bob")) = CricketGameScreenState(match)

    private fun CricketGameScreenState.throwing(vararg numbers: Int): CricketGameScreenState =
        numbers.fold(this) { state, number -> state.throwDart(Dart.single(number)) }

    /** The NON_THROWER_WIN fixture before Cid's T19: Cid throws next, and that T19 makes Ann win (CR9). */
    private val beforeNonThrowerWin = screen(CricketSaveFixtures.NON_THROWER_WIN.match.undo())

    private fun CricketGameScreenState.tripleOf(number: Int) = throwDart(Dart.triple(number))

    /** CR2: the zone shows a dart only if it gives a mark; any other dart is shown as a miss. */
    @Test
    fun `CR2 the zone darts keep the darts that give a mark and show the others as a miss`() {
        val state = screen().tripleOf(20).throwDart(Dart.single(5)).throwDart(Dart.OUTER_BULL)

        assertEquals(listOf(Dart.triple(20), Dart.MISS, Dart.OUTER_BULL), state.zoneDarts)
        assertEquals(listOf(Dart.triple(20), Dart.single(5), Dart.OUTER_BULL), state.highlightedTurnDarts)
    }

    @Test
    fun `CR2 a number below 15 is shown as a miss`() {
        val state = screen(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false)).throwDart(Dart.single(14))

        assertEquals(listOf(Dart.MISS), state.zoneDarts)
    }

    @Test
    fun `CR7 CR24 the third dart locks the keypad until Next player`() {
        val waiting = screen().throwing(20, 19, 18)

        assertTrue(waiting.isAwaitingConfirmation)
        assertFalse(waiting.isKeypadEnabled)
        assertTrue(waiting.canGoToNextPlayer)
        assertEquals(0, waiting.highlightedPlayerIndex)
        assertEquals(listOf(Dart.single(20), Dart.single(19), Dart.single(18)), waiting.highlightedTurnDarts)
        assertNull(waiting.nextDartIndex)
        // Locked keys do nothing.
        assertSame(waiting, waiting.throwDart(Dart.single(17)))
        assertSame(waiting, waiting.throwMiss())
        assertSame(waiting, waiting.throwDart(Dart.BULL))

        val next = waiting.nextPlayer()
        assertFalse(next.isAwaitingConfirmation)
        assertTrue(next.isKeypadEnabled)
        assertEquals(1, next.highlightedPlayerIndex)
        assertEquals(0, next.nextDartIndex)
    }

    @Test
    fun `CR24 Next player does nothing while no turn waits`() {
        val midTurn = screen().throwing(20)

        assertFalse(midTurn.canGoToNextPlayer)
        assertSame(midTurn, midTurn.nextPlayer())
        assertEquals(1, midTurn.nextDartIndex)
    }

    @Test
    fun `CR24 a triple is one dart and a miss is always a single miss`() {
        assertEquals(Dart.triple(20), screen().throwDart(Dart.triple(20)).game.darts.single())
        assertEquals(Dart.MISS, screen().throwMiss().game.darts.single())
    }

    @Test
    fun `CR24 the 25 and the Bull are recorded in order when 25-Bull is in play`() {
        val state = screen().throwDart(Dart.OUTER_BULL).throwDart(Dart.BULL)

        assertEquals(listOf(Dart.OUTER_BULL, Dart.BULL), state.game.darts)
    }

    @Test
    fun `CR1 CR24 without 25-Bull the 25 and Bull keys are absent and ignored`() {
        val noBull = screen(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false))

        assertTrue(screen().areBullKeysShown)
        assertFalse(noBull.areBullKeysShown)
        assertSame(noBull, noBull.throwDart(Dart.BULL))
        assertSame(noBull, noBull.throwDart(Dart.OUTER_BULL))
    }

    @Test
    fun `CR9 CR25 a win by a non-thrower highlights the winner in the VICTORY zone`() {
        val won = beforeNonThrowerWin.tripleOf(19)

        assertEquals(2, won.pendingTurn?.playerIndex)
        assertEquals(0, won.game.state.winnerIndex)
        assertTrue(won.isWinPending)
        assertEquals(CricketZoneMode.VICTORY, won.zoneMode)
        assertEquals(0, won.highlightedPlayerIndex)
        // Ann threw nothing in this turn: no dart under her name.
        assertEquals(emptyList<Dart>(), won.highlightedTurnDarts)
        assertFalse(won.isKeypadEnabled)
        assertFalse(won.canGoToNextPlayer)
        assertSame(won, won.nextPlayer())
        assertEquals(LegOutcome.MATCH, won.winOutcome)
    }

    @Test
    fun `CR11 CR25 Undo on a pending win reopens the thrower's turn`() {
        val won = beforeNonThrowerWin.tripleOf(19)
        val undone = won.undo()

        assertFalse(undone.isWinPending)
        assertEquals(CricketZoneMode.NORMAL, undone.zoneMode)
        assertNull(undone.winOutcome)
        assertEquals(2, undone.highlightedPlayerIndex)
        assertTrue(undone.isKeypadEnabled)
        assertEquals(beforeNonThrowerWin.match, undone.match)
    }

    @Test
    fun `CR25 a win by the thrower shows his winning darts`() {
        val solo = screen(CricketSaveFixtures.match("Ann"))
        val won = listOf(20, 19, 18, 17, 16, 15).fold(solo) { state, number ->
            state.tripleOf(number).let { if (it.canGoToNextPlayer) it.nextPlayer() else it }
        }.throwDart(Dart.BULL).throwDart(Dart.OUTER_BULL)

        assertTrue(won.isWinPending)
        assertEquals(0, won.highlightedPlayerIndex)
        assertEquals(listOf(Dart.BULL, Dart.OUTER_BULL), won.highlightedTurnDarts)
        assertEquals(LegOutcome.MATCH, won.winOutcome)
    }

    @Test
    fun `M15 CR25 the confirm button decides the leg, the set or the match`() {
        val legs = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)
        val sets = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 1, sets = 2)

        assertEquals(LegOutcome.LEG, pendingWin(legs).winOutcome)
        assertEquals(LegOutcome.SET, pendingWin(sets).winOutcome)
        assertEquals(LegOutcome.MATCH, pendingWin(legs.confirmLegsWonBy(0)).winOutcome)
        assertEquals(LegOutcome.MATCH, pendingWin(CricketSaveFixtures.match("Ann", "Bob")).winOutcome)
    }

    /** Ann wins the current leg of [match], with the win waiting, as after its winning dart. */
    private fun pendingWin(match: CricketMatch) =
        CricketGameScreenState(match.playLegWonBy(0), isAwaitingConfirmation = true)

    @Test
    fun `CR22 owner D6 the zone shows points, or the closed targets in a solo game`() {
        val duel = screen().tripleOf(20).throwDart(Dart.single(20))
        assertEquals(CricketZoneScore.Points(20), duel.zoneScoreOf(0))
        assertEquals(CricketZoneScore.Points(0), duel.zoneScoreOf(1))

        val solo = screen(CricketSaveFixtures.match("Ann")).tripleOf(20).tripleOf(19)
        assertEquals(CricketZoneScore.ClosedTargets(2, 7), solo.zoneScoreOf(0))
        val soloNoBull = screen(CricketSaveFixtures.match("Ann", isBullInPlay = false)).tripleOf(20)
        assertEquals(CricketZoneScore.ClosedTargets(1, 6), soloNoBull.zoneScoreOf(0))
    }

    @Test
    fun `CR24 one tap records one dart, a double or a triple included`() {
        val state = screen().throwDart(Dart.double(20)).throwDart(Dart.triple(20))

        assertEquals(listOf(Dart.double(20), Dart.triple(20)), state.game.darts)
    }

    @Test
    fun `CR1 CR24 throwDart refuses the 25 and the Bull with 25-Bull off`() {
        val noBull = screen(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false))

        assertSame(noBull, noBull.throwDart(Dart.OUTER_BULL))
        assertSame(noBull, noBull.throwDart(Dart.BULL))
        val withBull = screen().throwDart(Dart.OUTER_BULL).throwDart(Dart.BULL)
        assertEquals(listOf(Dart.OUTER_BULL, Dart.BULL), withBull.game.darts)
    }

    @Test
    fun `CR7 CR24 throwDart refuses any dart while a turn waits`() {
        val waiting = screen().throwDart(Dart.single(20)).throwDart(Dart.MISS).throwDart(Dart.MISS)

        assertTrue(waiting.isAwaitingConfirmation)
        assertSame(waiting, waiting.throwDart(Dart.triple(20)))
    }

    @Test
    fun `CR25 CR24 throwDart refuses any dart while a win is pending`() {
        val win = pendingWin(CricketSaveFixtures.match("Ann", "Bob"))

        assertTrue(win.isWinPending)
        assertSame(win, win.throwDart(Dart.single(20)))
        assertSame(win, win.throwDart(Dart.MISS))
    }

    @Test
    fun `CR15 CR24 a dart on a dead row is recorded`() {
        val annDone = screen().throwDart(Dart.triple(20)).throwDart(Dart.MISS).throwDart(Dart.MISS).nextPlayer()
        val bobClosing = annDone.throwDart(Dart.triple(20))

        assertTrue(bobClosing.boardRows[0].isDead)
        assertTrue(bobClosing.buttonRows[0].all { it.isEnabled })
        val after = bobClosing.throwDart(Dart.single(20))
        assertEquals(bobClosing.game.darts.size + 1, after.game.darts.size)
    }

    @Test
    fun `CR24 buttonRows give single double triple per number and 25 then Bull for the Bull row`() {
        val rows = screen().buttonRows

        assertEquals(7, rows.size)
        assertEquals(
            listOf(Dart.single(20), Dart.double(20), Dart.triple(20)),
            rows[0].map { it.dart },
        )
        assertEquals(listOf(0, 2, 3), rows[0].map { it.dotCount })
        assertEquals(listOf(Dart.single(15), Dart.double(15), Dart.triple(15)), rows[5].map { it.dart })
        assertEquals(listOf(Dart.OUTER_BULL, Dart.BULL), rows[6].map { it.dart })
        assertEquals(listOf(0, 2), rows[6].map { it.dotCount })
    }

    @Test
    fun `CR1 CR24 buttonRows have no Bull row with 25-Bull off`() {
        val rows = screen(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false)).buttonRows

        assertEquals(6, rows.size)
        assertTrue(rows.all { row -> row.none { it.dart.segment >= Dart.OUTER_BULL_SEGMENT } })
    }

    @Test
    fun `CR7 CR25 CR15 buttons are locked while a turn waits or a win is pending, never on a dead row`() {
        val open = screen()
        assertTrue(open.buttonRows.flatten().all { it.isEnabled })

        val waiting = open.throwDart(Dart.single(20)).throwDart(Dart.MISS).throwDart(Dart.MISS)
        assertTrue(waiting.buttonRows.flatten().none { it.isEnabled })

        assertTrue(pendingWin(CricketSaveFixtures.match("Ann", "Bob")).buttonRows.flatten().none { it.isEnabled })
    }

    @Test
    fun `CR23 headerTotals gives the points of each player, and null in a solo game`() {
        assertEquals(listOf(20, 0), screen().tripleOf(20).throwDart(Dart.single(20)).headerTotals)
        assertNull(screen(CricketSaveFixtures.match("Ann")).headerTotals)
    }

    @Test
    fun `CR23 headerTotals starts at zero for each player and is absent in a solo game`() {
        assertEquals(listOf(0, 0), screen().headerTotals)
        assertNull(screen(CricketSaveFixtures.match("Ann")).headerTotals)
    }

    @Test
    fun `CR13 MPR is absent before the first dart and counted over the match`() {
        val first = screen().tripleOf(20).tripleOf(19).tripleOf(18)

        assertNull(screen().mprOf(0))
        assertEquals(9.0, first.mprOf(0)!!, 0.0)
        assertNull(first.mprOf(1))
    }

    @Test
    fun `CR14 VT6 while a turn waits, the next player shows his last completed visit`() {
        val waiting = screen().tripleOf(20).throwDart(Dart.single(19)).throwMiss()

        assertEquals(listOf(CricketVisit.LastVisit(4), CricketVisit.NotPlayed), waiting.visits)
        val next = waiting.nextPlayer()
        assertEquals(listOf(CricketVisit.LastVisit(4), CricketVisit.InProgress(0)), next.visits)
        val twoDarts = next.throwDart(Dart.single(19)).throwDart(Dart.single(18))
        assertEquals(listOf(CricketVisit.LastVisit(4), CricketVisit.InProgress(2)), twoDarts.visits)
    }

    @Test
    fun `CR23 board rows carry every player's marks and the dead flag`() {
        val rows = screen().tripleOf(20).throwMiss().throwMiss().nextPlayer().throwDart(Dart.single(20)).boardRows

        assertEquals(listOf(3, 1), rows.first().marks)
        assertFalse(rows.first().isDead)
        val dead = screen().tripleOf(20).throwMiss().throwMiss().nextPlayer().tripleOf(20).boardRows.first()
        assertTrue(dead.isDead)
    }

    @Test
    fun `M14 M19 indicators and header position come from the match`() {
        val single = screen()
        assertNull(single.indicators)
        assertEquals(MatchPosition(null, null), single.headerPosition)

        val match = screen(CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(1))
        assertEquals(listOf(MatchIndicator(0, 0), MatchIndicator(0, 1)), match.indicators)
        assertEquals(MatchPosition(1, 2), match.headerPosition)
    }

    @Test
    fun `R21 the saved game keeps the waiting flag and drops the dialog`() {
        val waiting = screen().throwing(20, 19, 18).withAbandonDialog(shown = true)
        val restored = waiting.toSavedGame().toGameScreenState()

        assertTrue(waiting.toSavedGame().isAwaitingNextPlayer)
        assertEquals(waiting.match, restored.match)
        assertTrue(restored.isAwaitingConfirmation)
        assertFalse(restored.isAbandonDialogShown)
    }
}
