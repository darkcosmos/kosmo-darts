package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.board.BoardGeometry
import io.github.darkcosmos.kosmodarts.engine.board.BoardPoint
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Taps of the dartboard component (HM8, BC4, BC5): the pixel-to-millimetre conversion and the zone given by
 * [BoardGeometry.zoneAt]. On a 410 px square one pixel is one millimetre and the centre is at (205, 205).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DartboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun above(mm: Double) = Offset(CENTRE, (CENTRE - mm).toFloat())

    private fun zoneAt(offset: Offset, size: Float = SIDE) = zoneAtPixel(offset, size)

    @Test
    fun centreIsTheBull() = assertEquals(BoardZone(Dart.BULL_SEGMENT, Ring.BULL), zoneAt(Offset(CENTRE, CENTRE)))

    @Test
    fun nominalPointOfTriple20IsTriple20() = assertEquals(BoardZone(20, Ring.TRIPLE), zoneAt(above(103.0)))

    @Test
    fun outsideTheDoubleRingIsNothing() {
        assertNull(zoneAt(above(170.1)))
        assertNull(zoneAt(Offset(0f, 0f)))
        assertNull(zoneAt(Offset(SIDE, SIDE)))
    }

    @Test
    fun outerEdgeOfTheDoubleRingIsDouble20() = assertEquals(BoardZone(20, Ring.DOUBLE), zoneAt(above(170.0)))

    @Test
    fun justInsideTheDoubleRingIsDouble20() = assertEquals(BoardZone(20, Ring.DOUBLE), zoneAt(above(169.9)))

    @Test
    fun justInsideTheInnerEdgeOfTheDoubleRingIsSingle20() =
        assertEquals(BoardZone(20, Ring.SINGLE), zoneAt(above(161.9)))

    @Test
    fun yGrowsUpwardsAndXToTheRight() {
        assertEquals(20, zoneAt(above(130.0))?.number)
        assertEquals(3, zoneAt(Offset(CENTRE, CENTRE + 130f))?.number)
        assertEquals(6, zoneAt(Offset(CENTRE + 130f, CENTRE))?.number)
        assertEquals(11, zoneAt(Offset(CENTRE - 130f, CENTRE))?.number)
    }

    @Test
    fun aSectorEdgeIsInExactlyTheZoneOfTheEngine() {
        val radius = 130.0
        val angle = Math.toRadians(BoardGeometry.SECTOR_ANGLE_DEGREES / 2)
        val offset = Offset(
            CENTRE + (radius * Math.sin(angle)).toFloat(),
            CENTRE - (radius * Math.cos(angle)).toFloat(),
        )
        val expected = BoardGeometry.zoneAt(boardPointAt(offset, SIDE))
        assertEquals(expected, zoneAt(offset))
        assertTrue(expected?.number == 20 || expected?.number == 1)
    }

    @Test
    fun conversionScalesWithTheSizeOfTheBoard() {
        assertEquals(BoardPoint(0.0, 0.0), boardPointAt(Offset(CENTRE * 2, CENTRE * 2), SIDE * 2))
        assertEquals(BoardPoint(0.0, 103.0), boardPointAt(Offset(CENTRE * 2, (CENTRE - 103f) * 2), SIDE * 2))
        assertEquals(BoardZone(20, Ring.TRIPLE), zoneAt(Offset(CENTRE * 2, (CENTRE - 103f) * 2), SIDE * 2))
    }

    @Test
    fun conversionOfTheCornersAndOfAnEmptySize() {
        assertEquals(BoardPoint(-HALF_SIDE_MM, HALF_SIDE_MM), boardPointAt(Offset(0f, 0f), SIDE))
        assertEquals(BoardPoint(HALF_SIDE_MM, -HALF_SIDE_MM), boardPointAt(Offset(SIDE, SIDE), SIDE))
        assertNull(zoneAt(Offset(0f, 0f), 0f))
    }

    @Test
    fun tappingTheCentreReportsTheBullAndEveryZoneHasATalkBackAction() {
        val tapped = mutableListOf<BoardZone>()
        show {
            Dartboard(
                colors = emptyMap(),
                neutral = Color.Gray,
                selected = null,
                onZoneTapped = { tapped += it },
                contentDescription = "Board",
                zoneDescription = { "${it.number} ${it.ring}" },
            )
        }
        val board = composeRule.onNodeWithTag(DartboardTags.BOARD)
        board.performTouchInput { click(center) }
        assertEquals(listOf(BoardZone(Dart.BULL_SEGMENT, Ring.BULL)), tapped)
        val actions = board.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertEquals(BoardZone.ALL.size, actions?.size)
        actions?.first { it.label == "20 TRIPLE" }?.action?.invoke()
        assertEquals(BoardZone(20, Ring.TRIPLE), tapped.last())
    }

    @Test
    fun theBoardIsASquareOfAtLeast200Dp() {
        show { Dartboard(emptyMap(), Color.Gray, null, {}, "Board", { "" }) }
        val bounds = composeRule.onNodeWithTag(DartboardTags.BOARD).getUnclippedBoundsInRoot()
        assertEquals(bounds.width.value, bounds.height.value, 0.5f)
        assertTrue(bounds.width >= 200.dp)
    }

    /** The activity already has its content: the board goes in a ComposeView, like the screenshot tests. */
    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val SIDE = 410f
        const val CENTRE = 205f
    }
}
