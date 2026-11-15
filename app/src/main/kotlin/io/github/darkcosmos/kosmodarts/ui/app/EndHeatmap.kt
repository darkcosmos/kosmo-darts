package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.journal.LegSetup
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match

// The tabs of the between-legs and end screens (HM9, HM10, D6, D7, D9). Nothing here is saved.

/** HM9: the two tabs; every between-legs or end screen opens on [RESULT]. */
enum class EndTab { RESULT, LEG_HEATMAP }

/** Which screen a heatmap shows on: the last completed leg between legs, the finished current leg at the end (D6). */
enum class EndScreenKind { BETWEEN_LEGS, MATCH_END }

/**
 * Chooses the player whose heatmap shows first (D7): the kind of screen, the winner of the last completed leg (`null`
 * without one) and the winner of the match (`null` before it is decided). Replaced by a fake in tests.
 */
typealias HeatmapPlayerChoice = (kind: EndScreenKind, lastLegWinner: Int?, matchWinner: Int?) -> Int

/** N2 A (D7): the leg winner between legs, the match winner at the end; the first player when unknown. */
fun defaultHeatmapPlayer(kind: EndScreenKind, lastLegWinner: Int?, matchWinner: Int?): Int = when (kind) {
    EndScreenKind.BETWEEN_LEGS -> lastLegWinner
    EndScreenKind.MATCH_END -> matchWinner
} ?: 0

/** The player shown first on the [kind] screen of this match, as [choice] decides (D7). */
internal fun X01Match.heatmapPlayerOf(
    kind: EndScreenKind,
    choice: HeatmapPlayerChoice = ::defaultHeatmapPlayer,
): Int = choice(kind, completedLegs.lastOrNull()?.winnerIndex, finalScore?.winnerIndex)

/** The player shown first on the [kind] screen of this match, as [choice] decides (D7). */
internal fun CricketMatch.heatmapPlayerOf(
    kind: EndScreenKind,
    choice: HeatmapPlayerChoice = ::defaultHeatmapPlayer,
): Int = choice(kind, completedLegs.lastOrNull()?.winnerIndex, finalScore?.winnerIndex)

/** The between-legs and end screens of both modes, which share the tab, the player and the zone of the heatmap. */
sealed interface EndHeatmapScreen : Screen {
    val journal: MatchJournal
    val endTab: EndTab
    val heatmapPlayer: Int
    val heatmapZone: BoardZone?
    val kind: EndScreenKind

    /** The game and configuration of the legs of [journal], as the engine replays them (T18). */
    val legSetup: LegSetup
    val playerNames: List<String>
    val playerColors: List<PlayerColor>

    fun withHeatmap(tab: EndTab, player: Int, zone: BoardZone?): Screen
}

/** HM9: the screen after a tab, player or zone event, or `null` when [event] is not one. */
internal fun EndHeatmapScreen.reduceHeatmap(event: AppEvent): Screen? = when (event) {
    is AppEvent.EndTabSelected -> withHeatmap(event.tab, heatmapPlayer, heatmapZone)
    // A player switch keeps the zone: its band then shows that player's values.
    is AppEvent.HeatmapPlayerSelected ->
        if (event.index in playerNames.indices) withHeatmap(endTab, event.index, heatmapZone) else this
    is AppEvent.HeatmapZoneTapped -> withHeatmap(endTab, heatmapPlayer, event.zone)
    else -> null
}
