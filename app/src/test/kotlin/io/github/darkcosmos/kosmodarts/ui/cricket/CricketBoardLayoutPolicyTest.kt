package io.github.darkcosmos.kosmodarts.ui.cricket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The board layout contract of the Cricket game screen on the Pixel 7 (docs/rules/cricket.md, CR22, plan section 4).
 * Pure JVM.
 *
 * Fixtures: the line heights are the ones measured in Robolectric by developer-build-R2 at 360 dp wide. They are
 * heights of single-line texts and of the two-line action bar, so they are used unchanged at 411 dp, the bar being
 * the worst case (its text wraps at most as much at 411 dp). The values at scale 1.0 and 2.0 are measured; the ones at
 * 1.3 and 1.5, and the em of the totals (taken as the 20 sp effective scale times the size), are estimates. The real
 * fit at 411 x 814 dp is proven by R3's and R4's Robolectric tests.
 */
class CricketBoardLayoutPolicyTest {

    /** One font scale: 14 sp body line, 24 sp total line, match header line, "VALIDER LA PARTIE" bar, 20 sp scale. */
    private data class Fixture(
        val scale: Float,
        val bodyLine: Float,
        val totalLine: Float,
        val matchHeader: Float,
        val bar: Float,
        val spareAt814: Float,
        val effectiveScale20: Float,
    )

    private val fixtures = listOf(
        Fixture(1.0f, 22.5f, 37.5f, 24f, 71.5f, 201.5f, 1.0f),
        Fixture(1.3f, 30.2f, 41.3f, 32.2f, 115f, 109.5f, 1.18f),
        Fixture(1.5f, 35.3f, 43.8f, 37.7f, 125f, 73.2f, 1.3f),
        Fixture(2.0f, 41.8f, 56.3f, 44.6f, 151.5f, 20.9f, 1.7f),
    )

    private fun candidates(f: Fixture, sizes: List<Float>) =
        sizes.map { TotalSizeCandidate(it, it * f.effectiveScale20) }

    /** The portrait board of revision 2 (rows 48 to 56 dp) for one fixture, with the plan's name-line estimate. */
    private fun portrait(f: Fixture, players: Int, rows: Int, hasTotal: Boolean = players > 1): CricketBoardLayout {
        val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(if (hasTotal) f.totalLine else null, f.bodyLine)
        val nameLine = splitFixtures.first { it.scale == f.scale }.nameLine
        val available = 814f - 34f - f.matchHeader - f.bar
        val split = CricketBoardLayoutPolicy.portraitSplit(411f, available, header, nameLine, players, rows)
        assertTrue("pinned parts and rows fit 814 dp", header + split.board.rowHeightDp * rows <= available + 0.01f)
        return split.board
    }

    /** CR22: at 411 x 814 dp, 4 players, the tallest bar, nothing scrolls at any font scale. */
    @Test
    fun `portrait Pixel 7 never scrolls from scale 1 to 2 with rows of 48 to 56 dp`() {
        for (f in fixtures) {
            for (players in 1..4) {
                for (rows in listOf(6, 7)) {
                    val layout = portrait(f, players, rows)
                    assertFalse("scale ${f.scale}, $players players, $rows rows", layout.scrolls)
                    assertTrue(layout.rowHeightDp in 48f..56f)
                }
            }
        }
    }

    /** CR22: the column header is about 63 dp at scale 1.0 and 101 dp at 2.0; solo has no total line. */
    @Test
    fun `column header is strip plus total plus name`() {
        assertEquals(63f, CricketBoardLayoutPolicy.columnHeaderHeightDp(37.5f, 22.5f), 0.01f)
        assertEquals(101.1f, CricketBoardLayoutPolicy.columnHeaderHeightDp(56.3f, 41.8f), 0.01f)
        assertEquals(25.5f, CricketBoardLayoutPolicy.columnHeaderHeightDp(null, 22.5f), 0.01f)
    }
    /** CR22: the zone is 84 dp times the growth of the 14 sp line, capped at 1.5. */
    /** CR22, CR24: player columns 61/76/110/219 dp and button column 144/159/168/168 dp at 4/3/2/1 players. */
    @Test
    fun `portrait column widths at 411 dp`() {
        val f = fixtures.first()
        val expected = mapOf(4 to (61f to 144f), 3 to (76f to 159f), 2 to (110f to 168f), 1 to (219f to 168f))
        for ((players, widths) in expected) {
            val layout = portrait(f, players, 7)
            assertEquals("player column, $players players", widths.first, layout.playerColumnWidthDp, 0.5f)
            assertEquals("button column, $players players", widths.second, layout.buttonColumnWidthDp, 0.5f)
            val total = layout.playerColumnWidthDp * players + layout.buttonColumnWidthDp
            assertEquals(411f - 24f, total, 0.01f)
        }
    }

