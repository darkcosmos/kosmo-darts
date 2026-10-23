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
     * The mockup frame of a Pixel 7 (412 × 915 dp) at font scale 1.0 reaches no step: the mockup values hold, for 2 to
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

    /** Landscape Pixel 7 (915 × 412 dp) at font scale 1.0: no step, score 160. */
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
    fun `360 x 640 portrait applies the steps in order`() {
        val layouts = (1..4).map { portrait(360f, 640f, players = it) } + portrait(360f, 640f, 2f, 4)
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
        val smallest = portrait(360f, 640f, 2f, 4)
        assertTrue(smallest.isScrolling)
        assertEquals(4, smallest.step)
        assertEquals(GameLayoutPolicy.SCORE_FLOOR_PORTRAIT, smallest.scoreSizeDp)
    }

    /**
     * Steps in order on a short landscape screen: the score only shrinks once keys, action bar and margins are at their
     * minimum (steps 1 and 2 before 3); the left column width never depends on the step.
     */
    @Test
    fun `640 x 360 landscape applies the steps in order`() {
        val layouts = (2..4).flatMap { players -> listOf(1f, 1.5f).map { portrait(640f, 360f, it, players) } }
        layouts.forEach { layout ->
            if (layout.scoreSizeDp < GameLayoutPolicy.SCORE_LANDSCAPE) {
                assertEquals(GameLayoutPolicy.KEY_HEIGHT_MIN, layout.keyHeightDp)
                assertEquals(GameLayoutPolicy.ACTION_BAR_HEIGHT_MIN, layout.actionBarMinHeightDp)
                assertEquals(GameLayoutPolicy.MARGIN_MIN, layout.verticalMarginDp)
                assertEquals(GameLayoutPolicy.GAP_MIN, layout.gapDp)
                assertTrue(layout.step >= 3)
            }
            assertTrue(layout.scoreSizeDp >= GameLayoutPolicy.SCORE_FLOOR_LANDSCAPE)
            assertEquals(640f * 0.55f, layout.leftColumnWidthDp)
        }
        // At least one of these short screens reaches the score step, so the check above is not empty.
        assertTrue(layouts.any { it.scoreSizeDp < GameLayoutPolicy.SCORE_LANDSCAPE })
    }

    /**
     * U10: while a checkout waits the zone needs no score and no route, so its least height is lower, and nothing else
     * changes (the keypad keeps its size when the banner appears).
     */
    @Test
    fun `a waiting checkout lowers only the zone minimum`() {
        listOf(PIXEL7_WIDTH to PIXEL7_HEIGHT, PIXEL7_HEIGHT to PIXEL7_WIDTH, 360f to 640f).forEach { (width, height) ->
            listOf(1f, 1.5f).forEach { scale ->
                val normal = GameLayoutPolicy.of(GameSpace(width, height, scale, 4, isMatch = true))
                val checkout = GameLayoutPolicy.of(GameSpace(width, height, scale, 4, isMatch = true, true))
                val what = "$width x $height at $scale"
                assertTrue(what, checkout.zoneMinHeightDp < normal.zoneMinHeightDp)
                assertEquals(what, normal, checkout.copy(zoneMinHeightDp = normal.zoneMinHeightDp))
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
        val layout = portrait(360f, 640f, 2f, 4)

        assertTrue(layout.zoneMinHeightDp > GameLayoutPolicy.SCORE_FLOOR_PORTRAIT)
    }

    private companion object {
        const val PIXEL7_WIDTH = 412f
        const val PIXEL7_HEIGHT = 915f
    }
}
