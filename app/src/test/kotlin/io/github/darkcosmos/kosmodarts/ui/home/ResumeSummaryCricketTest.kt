package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures.saved
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The home summary of a saved Cricket game (docs/rules/cricket.md, CR20; docs/rules/match.md, M18). */
class ResumeSummaryCricketTest {

    /** CR20: the format line carries the variant and the Bull; the X01 settings do not exist. */
    @Test
    fun `CR20 the summary carries the variant and the Bull`() {
        val summary = ResumeSummary.of(
            saved(match("Ann", "Bob", variant = CricketVariant.CUT_THROAT, isBullInPlay = false)),
        )

        assertEquals(ResumeGame.Cricket(CricketVariant.CUT_THROAT, isBullInPlay = false), summary.game)
        assertNull(summary.startScore)
        assertNull(summary.checkoutRule)
        assertEquals(MatchFormat(), summary.format)
    }

    /** CR20: the tiles show each player's points. */
    @Test
    fun `CR20 the tiles show the points`() {
        val summary = ResumeSummary.of(
            saved(match("Ann", "Bob").throwing(Dart.triple(20), Dart.triple(20))),
        )

        // The first triple closes 20 and the second scores 60 for Ann, as Bob has not closed it (CR3, CR4).
        assertEquals(
            listOf(
                ResumeSummary.PlayerScore("Ann", 60, ColorSlot.SLOT_1),
                ResumeSummary.PlayerScore("Bob", 0, ColorSlot.SLOT_2),
            ),
            summary.players,
        )
        assertEquals("Ann", summary.highlightedPlayer)
    }

    /** CR20, Q6 A: a solo game shows no score on its tile, so the card shows no "n/7" either. */
    @Test
    fun `CR20 Q6 A a solo game shows no score on its tile`() {
        val summary = ResumeSummary.of(saved(match("Ann").throwing(*CricketSaveFixtures.darts("T20 T19"))))

        assertEquals(listOf<Int?>(null), summary.players.map { it.remaining })
    }

    /** CR20, M18: in a match, each player's sets and legs. */
    @Test
    fun `CR20 M18 the summary shows the match score`() {
        val summary = ResumeSummary.of(
            saved(match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(0, 0, 1)),
        )

        assertEquals(listOf(1, 0), summary.players.map { it.setsWon })
        assertEquals(listOf(0, 1), summary.players.map { it.legsWon })
    }

    /** CR20, M18: between legs, the highlighted player is the starter of the next leg. */
    @Test
    fun `CR20 M18 between legs the starter of the next leg is highlighted`() {
        val next = match("Ann", "Bob", "Cid", legsPerSet = 2).confirmLegsWonBy(0)

        val summary = ResumeSummary.of(saved(next, isBetweenLegs = true))

        assertEquals("Bob", summary.highlightedPlayer)
    }

    /** M18: a turn that waits for "Next player" keeps the thrower highlighted, as the resumed screen does. */
    @Test
    fun `M18 a waiting turn keeps its thrower highlighted`() {
        assertEquals("Ann", ResumeSummary.of(CricketSaveFixtures.WAITING).highlightedPlayer)
    }

    /** R14: [ResumeSummary.of] on a stored game of either mode picks its own summary. */
    /** R20: a stored game of either mode has its own summary. */
    @Test
    fun `R20 a stored game of either mode has its own summary`() {
        val cricket: StoredGame = CricketSaveFixtures.MID_TURN
        val x01: StoredGame = SaveFixtures.MID_TURN

        assertEquals(ResumeGame.Cricket(CricketVariant.STANDARD, isBullInPlay = true), ResumeSummary.of(cricket).game)
        assertEquals(ResumeGame.X01(501, CheckoutRule.DOUBLE_OUT), ResumeSummary.of(x01).game)
    }
}
