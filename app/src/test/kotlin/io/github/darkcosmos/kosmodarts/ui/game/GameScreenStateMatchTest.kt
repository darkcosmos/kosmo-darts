package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The game screen of a match (docs/rules/match.md, M5, M9, M14, M15). */
class GameScreenStateMatchTest {

    /** M15: the dialog outcome follows the match, and is `null` while no checkout waits. */
    @Test
    fun `M15 checkout dialog outcome`() {
        val start = match("Ann", "Bob", legsPerSet = 2, sets = 2)
        assertNull(GameScreenState(start).checkoutOutcome)
        mapOf(
            start.playLegWonBy(0) to LegOutcome.LEG,
            start.confirmLegsWonBy(0).playLegWonBy(0) to LegOutcome.SET,
            start.confirmLegsWonBy(0, 0, 0).playLegWonBy(0) to LegOutcome.MATCH,
        ).forEach { (finished, outcome) ->
            val screen = GameScreenState(finished, isAwaitingConfirmation = true)
            assertEquals(outcome, screen.checkoutOutcome)
        }
        val single = match("Ann", "Bob").playLegWonBy(1)
        assertEquals(LegOutcome.MATCH, GameScreenState(single, isAwaitingConfirmation = true).checkoutOutcome)
    }

    /** M9: the average shown is over the whole match, not the current leg. */
    @Test
    fun `M9 the average is over the match`() {
        val next = match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)
        val screen = GameScreenState(next.throwing(Dart.single(5)))

        assertEquals(next.playerStats[0].threeDartAverage, screen.averageOf(0))
        assertEquals(screen.match.playerStats[1].threeDartAverage, screen.averageOf(1))
        assertNull(screen.game.state.players[0].threeDartAverage)
    }

    /** M5, M14: no indicator in a single game; no set count with one set; legs of the current set only. */
    @Test
    fun `M14 indicators`() {
        assertNull(GameScreenState(match("Ann", "Bob")).indicators)
        assertEquals(
            listOf(MatchIndicator(setsWon = null, legsWon = 1), MatchIndicator(setsWon = null, legsWon = 0)),
            GameScreenState(match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(0)).indicators,
        )
        assertEquals(
            listOf(MatchIndicator(setsWon = 1, legsWon = 0), MatchIndicator(setsWon = 0, legsWon = 1)),
            GameScreenState(match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(0, 0, 1)).indicators,
        )
    }

    /** M14: a leg waiting in the checkout dialog is not counted yet. */
    @Test
    fun `M14 a pending leg is not counted`() {
        val pending = match("Ann", "Bob", legsPerSet = 3).playLegWonBy(0)

        assertEquals(0, GameScreenState(pending, isAwaitingConfirmation = true).indicators?.first()?.legsWon)
    }
}
