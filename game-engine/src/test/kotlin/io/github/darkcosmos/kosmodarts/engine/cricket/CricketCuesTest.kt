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
    fun `AN30 CR15 the dart that closes the target is a closing hit, the next one on it in the same visit is not`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS MISS").throwing("T20")
        assertEquals(listOf(CricketCue.ClosingHit(dart("T20"))), CricketCues.afterDart(game.undo(), game))
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("20"))), cuesOf(game, "20"))
    }

    @Test
    fun `AN30 CR15 a solo player's closed target is dead at once`() {
        val game = cricketGame("Anna")
        assertEquals(listOf(CricketCue.ClosingHit(dart("T20"))), cuesOf(game, "T20"))
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("D20"))), cuesOf(game.throwing("T20"), "D20"))
    }

    @Test
    fun `AN6 an undo is a single undo cue`() {
        assertEquals(CricketCue.DartUndone, CricketCues.dartUndone)
    }

    /** Review T7: the cue depends on the game before and after the dart, so an undone closing dart closes again. */
    @Test
    fun `CR17 AN30 undo of the closing dart makes the target live, thrown again it is a closing hit again`() {
        val beforeClosing = cricketGame("Anna", "Bob").turns("T20 MISS MISS").throwing("T20")
            .undo()
        assertEquals(listOf(CricketCue.ClosingHit(dart("T20"))), cuesOf(beforeClosing, "T20"))
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
    fun `AN31 AN33 the winning dart gives its dart cue then the win cue`() {
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
    fun `CR17 AN33 Kosmo's case, A throws T20 20 20, then B's T20 closes 20 and is a closing hit`() {
        val game = cricketGame("Anna", "Bob").turns("T20 20 20")
        assertEquals(listOf(CricketCue.ClosingHit(dart("T20"))), cuesOf(game, "T20"))
    }

    @Test
    fun `AN30 CR15 after the closure every dart with a mark on 20 is a closed-sector hit`() {
        val closed = cricketGame("Anna", "Bob").turns("T20 20 20").throwing("T20")
        assertEquals(listOf(CricketCue.ClosedSectorHit(dart("20"))), cuesOf(closed, "20"))
        val nextTurn = closed.throwing("20", "MISS", "MISS")
        listOf("D20", "T20").forEach {
            assertEquals(listOf(CricketCue.ClosedSectorHit(dart(it))), cuesOf(nextTurn, it), it)
        }
    }

    @Test
    fun `CR17 closing a target for the thrower while an opponent still has it open is a normal hit`() {
        assertEquals(listOf(CricketCue.DartHit(dart("T20"))), cuesOf(cricketGame("Anna", "Bob"), "T20"))
        val bobOpen = cricketGame("Anna", "Bob").turns("T20 MISS MISS", MISSES)
        assertEquals(listOf(CricketCue.DartHit(dart("T19"))), cuesOf(bobOpen, "T19"))
    }

    @Test
    fun `CR17 CR4 a closing dart with surplus marks is a closing hit and scores nothing`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS MISS").throwing("20")
        val after = game.throwing("T20")
        assertEquals(listOf(CricketCue.ClosingHit(dart("T20"))), CricketCues.afterDart(game, after))
        assertEquals(listOf(0, 0), after.points())
    }

    @Test
    fun `CR17 Cut-throat the closing dart is a closing hit`() {
        val game = cricketGame("Anna", "Bob", variant = CricketVariant.CUT_THROAT).turns("T20 MISS MISS")
        assertEquals(listOf(CricketCue.ClosingHit(dart("T20"))), cuesOf(game, "T20"))
    }

    @Test
    fun `CR17 4 players, only the dart of the last of the four to close 20 is a closing hit`() {
        var game = cricketGame("Anna", "Bob", "Cleo", "Dan")
        val expectedCues = listOf(CricketCue::DartHit, CricketCue::DartHit, CricketCue::DartHit, CricketCue::ClosingHit)
        expectedCues.forEach { cueOf ->
            assertEquals(listOf(cueOf(dart("T20"))), cuesOf(game, "T20"))
            game = game.turns("T20 MISS MISS")
        }
    }

    @Test
    fun `CR17 solo, each target's closing dart is a closing hit`() {
        val game = cricketGame("Anna")
        val cues = listOf("T20", "T19", "T18").runningFold(game) { g, code -> g.throwing(code) }
            .zipWithNext { before, after -> CricketCues.afterDart(before, after) }.flatten()
        assertEquals(listOf("T20", "T19", "T18").map { CricketCue.ClosingHit(dart(it)) }, cues)
    }

    @Test
    fun `CR17 the Bull closed by 25 then BULL is a closing hit`() {
        val game = cricketGame("Anna", "Bob").turns(CLOSE_BULL).throwing("25")
        assertEquals(listOf(CricketCue.ClosingHit(dart("BULL"))), cuesOf(game, "BULL"))
    }

    @Test
    fun `CR17 CR2 with 25-Bull off, 25 and BULL are misses, never closing hits`() {
        val game = cricketGame("Anna", "Bob", isBullInPlay = false).turns(CLOSE_20_19_18, CLOSE_20_19_18)
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(game, "25"))
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(game, "BULL"))
    }

    @Test
    fun `AN33 AN31 a 2-player closing dart that wins gives the hit cue then the win, never the closing cue`() {
        val before = cricketGame("Anna", "Bob").turns(*beforeWinningClosingTurns)
        assertEquals(
            listOf(CricketCue.DartHit(dart("25")), CricketCue.Win(0, "Anna", LegOutcome.MATCH)),
            cuesOf(before, "25"),
        )
    }

    @Test
    fun `AN33 AN27 a leg won by a closing dart gives the hit cue then the leg win cue`() {
        val before = cricketMatch("Anna", "Bob", legsPerSet = 2).turns(*beforeWinningClosingTurns)
        val after = before.throwing("25")
        assertEquals(
            listOf(CricketCue.DartHit(dart("25")), CricketCue.Win(0, "Anna", LegOutcome.LEG)),
            CricketCues.afterDart(before, after),
        )
    }

    @Test
    fun `CR9 AN31 a winning dart that closes a target only for the thrower stays a hit then the win`() {
        val before = cricketGame("Anna", "Bob").turns(
            "T20 T20 T20", MISSES, "T19 T18 T17", MISSES, "T16 T15 BULL", MISSES,
        )
        assertEquals(
            listOf(CricketCue.DartHit(dart("25")), CricketCue.Win(0, "Anna", LegOutcome.MATCH)),
            cuesOf(before, "25"),
        )
    }

    @Test
    fun `AN29 a miss on the turn of the last player to have a target open stays a miss`() {
        val game = cricketGame("Anna", "Bob").turns("T20 20 20")
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(game, "MISS"))
    }

    @Test
    fun `afterDart rejects a game that is not one dart further`() {
        val game = cricketGame("Anna", "Bob")
        assertThrows<IllegalArgumentException> { CricketCues.afterDart(game, game) }
        assertThrows<IllegalArgumentException> { CricketCues.afterDart(game, game.throwing("20", "20")) }
    }

    /** Anna leads on points and closes the Bull last; Bob had already closed everything: Anna's `25` wins. */
    private val beforeWinningClosingTurns = arrayOf(
        "T20 T20 T20", "T20 T19 T18", "T19 T18 T17", "T17 T16 T15", "T16 T15 MISS", CLOSE_BULL, "BULL",
    )

    /** The win cue of the dart that ends the current leg of [match] with [winner] as its winner. */
    private fun winCueOf(match: CricketMatch, winner: Int): CricketCue {
        val after = match.playLegWonBy(winner)
        return CricketCues.afterDart(after.undo(), after).last()
    }
}
