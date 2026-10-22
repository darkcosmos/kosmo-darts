package io.github.darkcosmos.kosmodarts.ui.end

import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The end screen ranking of a match (docs/rules/match.md, M9, M10, M17). */
class EndRowsMatchTest {

    /** M10, M17: ranked by sets then total legs; sets and total legs shown; average and darts over the match. */
    @Test
    fun `M17 match columns`() {
        val finished = match("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2).confirmLegsWonBy(1, 1, 2, 0, 0, 0)
            .playLegWonBy(0)

        val rows = endRows(finished)

        assertEquals(listOf("Ann", "Bob", "Cid"), rows.map(EndRow::name))
        assertEquals(listOf(1, 2, 3), rows.map(EndRow::rank))
        assertEquals(listOf(2, 1, 0), rows.map(EndRow::setsWon))
        assertEquals(listOf(4, 2, 1), rows.map(EndRow::legsWon))
        assertEquals(listOf(true, false, false), rows.map(EndRow::isWinner))
        assertEquals(finished.playerStats.map { it.dartsThrown }, rows.map(EndRow::dartsThrown))
        assertEquals(finished.playerStats[0].threeDartAverage, rows[0].threeDartAverage)
    }

    /** M14, M17: with one set, no set column. */
    @Test
    fun `M17 no set column with one set`() {
        val finished = endRows(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(1, 0).playLegWonBy(0))
        assertTrue(finished.all { it.setsWon == null })
        assertEquals(listOf(2, 1), finished.map(EndRow::legsWon))
    }

    /** M5: a single game has no match columns. */
    @Test
    fun `M5 a single game has no match columns`() {
        val rows = endRows(match("Ann", "Bob").playLegWonBy(1))

        assertNull(rows.first().legsWon)
        assertNull(rows.first().setsWon)
    }
}
