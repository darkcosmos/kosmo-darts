package io.github.darkcosmos.kosmodarts.screenshots

import io.github.darkcosmos.kosmodarts.testing.savedGame
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the screens added for the saved game (docs/rules/persistence.md), on a Pixel 7. Each state starts
 * from files written before a fresh launch, like after a force stop. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PersistenceScreenshotTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    @After
    fun tearDown() = process.close()

    /** Whole screen, dialog windows included. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        captureScreenRoboImage("$directory/$name.png")
    }

    private fun launchWith(saved: SavedGame) {
        process.writeGame(saved)
        process.launchFresh()
    }

    private fun click(id: Int) = composeRule.onNodeWithText(process.string(id)).performScrollTo().performClick()

    private fun homeThreePlayers(name: String) {
        launchWith(THREE_PLAYERS)
        capture(name)
    }

    private fun homeFourLongNames(name: String) {
        launchWith(FOUR_LONG_NAMES)
        capture(name)
    }

    private fun homeCheckout(name: String) {
        launchWith(SaveFixtures.WAITING_CHECKOUT)
        capture(name)
    }

    private fun newGameDialog(name: String) {
        launchWith(THREE_PLAYERS)
        click(R.string.home_new_game)
        capture(name)
    }

    private fun restoreFailed(name: String) {
        process.writeRawGame("not a save".toByteArray())
        process.writeSetup(X01Config(listOf("Ann", "Bob", "Cid")))
        process.launchFresh()
        capture(name)
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun homeResumePortraitEnglish() = homeThreePlayers("home_resume_3p_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun homeResumeLandscapeEnglish() = homeThreePlayers("home_resume_3p_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeResumePortraitFrench() = homeThreePlayers("home_resume_3p_portrait_fr")

    @Test
    @Config(qualifiers = "+en-land")
    fun homeResumeLongNamesLandscapeEnglish() = homeFourLongNames("home_resume_4p_long_names_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeResumeLongNamesPortraitFrench() = homeFourLongNames("home_resume_4p_long_names_portrait_fr")

    /** 2.0 is the largest font size offered by Android 14 and later: the top, then scrolled to "Resume game". */
    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun homeResumePortraitEnglishLargestFont() {
        launchWith(FOUR_LONG_NAMES)
        capture("home_resume_4p_portrait_en_font200_top")
        composeRule.onNodeWithTag(HomeTags.RESUME).performScrollTo()
        capture("home_resume_4p_portrait_en_font200_scrolled")
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun homeCheckoutPortraitEnglish() = homeCheckout("home_resume_pending_checkout_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun homeCheckoutLandscapeEnglish() = homeCheckout("home_resume_pending_checkout_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun homeCheckoutPortraitFrench() = homeCheckout("home_resume_pending_checkout_portrait_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun newGameDialogPortraitEnglish() = newGameDialog("home_new_game_dialog_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun newGameDialogLandscapeEnglish() = newGameDialog("home_new_game_dialog_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun newGameDialogPortraitFrench() = newGameDialog("home_new_game_dialog_portrait_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun restoreFailedPortraitEnglish() = restoreFailed("setup_restore_failed_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun restoreFailedLandscapeEnglish() = restoreFailed("setup_restore_failed_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun restoreFailedPortraitFrench() = restoreFailed("setup_restore_failed_portrait_fr")

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f

        /** 501, three players: Ann and Bob have played a turn, Cid has thrown one dart. */
        val THREE_PLAYERS: SavedGame = SaveFixtures.savedAfter(
            X01Config(listOf("Ann", "Bob", "Cid")),
            listOf(Dart.triple(20), Dart.triple(20), Dart.single(20), Dart.single(20), Dart.single(5), Dart.single(1)) +
                Dart.triple(19),
        )

        /** Four players, three of them with names of 20 characters. */
        val FOUR_LONG_NAMES: SavedGame = savedGame(
            X01Game(
                X01Config(listOf("Maximilien-Alexandre", "Bartholomew Jonathan", "Anne-Charlotte Marie", "Dan")),
                listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20)),
            ),
            isAwaitingNextPlayer = false,
        )
    }
}
