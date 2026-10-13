package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
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
}