    /** CR24: every button is at least 48 x 48 dp, at every player count and scale, in both orientations. */
    @Test
    fun `buttons are at least 48 dp`() {
        for (f in fixtures) {
            for (players in 1..4) {
                val layout = portrait(f, players, 7)
                assertTrue(layout.buttonWidthDp >= 48f)
                assertTrue(layout.rowHeightDp >= 48f)
                assertEquals(layout.buttonColumnWidthDp / 3, layout.buttonWidthDp, 0.001f)
            }
        }
        for (players in 1..4) {
            val layout = CricketBoardLayoutPolicy.landscape(landscapeSpace(players = players)).board
            assertTrue(layout.buttonWidthDp >= 48f)
            assertTrue(layout.rowHeightDp >= 48f)
        }
    }

    /** CR23: "RATE" has the button column's width and the header's height (48 dp at least). */
    @Test
    fun `miss cell matches the button column and the header`() {
        val f = fixtures.last()
        val layout = portrait(f, 4, 7)
        val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(f.totalLine, f.bodyLine)
        assertEquals(layout.buttonColumnWidthDp, layout.missCellWidthDp, 0.001f)
        assertEquals(header, layout.missCellHeightDp, 0.001f)
        val solo = portrait(f, 1, 7)
        assertEquals(48f, solo.missCellHeightDp, 0.001f)
    }

    /** CR22: the action bar has 187 dp per button at 411 dp, 12 dp apart. */
    @Test
    fun `portrait bar width`() {
        val room = 411f - 2 * CricketBoardLayoutPolicy.SIDE_MARGIN
        assertEquals(187.5f, (room - CricketBoardLayoutPolicy.BAR_GAP) / 2, 0.01f)
    }

    /** CR22: totals never wider than the column, the largest candidate that fits, the fixed 14 sp last resort. */
    @Test
    fun `total text size fits its column`() {
        for (f in fixtures) {
            for (columnWidth in listOf(61f, 110f)) {
                for (digits in 1..5) {
                    val c = candidates(f, CricketBoardLayoutPolicy.PORTRAIT_TOTAL_SIZES_SP)
                    val size = CricketBoardLayoutPolicy.totalTextSize(digits, columnWidth, c)
                    if (!size.isFixed) {
                        val em = c.first { it.sizeSp == size.sizeSp }.emDp
                        assertTrue(CricketBoardLayoutPolicy.totalLineWidthDp(digits, em) <= columnWidth)
                    } else {
                        assertEquals(CricketBoardLayoutPolicy.TOTAL_FALLBACK_SP, size.sizeSp, 0f)
                    }
                }
            }
        }
    }

    /** CR22: 4 players (61 dp): 3 digits at 24 sp and 4 at 20 sp at scale 1.0; 16 sp and fixed 14 sp at scale 2.0. */
    @Test
    fun `total text size at 4 players`() {
        val sizes = CricketBoardLayoutPolicy.PORTRAIT_TOTAL_SIZES_SP
        val one = candidates(fixtures.first(), sizes)
        val two = candidates(fixtures.last(), sizes)
        assertEquals(24f, CricketBoardLayoutPolicy.totalTextSize(3, 61f, one).sizeSp, 0f)
        assertEquals(20f, CricketBoardLayoutPolicy.totalTextSize(4, 61f, one).sizeSp, 0f)
        assertEquals(16f, CricketBoardLayoutPolicy.totalTextSize(3, 61f, two).sizeSp, 0f)
        val fallback = CricketBoardLayoutPolicy.totalTextSize(4, 61f, two)
        assertEquals(CricketBoardLayoutPolicy.TOTAL_FALLBACK_SP, fallback.sizeSp, 0f)
        assertTrue(fallback.isFixed)
    }

