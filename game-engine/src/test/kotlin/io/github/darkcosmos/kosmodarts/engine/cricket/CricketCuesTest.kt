package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** AN29 to AN32 (docs/rules/announcements.md), with CR15. */
class CricketCuesTest {

    private fun cuesOf(game: CricketGame, code: String): List<CricketCue> =
        CricketCues.afterDart(game, game.throwing(code))

    /** Anna and Bob closed 20, so it is dead; Anna throws next. */
    private fun deadTwenty(): CricketGame = cricketGame("Anna", "Bob").turns("T20 MISS MISS", "T20 MISS MISS")

    @Test
    fun `AN29 a dart with marks on a live target is a hit`() {
        listOf("20", "D19", "T18", "25", "BULL").forEach {
            assertEquals(listOf(CricketCue.DartHit(dart(it))), cuesOf(cricketGame("Anna", "Bob"), it), it)
        }
    }

    @Test
    fun `AN29 a dart with no mark is a miss only`() {
        listOf("MISS", "T14", "S1", "D10").forEach {
            assertEquals(listOf(CricketCue.DartMiss), cuesOf(cricketGame("Anna", "Bob"), it), it)
        }
    }

    @Test
    fun `AN29 the Bull without 25-Bull in play is a miss`() {
        val game = cricketGame("Anna", isBullInPlay = false)
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(game, "BULL"))
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(game, "25"))
    }

    @Test
    fun `AN30 CR15 a dart with marks on a dead target is a closed-sector hit`() {
        val game = deadTwenty()
        listOf("20", "D20", "T20").forEach {
            assertEquals(listOf(CricketCue.ClosedSectorHit(dart(it))), cuesOf(game, it), it)
        }
    }

    @Test
    fun `AN30 CR15 a target closed by only some players is not dead, so its hit is a normal hit`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS MISS")
        assertEquals(listOf(CricketCue.DartHit(dart("20"))), cuesOf(game, "20"))
    }

    @Test
    fun `AN30 CR15 a dead Bull is a closed-sector hit for the outer and the inner bull`() {
        val game = cricketGame("Anna", "Bob").turns(CLOSE_BULL, CLOSE_BULL)
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("25"))), cuesOf(game, "25"))
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("BULL"))), cuesOf(game, "BULL"))
    }

    @Test
    fun `AN30 CR15 the dart that closes the target is a hit, the next one on it in the same visit is not`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS MISS").throwing("T20")
        assertEquals(listOf(CricketCue.DartHit(dart("T20"))), CricketCues.afterDart(game.undo(), game))
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("20"))), cuesOf(game, "20"))
    }

    @Test
    fun `AN30 CR15 a solo player's closed target is dead at once`() {
        val game = cricketGame("Anna").throwing("T20")
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("D20"))), cuesOf(game, "D20"))
    }

    @Test
    fun `AN6 an undo is a single undo cue`() {
        assertEquals(CricketCue.DartUndone, CricketCues.dartUndone)
    }

    /** Review T7: the cue depends on the game as it was before the dart, so an undone closing dart is a hit again. */
    @Test
    fun `AN30 undoing the dart that made a target dead then throwing it again is a normal hit`() {
        val beforeClosing = cricketGame("Anna", "Bob").turns("T20 MISS MISS").throwing("T20")
            .undo()
        assertEquals(listOf(CricketCue.DartHit(dart("T20"))), cuesOf(beforeClosing, "T20"))
    }

    @Test
    fun `AN31 turn start at the game start, after a turn and in the next leg`() {
        val game = cricketGame("Anna", "Bob")
        assertEquals(CricketCue.TurnStart(0, "Anna", isGameStart = true), CricketCues.turnStart(game))
        assertEquals(CricketCue.TurnStart(1, "Bob", isGameStart = false), CricketCues.turnStart(game.turns(MISSES)))

        val match = cricketMatch("Anna", "Bob", legsPerSet = 2).playLegWonBy(0).confirmLeg()
        assertEquals(CricketCue.TurnStart(1, "Bob", isGameStart = true), CricketCues.turnStart(match.currentLeg))
    }

    @Test
    fun `AN31 no turn start once the leg is finished`() {
        val won = cricketGame("Anna").turns(CLOSE_20_19_18, CLOSE_17_16_15, "BULL 25")
        assertNull(CricketCues.turnStart(won))
    }

    @Test
    fun `AN31 the winning dart gives its dart cue then the win cue`() {
        val beforeLastDart = cricketGame("Anna").turns(CLOSE_20_19_18, CLOSE_17_16_15, "BULL")
        assertEquals(
            listOf(CricketCue.DartHit(dart("25")), CricketCue.Win(0, "Anna", LegOutcome.MATCH)),
            cuesOf(beforeLastDart, "25"),
        )
    }

    @Test
    fun `AN27 the win cue of a match carries the leg, set or match outcome`() {
        val leg = cricketMatch("Anna", "Bob", legsPerSet = 2)
        assertEquals(CricketCue.Win(0, "Anna", LegOutcome.LEG), winCueOf(leg, 0))

        val set = cricketMatch("Anna", "Bob", legsPerSet = 2, sets = 2)
        assertEquals(CricketCue.Win(0, "Anna", LegOutcome.LEG), winCueOf(set, 0))
        assertEquals(CricketCue.Win(0, "Anna", LegOutcome.SET), winCueOf(set.confirmLegsWonBy(0), 0))

        val single = cricketMatch("Anna", "Bob")
        assertEquals(CricketCue.Win(1, "Bob", LegOutcome.MATCH), winCueOf(single, 1))
    }

    /** CR9: the winner is the player the dart makes win, who may not be the thrower. */
    @Test
    fun `CR9 AN31 a win by a player who did not throw`() {
        val before = cricketGame("Anna", "Bob", "Cleo", variant = CricketVariant.CUT_THROAT).turns(
            MISSES, "T20 T20 MISS", MISSES,
            CLOSE_20_19_18, MISSES, "T19 T19 MISS",
            CLOSE_17_16_15, MISSES, MISSES,
            CLOSE_BULL, MISSES,
        )
        assertEquals(
            listOf(CricketCue.DartHit(dart("T19")), CricketCue.Win(0, "Anna", LegOutcome.MATCH)),
            cuesOf(before, "T19"),
        )
    }

    @Test
    fun `AN32 a triple 20 visit gives only dart cues, no bust, 180 or score cue`() {
        val game = cricketGame("Anna", "Bob")
        val states = listOf("T20", "T20", "T20").runningFold(game) { g, code -> g.throwing(code) }
        val cues = states.zipWithNext { before, after -> CricketCues.afterDart(before, after) }.flatten()
        assertEquals(List(3) { CricketCue.DartHit(dart("T20")) }, cues)
    }

    @Test
    fun `afterDart rejects a game that is not one dart further`() {
        val game = cricketGame("Anna", "Bob")
        assertThrows<IllegalArgumentException> { CricketCues.afterDart(game, game) }
        assertThrows<IllegalArgumentException> { CricketCues.afterDart(game, game.throwing("20", "20")) }
    }

    /** The win cue of the dart that ends the current leg of [match] with [winner] as its winner. */
    private fun winCueOf(match: CricketMatch, winner: Int): CricketCue {
        val after = match.playLegWonBy(winner)
        return CricketCues.afterDart(after.undo(), after).last()
    }
}
