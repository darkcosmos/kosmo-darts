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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.MatchScript
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsTags
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The screens of a match (docs/rules/match.md, M13 to M19, as amended by issue #49), on a Pixel 7, driven through the
 * app's ViewModel like the keypad does.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MatchScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val viewModel: KosmoDartsViewModel
        get() = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun play(script: MatchScript) = send(*script.events.toTypedArray())

    private fun send(vararg events: AppEvent) {
        val model = viewModel
        composeRule.runOnUiThread { events.forEach(model::onEvent) }
        composeRule.waitForIdle()
    }

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    // Setup (M13 as amended by D5: "Sets · first to" then "Legs · first to", in every format).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M13 setup steppers in English`() {
        send(AppEvent.NewGame(GameMode.X01))

        tag(SetupTags.label(SetupTags.SETS)).performScrollTo().assertTextEquals("Sets · first to")
        tag(SetupTags.label(SetupTags.LEGS)).assertTextEquals("Legs · first to")
        tag(SetupTags.value(SetupTags.LEGS)).assertContentDescriptionEquals("Legs: first to 1")
        tag(SetupTags.value(SetupTags.SETS)).assertContentDescriptionEquals("Sets: first to 1")
        tag(SetupTags.value(SetupTags.SETS)).assertTextEquals("1")
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.LEGS)).assertIsNotEnabled()
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.SETS)).assertIsNotEnabled()
        composeRule.onNodeWithTag(SetupTags.increase(SetupTags.SETS)).performScrollTo().performClick()

        tag(SetupTags.label(SetupTags.LEGS)).assertTextEquals("Legs · first to")
        tag(SetupTags.value(SetupTags.SETS)).assertContentDescriptionEquals("Sets: first to 2")
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.SETS)).assertIsEnabled()
        repeat(8) { composeRule.onNodeWithTag(SetupTags.increase(SetupTags.LEGS)).performClick() }
        composeRule.onNodeWithTag(SetupTags.increase(SetupTags.LEGS)).assertIsNotEnabled()
        tag(SetupTags.value(SetupTags.LEGS)).assertTextEquals("9")
        composeRule.onNodeWithTag(SetupTags.decrease(SetupTags.LEGS))
            .assertContentDescriptionEquals("Fewer legs")
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M13 setup steppers in French`() {
        send(AppEvent.NewGame(GameMode.X01), AppEvent.SetsChanged(1))

        tag(SetupTags.label(SetupTags.SETS)).performScrollTo().assertTextEquals("Sets · premier à")
        tag(SetupTags.label(SetupTags.LEGS)).assertTextEquals("Manches · premier à")
        tag(SetupTags.value(SetupTags.LEGS)).assertTextEquals("1")
        tag(SetupTags.value(SetupTags.SETS)).assertContentDescriptionEquals("Sets : premier à 2")
    }

    /** D3: the − and + of the steppers are 48 dp touch targets (drawn 44 dp). */
    @Test
    @Config(qualifiers = "+en-port")
    fun `D3 stepper buttons are 48 dp touch targets`() {
        send(AppEvent.NewGame(GameMode.X01))
        listOf(SetupTags.LEGS, SetupTags.SETS).forEach { prefix ->
            listOf(SetupTags.decrease(prefix), SetupTags.increase(prefix)).forEach { button ->
                val bounds = composeRule.onNodeWithTag(button).performScrollTo().getUnclippedBoundsInRoot()
                assertTrue("$button $bounds", bounds.right - bounds.left >= MIN_TOUCH)
                assertTrue("$button $bounds", bounds.bottom - bounds.top >= MIN_TOUCH)
            }
        }
    }

    // Game screen (M5, M14, M19).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M5 a single game shows no indicator and no position`() {
        play(MatchScript().start("Ann", "Bob"))

        composeRule.onAllNodesWithTag(GameTags.matchIndicators(0), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(GameTags.matchIndicators(1), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(GameTags.HEADER_POSITION, useUnmergedTree = true).assertCountEquals(0)
    }

    /** M14: the zone's chips "S1" "L1" and the tile line "S0 · L1", each with its TalkBack description. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `M14 chips and tile line with their descriptions`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 3, sets = 2).legsWonBy(0, 0, 0, 1, 0))

        // Leg 3 of set 2: Bob starts (legs 0 to 4 played). Ann: 1 set, 1 leg in set 2. Bob: 0 sets, 1 leg.
        composeRule.onNodeWithTag(GameTags.matchIndicators(1)).assertContentDescriptionEquals("0 sets won, 1 leg won")
        composeRule.onNodeWithTag(GameTags.matchIndicators(0)).assertContentDescriptionEquals("1 set won, 1 leg won")
        tag(GameTags.matchIndicators(0)).assertTextEquals("S1 · L1")
        composeRule.onNodeWithTag(GameTags.matchIndicators(1)).assertTextEquals("S0", "L1")
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M14 legs only in French`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 3).legsWonBy(1, 1))

        // Leg 3: Ann throws, Bob is on the tile.
        composeRule.onNodeWithTag(GameTags.matchIndicators(0)).assertTextEquals("M0")
        composeRule.onNodeWithTag(GameTags.matchIndicators(0)).assertContentDescriptionEquals("0 manche gagnée")
        tag(GameTags.matchIndicators(1)).assertTextEquals("M2")
        composeRule.onNodeWithTag(GameTags.matchIndicators(1)).assertContentDescriptionEquals("2 manches gagnées")
    }

    /** M14, M19, A2: the header position is the engine's set and leg numbers, never counted by the app. */
    @Test
    @Config(qualifiers = "+en-port")
    fun `M19 header position comes from the engine`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 3).legsWonBy(0, 0, 1))
        val score = (viewModel.state.value.screen as Screen.Game).game.match.score

        tag(GameTags.HEADER_POSITION)
            .assertTextEquals(string(R.string.match_position_set_leg, score.currentSetNumber, score.currentLegNumber))
        tag(GameTags.HEADER_POSITION).assertTextEquals("SET 2 · LEG 2")
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M19 header position with legs only in French`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 3).legsWonBy(1))
        val score = (viewModel.state.value.screen as Screen.Game).game.match.score

        tag(GameTags.HEADER_POSITION).assertTextEquals(string(R.string.match_position_leg, score.currentLegNumber))
        tag(GameTags.HEADER_POSITION).assertTextEquals("MANCHE 2")
    }

    // Pending checkout (M15 as amended: the action bar).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M15 confirm labels in English`() = assertConfirmLabels("CONFIRM LEG", "CONFIRM SET", "CONFIRM MATCH")

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M15 confirm labels in French`() =
        assertConfirmLabels("VALIDER LA MANCHE", "VALIDER LE SET", "VALIDER LA PARTIE")

    private fun assertConfirmLabels(leg: String, set: String, match: String) {
        val script = MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 2).playLegWonBy(0)
        play(script)
        composeRule.onNodeWithTag(GameTags.CONFIRM).assertTextEquals(leg)
        val toSet = script.events.size
        script.finish().nextLeg().playLegWonBy(0)
        send(*script.events.drop(toSet).toTypedArray())
        composeRule.onNodeWithTag(GameTags.CONFIRM).assertTextEquals(set)
        val toMatch = script.events.size
        script.finish().nextLeg().legsWonBy(0).playLegWonBy(0)
        send(*script.events.drop(toMatch).toTypedArray())
        composeRule.onNodeWithTag(GameTags.CONFIRM).assertTextEquals(match)
    }

    /** M15: a single game says "VALIDER LA PARTIE". */
    @Test
    @Config(qualifiers = "+fr-port")
    fun `M15 a single game confirms the match`() {
        play(MatchScript().start("Ann", "Bob").playLegWonBy(1))

        composeRule.onNodeWithTag(GameTags.CONFIRM).assertTextEquals("VALIDER LA PARTIE")
    }

    // Between legs (M16, M19).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M16 between-legs screen in English`() {
        play(MatchScript().start("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2).playLegWonBy(2).finish())

        tag(BetweenLegsTags.TITLE).assertTextEquals("SET 1 · LEG 1 WON")
        tag(BetweenLegsTags.WINNER).assertTextEquals("CID")
        tag(BetweenLegsTags.SETS_HEADER).assertTextEquals("SETS")
        tag(BetweenLegsTags.LEGS_HEADER).assertTextEquals("LEGS")
        tag(BetweenLegsTags.legs(2)).assertTextEquals("1")
        tag(BetweenLegsTags.sets(2)).assertTextEquals("0")
        composeRule.onNodeWithTag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("SET 1 · LEG 2", "BOB STARTS")
        tag(BetweenLegsTags.CHECKOUT).assertExists()
        composeRule.onNodeWithTag(BetweenLegsTags.NEXT_LEG).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.game_abandon_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M16 between-legs screen in French after a set`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 1, sets = 2).playLegWonBy(1).finish())

        tag(BetweenLegsTags.TITLE).assertTextEquals("SET 1 GAGNÉ")
        tag(BetweenLegsTags.SETS_HEADER).assertTextEquals("SETS")
        tag(BetweenLegsTags.LEGS_HEADER).assertTextEquals("MAN.")
        composeRule.onNodeWithTag(BetweenLegsTags.NEXT_STARTER).assertTextEquals("SET 2 · MANCHE 1", "BOB COMMENCE")
        composeRule.onNodeWithText("MANCHE SUIVANTE").assertIsDisplayed()
    }

    /** M16, M14: with one set, no "Sets" column, and the title has no set. */
    @Test
    @Config(qualifiers = "+fr-port")
    fun `M16 no sets column with one set`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2).playLegWonBy(0).finish())

        composeRule.onAllNodesWithTag(BetweenLegsTags.SETS_HEADER, useUnmergedTree = true).assertCountEquals(0)
        tag(BetweenLegsTags.TITLE).assertTextEquals("MANCHE 1 GAGNÉE")
    }

    // End screen (M17, owner Q8 and O2).

    @Test
    @Config(qualifiers = "+en-port")
    fun `M17 end screen with sets in English`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2, sets = 2).legsWonBy(0, 0, 1, 0, 0))
        val match = (viewModel.state.value.screen as Screen.End).match

        tag(EndTags.TITLE).assertTextEquals("MATCH WINNER")
        tag(EndTags.WINNER).assertTextEquals("ANN")
        tag(EndTags.SCORE_HEADER).assertTextEquals("SETS")
        tag(EndTags.score(0)).assertTextEquals("2")
        tag(EndTags.score(1)).assertTextEquals("0")
        tag(EndTags.DARTS_HEADER).assertTextEquals("DARTS")
        tag(EndTags.darts(0)).assertTextEquals(match.playerStats[0].dartsThrown.toString())
        // Owner Q8: no "2 SETS TO 0" line.
        composeRule.onAllNodesWithTag(EndTags.DETAIL, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "+fr-port")
    fun `M17 end screen with legs only in French`() {
        play(MatchScript().start("Ann", "Bob", legsPerSet = 2).legsWonBy(1, 1))

        tag(EndTags.SCORE_HEADER).assertTextEquals("MANCHES")
        tag(EndTags.score(0)).assertTextEquals("2")
        tag(EndTags.DARTS_HEADER).assertTextEquals("FLÉCH.")
        val row = composeRule.onNodeWithTag(EndTags.row(0)).fetchDescription()
        assertTrue(row, row.startsWith("1, BOB, Manches 2, Moy. 3 fléchettes "))
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun `M17 a single game has no score column but keeps the darts`() {
        play(MatchScript().start("Ann", "Bob").playLegWonBy(0).finish())

        composeRule.onAllNodesWithTag(EndTags.SCORE_HEADER, useUnmergedTree = true).assertCountEquals(0)
        tag(EndTags.darts(0)).assertExists()
        assertEquals("ANN", tag(EndTags.WINNER).fetchText())
    }

    private fun SemanticsNodeInteraction.fetchText(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")

    private fun SemanticsNodeInteraction.fetchDescription(): String =
        fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString("")

    private companion object {
        val MIN_TOUCH = 48.dp
    }
}
