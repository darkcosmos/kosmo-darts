package io.github.darkcosmos.kosmodarts.ui.resume

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.V1Fixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsTags
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A match survives process death and a fresh launch (docs/rules/persistence.md, R14, R16; docs/rules/match.md, M18),
 * through [io.github.darkcosmos.kosmodarts.MainActivity] and the real files of the app.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MatchResumeScreensTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()

    @After
    fun tearDown() = process.close()

    private fun string(id: Int, vararg args: Any): String = process.string(id, *args)

    private fun send(events: List<AppEvent>) {
        val viewModel = ViewModelProvider(process.activity)[KosmoDartsViewModel::class.java]
        process.activity.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    /** R14 (owner Q6 ii): killed on the between-legs screen, the app comes back to it; back asks to abandon. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `R14 a kill on the between-legs screen comes back to it`() {
        process.launchFresh()
        send(MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 2).playLegWonBy(1).finish().events)
        tag(BetweenLegsTags.WINNER).assertTextEquals("Leg won by Bob")

        process.killInBackgroundAndReturn()
        composeRule.waitForIdle()

        tag(BetweenLegsTags.WINNER).assertTextEquals("Leg won by Bob")
        tag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("Bob starts the next leg")
        process.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.game_abandon_title)).assertIsDisplayed()
    }

    /** R14: killed in the middle of the second leg, the leg comes back with its starter and the match score. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `R14 a kill in the middle of a leg comes back with the match`() {
        process.launchFresh()
        send(MatchScript().start("Ann", "Bob", legsPerSet = 3).legsWonBy(0).then(AppEvent.MissTapped).events)

        process.killInBackgroundAndReturn()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER).assert(hasAnyDescendant(hasText("Bob")))
        composeRule.onNodeWithTag(GameTags.matchIndicators(0))
            .assert(hasContentDescription("1 leg won"))
    }

    /** M18: a fresh launch shows the match score in the summary, then the starter of the next leg between legs. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `M18 the home summary shows the match score`() {
        val between = match("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2).confirmLegsWonBy(0, 0, 2)
        process.writeGame(SavedGame(between, isAwaitingNextPlayer = false, isBetweenLegs = true))
        process.launchFresh()

        composeRule.onNodeWithTag(HomeTags.SUMMARY).performScrollTo().assertIsDisplayed()
        tag(HomeTags.matchScore(0)).assertTextEquals("Sets 1 · Legs 0")
        tag(HomeTags.matchScore(2)).assertTextEquals("Sets 0 · Legs 1")
        composeRule.onNodeWithText(string(R.string.game_to_throw, "Ann")).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.home_resume_game)).performScrollTo().performClick()
        tag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("Ann starts the next leg")
    }

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun `M18 the home summary in French at 200 percent`() {
        val saved = SavedGame(match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(1).throwing(Dart.MISS), false)
        process.writeGame(saved)
        process.launchFresh()

        tag(HomeTags.matchScore(1)).performScrollTo().assertTextEquals("Manches 1")
    }

    /** R16: a game saved by the previous version resumes as a single game, without any message. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `R16 a version 1 save resumes as a single game`() {
        process.writeRawGame(V1Fixtures.bytes("game-mid-turn.save"))
        process.launchFresh()

        composeRule.onNodeWithTag(HomeTags.SUMMARY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.restore_failed_message)).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTags.matchScore(0), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.home_resume_game)).performScrollTo().performClick()
        composeRule.onNodeWithTag(GameTags.matchIndicators(0), useUnmergedTree = true).assertDoesNotExist()
    }

    /** U1: after a return from process death, the highlighted card is fully visible (4 long names, 200 %). */
    @Test
    @Config(qualifiers = "+en-port", fontScale = 2.0f)
    fun `U1 the current card is visible after a resume`() {
        val names = arrayOf("Alexandrine Martinez", "Bartholomew Jenkins", "Clementine Dubois", "Dimitri Kowalczyk")
        process.launchFresh()
        send(MatchScript().start(*names, legsPerSet = 3, sets = 2).legsWonBy(2).events)

        process.killInBackgroundAndReturn()
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER)
        card.assertIsDisplayed()
        val bounds = card.getUnclippedBoundsInRoot()
        val viewport = composeRule.onNodeWithTag(GameTags.SCOREBOARD).getUnclippedBoundsInRoot()
        assertTrue("$bounds outside $viewport", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
    }
}
