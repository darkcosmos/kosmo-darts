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

    private fun portrait(f: Fixture, players: Int, rows: Int, hasTotal: Boolean = players > 1): CricketBoardLayout {
        val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(if (hasTotal) f.totalLine else null, f.bodyLine)
        val zone = CricketBoardLayoutPolicy.portraitZoneHeightDp(f.bodyLine)
        val board = CricketBoardLayoutPolicy.portraitBoardHeightDp(814f, f.matchHeader, zone, f.bar)
        val layout = CricketBoardLayoutPolicy.portrait(411f, board, header, players, rows)
        assertTrue("pinned parts and rows fit 814 dp", header + layout.rowHeightDp * rows <= board + 0.01f)
        return layout
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

    /** CR22: the worst case (scale 2.0, 4 players, 7 rows) keeps about 21 dp of spare, each scale its own spare. */
    @Test
    fun `portrait spare matches the plan table`() {
        for (f in fixtures) {
            val header = CricketBoardLayoutPolicy.columnHeaderHeightDp(f.totalLine, f.bodyLine)
            val zone = CricketBoardLayoutPolicy.portraitZoneHeightDp(f.bodyLine)
            val board = CricketBoardLayoutPolicy.portraitBoardHeightDp(814f, f.matchHeader, zone, f.bar)
            assertEquals("scale ${f.scale}", f.spareAt814, board - header - 7 * 48f, 0.6f)
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
    @Test
    fun `portrait zone height grows with the body line up to 1_5`() {
        assertEquals(84f, CricketBoardLayoutPolicy.portraitZoneHeightDp(22.5f), 0.01f)
        assertEquals(112.8f, CricketBoardLayoutPolicy.portraitZoneHeightDp(30.2f), 0.1f)
        assertEquals(126f, CricketBoardLayoutPolicy.portraitZoneHeightDp(41.8f), 0.01f)
    }

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
        val portrait = CricketBoardLayoutPolicy.portrait(360f, header + 7 * 48f - 1f, header, 4, 7)
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
}
