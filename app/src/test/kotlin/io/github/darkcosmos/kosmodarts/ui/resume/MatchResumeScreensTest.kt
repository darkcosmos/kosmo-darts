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
        tag(BetweenLegsTags.TITLE).assertTextEquals("SET 1 · LEG 1 WON")
        tag(BetweenLegsTags.WINNER).assertTextEquals("BOB")
        tag(BetweenLegsTags.CHECKOUT).assertExists()

        process.killInBackgroundAndReturn()
        composeRule.waitForIdle()

        tag(BetweenLegsTags.TITLE).assertTextEquals("SET 1 · LEG 1 WON")
        tag(BetweenLegsTags.WINNER).assertTextEquals("BOB")
        // Known limitation: the winning visit is not saved (ADR 0005 keeps leg summaries only).
        tag(BetweenLegsTags.CHECKOUT).assertDoesNotExist()
        composeRule.onNodeWithTag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("SET 1 · LEG 2", "BOB STARTS")
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

        composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER).assert(hasAnyDescendant(hasText("Bob", ignoreCase = true)))
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
        tag(HomeTags.matchScore(0)).assertTextEquals("S1 · L0")
        tag(HomeTags.matchScore(2)).assertTextEquals("S0 · L1")
        composeRule.onNodeWithTag(HomeTags.RESUME).assertTextEquals(string(R.string.home_resume, "ANN"))

        composeRule.onNodeWithTag(HomeTags.RESUME).performScrollTo().performClick()
        composeRule.onNodeWithTag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("SET 2 · LEG 2", "ANN STARTS")
    }

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun `M18 the home summary in French at 200 percent`() {
        val saved = SavedGame(match("Ann", "Bob", legsPerSet = 3).confirmLegsWonBy(1).throwing(Dart.MISS), false)
        process.writeGame(saved)
        process.launchFresh()

        tag(HomeTags.matchScore(1)).performScrollTo().assertTextEquals("M1")
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
        val viewport = composeRule.onNodeWithTag(GameTags.SCREEN).getUnclippedBoundsInRoot()
        assertTrue("$bounds outside $viewport", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
    }
}