    /** CR22 fallback: below the Pixel 7 the rows scroll alone and keep 48 dp, in portrait and landscape. */
    @Test
    fun `rows scroll and keep 48 dp when they do not fit`() {
        val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(37.5f, 22.5f)
        val portrait = CricketBoardLayoutPolicy.portraitSplit(360f, header + 7 * 48f + 10f, header, 28.9f, 4, 7).board
        assertTrue(portrait.scrolls)
        assertEquals(48f, portrait.rowHeightDp, 0.001f)
        val landscape = CricketBoardLayoutPolicy.landscape(landscapeSpace(height = 355f)).board
        assertTrue(landscape.scrolls)
        assertEquals(48f, landscape.rowHeightDp, 0.001f)
    }

    private fun landscapeSpace(
        width: Float = 914f,
        height: Float = 411f,
        cutout: Float = 0f,
        players: Int = 4,
        rows: Int = 7,
    ) = CricketLandscapeSpace(
        screenWidthDp = width,
        screenHeightDp = height,
        cutoutInsetDp = cutout,
        headerHeightDp = 3f + 31.3f + 22.5f,
        titleLineDp = 24f,
        zoneLineDp = 28f,
        playerCount = players,
        rowCount = rows,
    )

    /** CR22 landscape: 914 x 411 dp, bars hidden, nothing scrolls, 49.5 dp rows at 7 rows, capped at 56 dp at 6. */
    @Test
    fun `landscape Pixel 7 rows`() {
        for (cutout in listOf(0f, 48f)) {
            for (players in 1..4) {
                val seven = CricketBoardLayoutPolicy.landscape(landscapeSpace(cutout = cutout, players = players))
                assertFalse(seven.board.scrolls)
                assertEquals(49.5f, seven.board.rowHeightDp, 0.1f)
                val sixRows = landscapeSpace(cutout = cutout, players = players, rows = 6)
                val six = CricketBoardLayoutPolicy.landscape(sixRows)
                assertFalse(six.board.scrolls)
                assertEquals(56f, six.board.rowHeightDp, 0.001f)
            }
        }
    }

    /** CR22 landscape: one band height for the board header and the right column's title plus zone line. */
    @Test
    fun `landscape band is the taller of the header and the title plus zone`() {
        val normal = CricketBoardLayoutPolicy.landscape(landscapeSpace())
        assertEquals(56.8f, normal.bandHeightDp, 0.01f)
        assertEquals(normal.bandHeightDp, normal.board.missCellHeightDp, 0.01f)
        val tallZone = CricketBoardLayoutPolicy.landscape(landscapeSpace().copy(zoneLineDp = 40f))
        assertEquals(68f, tallZone.bandHeightDp, 0.01f)
    }

    /** CR22 landscape: right column at least 340 dp with a 48 dp cutout, 12 dp gap, 173 dp per bar button at 4. */
    @Test
    fun `landscape right column`() {
        for (cutout in listOf(0f, 48f)) {
            for (players in 1..4) {
                val layout = CricketBoardLayoutPolicy.landscape(landscapeSpace(cutout = cutout, players = players))
                assertTrue(layout.rightColumnWidthDp >= 340f)
                val used = 2 * CricketBoardLayoutPolicy.SIDE_MARGIN + cutout + layout.boardWidthDp +
                    CricketBoardLayoutPolicy.BOARD_GAP + layout.rightColumnWidthDp
                assertEquals(914f, used, 0.01f)
            }
        }
        val four = CricketBoardLayoutPolicy.landscape(landscapeSpace(players = 4))
        assertEquals(358f + 48f, four.rightColumnWidthDp, 0.01f)
        val fourWithCutout = CricketBoardLayoutPolicy.landscape(landscapeSpace(cutout = 48f, players = 4))
        assertEquals(358f, fourWithCutout.rightColumnWidthDp, 0.01f)
        assertTrue(fourWithCutout.barButtonWidthDp >= 173f)
    }

    /** CR22 landscape: under the band there is room for the 151.5 dp bar of scale 2.0 with "VALIDER LA PARTIE". */
    @Test
    fun `landscape holds the tallest bar`() {
        val layout = CricketBoardLayoutPolicy.landscape(landscapeSpace())
        assertEquals(340f, layout.barRoomHeightDp, 0.5f)
        assertTrue(layout.barRoomHeightDp >= 151.5f)
    }

