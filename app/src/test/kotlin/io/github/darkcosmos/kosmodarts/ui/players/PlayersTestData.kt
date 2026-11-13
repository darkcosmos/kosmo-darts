package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary

/** A fixed date (2023-11-14), so that the captures and the texts do not depend on the day of the run. */
const val SOME_MATCH_END = 1_700_000_000_000L

fun playerSummary(id: Long, name: String, matches: Int = 3, lastEndedAt: Long? = SOME_MATCH_END): PlayerSummary =
    PlayerSummary(id, name, PlayerListRules.nameKey(name), matches, if (matches == 0) null else lastEndedAt)

/** The state after a successful read of [players], with the given card and panel open. */
fun loadedPlayers(
    vararg players: PlayerSummary,
    expanded: Long? = null,
    panel: PlayersPanel? = null,
    message: PlayersMessage? = null,
): PlayersState = PlayersState()
    .loaded(1, DbResult.Ok(players.toList()))
    .copy(expandedId = expanded, panel = panel, message = message)
