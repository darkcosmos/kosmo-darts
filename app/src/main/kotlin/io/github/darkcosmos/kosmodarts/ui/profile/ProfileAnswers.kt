package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.RenameResult

/**
 * The database answered [done] with [answer]. An answer to a request that is no longer the pending one changes
 * nothing. A failure keeps the panel open with the PM10 message. A success that changes what the profile shows raises
 * the read number (PM3, PM4, PM5): the app then reads it again. Leaving the screen after a merge or a delete is the
 * app's business (PS7).
 */
fun ProfileState.answered(done: ProfileRequest, answer: ProfileAnswer): ProfileState {
    if (done != request) return this
    val idle = copy(request = null, message = null)
    val next = when (answer) {
        is ProfileAnswer.Renamed -> idle.afterRename(done, answer.result)
        is ProfileAnswer.Merged -> idle.afterMerge(done, answer.result)
        is ProfileAnswer.ResetDone -> idle.afterReset(done, answer.result)
        is ProfileAnswer.Counted -> idle.afterCount(done, answer.result)
        is ProfileAnswer.Deleted -> idle.afterDelete(done, answer.result)
    }
    return next ?: copy(request = null, message = ProfileMessage.DatabaseError)
}

/** Each `after` function gives `null` for a failure, or an answer that does not fit the request. */
private fun ProfileState.afterRename(done: ProfileRequest, result: DbResult<RenameResult>): ProfileState? =
    if (done is ProfileRequest.Rename && result is DbResult.Ok) renamed(done, result.value) else null

private fun ProfileState.afterMerge(done: ProfileRequest, result: DbResult<MergeResult>): ProfileState? =
    if (done is ProfileRequest.Merge && result is DbResult.Ok) merged(result.value) else null

private fun ProfileState.afterReset(done: ProfileRequest, result: DbResult<Unit>): ProfileState? =
    if (done is ProfileRequest.Reset && result is DbResult.Ok) copy(panel = null).reloaded() else null

private fun ProfileState.afterCount(done: ProfileRequest, result: DbResult<Int>): ProfileState? =
    if (done is ProfileRequest.CountDeletion && result is DbResult.Ok) {
        copy(panel = ProfilePanel.DeleteConfirm(result.value))
    } else {
        null
    }

private fun ProfileState.afterDelete(done: ProfileRequest, result: DbResult<Int>): ProfileState? =
    if (done is ProfileRequest.Delete && result is DbResult.Ok) copy(panel = null).reloaded() else null

private fun ProfileState.renamed(done: ProfileRequest.Rename, result: RenameResult): ProfileState = when (result) {
    is RenameResult.Renamed -> copy(panel = null).reloaded()
    is RenameResult.Invalid -> copy(panel = ProfilePanel.Rename(done.name, RenameError.Invalid(result.violation)))
    is RenameResult.Clash -> mergeTargets.find { it.id == result.targetId }
        ?.let { copy(panel = ProfilePanel.Rename(done.name, RenameError.Taken(it))) }
        ?: reloaded()
    RenameResult.Unknown -> copy(panel = null).reloaded()
}

private fun ProfileState.merged(result: MergeResult): ProfileState = when (result) {
    MergeResult.SHARED_MATCH -> copy(message = ProfileMessage.SharedMatch)
    MergeResult.MERGED, MergeResult.UNKNOWN -> copy(panel = null).reloaded()
}
