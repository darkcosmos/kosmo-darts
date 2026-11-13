package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.ui.players.PlayersAnswer
import io.github.darkcosmos.kosmodarts.ui.players.PlayersRequest
import io.github.darkcosmos.kosmodarts.ui.players.PlayersState

/**
 * PM3 to PM6: runs [request] on the persistence queue (writes) or the read executor (the count of a deletion) and hands
 * its answer to [onEvent], on that thread: the caller moves it to the main thread.
 */
internal fun Persistence.runPlayersRequest(request: PlayersRequest, onEvent: (AppEvent) -> Unit) {
    fun answer(value: PlayersAnswer) = onEvent(PlayersEvent.Answered(request, value))
    when (request) {
        is PlayersRequest.Rename ->
            playersAccess.change({ it.rename(request.profile.id, request.name) }) { answer(PlayersAnswer.Renamed(it)) }
        is PlayersRequest.Merge ->
            playersAccess.change({ it.merge(request.source.id, request.target.id) }) {
                answer(PlayersAnswer.Merged(it))
            }
        is PlayersRequest.Reset ->
            playersAccess.change({ it.reset(request.profile.id, request.at) }) { answer(PlayersAnswer.ResetDone(it)) }
        is PlayersRequest.CountDeletion ->
            playersAccess.countDeletion(request.profile.id) { answer(PlayersAnswer.Counted(it)) }
        is PlayersRequest.Delete ->
            playersAccess.change({ it.delete(request.profile.id, request.at) }) { answer(PlayersAnswer.Deleted(it)) }
    }
}

internal fun playersOf(screen: Screen): PlayersState? = (screen as? Screen.Players)?.players

/** PM2, PM10: Players reads when it opens and each time a retry or a successful action raises the read number. */
internal fun loadPlayersEffect(before: AppState, after: AppState): PersistenceEffect.LoadPlayers? {
    val players = playersOf(after.screen) ?: return null
    return PersistenceEffect.LoadPlayers(players.loadId).takeIf { playersOf(before.screen)?.loadId != players.loadId }
}

/** PM3 to PM6: a request is run once, when it appears in the state; its answer clears it. */
internal fun playersRequestEffect(before: AppState, after: AppState): PersistenceEffect.RunPlayersRequest? {
    val request = playersOf(after.screen)?.request ?: return null
    return PersistenceEffect.RunPlayersRequest(request).takeIf { playersOf(before.screen)?.request != request }
}
