package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import org.junit.Assert.assertEquals
import org.junit.Test

/** The home summary of a saved match (docs/rules/match.md, M18). */
class ResumeSummaryMatchTest {

    /** M18: each player's sets and legs of the current set, as on the cards. */
    @Test
    fun `M18 the summary shows the match score`() {
        val saved = SavedGame(
            match("Ann", "Bob", legsPerSet = 2, sets = 2).confirmLegsWonBy(0, 0, 1).throwing(Dart.triple(20)),
            isAwaitingNextPlayer = false,
        )

        assertEquals(
            listOf(
                ResumeSummary.PlayerScore("Ann", 301, setsWon = 1, legsWon = 0),
                ResumeSummary.PlayerScore("Bob", 241, setsWon = 0, legsWon = 1),
            ),
            ResumeSummary.of(saved).players,
        )
    }

    /** M14, M18: with one set, legs only. */
    @Test
    fun `M18 one set shows legs only`() {
        val saved = SavedGame(match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(1), isAwaitingNextPlayer = false)

        assertEquals(listOf(null, null), ResumeSummary.of(saved).players.map { it.setsWon })
        assertEquals(listOf(0, 1), ResumeSummary.of(saved).players.map { it.legsWon })
    }

    /** M18: between legs, the highlighted player is the starter of the next leg. */
    @Test
    fun `M18 between legs the starter of the next leg is highlighted`() {
        val next = match("Ann", "Bob", "Cid", legsPerSet = 2).confirmLegsWonBy(0)

        val summary = ResumeSummary.of(SavedGame(next, isAwaitingNextPlayer = false, isBetweenLegs = true))

        assertEquals("Bob", summary.highlightedPlayer)
        assertEquals(false, summary.isCheckoutToConfirm)
    }

    /** M5: a single game has no match score. */
    @Test
    fun `M5 a single game has no match score`() {
        assertEquals(listOf(null, null), ResumeSummary.of(SaveFixtures.MID_TURN).players.map { it.legsWon })
    }
}
