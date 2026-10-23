package io.github.darkcosmos.kosmodarts.ui.cricket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The layout contract of the Cricket game screen (docs/rules/cricket.md, CR22, review U1). Pure JVM. */
class CricketLayoutPolicyTest {

    private fun layout(
        width: Float,
        height: Float,
        scale: Float = 1f,
        players: Int = 4,
        rows: Int = 7,
        hasPoints: Boolean = players > 1,
    ) = CricketLayoutPolicy.of(CricketSpace(width, height, scale, players, rows, hasPoints))

    /** CR22: nominal sizes when there is room: keys 54, action bar 58, zone 96, rows 32. */
    @Test
    fun `a tall portrait screen keeps the nominal sizes`() {
        val layout = layout(width = 412f, height = 915f)

        assertEquals(CricketLayoutPolicy.KEY_HEIGHT, layout.keyHeightDp)
        assertEquals(CricketLayoutPolicy.ACTION_BAR_HEIGHT, layout.actionBarMinHeightDp)
        assertEquals(CricketLayoutPolicy.GAP, layout.gapDp)
        assertEquals(CricketLayoutPolicy.ZONE_HEIGHT, layout.zoneHeightDp)
        assertEquals(CricketLayoutPolicy.ROW_HEIGHT, layout.rowHeightDp)
        assertFalse(layout.boardScrolls)
    }

    /** CR22: at 360 x 640 (about 568 dp usable), 4 players, 25-Bull on, everything is at its floor and it fits. */
    @Test
    fun `360 by 568 with 4 players and 7 rows fits without scrolling at the floors`() {
        val layout = layout(width = 360f, height = 568f)

        assertEquals(CricketLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
        assertEquals(CricketLayoutPolicy.ACTION_BAR_HEIGHT_MIN, layout.actionBarMinHeightDp)
        assertEquals(CricketLayoutPolicy.GAP_MIN, layout.gapDp)
        assertEquals(CricketLayoutPolicy.ZONE_HEIGHT_MIN, layout.zoneHeightDp)
        assertEquals(CricketLayoutPolicy.ROW_HEIGHT_MIN, layout.rowHeightDp)
        assertEquals(44f, layout.columnHeaderHeightDp)
        assertFalse(layout.boardScrolls)
        // header 24 + zone 76 + keypad 152 + action bar 48 + 16 of margins and gaps + the board 252 = 568.
        val total = layout.headerHeightDp + layout.zoneHeightDp + (3 * layout.keyHeightDp + 2 * layout.gapDp) +
            layout.actionBarMinHeightDp + layout.topMarginDp + layout.bottomMarginDp + 3 * layout.gapDp +
            layout.columnHeaderHeightDp + 8 * layout.rowHeightDp
        assertEquals(568f, total)
    }

    /** CR22: 25-Bull off gives six rows, so a shorter screen still fits. */
    @Test
    fun `six rows fit where seven only fit at the floors`() {
        val layout = layout(width = 360f, height = 568f, rows = 6)

        assertFalse(layout.boardScrolls)
        assertTrue(layout.rowHeightDp > CricketLayoutPolicy.ROW_HEIGHT_MIN)
    }

    /** CR22: the fixed parts never go below 48 dp and the board scrolls below its floor. */
    @Test
    fun `a very short screen scrolls the board and keeps the keys at 48`() {
        val layout = layout(width = 360f, height = 480f)

        assertEquals(CricketLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
        assertEquals(CricketLayoutPolicy.ACTION_BAR_HEIGHT_MIN, layout.actionBarMinHeightDp)
        assertTrue(layout.boardScrolls)
    }

    /** CR22: at font scale 2.0 the zone grows 1.5 times, the label column is 48 dp and the board scrolls. */
    @Test
    fun `font scale 2 grows the zone and scrolls the board at 360 by 640`() {
        val layout = layout(width = 360f, height = 640f, scale = 2f)

        assertEquals(CricketLayoutPolicy.ZONE_HEIGHT_MIN * 1.5f, layout.zoneHeightDp)
        assertEquals(CricketLayoutPolicy.ZONE_HEIGHT_MIN * 1.5f, layout.zoneMinHeightDp)
        assertEquals(CricketLayoutPolicy.LABEL_COLUMN_WIDTH_LARGE, layout.labelColumnWidthDp)
        assertEquals(CricketLayoutPolicy.rowFloor(2f), layout.rowHeightDp)
        assertEquals(CricketLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
        assertTrue(layout.boardScrolls)
    }

    @Test
    fun `the row floor follows the text line at large font scales`() {
        assertEquals(26f, CricketLayoutPolicy.rowFloor(1f))
        assertEquals(30f, CricketLayoutPolicy.rowFloor(1.2f))
        assertEquals(46f, CricketLayoutPolicy.rowFloor(2f))
    }

    /** CR22: about 76 dp per player at 360 dp wide with 4 players, from 1 to 4 players. */
    @Test
    fun `column widths at 360 dp share the width left by the label column`() {
        (1..4).forEach { players ->
            val layout = layout(width = 360f, height = 568f, players = players)

            assertEquals(CricketLayoutPolicy.LABEL_COLUMN_WIDTH, layout.labelColumnWidthDp)
            assertEquals(336f, layout.boardWidthDp)
            assertEquals((336f - 40f) / players, layout.playerColumnWidthDp, 0.001f)
        }
        assertTrue(layout(360f, 568f, players = 4).playerColumnWidthDp >= 72f)
    }

    /** CR22: a solo game has no points row, so one row less is needed. */
    @Test
    fun `a solo game has no points row`() {
        val solo = layout(width = 360f, height = 568f, players = 1, rows = 7)
        val four = layout(width = 360f, height = 568f, players = 4, rows = 7)

        assertTrue(solo.rowHeightDp >= four.rowHeightDp)
        assertFalse(solo.boardScrolls)
    }

    /** CR22, landscape 640 x 360: the zone gives way first, the keypad keeps 54 dp keys, the board is on the left. */
    @Test
    fun `landscape 640 by 360 fits the board and keeps the keypad`() {
        val layout = layout(width = 640f, height = 360f)

        assertEquals(CricketLayoutPolicy.KEY_HEIGHT, layout.keyHeightDp)
        assertFalse(layout.boardScrolls)
        assertTrue(layout.boardWidthDp <= (640f - 24f) * 0.55f + 0.001f)
        assertTrue(layout.zoneHeightDp >= layout.zoneMinHeightDp)
    }

    /** CR22, landscape: the zone shrinks to its minimum before the keys leave 54 dp, then they go to 48. */
    @Test
    fun `landscape on a very short screen takes the floors and the zone minimum`() {
        val layout = layout(width = 640f, height = 270f)

        assertEquals(CricketLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
        assertEquals(CricketLayoutPolicy.ZONE_HEIGHT_MIN, layout.zoneHeightDp)
    }

    @Test
    fun `landscape at font scale 2 scrolls the board only`() {
        val layout = layout(width = 640f, height = 360f, scale = 2f)

        assertTrue(layout.boardScrolls)
        assertEquals(CricketLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
    }
}
