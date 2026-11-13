package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayersTextTest {
    @Test
    fun `PM2 the summary says no match, a count, or a count with the last match date`() {
        assertEquals(PlayerSummaryText.NoMatch, playerSummary(1, "Kevin", matches = 0).summaryText())
        val one = playerSummary(1, "Kevin", matches = 1, lastEndedAt = null)
        assertEquals(PlayerSummaryText.Matches(1), one.summaryText())
        val four = playerSummary(1, "Kevin", matches = 4, lastEndedAt = null)
        assertEquals(PlayerSummaryText.Matches(4), four.summaryText())
        assertEquals(
            PlayerSummaryText.MatchesAndLast(1, SOME_MATCH_END),
            playerSummary(1, "Kevin", matches = 1).summaryText(),
        )
        assertEquals(
            PlayerSummaryText.MatchesAndLast(7, SOME_MATCH_END),
            playerSummary(1, "Kevin", matches = 7).summaryText(),
        )
    }

    @Test
    fun `PM6 the deletion says no match, one match or several matches erased`() {
        assertEquals(ErasedText.None, erasedText(0))
        assertEquals(ErasedText.Matches(1), erasedText(1))
        assertEquals(ErasedText.Matches(5), erasedText(5))
    }

    @Test
    fun `PM4 the merge targets are every profile but the open one, none when no card is open`() {
        val kevin = playerSummary(1, "Kevin")
        val lea = playerSummary(2, "Lea")
        val ana = playerSummary(3, "Ana")
        val state = PlayersState().loaded(1, DbResult.Ok(listOf(kevin, lea, ana)))

        assertEquals(listOf(lea, ana), state.profileTapped(1).mergeTargets)
        assertEquals(listOf(kevin, ana), state.profileTapped(2).mergeTargets)
        assertEquals(listOf(kevin, lea, ana), state.mergeTargets)
        val alone = PlayersState().loaded(1, DbResult.Ok(listOf(kevin)))
        assertEquals(emptyList<PlayerSummary>(), alone.profileTapped(1).mergeTargets)
    }
}
