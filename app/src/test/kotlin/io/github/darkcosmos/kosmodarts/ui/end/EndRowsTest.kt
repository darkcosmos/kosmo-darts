package io.github.darkcosmos.kosmodarts.ui.end

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndRowsTest {

    @Test
    fun rowsFollowTheEngineRankingAndTiesShareARank() {
        val config = X01Config(listOf("Ann", "Bob", "Cid"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        // Bob and Cid score 60 each, then Ann wins in two turns with 301.
        val darts = List(3) { Dart.triple(20) } +
            List(3) { Dart.single(20) } +
            List(3) { Dart.single(20) } +
            listOf(Dart.triple(20), Dart.triple(20), Dart.single(1))

        val rows = endRows(X01Game(config, darts))

        assertEquals(
            listOf(
                EndRow(rank = 1, name = "Ann", threeDartAverage = 301 * 3 / 6.0, dartsThrown = 6, isWinner = true),
                EndRow(rank = 2, name = "Bob", threeDartAverage = 60.0, dartsThrown = 3, isWinner = false),
                EndRow(rank = 2, name = "Cid", threeDartAverage = 60.0, dartsThrown = 3, isWinner = false),
            ),
            rows,
        )
    }

    @Test
    fun noRowsWhileTheLegIsInProgress() {
        assertTrue(endRows(X01Game(X01Config(listOf("Ann")))).isEmpty())
    }
}
