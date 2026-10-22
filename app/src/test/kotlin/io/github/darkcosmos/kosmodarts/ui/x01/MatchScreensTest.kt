package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsTags
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The screens of a match (docs/rules/match.md, M13 to M17; U1 to U8 of the design review), on a Pixel 7, driven
 * through the app's ViewModel like the keypad does.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MatchScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun plural(id: Int, count: Int): String =
        composeRule.activity.resources.getQuantityString(id, count, count)

    private fun play(script: MatchScript) = send(*script.events.toTypedArray())

    private fun send(vararg events: AppEvent) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { events.forEach(viewModel::onEvent) }
        composeRule.waitForIdle()
    }

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    // Setup (M13, U4, U5).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M13 setup steppers in English`() {
        send(AppEvent.NewGame)

        tag(SetupTags.label(SetupTags.LEGS)).performScrollTo().assertTextEquals("Legs")
        tag(SetupTags.value(SetupTags.LEGS)).assertContentDescriptionEquals("Legs: first to 1")
        tag(SetupTags.value(SetupTags.SETS)).assertContentDescriptionEquals("Sets: first to 1")
        tag(SetupTags.value(SetupTags.SETS)).assertTextEquals("First to 1")
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.LEGS)).assertIsNotEnabled()
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.SETS)).assertIsNotEnabled()
        composeRule.onNodeWithTag(SetupTags.increase(SetupTags.SETS)).performScrollTo().performClick()

        tag(SetupTags.label(SetupTags.LEGS)).assertTextEquals("Legs per set")
        tag(SetupTags.value(SetupTags.LEGS)).assertContentDescriptionEquals("Legs per set: first to 1")
        tag(SetupTags.value(SetupTags.SETS)).assertContentDescriptionEquals("Sets: first to 2")
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.SETS)).assertIsEnabled()
        repeat(8) { composeRule.onNodeWithTag(SetupTags.increase(SetupTags.LEGS)).performClick() }
        composeRule.onNodeWithTag(SetupTags.increase(SetupTags.LEGS)).assertIsNotEnabled()
        tag(SetupTags.value(SetupTags.LEGS)).assertTextEquals("First to 9")
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.LEGS))
            .assertContentDescriptionEquals("Fewer legs")
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M13 setup steppers in French`() {
        send(AppEvent.NewGame, AppEvent.SetsChanged(1))

        tag(SetupTags.label(SetupTags.LEGS)).performScrollTo().assertTextEquals("Manches par set")
        tag(SetupTags.label(SetupTags.SETS)).assertTextEquals("Sets")
        tag(SetupTags.value(SetupTags.LEGS)).assertTextEquals("Premier à 1")
        tag(SetupTags.value(SetupTags.SETS)).assertContentDescriptionEquals("Sets : premier à 2")
    }

    // Game screen (M5, M14, U1 to U3).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M5 a single game shows no indicator`() {
        play(MatchScript().start("Ann", "Bob"))

        composeRule.onAllNodesWithTag(GameTags.matchIndicators(0), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(GameTags.matchIndicators(1), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `M14 indicators show labelled marks and their description`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 3, sets = 2).legsWonBy(0, 0, 0, 1, 0))

        // Ann: 1 set, 1 leg in set 2. Bob: 0 sets, 1 leg.
        composeRule.onAllNodesWithTag(GameTags.setMark(0), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag(GameTags.legMark(0), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag(GameTags.setMark(1), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(GameTags.legMark(1), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithTag(GameTags.matchIndicators(0))
            .assertContentDescriptionEquals("1 set won, 1 leg won")
        composeRule.onNodeWithTag(GameTags.matchIndicators(1))
            .assertContentDescriptionEquals("0 sets won, 1 leg won")
        nodesWithText("Sets").assertCountEquals(2)
        nodesWithText("Legs").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M14 indicator labels in French, legs only with one set`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 3).legsWonBy(1, 1))

        nodesWithText("Manches").assertCountEquals(2)
        nodesWithText("Sets").assertCountEquals(0)
        composeRule.onAllNodesWithTag(GameTags.legMark(1), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithTag(GameTags.matchIndicators(1)).assertContentDescriptionEquals("2 manches gagnées")
    }

    /** U3: from 5, one mark and "×N". */
    @Test
    @Config(qualifiers = "+en-port")
    fun `M14 from five legs a group shows one mark and the count`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 9).legsWonBy(0, 0, 0, 0, 0))

        composeRule.onAllNodesWithTag(GameTags.legMark(0), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText(string(R.string.match_count_overflow, 5), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(GameTags.matchIndicators(0))
            .assertContentDescriptionEquals(plural(R.plurals.match_legs_won, 5))
    }

    /** U1: 4 players, long names, 200 %: the highlighted card is fully inside the scoreboard after every turn. */
    @Test
    @Config(qualifiers = "+en-port", fontScale = 2.0f)
    fun `U1 the current card is always fully visible`() {
        val names = arrayOf("Alexandrine Martinez", "Bartholomew Jenkins", "Clementine Dubois", "Dimitri Kowalczyk")
        play(MatchScript().start(*names, legsPerSet = 3, sets = 2).legsWonBy(3))
        assertCurrentCardVisible()
        repeat(names.size) {
            send(AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.MissTapped, AppEvent.NextPlayerTapped)
            assertCurrentCardVisible()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertCurrentCardVisible()
    }

    private fun assertCurrentCardVisible() {
        val card = composeRule.onNodeWithTag(GameTags.CURRENT_PLAYER)
        card.assertIsDisplayed()
        val cardBounds = card.getUnclippedBoundsInRoot()
        val viewport = composeRule.onNodeWithTag(GameTags.SCOREBOARD).getUnclippedBoundsInRoot()
        assertTrue(
            "card $cardBounds outside $viewport",
            cardBounds.top >= viewport.top && cardBounds.bottom <= viewport.bottom,
        )
    }

    // Checkout dialog (M15).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M15 dialog labels in English`() = assertDialogLabels("Finish leg", "Finish set", "Finish game")

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M15 dialog labels in French`() =
        assertDialogLabels("Terminer la manche", "Terminer le set", "Terminer la partie")

    private fun assertDialogLabels(leg: String, set: String, match: String) {
        val script = MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 2).playLegWonBy(0)
        play(script)
        composeRule.onNodeWithText(leg).assertIsDisplayed()
        val toSet = script.events.size
        script.finish().nextLeg().playLegWonBy(0)
        send(*script.events.drop(toSet).toTypedArray())
        composeRule.onNodeWithText(set).assertIsDisplayed()
        val toMatch = script.events.size
        script.finish().nextLeg().legsWonBy(0).playLegWonBy(0)
        send(*script.events.drop(toMatch).toTypedArray())
        composeRule.onNodeWithText(match).assertIsDisplayed()
    }

    // Between legs (M16, U7).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M16 between-legs screen in English`() {
        play(MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2).playLegWonBy(2).finish())

        tag(BetweenLegsTags.WINNER).assertTextEquals("Leg won by Cid")
        tag(BetweenLegsTags.SETS_HEADER).assertTextEquals("Sets")
        tag(BetweenLegsTags.LEGS_HEADER).assertTextEquals("Legs")
        tag(BetweenLegsTags.legs(2)).assertTextEquals("1")
        tag(BetweenLegsTags.sets(2)).assertTextEquals("0")
        tag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("Bob starts the next leg")
        composeRule.onNodeWithTag(BetweenLegsTags.NEXT_LEG).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.game_abandon_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M16 between-legs screen in French after a set`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 1, sets = 2).playLegWonBy(1).finish())

        tag(BetweenLegsTags.WINNER).assertTextEquals("Set gagné par Bob")
        tag(BetweenLegsTags.SETS_HEADER).assertTextEquals("Sets")
        tag(BetweenLegsTags.LEGS_HEADER).assertTextEquals("Manches")
        tag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("Bob commence la manche suivante")
        composeRule.onNodeWithText("Manche suivante").assertIsDisplayed()
    }

    /** M16, M14: with one set, no "Sets" column. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `M16 no sets column with one set`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2).playLegWonBy(0).finish())

        composeRule.onAllNodesWithTag(BetweenLegsTags.SETS_HEADER, useUnmergedTree = true).assertCountEquals(0)
    }

    // End screen (M17, U8).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M17 end screen match figures in English`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 2).legsWonBy(0, 0, 1, 0, 0))

        val figures = tag(EndTags.figures(0)).fetchText()
        assertTrue(figures, figures.startsWith("Sets 2 · Legs 4 · 3-dart avg. "))
        assertTrue(figures, figures.endsWith(plural(R.plurals.end_darts_thrown, figureDarts(figures))))
        assertTrue(tag(EndTags.figures(1)).fetchText().startsWith("Sets 0 · Legs 1 · "))
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M17 end screen match figures in French, one set`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2).legsWonBy(1, 1))

        assertTrue(tag(EndTags.figures(0)).fetchText().startsWith("Manches 2 · Moy. 3 fléchettes "))
    }

    private fun SemanticsNodeInteraction.fetchText(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")

    private fun figureDarts(figures: String): Int = figures.substringAfterLast(" · ").substringBefore(' ').toInt()

    private fun nodesWithText(text: String) = composeRule.onAllNodes(hasText(text), useUnmergedTree = true)
}
