package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonByScoring20
import io.github.darkcosmos.kosmodarts.testing.MatchJournals
import io.github.darkcosmos.kosmodarts.testing.betweenLegs
import io.github.darkcosmos.kosmodarts.testing.cricketBetweenLegs
import io.github.darkcosmos.kosmodarts.testing.endScreen
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.app.reduceHeatmap
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketBetweenLegsScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketEndScreen
import io.github.darkcosmos.kosmodarts.ui.end.EndScreen
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

// Fixtures and a host of the four between-legs and end screens for the tests of the tabs (HM9, HM10): the host applies
// the three events of the tabs to its screen like the reducer, and records every event it receives.

val FOUR_NAMES = listOf("Ann", "Bob", "Cid", "Dan")
val LONG_NAMES = listOf("Alexandrina-Maximili", "Bartholomew Fitzgera", "Christopher Montgome", "Dominique")

/** Between two legs of an X01 match won by the first player. */
fun x01BetweenLegs(names: List<String>): Screen.BetweenLegs =
    betweenLegs(MatchFixtures.match(*names.toTypedArray(), legsPerSet = 3).confirmLegsWonBy(0))

/** The end of an X01 match won by the first player. */
fun x01End(names: List<String>): Screen.End =
    endScreen(MatchFixtures.match(*names.toTypedArray()).playLegWonBy(0))

/** Between two legs of a Cricket match won by the first player. */
fun cricketBetween(names: List<String>): Screen.CricketBetweenLegs =
    cricketBetweenLegs(
        CricketSaveFixtures.match(*names.toTypedArray(), legsPerSet = 3).playLegWonByScoring20(0).confirmLeg(),
    )

/** The end of a Cricket match won by the first player. */
fun cricketEnd(names: List<String>): Screen.CricketEnd {
    val match = CricketSaveFixtures.match(*names.toTypedArray()).playLegWonByScoring20(0)
    return Screen.CricketEnd(match, MatchJournals.of(match))
}

/** The four screens with [names], in a fixed order, for the tests that go through all of them. */
fun allEndScreens(names: List<String>): List<EndHeatmapScreen> =
    listOf(x01BetweenLegs(names), x01End(names), cricketBetween(names), cricketEnd(names))

/** Shows [initial] and keeps it up to date with the tab, player and zone events; [events] collects every event. */
fun showEndScreen(
    composeRule: AndroidComposeTestRule<*, MainActivity>,
    initial: EndHeatmapScreen,
    events: MutableList<AppEvent>,
    isZoneTapEnabled: Boolean = END_SCREEN_ZONE_TAP_ENABLED,
) {
    composeRule.runOnUiThread {
        val view = ComposeView(composeRule.activity)
        view.setContent {
            KosmoDartsTheme { EndScreensHost(initial, events, isZoneTapEnabled) }
        }
        composeRule.activity.setContentView(view)
    }
    composeRule.waitForIdle()
}

@Composable
private fun EndScreensHost(initial: EndHeatmapScreen, events: MutableList<AppEvent>, isZoneTapEnabled: Boolean) {
    val current = remember { mutableStateOf<EndHeatmapScreen>(initial) }
    val onEvent: (AppEvent) -> Unit = { event ->
        events += event
        (current.value.reduceHeatmap(event) as? EndHeatmapScreen)?.let { current.value = it }
    }
    when (val screen = current.value) {
        is Screen.BetweenLegs ->
            BetweenLegsScreen(screen.match, onEvent, heatmapScreen = screen, isZoneTapEnabled = isZoneTapEnabled)
        is Screen.End -> EndScreen(screen.match, onEvent, heatmapScreen = screen, isZoneTapEnabled = isZoneTapEnabled)
        is Screen.CricketBetweenLegs -> CricketBetweenLegsScreen(
            screen.match,
            screen.journal,
            onEvent,
            heatmapScreen = screen,
            isZoneTapEnabled = isZoneTapEnabled,
        )
        is Screen.CricketEnd -> CricketEndScreen(
            screen.match,
            screen.journal,
            onEvent,
            heatmapScreen = screen,
            isZoneTapEnabled = isZoneTapEnabled,
        )
    }
}

/** Scrolls the node into view when it is not already displayed (a pinned button has no scrolling parent). */
fun SemanticsNodeInteraction.reach(): SemanticsNodeInteraction {
    if (!isDisplayed()) performScrollTo()
    return this
}
