package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The state of the redesigned game screen (issue #49): zone mode, tile order (M6), confirm label (M15), header
 * position (M14, M19), checkout summary (U2), color slots (PC6, PC9) and the Double/Triple toggles (owner X1).
 */
class GameScreenStateZoneTest {

    private fun t20(): Array<Dart> = arrayOf(Dart.triple(20))

    /** 301 single out, two players: Ann throws T20 T20 T20 (121 left), Bob misses three times. */
    private fun annAt121(): GameScreenState = GameScreenState(
        match("Ann", "Bob").throwing(*t20(), *t20(), *t20(), Dart.MISS, Dart.MISS, Dart.MISS),
    )

    @Test
    fun `zone mode is NORMAL while playing and after Next player`() {
        val waiting = annAt121().throwNumber(1).throwNumber(1).throwNumber(1)

        assertEquals(ZoneMode.NORMAL, annAt121().zoneMode)
        assertEquals(ZoneMode.NORMAL, waiting.zoneMode)
        assertEquals(ZoneMode.NORMAL, waiting.nextPlayer().zoneMode)
    }

    @Test
    fun `VT13 zone mode is BUST while a bust waits, NORMAL after Next player or undo`() {
        // 121 left in single out: T20 T20 T20 is 180, a bust.
        val bust = annAt121().toggleMultiplier(Multiplier.TRIPLE).throwNumber(20)
            .toggleMultiplier(Multiplier.TRIPLE).throwNumber(20)
            .toggleMultiplier(Multiplier.TRIPLE).throwNumber(20)

        assertEquals(ZoneMode.BUST, bust.zoneMode)
        assertEquals(ZoneMode.NORMAL, bust.nextPlayer().zoneMode)
        assertEquals(ZoneMode.NORMAL, bust.undo().zoneMode)
    }

    @Test
    fun `M15 zone mode is CHECKOUT while the winning dart waits`() {
        val won = annAt121().toggleMultiplier(Multiplier.TRIPLE).throwNumber(20)
            .toggleMultiplier(Multiplier.TRIPLE).throwNumber(20).throwNumber(1)

        assertEquals(ZoneMode.CHECKOUT, won.zoneMode)
        assertEquals(ZoneMode.NORMAL, won.undo().zoneMode)
    }

    /** T1, R19: BUST and CHECKOUT also come back from a save. */
    @Test
    fun `R19 restored games show BUST and CHECKOUT`() {
        assertEquals(ZoneMode.BUST, SaveFixtures.WAITING_BUST.toGameScreenState().zoneMode)
        assertEquals(ZoneMode.CHECKOUT, SaveFixtures.WAITING_CHECKOUT.toGameScreenState().zoneMode)
        assertEquals(ZoneMode.NORMAL, SaveFixtures.WAITING_NORMAL.toGameScreenState().zoneMode)
    }

    /** M6 (as amended): the other players in play order, starting from the next one. */
    @Test
    fun `M6 tile order from the next player for 1 to 4 players`() {
        assertEquals(emptyList<Int>(), GameScreenState(match("Ann")).tileOrder)
        assertEquals(listOf(1), GameScreenState(match("Ann", "Bob")).tileOrder)
        assertEquals(listOf(1, 2), GameScreenState(match("Ann", "Bob", "Cid")).tileOrder)
        assertEquals(listOf(1, 2, 3), GameScreenState(match("Ann", "Bob", "Cid", "Dan")).tileOrder)
    }

    /** M6: the order follows the highlighted player after each "Next player". */
    @Test
    fun `M6 tile order after each Next player`() {
        var game = GameScreenState(match("Ann", "Bob", "Cid", "Dan"))
        val orders = mutableListOf(game.tileOrder)
        repeat(4) {
            game = game.throwMiss().throwMiss().throwMiss()
            // While the turn waits, the thrower stays in the zone: the order does not move yet.
            assertEquals(orders.last(), game.tileOrder)
            game = game.nextPlayer()
            orders += game.tileOrder
        }

        assertEquals(
            listOf(listOf(1, 2, 3), listOf(2, 3, 0), listOf(3, 0, 1), listOf(0, 1, 2), listOf(1, 2, 3)),
            orders,
        )
    }

    /** M15, T3: the confirm label in the four cases. */
    @Test
    fun `M15 confirm label for a single game, a leg, a set and the match`() {
        val single = GameScreenState(match("Ann", "Bob").playLegWonBy(0), isAwaitingConfirmation = true)
        val leg =
            GameScreenState(match("Ann", "Bob", legsPerSet = 2).playLegWonBy(0), isAwaitingConfirmation = true)
        val set = GameScreenState(
            match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(0).playLegWonBy(0),
            isAwaitingConfirmation = true,
        )
        val matchWon = GameScreenState(
            match("Ann", "Bob", legsPerSet = 1, sets = 2).confirmLegsWonBy(1).playLegWonBy(1),
            isAwaitingConfirmation = true,
        )

        assertEquals(LegOutcome.MATCH, single.checkoutOutcome)
        assertEquals(LegOutcome.LEG, leg.checkoutOutcome)
        assertEquals(LegOutcome.SET, set.checkoutOutcome)
        assertEquals(LegOutcome.MATCH, matchWon.checkoutOutcome)
        assertNull(annAt121().checkoutOutcome)
    }

    /** M14, M19, A2: the header position is the engine's numbers, with the set only when there are several. */
    @Test
    fun `M19 header position follows the engine numbers per format`() {
        val sets = match("Ann", "Bob", legsPerSet = 2, sets = 3).confirmLegsWonBy(0, 0, 1)
        val legs = match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(1, 0)

        assertEquals(
            MatchPosition(sets.score.currentSetNumber, sets.score.currentLegNumber),
            GameScreenState(sets).headerPosition,
        )
        assertEquals(MatchPosition(2, 2), GameScreenState(sets).headerPosition)
        assertEquals(MatchPosition(null, legs.score.currentLegNumber), GameScreenState(legs).headerPosition)
        assertEquals(MatchPosition(null, 3), GameScreenState(legs).headerPosition)
        assertEquals(MatchPosition(null, null), GameScreenState(match("Ann", "Bob")).headerPosition)
    }

    /** U2: the checkout summary gives the points at the start of the turn and its darts, for 1 and 3 darts. */
    @Test
    fun `U2 checkout summary for 1 and 3 darts`() {
        val threeDarts = annAt121().toggleMultiplier(Multiplier.TRIPLE).throwNumber(20)
            .toggleMultiplier(Multiplier.TRIPLE).throwNumber(20).throwNumber(1)
        // Ann leaves 60 (T20, 1, miss), Bob misses, then Ann checks out with one T20.
        val oneDart = annAt121().toggleMultiplier(Multiplier.TRIPLE).throwNumber(20).throwNumber(1).throwMiss()
            .nextPlayer().throwMiss().throwMiss().throwMiss().nextPlayer()
            .toggleMultiplier(Multiplier.TRIPLE).throwNumber(20)

        val three = listOf(Dart.triple(20), Dart.triple(20), Dart.single(1))
        assertEquals(CheckoutSummary(121, three), threeDarts.checkoutSummary)
        assertEquals(CheckoutSummary(60, listOf(Dart.triple(20))), oneDart.checkoutSummary)
        assertNull(annAt121().checkoutSummary)
    }

    /** PC6, PC9: each player's slot, whatever his position. */
    @Test
    fun `PC9 color slot of each player`() {
        val slots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_4, ColorSlot.SLOT_1)
        val game = GameScreenState(match("Ann", "Bob", "Cid", colorSlots = slots))

        assertEquals(listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_4, ColorSlot.SLOT_1), (0..2).map(game::colorSlotOf))
    }

    /** Owner X1: at most one of Double and Triple is active; the same key again turns it off. */
    @Test
    fun `X1 at most one multiplier is active`() {
        val double = annAt121().toggleMultiplier(Multiplier.DOUBLE)
        val triple = double.toggleMultiplier(Multiplier.TRIPLE)
        val none = triple.toggleMultiplier(Multiplier.TRIPLE)

        assertEquals(Multiplier.DOUBLE, double.multiplier)
        assertEquals(Multiplier.TRIPLE, triple.multiplier)
        assertEquals(Multiplier.SINGLE, none.multiplier)
    }

    /** The next dart slot is underlined while the turn is open, and not while it waits. */
    @Test
    fun `next dart index follows the turn`() {
        assertEquals(0, annAt121().nextDartIndex)
        assertEquals(1, annAt121().throwNumber(1).nextDartIndex)
        assertNull(annAt121().throwNumber(1).throwNumber(1).throwNumber(1).nextDartIndex)
    }
}
