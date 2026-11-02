package io.github.darkcosmos.kosmodarts.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The layout policy of the game screen (plan of issue #49, section 4b; U1, U3). Pure JVM. */
class GameLayoutPolicyTest {

    private fun portrait(width: Float, height: Float, scale: Float = 1f, players: Int = 3, isMatch: Boolean = true) =
        GameLayoutPolicy.of(GameSpace(width, height, scale, players, isMatch))

    /**
     * The mockup frame of a Pixel 7 (411 × 914 dp) at font scale 1.0 reaches no step: the mockup values hold, for 2 to
     * 4 players, in a match.
     */
    @Test
    fun `Pixel 7 mockup frame at font scale 1 reaches no step`() {
        (2..4).forEach { players ->
            val layout = portrait(PIXEL7_WIDTH, PIXEL7_HEIGHT, players = players)
            assertEquals("$players players", 0, layout.step)
            assertEquals(GameLayoutPolicy.KEY_HEIGHT, layout.keyHeightDp)
            assertEquals(GameLayoutPolicy.ACTION_BAR_HEIGHT, layout.actionBarMinHeightDp)
            val expected =
                if (players == 4) GameLayoutPolicy.SCORE_PORTRAIT_FOUR_PLAYERS else GameLayoutPolicy.SCORE_PORTRAIT
            assertEquals(expected, layout.scoreSizeDp)
            assertFalse(layout.isScrolling)
        }
    }

    /** Landscape Pixel 7 (914 × 411 dp) at font scale 1.0: no step, score 160. */
    @Test
    fun `Pixel 7 landscape at font scale 1 reaches no step`() {
        val layout = portrait(PIXEL7_HEIGHT, PIXEL7_WIDTH)

        assertEquals(0, layout.step)
        assertEquals(GameLayoutPolicy.SCORE_LANDSCAPE, layout.scoreSizeDp)
        assertEquals(GameLayoutPolicy.LEFT_COLUMN_WIDTH, layout.leftColumnWidthDp)
    }

    /**
     * The Pixel 7 frame at an effective text scale of 1.42 (a 24 sp text drawn 1.42 times larger: the U10
     * acceptance criterion; Compose scales large text non-linearly, the largest setting 2.0 gives 1.5, see
     * GameScreenFitTest) keeps every part on screen without scrolling, 4 players included.
     */
    @Test
    fun `Pixel 7 at effective text scale 1_42 does not scroll`() {
        val scale = 1.42f
        assertFalse("portrait", portrait(PIXEL7_WIDTH, PIXEL7_HEIGHT, scale, players = 4).isScrolling)
        assertFalse("landscape", portrait(PIXEL7_HEIGHT, PIXEL7_WIDTH, scale, players = 4).isScrolling)
    }

    /** Steps in order on a short portrait screen: keys and action bar to 48 before the score shrinks. */
    @Test
    fun `411 x 814 portrait applies the steps in order`() {
        val layouts = (1..4).map { portrait(INSIDE_BARS_WIDTH, INSIDE_BARS_HEIGHT, players = it) } +
            portrait(INSIDE_BARS_WIDTH, INSIDE_BARS_HEIGHT, 2f, 4)
        layouts.forEach { layout ->
            if (layout.scoreSizeDp < GameLayoutPolicy.SCORE_PORTRAIT_FOUR_PLAYERS) {
                // The score only shrinks once keys and action bar are at their minimum (steps 1 and 2 before 3).
                assertEquals(GameLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
                assertEquals(GameLayoutPolicy.ACTION_BAR_HEIGHT_MIN, layout.actionBarMinHeightDp)
                assertEquals(GameLayoutPolicy.MARGIN_MIN, layout.verticalMarginDp)
                assertTrue(layout.step >= 3)
            }
            assertTrue(layout.scoreSizeDp >= GameLayoutPolicy.SCORE_FLOOR_PORTRAIT)
        }
        // At 4 players and the largest font size, below the floor: the screen scrolls.
        val smallest = portrait(INSIDE_BARS_WIDTH, INSIDE_BARS_HEIGHT, 2f, 4)
        assertTrue(smallest.isScrolling)
        assertEquals(4, smallest.step)
        assertEquals(GameLayoutPolicy.SCORE_FLOOR_PORTRAIT, smallest.scoreSizeDp)
    }

    /**
     * Steps in order on a short landscape screen: the score only shrinks once keys, action bar and margins are at their
     * minimum (steps 1 and 2 before 3); the left column width never depends on the step.
     */
    @Test
    fun `800 x 360 landscape applies the steps in order`() {
        val layouts = (2..4).flatMap { players ->
            listOf(1f, 1.5f).map { portrait(OWNER_LANDSCAPE_WIDTH, OWNER_LANDSCAPE_HEIGHT, it, players) }
        }
        layouts.forEach { layout ->
            if (layout.scoreSizeDp < GameLayoutPolicy.SCORE_LANDSCAPE) {
                assertEquals(GameLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
                assertEquals(GameLayoutPolicy.ACTION_BAR_HEIGHT_MIN, layout.actionBarMinHeightDp)
                assertEquals(GameLayoutPolicy.MARGIN_MIN, layout.verticalMarginDp)
                assertEquals(GameLayoutPolicy.GAP_MIN, layout.gapDp)
                assertTrue(layout.step >= 3)
            }
            assertTrue(layout.scoreSizeDp >= GameLayoutPolicy.SCORE_FLOOR_LANDSCAPE)
            assertEquals(OWNER_LANDSCAPE_WIDTH * 0.55f, layout.leftColumnWidthDp)
        }
        // At least one of these short screens reaches the score step, so the check above is not empty.
        assertTrue(layouts.any { it.scoreSizeDp < GameLayoutPolicy.SCORE_LANDSCAPE })
    }

    /**
     * U10: while a checkout waits the zone needs no score and no route, so its least height never rises: it is lower,
     * except in landscape at large text sizes where it equals the one-line route zone (owner review of PR #57); nothing
     * else changes (the keypad keeps its size when the banner appears).
     */
    @Test
    fun `a waiting checkout never raises the zone minimum and changes nothing else`() {
        listOf(PIXEL7_WIDTH to PIXEL7_HEIGHT, PIXEL7_HEIGHT to PIXEL7_WIDTH, 411f to 814f).forEach { (width, height) ->
            listOf(1f, 1.5f).forEach { scale ->
                val normal = GameLayoutPolicy.of(GameSpace(width, height, scale, 4, isMatch = true))
                val checkout = GameLayoutPolicy.of(GameSpace(width, height, scale, 4, isMatch = true, true))
                val what = "$width x $height at $scale"
                if (width > height && scale > 1f) {
                    // Landscape, large text: the one-line route keeps the score zone so low (owner review of PR #57)
                    // that the checkout zone is capped at its height, its banner shrinking to fit.
                    assertEquals(what, normal.zoneMinHeightDp, checkout.zoneMinHeightDp)
                } else {
                    assertTrue(what, checkout.zoneMinHeightDp < normal.zoneMinHeightDp)
                }
                assertEquals(what, normal, checkout.copy(zoneMinHeightDp = normal.zoneMinHeightDp))
            }
        }
    }

    /**
     * Issue #69, M15, U10: whatever the font scale, the zone's least height while a checkout waits holds the frame at
     * font scale 1.0, the CHECKOUT chip at its least height, the gap and one checkout line at font scale 1.0, so the
     * reduced text scale of the zone (never below 1.0) always finds the room it needs.
     */
    @Test
    fun `a waiting checkout zone always has room for the banner at its least height`() {
        val floor = ZONE_FRAME_AT_1 + GameLayoutPolicy.CHECKOUT_BANNER_MIN + ZONE_GAP + CHECKOUT_LINE_AT_1
        listOf(
            PIXEL7_WIDTH to PIXEL7_HEIGHT, PIXEL7_HEIGHT to PIXEL7_WIDTH,
            411f to 814f, OWNER_LANDSCAPE_WIDTH to OWNER_LANDSCAPE_HEIGHT,
        ).forEach { (width, height) ->
            listOf(1f, 1.3f, 1.5f, 2f).forEach { scale ->
                (2..4).forEach { players ->
                    listOf(true, false).forEach { isMatch ->
                        val layout = GameLayoutPolicy.of(GameSpace(width, height, scale, players, isMatch, true))
                        assertTrue(
                            "$width x $height at $scale, $players players: ${layout.zoneMinHeightDp} < $floor",
                            layout.zoneMinHeightDp >= floor,
                        )
                    }
                }
            }
        }
    }

    /** Touch targets: keys and action bar never below 48 dp, whatever the space. */
    @Test
    fun `keys and action bar are never below 48 dp`() {
        listOf(200f, 360f, 640f, 915f).forEach { height ->
            listOf(1f, 1.5f, 2f).forEach { scale ->
                listOf(true, false).forEach { landscape ->
                    val layout = if (landscape) portrait(height * 2, height, scale) else portrait(360f, height, scale)
                    assertTrue(layout.keyHeightDp >= 48f)
                    assertTrue(layout.actionBarMinHeightDp >= 48f)
                }
            }
        }
    }

    /** U3, owner Q9: tiles per player count and orientation. */
    @Test
    fun `tile variants per player count and orientation`() {
        assertEquals(TileVariant.WIDE, portrait(PIXEL7_WIDTH, PIXEL7_HEIGHT, players = 3).tileVariant)
        assertEquals(2, portrait(PIXEL7_WIDTH, PIXEL7_HEIGHT, players = 3).tilesPerRow)
        assertEquals(TileVariant.NARROW, portrait(PIXEL7_WIDTH, PIXEL7_HEIGHT, players = 4).tileVariant)
        assertEquals(3, portrait(PIXEL7_WIDTH, PIXEL7_HEIGHT, players = 4).tilesPerRow)
        assertEquals(TileVariant.COMPACT, portrait(PIXEL7_HEIGHT, PIXEL7_WIDTH, players = 3).tileVariant)
        val landscapeFour = portrait(PIXEL7_HEIGHT, PIXEL7_WIDTH, players = 4)
        assertEquals(TileVariant.COMPACT_TWO_LINES, landscapeFour.tileVariant)
        assertEquals(3, landscapeFour.tilesPerRow)
    }

    /** The zone never gets less than its fixed parts with the score at its floor. */
    @Test
    fun `zone minimum covers the floor`() {
        val layout = portrait(INSIDE_BARS_WIDTH, INSIDE_BARS_HEIGHT, 2f, 4)

        assertTrue(layout.zoneMinHeightDp > GameLayoutPolicy.SCORE_FLOOR_PORTRAIT)
    }

    /**
     * Owner review of PR #57: a Pixel 7 in landscape inside its bars and cutout (about 800 × 360 dp), in a match at
     * font scale 1, does not scroll for 2 to 4 players, and the one-line route keeps the zone's least height at its
     * frame plus the score floor (the old two-line route needed more).
     */
    @Test
    fun `Pixel 7 landscape inside its bars fits without scrolling`() {
        (2..4).forEach { players ->
            val layout = portrait(OWNER_LANDSCAPE_WIDTH, OWNER_LANDSCAPE_HEIGHT, players = players)
            assertFalse("$players players", layout.isScrolling)
            assertTrue("$players players", layout.scoreSizeDp >= GameLayoutPolicy.SCORE_FLOOR_LANDSCAPE)
            assertTrue("$players players: ${layout.zoneMinHeightDp}", layout.zoneMinHeightDp < ONE_LINE_ZONE_MIN)
        }
    }

    private companion object {
        const val INSIDE_BARS_WIDTH = 411f
        const val INSIDE_BARS_HEIGHT = 814f
const val OWNER_LANDSCAPE_WIDTH = 800f
        const val OWNER_LANDSCAPE_HEIGHT = 360f

        /** Zone frame (120) with its margin (× 1.1), plus the landscape score floor (72), plus rounding. */
        const val ONE_LINE_ZONE_MIN = 205f
        /** Padding (2 x 12), name line (34), two gaps (2 x 8) and the dart slots (46) at font scale 1.0. */
        const val ZONE_FRAME_AT_1 = 120f
        const val ZONE_GAP = 8f
        const val CHECKOUT_LINE_AT_1 = 22f
        const val PIXEL7_WIDTH = 411f
        const val PIXEL7_HEIGHT = 914f
    }
}