    /** CR22 landscape: the board's columns are the button column last, 76 dp players, 56 dp buttons. */
    @Test
    fun `landscape board columns`() {
        val layout = CricketBoardLayoutPolicy.landscape(landscapeSpace())
        assertEquals(76f, layout.board.playerColumnWidthDp, 0.01f)
        assertEquals(168f, layout.board.buttonColumnWidthDp, 0.01f)
        assertEquals(472f, layout.boardWidthDp, 0.01f)
    }

    // ---- Revision 2 (plan R2.1 and R2.2; Q2 reversal for the portrait zone, Q14 B 42 dp landscape floor) ----

    /** Scale, name line (18 sp, estimated), column header (strip + total + body line), A for the one-line bar. */
    private data class SplitFixture(
        val scale: Float,
        val nameLine: Float,
        val bodyLine: Float,
        val totalLine: Float,
        val matchHeader: Float,
        val oneLineBar: Float,
        val pendingBar: Float,
    )

    private val splitFixtures = listOf(
        SplitFixture(1.0f, 28.9f, 22.5f, 37.5f, 24f, 48f, 71.5f),
        SplitFixture(1.3f, 37.0f, 30.2f, 41.3f, 32.2f, 82f, 115f),
        SplitFixture(1.5f, 42.0f, 35.3f, 43.8f, 37.7f, 88.5f, 125f),
        SplitFixture(2.0f, 50.0f, 41.8f, 56.3f, 44.6f, 110.5f, 151.5f),
    )

    private fun split(
        f: SplitFixture,
        bar: Float,
        players: Int = 4,
        rows: Int = 7,
        screenHeight: Float = 814f,
    ): CricketPortraitSplit {
        val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(if (players > 1) f.totalLine else null, f.bodyLine)
        val available = screenHeight - 34f - f.matchHeader - bar
        return CricketBoardLayoutPolicy.portraitSplit(411f, available, header, f.nameLine, players, rows)
    }

    /** CR22 (R2.1): rows 48 to 56 dp, zone = A - header - rows, a total of 32 dp at least, nothing scrolls. */
    @Test
    fun `portraitSplit keeps rows 48 to 56 and a zone total of 32 dp`() {
        for (f in splitFixtures) {
            for (players in 1..4) {
                for (rows in listOf(6, 7)) {
                    for (bar in listOf(f.oneLineBar, f.pendingBar)) assertSplitInvariants(f, bar, players, rows)
                }
            }
        }
    }

    private fun assertSplitInvariants(f: SplitFixture, bar: Float, players: Int, rows: Int) {
        val s = split(f, bar, players, rows)
        val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(if (players > 1) f.totalLine else null, f.bodyLine)
        val a = 780f - f.matchHeader - bar
        val label = "scale ${f.scale}, bar $bar, $players players, $rows rows"
        assertFalse(label, s.board.scrolls)
        assertTrue(label, s.board.rowHeightDp in 48f..56f)
        assertEquals(label, a - header - s.board.rowHeightDp * rows, s.zoneHeightDp, 0.01f)
        val twoLine = f.scale == 2.0f && bar == f.pendingBar && players > 1 && rows == 7
        if (!twoLine) {
            assertTrue(label, s.showsTotal)
            assertTrue(label, s.totalAreaHeightDp >= 32f - 0.01f)
        }
    }

    /** CR22 (R2.1): the rows of the plan table (4 players, 7 rows) and the zone heights. */
    @Test
    fun `portraitSplit matches the plan table`() {
        val expected = listOf(
            Triple(splitFixtures[0], splitFixtures[0].oneLineBar, 56f to 253f),
            Triple(splitFixtures[0], splitFixtures[0].pendingBar, 56f to 229.5f),
            Triple(splitFixtures[1], splitFixtures[1].oneLineBar, 56f to 199.3f),
            Triple(splitFixtures[1], splitFixtures[1].pendingBar, 56f to 166.3f),
            Triple(splitFixtures[2], splitFixtures[2].oneLineBar, 56f to 179.7f),
            Triple(splitFixtures[2], splitFixtures[2].pendingBar, 55.3f to 148f),
            Triple(splitFixtures[3], splitFixtures[3].oneLineBar, 52.6f to 156f),
            Triple(splitFixtures[3], splitFixtures[3].pendingBar, 48f to 146.9f),
        )
        for ((f, bar, rowsAndZone) in expected) {
            val s = split(f, bar)
            assertEquals("rows, scale ${f.scale}, bar $bar", rowsAndZone.first, s.board.rowHeightDp, 0.1f)
            assertEquals("zone, scale ${f.scale}, bar $bar", rowsAndZone.second, s.zoneHeightDp, 0.3f)
        }
    }

