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
    val value = answer.result.let { (it as? DbResult.Ok)?.value }
    val next = value?.let { idle.afterSuccess(done, it) }
    return next ?: copy(request = null, message = PlayersMessage.DatabaseError)
}

private fun PlayersState.afterSuccess(done: PlayersRequest, value: Any): PlayersState? = when {
    done is PlayersRequest.Rename && value is RenameResult -> renamed(done, value)
    done is PlayersRequest.Merge && value is MergeResult -> merged(value)
    done is PlayersRequest.Reset && value == Unit -> copy(panel = null)
    done is PlayersRequest.CountDeletion && value is Int -> copy(panel = PlayersPanel.DeleteConfirm(value))
    done is PlayersRequest.Delete && value is Int -> copy(panel = null, expandedId = null).reload()
    else -> null
}

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
