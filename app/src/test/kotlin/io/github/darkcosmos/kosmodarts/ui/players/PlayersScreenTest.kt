package io.github.darkcosmos.kosmodarts.ui.players

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.PlayersEvent
import io.github.darkcosmos.kosmodarts.ui.history.formatEndDate
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/** PM2: the Players screen alone, a list that opens the Profile, with a state given directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class PlayersScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val events = mutableListOf<AppEvent>()
    private val kevin = playerSummary(1, "Kevin", matches = 3)
    private val lea = playerSummary(2, "Lea", matches = 1)
    private val ana = playerSummary(3, "Ana", matches = 0)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** A node inside a merged parent (the text of a card header, the supporting text of a field). */
    private fun unmerged(tag: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun show(content: @Composable () -> Unit) {
        composeRule.runOnUiThread {
            val view = ComposeView(composeRule.activity)
            view.setContent { KosmoDartsTheme { content() } }
            composeRule.activity.setContentView(view)
        }
        composeRule.waitForIdle()
    }

    private fun showState(state: PlayersState) = show { PlayersScreen(state, events::add) }

    private fun assertLiveRegion(tag: String) =
        unmerged(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

    @Test
    fun `PM2 each card shows the name, the number of matches and the date of the last match`() {
        showState(loadedPlayers(kevin, lea, ana))
        node(PlayersTags.card(1)).assertIsDisplayed()
        val locale = composeRule.activity.resources.configuration.locales[0]
        val date = formatEndDate(SOME_MATCH_END, locale, ZoneId.systemDefault())
        unmerged(PlayersTags.summary(1)).assertTextEquals("3 matches · Last match: $date")
    }

    @Test
    fun `PM2 one match and no match use their own texts`() {
        showState(loadedPlayers(kevin, lea, ana))
        val locale = composeRule.activity.resources.configuration.locales[0]
        val date = formatEndDate(SOME_MATCH_END, locale, ZoneId.systemDefault())
        unmerged(PlayersTags.summary(2)).assertTextEquals("1 match · Last match: $date")
        unmerged(PlayersTags.summary(3)).assertTextEquals("No match yet")
    }

    @Test
    fun `PM2 an empty list, a failed read and a loading read each show their own screen`() {
        showState(loadedPlayers())
        node(PlayersTags.EMPTY).assertIsDisplayed()

        showState(PlayersState(content = PlayersContent.Failed))
        node(PlayersTags.FAILED).assertIsDisplayed()
        assertLiveRegion(PlayersTags.FAILED)
        node(PlayersTags.RETRY).performClick()
        assertEquals(listOf<AppEvent>(PlayersEvent.RetryTapped), events)
    }

    @Test
    fun `PM2 PS1 a row is one tappable target of at least 48dp, read as opening the profile`() {
        showState(loadedPlayers(kevin, lea))
        node(PlayersTags.card(1)).assertHeightIsAtLeast(48.dp)
        composeRule.onNode(
            SemanticsMatcher("Open the profile of Kevin") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open the profile of Kevin"
            },
        ).assertExists()
    }

    @Test
    fun `PM2 the list has no action button, the actions are in the Profile`() {
        showState(loadedPlayers(kevin, lea))
        listOf(PlayersTags.RENAME, PlayersTags.MERGE, PlayersTags.RESET, PlayersTags.DELETE).forEach {
            node(it).assertDoesNotExist()
        }
    }

    @Test
    fun `PM2 a tap on a card sends the tap and the back arrow sends Back`() {
        showState(loadedPlayers(kevin, lea))
        node(PlayersTags.card(2)).performClick()
        node(PlayersTags.BACK).performClick()
        assertEquals(listOf(PlayersEvent.ProfileTapped(2), AppEvent.Back), events)
    }
}
