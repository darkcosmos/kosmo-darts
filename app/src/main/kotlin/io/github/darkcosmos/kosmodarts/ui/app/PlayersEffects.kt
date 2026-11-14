package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.ui.players.PlayersState

internal fun playersOf(screen: Screen): PlayersState? = when (screen) {
    is Screen.Players -> screen.players
    is Screen.Stacked -> playersOf(screen.parent)
    else -> null
}

/** PM2, PM10: Players reads when it opens and each time a retry or a return from a Profile raises the read number. */
internal fun loadPlayersEffect(before: AppState, after: AppState): PersistenceEffect.LoadPlayers? {
    val players = playersOf(after.screen) ?: return null
    return PersistenceEffect.LoadPlayers(players.loadId).takeIf { playersOf(before.screen)?.loadId != players.loadId }
}
