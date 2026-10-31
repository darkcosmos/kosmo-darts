package io.github.darkcosmos.kosmodarts.screenshots

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.annScoring
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonByScoring20
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketBetweenLegsScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketEndScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshots of the Cricket game screen (docs/rules/cricket.md, CR22 to CR25) for visual review, on a Pixel 7 unless
 * the name says otherwise, French unless the name ends in "_en". The state is given directly. Record only: see
 * [HomeScreenScreenshotTest]. The grayscale copy of the dead rows is a review aid; the non-color cue is proven by the
 * strike-node assertions of `CricketScreenTest` (CR23).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CricketScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun show(state: CricketGameScreenState) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { CricketGameScreen(state, onEvent = {}) } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun path(name: String): String =
        "${System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)}/cricket_$name.png"

    private fun capture(name: String, state: CricketGameScreenState) {
        show(state)
        composeRule.onRoot().captureRoboImage(path(name))
    }

    private fun game(
        vararg names: String,
        darts: String = "",
        variant: CricketVariant = CricketVariant.STANDARD,
        isBullInPlay: Boolean = true,
    ): CricketGameScreenState {
        val thrown = darts.split(' ').filter { it.isNotEmpty() }.toTypedArray()
        return CricketGameScreenState(
            CricketSaveFixtures.match(*names, variant = variant, isBullInPlay = isBullInPlay)
                .throwing(*CricketSaveFixtures.darts(*thrown)),
        )
    }

    /** Ann and Bob close 20 and 19, so those two rows are dead. */
    private fun deadRows(vararg names: String): CricketGameScreenState {
        val closing = "T20 T19 0 ".repeat(names.size)
        return game(*names, darts = closing.trim())
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun board2PlayersPortrait() = capture("board_2p_portrait_fr", game("Anna", "Bob", darts = "T20 19 D18"))

    /** Owner review: the zone shows T20, then a miss for a dart that gives no mark (5), as the X01 darts. */
    @Test
    @Config(qualifiers = "+fr-port")
    fun zoneWithMiss() = capture("zone_miss_2p_portrait_fr", game("Anna", "Bob", darts = "T20 5"))

    @Test
    @Config(qualifiers = "+fr-land")
    fun board2PlayersLandscape() = capture("board_2p_landscape_fr", game("Anna", "Bob", darts = "T20 19 D18"))

    @Test
    @Config(qualifiers = "+fr-port")
    fun board4PlayersPortrait() = capture("board_4p_portrait_fr", FOUR.invoke())

    @Test
    @Config(qualifiers = "+fr-land")
    fun board4PlayersLandscape() = capture("board_4p_landscape_fr", FOUR.invoke())

    @Test
    @Config(qualifiers = "+en-port")
    fun board4PlayersPortraitEnglish() = capture("board_4p_portrait_en", FOUR.invoke())

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun board4PlayersLargestFont() = capture("board_4p_portrait_fr_font200", FOUR.invoke())

    @Test
    @Config(qualifiers = "+fr-port")
    fun deadRows2Players() = capture("dead_rows_2p_portrait_fr", deadRows("Anna", "Bob"))

    @Test
    @Config(qualifiers = "+fr-port")
    fun deadRows4Players() {
        val state = deadRows("Anna", "Bob", "Cid", "Dan")
        capture("dead_rows_4p_portrait_fr", state)
        saveGrayscale(path("dead_rows_4p_portrait_fr_gray"))
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun solo() = capture("solo_portrait_fr", game("Anna", darts = "T20 19 D18"))

    @Test
    @Config(qualifiers = "+fr-port")
    fun cutThroat() =
        capture(
            "cut_throat_portrait_fr",
            game("Anna", "Bob", darts = "T20 T20 19 0", variant = CricketVariant.CUT_THROAT),
        )

    @Test
    @Config(qualifiers = "+fr-port")
    fun turnWaiting() =
        capture(
            "turn_waiting_portrait_fr",
            game("Anna", "Bob", darts = "T20 T19 18").copy(isAwaitingConfirmation = true),
        )

    @Test
    @Config(qualifiers = "+fr-port")
    fun buttonsWithBull() {
        show(game("Anna", "Bob"))
        composeRule.onNodeWithTag(CricketTags.BOARD).captureRoboImage(path("buttons_with_bull_fr"))
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun buttonsWithoutBull() {
        show(game("Anna", "Bob", isBullInPlay = false))
        composeRule.onNodeWithTag(CricketTags.BOARD).captureRoboImage(path("buttons_without_bull_fr"))
    }

    /** Ann has 1 020 points: the 4-digit totals in four columns (CR22). */
    @Test
    @Config(qualifiers = "fr-w411dp-h814dp-port-420dpi")
    fun totals4Digits() = capture(
        "totals_4digits_4p_portrait_fr",
        CricketGameScreenState(CricketSaveFixtures.match("Anna", "Bob", "Cid", "Dan").annScoring(6)),
    )

    /** The 20 is dead (Ann and Bob closed it) and Anna waits: the locked, still readable keys (CR15, CR24). */
    @Test
    @Config(qualifiers = "+fr-port")
    fun deadRowLocked() = capture(
        "dead_row_locked_2p_portrait_fr",
        game("Anna", "Bob", darts = "T20 0 0 T20 0 0 T20 0 0").copy(isAwaitingConfirmation = true),
    )

    /** The tightest case: 4 players, "VALIDER LA PARTIE", largest font, the Pixel 7 with its bars (411 x 814). */
    @Test
    @Config(qualifiers = "fr-w411dp-h814dp-port-420dpi", fontScale = 2.0f)
    fun winPending4PlayersLargestFont() = capture(
        "win_pending_4p_portrait_fr_font200",
        CricketGameScreenState(
            CricketSaveFixtures.match("Anna", "Bob", "Cid", "Dan").playLegWonBy(0),
            isAwaitingConfirmation = true,
        ),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun matchWin() = capture(
        "win_match_portrait_fr",
        CricketGameScreenState(CricketSaveFixtures.PENDING_MATCH_WIN.match, isAwaitingConfirmation = true),
    )

    @Test
    @Config(qualifiers = "+fr-port")
    fun winByNonThrower() = capture(
        "win_non_thrower_portrait_fr",
        CricketGameScreenState(CricketSaveFixtures.NON_THROWER_WIN.match, isAwaitingConfirmation = true),
    )

    @Test
    @Config(qualifiers = "+en-port")
    fun matchWinEnglish() = capture(
        "win_match_portrait_en",
        CricketGameScreenState(CricketSaveFixtures.PENDING_MATCH_WIN.match, isAwaitingConfirmation = true),
    )

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(path(name))
    }

    private fun betweenLegs(vararg names: String, legsPerSet: Int = 3, sets: Int = 1): CricketMatch =
        CricketSaveFixtures.match(*names, legsPerSet = legsPerSet, sets = sets).playLegWonByScoring20(0).confirmLeg()

    private fun finished(vararg names: String, legsPerSet: Int = 1, sets: Int = 1): CricketMatch =
        CricketSaveFixtures.match(*names, legsPerSet = legsPerSet, sets = sets).playLegWonByScoring20(0)

    private fun between(name: String, match: CricketMatch) =
        captureScreen(name) { CricketBetweenLegsScreen(match, onEvent = {}) }

    private fun end(name: String, match: CricketMatch) = captureScreen(name) { CricketEndScreen(match, onEvent = {}) }

    @Test
    @Config(qualifiers = "+fr-port")
    fun betweenLegsLegs() = between("between_legs_2p_portrait_fr", betweenLegs("Anna", "Bob"))

    @Test
    @Config(qualifiers = "+fr-port")
    fun betweenLegsSets() =
        between("between_sets_2p_portrait_fr", betweenLegs("Anna", "Bob", legsPerSet = 1, sets = 2))

    @Test
    @Config(qualifiers = "+fr-port")
    fun betweenLegsSolo() = between("between_legs_solo_portrait_fr", betweenLegs("Anna", legsPerSet = 2))

    @Test
    @Config(qualifiers = "+en-port")
    fun betweenLegsEnglish() = between("between_legs_2p_portrait_en", betweenLegs("Anna", "Bob"))

    @Test
    @Config(qualifiers = "+fr-land")
    fun betweenLegs4PlayersLandscape() =
        between("between_legs_4p_landscape_fr", betweenLegs("Anna", "Bob", "Cid", "Dan"))

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
    fun betweenLegs4PlayersSmall() = between("between_legs_4p_360x640_fr", betweenLegs("Anna", "Bob", "Cid", "Dan"))

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun betweenLegs4PlayersLargestFont() =
        between("between_legs_4p_portrait_fr_font200", betweenLegs("Anna", "Bob", "Cid", "Dan"))

    @Test
    @Config(qualifiers = "+fr-port")
    fun endSingleGame() = end("end_single_2p_portrait_fr", finished("Anna", "Bob"))

    @Test
    @Config(qualifiers = "+fr-port")
    fun endLegs() = end("end_legs_3p_portrait_fr", finished("Anna", "Bob", "Cid", legsPerSet = 2))

    @Test
    @Config(qualifiers = "+fr-port")
    fun endSets() = end("end_sets_2p_portrait_fr", finished("Anna", "Bob", legsPerSet = 1, sets = 2))

    @Test
    @Config(qualifiers = "+fr-port")
    fun endSolo() = end("end_solo_portrait_fr", finished("Anna"))

    @Test
    @Config(qualifiers = "+en-port")
    fun endEnglish() = end("end_single_2p_portrait_en", finished("Anna", "Bob"))

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
    fun end4PlayersSmall() = end("end_4p_360x640_fr", finished("Anna", "Bob", "Cid", "Dan"))

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun end4PlayersLargestFont() = end("end_4p_portrait_fr_font200", finished("Anna", "Bob", "Cid", "Dan"))

    /** The root as pixels, in gray levels (review aid for color-blind readers). */
    private fun saveGrayscale(file: String) {
        val source = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val gray = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                val pixel = source.getPixel(x, y)
                val sum = LUMA_RED * Color.red(pixel) + LUMA_GREEN * Color.green(pixel) + LUMA_BLUE * Color.blue(pixel)
                val level = sum.toInt()
                gray.setPixel(x, y, Color.rgb(level, level, level))
            }
        }
        File(file).also { it.parentFile?.mkdirs() }.outputStream().use {
            gray.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LUMA_RED = 0.299
        const val LUMA_GREEN = 0.587
        const val LUMA_BLUE = 0.114

        val FOUR: () -> CricketGameScreenState = {
            CricketGameScreenState(
                CricketSaveFixtures.match("Anna", "Bob", "Cid", "Dan").throwing(
                    *CricketSaveFixtures.darts("T20 19 D18", "T19 T19 0", "D20 17 0", "15 T16 0", "T20"),
                ),
            )
        }
    }
}
