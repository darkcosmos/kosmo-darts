package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.stats.LastLeg
import io.github.darkcosmos.kosmodarts.engine.stats.lastLegHeatmap
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndScreenKind
import io.github.darkcosmos.kosmodarts.ui.app.EndTab
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapFigures
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapSectionState
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapSelections

/** One colour chip of the player switch (HM9): the player at [index], with his name and colour. */
data class HeatmapPlayerChip(val index: Int, val name: String, val color: PlayerColor, val isSelected: Boolean)

/**
 * The "Leg heatmap" tab of a between-legs or end screen as the screen shows it (HM9, HM10), all computed here.
 *
 * @property tab the selected tab (HM9).
 * @property players one chip per player in throwing order; empty in a solo game, which has no switch (HM9).
 * @property section the heatmap of the last leg of the selected player, with its zone, top 5 and band (HM6): the same
 *   figures as the Profile section, so the screens share the zone texts.
 */
data class EndHeatmapState(val tab: EndTab, val players: List<HeatmapPlayerChip>, val section: HeatmapSectionState)

/**
 * M19, HM9: the position of the leg the heatmap shows, for the "LEG n" title: the last completed leg between legs, the
 * finished current leg at the end (D6). No numbers in a single game.
 */
fun EndHeatmapScreen.legPosition(): MatchPosition = when (this) {
    is Screen.BetweenLegs -> MatchPosition.lastLeg(match)
    is Screen.CricketBetweenLegs -> MatchPosition.lastLeg(match.score)
    is Screen.End -> MatchPosition.current(match)
    is Screen.CricketEnd -> MatchPosition.current(match.score)
} ?: MatchPosition(null, null)

/**
 * HM9, D6: the heatmap of the last leg played, derived from the match journal (DJ2, DJ4): between legs the last
 * completed leg (the current leg is the empty next one), at the end the finished current leg. Never throws: a journal
 * that cannot be replayed gives the empty heatmap (HM7).
 */
fun EndHeatmapScreen.heatmapState(): EndHeatmapState {
    val lastLeg = when (kind) {
        EndScreenKind.BETWEEN_LEGS -> LastLeg.COMPLETED
        EndScreenKind.MATCH_END -> LastLeg.CURRENT
    }
    val heatmap = lastLegHeatmap(journal, legSetup, heatmapPlayer, lastLeg)
    val chips = if (playerNames.size < 2) {
        emptyList()
    } else {
        playerNames.mapIndexed { index, name ->
            HeatmapPlayerChip(index, name, playerColors[index], isSelected = index == heatmapPlayer)
        }
    }
    val selections = HeatmapSelections(selectedZone = heatmapZone)
    return EndHeatmapState(endTab, chips, HeatmapSectionState(selections, HeatmapFigures(1, heatmap)))
}
