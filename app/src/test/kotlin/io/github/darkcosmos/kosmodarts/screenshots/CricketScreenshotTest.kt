package io.github.darkcosmos.kosmodarts.screenshots

import android.graphics.Bitmap
import android.graphics.Color
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
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.throwing
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
    @Config(qualifiers = "fr-w360dp-h640dp-port-xhdpi")
    fun smallPortrait() = capture("board_4p_360x640_fr", FOUR.invoke())

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-land-xhdpi")
    fun smallLandscape() = capture("board_4p_640x360_fr", FOUR.invoke())

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
        capture("cut_throat_portrait_fr", game("Anna", "Bob", darts = "T20 T20 19 0", variant = CricketVariant.CUT_THROAT))

    @Test
    @Config(qualifiers = "+fr-port")
    fun modifierActive() = capture("modifier_triple_portrait_fr", game("Anna", "Bob").toggleMultiplier(Multiplier.TRIPLE))

    @Test
    @Config(qualifiers = "+fr-port")
    fun turnWaiting() =
        capture("turn_waiting_portrait_fr", game("Anna", "Bob", darts = "T20 T19 18").copy(isAwaitingConfirmation = true))

    @Test
    @Config(qualifiers = "+fr-port")
    fun keypadWithBull() {
        show(game("Anna", "Bob"))
        composeRule.onNodeWithTag(CricketTags.KEYPAD).captureRoboImage(path("keypad_with_bull_fr"))
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun keypadWithoutBull() {
        show(game("Anna", "Bob", isBullInPlay = false))
        composeRule.onNodeWithTag(CricketTags.KEYPAD).captureRoboImage(path("keypad_without_bull_fr"))
    }

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

    /** The root as pixels, in gray levels (review aid for color-blind readers). */
    private fun saveGrayscale(file: String) {
        val source = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val gray = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                val pixel = source.getPixel(x, y)
                val level = (LUMA_RED * Color.red(pixel) + LUMA_GREEN * Color.green(pixel) + LUMA_BLUE * Color.blue(pixel)).toInt()
                gray.setPixel(x, y, Color.rgb(level, level, level))
            }
        }
        File(file).also { it.parentFile?.mkdirs() }.outputStream().use { gray.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
