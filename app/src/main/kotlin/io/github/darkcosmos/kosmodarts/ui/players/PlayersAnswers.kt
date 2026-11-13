package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.RenameResult

/**
 * The database answered [done] with [answer]. An answer to a request that is no longer the pending one changes
 * nothing. A failure keeps the panel open with the PM10 message.
 */
fun PlayersState.answered(done: PlayersRequest, answer: PlayersAnswer): PlayersState {
    if (done != request) return this
    val idle = copy(request = null, message = null)
    val next = when (answer) {
        is PlayersAnswer.Renamed -> idle.afterRename(done, answer.result)
        is PlayersAnswer.Merged -> idle.afterMerge(done, answer.result)
        is PlayersAnswer.ResetDone -> idle.afterReset(done, answer.result)
        is PlayersAnswer.Counted -> idle.afterCount(done, answer.result)
        is PlayersAnswer.Deleted -> idle.afterDelete(done, answer.result)
    }
    return next ?: copy(request = null, message = PlayersMessage.DatabaseError)
}

/** Each `after` function gives `null` for a failure, or an answer that does not fit the request. */
private fun PlayersState.afterRename(done: PlayersRequest, result: DbResult<RenameResult>): PlayersState? =
    if (done is PlayersRequest.Rename && result is DbResult.Ok) renamed(done, result.value) else null

private fun PlayersState.afterMerge(done: PlayersRequest, result: DbResult<MergeResult>): PlayersState? =
    if (done is PlayersRequest.Merge && result is DbResult.Ok) merged(result.value) else null

private fun PlayersState.afterReset(done: PlayersRequest, result: DbResult<Unit>): PlayersState? =
    if (done is PlayersRequest.Reset && result is DbResult.Ok) copy(panel = null) else null

private fun PlayersState.afterCount(done: PlayersRequest, result: DbResult<Int>): PlayersState? =
    if (done is PlayersRequest.CountDeletion && result is DbResult.Ok) {
        copy(panel = PlayersPanel.DeleteConfirm(result.value))
    } else {
        null
    }

private fun PlayersState.afterDelete(done: PlayersRequest, result: DbResult<Int>): PlayersState? =
    if (done is PlayersRequest.Delete && result is DbResult.Ok) copy(panel = null, expandedId = null).reload() else null

private fun PlayersState.renamed(done: PlayersRequest.Rename, result: RenameResult): PlayersState = when (result) {
    is RenameResult.Renamed -> copy(panel = null).reload()
    is RenameResult.Invalid -> copy(panel = PlayersPanel.Rename(done.name, RenameError.Invalid(result.violation)))
    is RenameResult.Clash -> players.find { it.id == result.targetId }
        ?.let { copy(panel = PlayersPanel.Rename(done.name, RenameError.Taken(it))) }
        ?: reload()
    RenameResult.Unknown -> copy(panel = null, expandedId = null).reload()
}

private fun PlayersState.merged(result: MergeResult): PlayersState = when (result) {
    MergeResult.SHARED_MATCH -> copy(message = PlayersMessage.SharedMatch)
    MergeResult.MERGED, MergeResult.UNKNOWN -> copy(panel = null, expandedId = null).reload()
}