    /** CR22 (R2.1): scale 2.0 with a pending win is the two-line zone (22.9 dp left), the total stays in the header. */
    @Test
    fun `portraitSplit falls back to the two-line zone at scale 2 with a pending win`() {
        val f = splitFixtures[3]
        val s = split(f, f.pendingBar)
        assertFalse(s.showsTotal)
        assertEquals(0f, s.totalAreaHeightDp, 0.0f)
        assertEquals(48f, s.board.rowHeightDp, 0.01f)
        assertFalse(s.board.scrolls)
        assertEquals(22.9f, s.zoneHeightDp - zoneFixedHeightDp(f.nameLine), 0.2f)
    }
    /**
     * CR22 (R2.1), measured by R9 in Robolectric at 411 x 814 dp (name line 28.9, 33.9, 38.1 and 47.2 dp at scales
     * 1.0, 1.3, 1.5 and 2.0; the action bar is at most two lines there, 110.5 dp at 2.0 even with the pending
     * label): the real bars are lower than the plan's estimates, so scale 2.0 keeps its total (32 dp).
     */
    @Test
    fun `portraitSplit with the measured name line at scale 2 keeps the total with a pending win`() {
        val f = splitFixtures[3].copy(nameLine = 47.2f, pendingBar = 110.5f)
        val s = split(f, f.pendingBar)
        assertTrue(s.showsTotal)
        assertEquals(32f, s.totalAreaHeightDp, 0.2f)
        assertEquals(53f, s.board.rowHeightDp, 0.1f)
        assertFalse(s.board.scrolls)
    }


    /** CR22 (R2.1): the zone's fixed part is 2 x 6 + name line + 2 x 8 + 46 dp of dart cells. */
    @Test
    fun `zone fixed height`() {
        assertEquals(102.9f, zoneFixedHeightDp(28.9f), 0.01f)
        assertEquals(124f, zoneFixedHeightDp(50f), 0.01f)
    }

    /** CR22 (R2.1): the column widths are those of portrait, and the "RATE" cell the header's height. */
    @Test
    fun `portraitSplit column widths and miss cell`() {
        val f = splitFixtures[0]
        val s = split(f, f.oneLineBar)
        assertEquals(61f, s.board.playerColumnWidthDp, 0.5f)
        assertEquals(144f, s.board.buttonColumnWidthDp, 0.5f)
        assertEquals(63f, s.board.missCellHeightDp, 0.01f)
    }

    /** CR22 (R2.1): a smaller screen keeps 48 dp rows and scrolls only below the two-line zone. */
    @Test
    fun `portraitSplit on a small screen keeps 48 dp rows and scrolls under the two-line zone`() {
        val f = splitFixtures[0]
        val s = split(f, f.oneLineBar, screenHeight = 600f)
        assertTrue(s.board.scrolls)
        assertEquals(48f, s.board.rowHeightDp, 0.01f)
        assertFalse(s.showsTotal)
        assertEquals(zoneFixedHeightDp(f.nameLine), s.zoneHeightDp, 0.01f)
    }

    /** CR22 (R2.1): the solo header has no total line, so there is more room and the zone is taller. */
    @Test
    fun `portraitSplit solo has a taller zone than four players`() {
        val f = splitFixtures[1]
        val solo = split(f, f.pendingBar, players = 1)
        val four = split(f, f.pendingBar, players = 4)
        assertTrue(solo.zoneHeightDp >= four.zoneHeightDp)
        assertTrue(solo.showsTotal)
    }

    private fun band(
        cutout: Float = 0f,
        players: Int = 4,
        rows: Int = 7,
        header: Float = 58.5f,
        height: Float = 411f,
    ) = CricketBoardLayoutPolicy.landscapeBand(CricketLandscapeBandSpace(914f, height, cutout, header, players, rows))

