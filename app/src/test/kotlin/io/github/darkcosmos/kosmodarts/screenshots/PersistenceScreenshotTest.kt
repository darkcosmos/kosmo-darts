package io.github.darkcosmos.kosmodarts.screenshots

import io.github.darkcosmos.kosmodarts.testing.savedGame
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import java.util.concurrent.CountDownLatch
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun launchWith(saved: StoredGame) {
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

    /** AR3: the failure message is displayed and its text is laid out whole in the node that holds it (no clipping). */
    private fun assertArchiveMessageNotClipped() {
        val message = composeRule.onNode(hasTestTag(GameTags.ARCHIVE_FAILED), useUnmergedTree = true)
        message.assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        message.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        assertFalse("text overflows its height", layout.didOverflowHeight)
        assertTrue(
            "node ${message.fetchSemanticsNode().size.height} px < text ${layout.size.height} px",
            message.fetchSemanticsNode().size.height >= layout.size.height,
        )
    }

    /** AR3: the confirmed win while the archive is held ("Saving…") or after it failed (the message). */
    private fun confirmWin(saved: StoredGame, name: String, isFailure: Boolean) {
        val gate = CountDownLatch(1)
        if (isFailure) process.archive.archiveAnswer = DbResult.Failed else process.archive.gate = gate
        try {
            launchWith(saved)
            composeRule.onNodeWithTag(HomeTags.RESUME).performScrollTo().performClick()
            composeRule.waitForIdle()
            val confirm = composeRule.onNode(hasTestTag(GameTags.CONFIRM), useUnmergedTree = true)
            // The X01 screen scrolls below the score floor; the Cricket screen never does.
            runCatching { confirm.performScrollTo() }
            confirm.performClick()
            if (isFailure) process.settle() else composeRule.waitForIdle()
            if (isFailure) assertArchiveMessageNotClipped()
            capture(name)
        } finally {
            gate.countDown()
        }
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun x01ArchivingPortraitEnglish() = confirmWin(MATCH_WIN, "archive_x01_saving_portrait_en", isFailure = false)

    @Test
    @Config(qualifiers = "+fr-land")
    fun x01ArchivingLandscapeFrench() = confirmWin(MATCH_WIN, "archive_x01_saving_landscape_fr", isFailure = false)

    @Test
    @Config(qualifiers = "+en-port")
    fun x01FailedPortraitEnglish() = confirmWin(MATCH_WIN, "archive_x01_failed_portrait_en", isFailure = true)

    @Test
    @Config(qualifiers = "+fr-land")
    fun x01FailedLandscapeFrench() = confirmWin(MATCH_WIN, "archive_x01_failed_landscape_fr", isFailure = true)

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun x01FailedPortraitEnglishLargestFont() =
        confirmWin(MATCH_WIN, "archive_x01_failed_portrait_en_font200", isFailure = true)

    @Test
    @Config(qualifiers = "+en-port")
    fun cricketArchivingPortraitEnglish() =
        confirmWin(CricketSaveFixtures.PENDING_MATCH_WIN, "archive_cricket_saving_portrait_en", isFailure = false)

    @Test
    @Config(qualifiers = "+en-land")
    fun cricketArchivingLandscapeEnglish() =
        confirmWin(CricketSaveFixtures.PENDING_MATCH_WIN, "archive_cricket_saving_landscape_en", isFailure = false)

    @Test
    @Config(qualifiers = "+fr-port")
    fun cricketFailedPortraitFrench() =
        confirmWin(CricketSaveFixtures.PENDING_MATCH_WIN, "archive_cricket_failed_portrait_fr", isFailure = true)

    @Test
    @Config(qualifiers = "+en-land")
    fun cricketFailedLandscapeEnglish() =
        confirmWin(CricketSaveFixtures.PENDING_MATCH_WIN, "archive_cricket_failed_landscape_en", isFailure = true)

    @Test
    @Config(qualifiers = "+en-port", fontScale = LARGEST_FONT_SCALE)
    fun cricketFailedPortraitEnglishLargestFont() =
        confirmWin(CricketSaveFixtures.PENDING_MATCH_WIN, "archive_cricket_failed_portrait_en_font200", true)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun x01FailedLandscapeFrenchLargestFont() =
        confirmWin(MATCH_WIN, "archive_x01_failed_landscape_fr_font200", isFailure = true)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = LARGEST_FONT_SCALE)
    fun cricketFailedLandscapeFrenchLargestFont() =
        confirmWin(CricketSaveFixtures.PENDING_MATCH_WIN, "archive_cricket_failed_landscape_fr_font200", true)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
        const val LARGEST_FONT_SCALE = 2.0f

        /** A single game whose winning dart waits for "Confirm match". */
        val MATCH_WIN: SavedGame = SaveFixtures.WAITING_CHECKOUT

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
