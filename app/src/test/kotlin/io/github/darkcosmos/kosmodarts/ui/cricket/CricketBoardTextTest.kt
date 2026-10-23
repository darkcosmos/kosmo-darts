package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.throwing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure row model of the Cricket board (docs/rules/cricket.md, CR15, CR23; review T3). */
class CricketBoardTextTest {

    /** Ann and Bob have both closed 20 (20 is dead); Ann has 2 marks on 19 and Bob 1 on 18. */
    private val rows = CricketGameScreenState(
        CricketSaveFixtures.match("Ann", "Bob").throwing(
            Dart.triple(20), Dart.double(19), Dart.MISS,
            Dart.triple(20), Dart.single(18),
        ),
    ).boardRows

    private val texts = CricketBoardText.of(rows)

    @Test
    fun `CR23 a dead row is struck through and muted`() {
        val dead = texts.first()

        assertEquals(RowStyle.DEAD, dead.style)
        assertEquals(BoardTextColor.MUTED, dead.textColor)
        assertTrue(dead.isStruck)
        // CR23: the row keeps its marks, the closed glyph for everyone.
        assertEquals(listOf("Ⓧ", "Ⓧ"), dead.cells)
    }

    @Test
    fun `CR23 a live row is plain, struck nowhere`() {
        texts.drop(1).forEach { live ->
            assertEquals(RowStyle.LIVE, live.style)
            assertEquals(BoardTextColor.PRIMARY, live.textColor)
            assertFalse(live.isStruck)
        }
    }

    @Test
    fun `CR23 the cells show empty, slash, X and the closed glyph`() {
        assertEquals(listOf("X", ""), texts[1].cells)
        assertEquals(listOf("", "/"), texts[2].cells)
        assertEquals(listOf("", "/", "X", "Ⓧ"), (0..3).map(CricketBoardText::glyphOf))
    }

    /** CR3, CR23, review A7: only 3 marks are read "closed"; 0, 1 and 2 are read as a number of marks. */
    @Test
    fun `CR3 CR23 a cell is closed from 3 marks`() {
        assertEquals(listOf(false, false, false, true), (0..3).map(CricketBoardText::isClosed))
        assertEquals(texts.map { row -> row.cells.map { it == "Ⓧ" } }, texts.map { it.isClosed })
    }

    @Test
    fun `CR23 rows go from 20 to 15 then Bull, with their labels`() {
        assertEquals(CricketTarget.entries.toList(), rows.map { it.target })
        assertEquals((20 downTo 15).map { listOf(it) }, texts.take(6).map { it.labelArgs })
        assertTrue(texts.take(6).all { it.labelRes == R.string.game_visit_value })
        assertEquals(R.string.dart_bull, texts.last().labelRes)
        assertEquals(emptyList<Int>(), texts.last().labelArgs)
    }

    @Test
    fun `CR1 CR23 without 25-Bull there is no Bull row`() {
        val noBull = CricketGameScreenState(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false))

        assertEquals(CricketTarget.NUMBERS, noBull.boardRows.map { it.target })
        assertEquals(6, CricketBoardText.of(noBull.boardRows).size)
    }

    @Test
    fun `CR15 CR23 solo, a target closed by the only player is dead`() {
        val solo = CricketGameScreenState(CricketSaveFixtures.match("Ann").throwing(Dart.triple(19)))
        val soloTexts = CricketBoardText.of(solo.boardRows)

        assertEquals(listOf(false, true, false, false, false, false, false), soloTexts.map { it.isStruck })
        assertEquals(RowStyle.DEAD, soloTexts[1].style)
        assertEquals(BoardTextColor.MUTED, soloTexts[1].textColor)
        assertEquals(listOf("Ⓧ"), soloTexts[1].cells)
    }

    @Test
    fun `CR15 CR23 a row closed by one player only stays live`() {
        val oneClosed = CricketGameScreenState(CricketSaveFixtures.match("Ann", "Bob").throwing(Dart.triple(17)))
        val row17 = CricketBoardText.of(oneClosed.boardRows)[3]

        assertEquals(RowStyle.LIVE, row17.style)
        assertFalse(row17.isStruck)
        assertEquals(listOf("Ⓧ", ""), row17.cells)
    }
}