    /** CR22 (R2.2): band 44, header row 58.5, rows 43.5 dp at 7 and 50.75 dp at 6, 42 dp at least, no scroll. */
    @Test
    fun `landscapeBand rows fit the 411 dp height`() {
        for (cutout in listOf(0f, 48f)) {
            for (players in 1..4) {
                val seven = band(cutout, players, 7)
                val six = band(cutout, players, 6)
                assertEquals(44f, seven.bandHeightDp, 0.0f)
                assertEquals(58.5f, seven.headerRowHeightDp, 0.01f)
                assertEquals(43.5f, seven.board.rowHeightDp, 0.01f)
                assertEquals(50.75f, six.board.rowHeightDp, 0.01f)
                assertTrue(seven.board.rowHeightDp >= CricketBoardLayoutPolicy.LANDSCAPE_ROW_MIN)
                assertFalse(seven.board.scrolls)
                assertFalse(six.board.scrolls)
                assertTrue(44f + seven.headerRowHeightDp + 7 * seven.board.rowHeightDp + 4f <= 411f + 0.01f)
                assertEquals(304.5f, seven.nextKeyHeightDp, 0.01f)
                assertEquals(304.5f, six.nextKeyHeightDp, 0.01f)
            }
        }
    }

    /** CR22 (R2.2): a header 10 dp taller than estimated still gives 42 dp rows; a smaller height scrolls at 42 dp. */
    @Test
    fun `landscapeBand tolerates a taller header and scrolls on a small height`() {
        val taller = band(header = 68.5f)
        assertTrue(taller.board.rowHeightDp >= 42f)
        assertFalse(taller.board.scrolls)
        val small = band(height = 360f)
        assertTrue(small.board.scrolls)
        assertEquals(42f, small.board.rowHeightDp, 0.01f)
        assertEquals(small.rowsViewportHeightDp, small.nextKeyHeightDp, 0.01f)
    }

    /** CR22 (R2.2): the header row is 48 dp at least. */
    @Test
    fun `landscapeBand header row is 48 dp at least`() {
        val s = band(header = 30f)
        assertEquals(48f, s.headerRowHeightDp, 0.01f)
        assertEquals(48f, s.board.missCellHeightDp, 0.01f)
    }

    /** CR22 (R2.2): player columns share the width left, 135.5/180.7/271/542 dp with a 48 dp cutout. */
    @Test
    fun `landscapeBand player columns`() {
        val withCutout = mapOf(4 to 135.5f, 3 to 180.67f, 2 to 271f, 1 to 542f)
        val without = mapOf(4 to 147.5f, 3 to 196.67f, 2 to 295f, 1 to 590f)
        for ((players, width) in withCutout) {
            assertEquals(width, band(48f, players).board.playerColumnWidthDp, 0.05f)
        }
        for ((players, width) in without) {
            assertEquals(width, band(0f, players).board.playerColumnWidthDp, 0.05f)
        }
    }

    /** CR22 (R2.2): button column 168 dp (56 dp buttons), "RATE" 168 dp, undo and action column 120 dp. */
    @Test
    fun `landscapeBand button action and miss columns`() {
        val s = band(48f)
        assertEquals(168f, s.board.buttonColumnWidthDp, 0.01f)
        assertEquals(56f, s.board.buttonWidthDp, 0.01f)
        assertTrue(s.board.buttonWidthDp >= 48f)
        assertEquals(168f, s.board.missCellWidthDp, 0.01f)
        assertEquals(120f, s.actionColumnWidthDp, 0.01f)
        assertEquals(120f, s.undoWidthDp, 0.01f)
        assertEquals(135.5f * 4 + 168f, s.boardWidthDp, 0.05f)
        // 12 dp side margins, the board, a 12 dp gap, the action column
        val used = s.boardWidthDp + CricketBoardLayoutPolicy.BOARD_GAP + s.actionColumnWidthDp
        assertEquals(914f - 24f - 48f, used, 0.05f)
    }

    /** CR22: portrait keeps its 48 dp floor, whatever the screen. */
    @Test
    fun `portrait never returns rows under 48 dp`() {
        for (height in listOf(500f, 600f, 700f, 814f)) {
            for (f in splitFixtures) {
                assertTrue(split(f, f.pendingBar, screenHeight = height).board.rowHeightDp >= 48f)
            }
        }
    }
}
