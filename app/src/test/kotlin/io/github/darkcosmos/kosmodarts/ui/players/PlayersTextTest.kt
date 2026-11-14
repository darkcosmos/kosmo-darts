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
}
