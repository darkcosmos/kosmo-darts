package io.github.darkcosmos.kosmodarts.ui.end

import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonByScoring20
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The end screen rows of both modes (docs/rules/match.md, M10, M17; docs/rules/cricket.md, CR16, CR27). */
class EndContentTest {

    /** M17: the X01 rows have no points. */
    @Test
    fun `M17 the X01 rows carry no points`() {
        val rows = endRows(MatchFixtures.match("Ann", "Bob").playLegWonBy(0))

        assertTrue(rows.all { it.points == null })
    }

    /** CR27: the points of the last leg and the MPR, the darts kept, the ranking by M10 and CR16. */
    @Test
    fun `CR27 the Cricket rows give the last leg points, the MPR and the darts`() {
        val finished = CricketSaveFixtures.match("Ann", "Bob", "Cid").playLegWonByScoring20(1)
        val rows = endRows(finished)

        assertEquals(listOf("Bob", "Ann", "Cid"), rows.map(EndRow::name))
        assertEquals(listOf(1, 2, 2), rows.map(EndRow::rank))
        assertEquals(listOf(true, false, false), rows.map(EndRow::isWinner))
        assertEquals(listOf(20, 0, 0), rows.map(EndRow::points))
        assertEquals(finished.playerStats[1].marksPerRound, rows[0].threeDartAverage)
        assertEquals(finished.playerStats[1].dartsThrown, rows[0].dartsThrown)
        assertNull(rows[0].legsWon)
        assertNull(rows[0].setsWon)
    }

    /** M10, CR16 (review T8): two players equal on sets, legs, points and board marks share the second place. */
    @Test
    fun `M10 a full tie of the losers gives an ex aequo row`() {
        val rows = endRows(CricketSaveFixtures.match("Ann", "Bob", "Cid").playLegWonBy(2))

        assertEquals(listOf(1, 2, 2), rows.map(EndRow::rank))
        assertEquals(listOf("Cid", "Ann", "Bob"), rows.map(EndRow::name))
    }

    /** CR27: no PTS column in a solo game. */
    @Test
    fun `CR27 a solo game has no points`() {
        val rows = endRows(CricketSaveFixtures.match("Ann").playLegWonByScoring20(0))

        assertEquals(listOf(null), rows.map(EndRow::points))
    }

    /** M17: sets and legs columns in a Cricket match, as in X01. */
    @Test
    fun `M17 a Cricket match has legs over the match and no set column with one set`() {
        val finished = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)
            .confirmLegsWonBy(1, 0).playLegWonBy(0)
        val rows = endRows(finished)

        assertEquals(listOf(2, 1), rows.map(EndRow::legsWon))
        assertTrue(rows.all { it.setsWon == null })
    }

    /** M17: while the match is in progress there is no ranking. */
    @Test
    fun `no rows while the match is in progress`() {
        assertTrue(endRows(CricketSaveFixtures.match("Ann", "Bob")).isEmpty())
    }
}
