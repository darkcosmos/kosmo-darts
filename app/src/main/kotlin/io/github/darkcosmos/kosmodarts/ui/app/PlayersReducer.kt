package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.profile.withProfileDeleted
import io.github.darkcosmos.kosmodarts.engine.profile.withProfileMerged
import io.github.darkcosmos.kosmodarts.engine.profile.withProfileRenamed
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.RenameResult
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.players.PlayersAnswer
import io.github.darkcosmos.kosmodarts.ui.players.PlayersRequest
import io.github.darkcosmos.kosmodarts.ui.players.PlayersState
import io.github.darkcosmos.kosmodarts.ui.players.answered
import io.github.darkcosmos.kosmodarts.ui.players.confirmed
import io.github.darkcosmos.kosmodarts.ui.players.deleteStarted
import io.github.darkcosmos.kosmodarts.ui.players.mergeIntoTakenTapped
import io.github.darkcosmos.kosmodarts.ui.players.mergeStarted
import io.github.darkcosmos.kosmodarts.ui.players.mergeTargetPicked
import io.github.darkcosmos.kosmodarts.ui.players.renameSaved
import io.github.darkcosmos.kosmodarts.ui.players.renameStarted
import io.github.darkcosmos.kosmodarts.ui.players.renameTextChanged
import io.github.darkcosmos.kosmodarts.ui.players.resetStarted

/**
 * PM2 to PM11: back returns to the home screen Players came from; the other events go to [PlayersState]. The names of
 * the saved game of that home screen lock the profiles that PM8 protects.
 */
internal fun reducePlayers(state: AppState, screen: Screen.Players, event: AppEvent, stamp: EventStamp): AppState {
    if (event == AppEvent.Back) return state.copy(screen = screen.home)
    val next = screen.players.after(event, screen.home.resumableGame?.playerNames().orEmpty(), stamp.at)
    return if (next == screen.players) state else state.copy(screen = screen.copy(players = next))
}

/** The Players state after [event]; events it does not know change nothing. [savedNames] are PM8's locked names. */
internal fun PlayersState.after(event: AppEvent, savedNames: List<String>, at: Long): PlayersState = when (event) {
    PlayersEvent.RetryTapped -> retry()
    is PlayersEvent.Loaded -> loaded(event.loadId, event.result)
    is PlayersEvent.ProfileTapped -> profileTapped(event.id)
    else -> afterAction(event, savedNames, at)
}

/** The part of [PlayersState.after] for the actions of the open panel. */
private fun PlayersState.afterAction(event: AppEvent, savedNames: List<String>, at: Long): PlayersState = when (event) {
    PlayersEvent.RenameTapped -> renameStarted(savedNames)
    is PlayersEvent.RenameTextChanged -> renameTextChanged(event.text)
    PlayersEvent.RenameSaved -> renameSaved()
    PlayersEvent.MergeTapped -> mergeStarted(savedNames)
    is PlayersEvent.MergeTargetPicked -> mergeTargetPicked(event.id, savedNames)
    PlayersEvent.MergeIntoTakenTapped -> mergeIntoTakenTapped(savedNames)
    PlayersEvent.ResetTapped -> resetStarted()
    PlayersEvent.DeleteTapped -> deleteStarted(savedNames)
    PlayersEvent.Confirmed -> confirmed(at)
    PlayersEvent.Cancelled -> cancelled()
    is PlayersEvent.Answered -> answered(event.request, event.answer)
    else -> this
}

/**
 * The database answered a Players request. The Players screen, if still shown, takes the answer; and in every case a
 * successful rename, merge or delete updates the last setup (PM9), even when the player already left the screen.
 */
internal fun reducePlayersAnswer(state: AppState, event: PlayersEvent.Answered): AppState {
    val screen = state.screen
    val next = if (screen is Screen.Players) {
        screen.copy(players = screen.players.answered(event.request, event.answer))
    } else {
        screen
    }
    return state.copy(screen = next, lastSetup = state.lastSetup?.afterAnswer(event.request, event.answer))
}

/** PM9: the last setup after a successful [request]; a failure, a refusal or any other request changes nothing. */
private fun LastSetups.afterAnswer(request: PlayersRequest, answer: PlayersAnswer): LastSetups = when {
    request is PlayersRequest.Rename && answer is PlayersAnswer.Renamed ->
        (answer.result as? DbResult.Ok)?.value.let { result ->
            if (result is RenameResult.Renamed) {
                withRosterUpdated { it.withProfileRenamed(request.profile.nameKey, result.name) }
            } else {
                this
            }
        }
    request is PlayersRequest.Merge && answer is PlayersAnswer.Merged ->
        if ((answer.result as? DbResult.Ok)?.value == MergeResult.MERGED) {
            withRosterUpdated { it.withProfileMerged(request.source.nameKey, request.target.name) }
        } else {
            this
        }
    request is PlayersRequest.Delete && answer is PlayersAnswer.Deleted ->
        if (answer.result is DbResult.Ok) withRosterUpdated { it.withProfileDeleted(request.profile.nameKey) } else this
    else -> this
}

/** The names of the players of the saved game, whatever its mode (PM8). */
internal fun StoredGame.playerNames(): List<String> = when (this) {
    is SavedGame -> match.matchConfig.config.players
    is SavedCricketGame -> match.matchConfig.config.players
}
