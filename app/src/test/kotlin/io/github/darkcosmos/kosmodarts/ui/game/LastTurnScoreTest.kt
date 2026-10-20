package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotal
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotals
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Test

class LastTurnScoreTest {

    private val threePlayers = X01Config(listOf("Ann", "Bob", "Cid"), startScore = 301)

    @Test
    fun noScoreBeforeTheFirstTurn() {
        val scores = lastTurnScores(X01Game(threePlayers, listOf(Dart.single(20))).state)

        assertEquals(List(3) { LastTurnScore.None }, scores)
    }

    @Test
    fun eachPlayerShowsTheScoreOfTheirOwnLastCompletedTurn() {
        // Ann 60 then 135, Bob 3 twice, Cid 180 then T20 T20 (leaves 1: bust), Ann has a turn in progress.
        val ones = List(3) { Dart.single(1) }
        val darts = List(3) { Dart.single(20) } + ones + List(3) { Dart.triple(20) } +
            List(3) { Dart.triple(15) } + ones + List(2) { Dart.triple(20) } +
            Dart.single(20)

        val scores = lastTurnScores(X01Game(threePlayers, darts).state)

        assertEquals(listOf(LastTurnScore.Points(135), LastTurnScore.Points(3), LastTurnScore.Bust), scores)
    }

    @Test
    fun aCheckoutScoresTheRemainingBeforeTheTurn() {
        val single = X01Config(listOf("Ann"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        val darts = List(5) { Dart.triple(20) } + Dart.single(1)

        assertEquals(listOf(LastTurnScore.Points(121)), lastTurnScores(X01Game(single, darts).state))
    }

    /**
     * VT6, A1 (design review of #36): the "Prev." label and the visit total box read the last completed turn in two
     * places; for normal, bust and checkout turns they agree, so they cannot diverge unnoticed.
     */
    @Test
    fun lastTurnScoresAgreeWithTheEngineLastCompletedVisit() {
        val ones = List(3) { Dart.single(1) }
        val inProgress = List(3) { Dart.single(20) } + ones + List(3) { Dart.triple(20) } +
            List(3) { Dart.triple(15) } + ones + List(2) { Dart.triple(20) } + Dart.single(20)
        val single = X01Config(listOf("Ann", "Bob"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        // Ann leaves 121, then T20 T20 S1 checks out in single out.
        val checkoutDarts = List(3) { Dart.triple(20) } + ones + List(2) { Dart.triple(20) } + Dart.single(1)
        val checkout = X01Game(single, checkoutDarts)
        val games = listOf(X01Game(threePlayers), X01Game(threePlayers, inProgress), checkout)

        for (game in games) {
            val expected = game.state.players.indices.map { player ->
                when (val visit = VisitTotals.lastCompleted(game.state, player)) {
                    VisitTotal.NotPlayed -> LastTurnScore.None
                    VisitTotal.Bust -> LastTurnScore.Bust
                    is VisitTotal.LastVisit -> LastTurnScore.Points(visit.points)
                    is VisitTotal.InProgress -> error("lastCompleted never returns a turn in progress")
                }
            }
            assertEquals(expected, lastTurnScores(game.state))
        }
        assertEquals(LastTurnScore.Points(121), lastTurnScores(checkout.state)[0])
    }
}
