package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MatchDetail
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.RenameResult
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailContent
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileAnswer
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRequest
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState
import io.github.darkcosmos.kosmodarts.ui.profile.answered

/** Where the app goes when an answer ends the Profile it came from (PS7). */
private sealed interface ProfileExit {
    /** The shown profile was merged away: the Profile of [targetId] replaces it. */
    data class MergedInto(val targetId: Long) : ProfileExit

    /** The shown profile was deleted. */
    data class Deleted(val profile: PlayerSummary) : ProfileExit
}

/**
 * PS7, PM3 to PM10: the database answered a Profile request. The Profile, if still in the stack, takes the answer;
 * a merge where it is the source, the target's Profile replaces it; after a delete the app goes back to the previous
 * screen, reloaded. In every case a successful rename, merge or delete updates the last setup (PM9), even when the
 * already left the screen.
 */
internal fun reduceProfileAnswer(state: AppState, event: ProfileEvent.Answered, stamp: EventStamp): AppState {
    val screen = state.screen
    val top = (screen as? Screen.Profile)?.takeIf { it.profileId == event.profileId }
    val exit = profileExit(event.request, event.answer)
    val next = when {
        top != null && exit is ProfileExit.MergedInto && exit.targetId != top.profileId ->
            top.replacedBy(exit.targetId, stamp.at)
        top != null && exit is ProfileExit.Deleted -> top.leftAfterDeletion(exit.profile)
        else -> screen.withAnswer(event)
    }
    return state.copy(screen = next, lastSetup = state.lastSetup?.afterAnswer(event.request, event.answer))
}

private fun profileExit(request: ProfileRequest, answer: ProfileAnswer): ProfileExit? = when {
    request is ProfileRequest.Merge && answer is ProfileAnswer.Merged ->
        ProfileExit.MergedInto(request.target.id).takeIf { isMerged(answer) }
    request is ProfileRequest.Delete && answer is ProfileAnswer.Deleted ->
        ProfileExit.Deleted(request.profile).takeIf { answer.result is DbResult.Ok }
    else -> null
}

/** The answer applied to its Profile in the chain, wherever it is; the screens behind it follow a rename or a merge. */
private fun Screen.withAnswer(event: ProfileEvent.Answered): Screen {
    val answered = withProfile(event.profileId) { it.answered(event.request, event.answer) }
    if (!changesNames(event.request, event.answer)) return answered
    val behind = answered.mapProfile(event.profileId) { it.copy(parent = it.parent.reloaded()) }
    val request = event.request
    // PM4: a Profile of the merge target in the chain reads again, to show what it just received.
    return if (request is ProfileRequest.Merge) behind.withProfile(request.target.id) { it.reloaded() } else behind
}

/** PM3, PM4: a rename or a merge changes names that the screens behind the Profile show. */
private fun changesNames(request: ProfileRequest, answer: ProfileAnswer): Boolean = when {
    request is ProfileRequest.Rename && answer is ProfileAnswer.Renamed ->
        (answer.result as? DbResult.Ok)?.value is RenameResult.Renamed
    request is ProfileRequest.Merge && answer is ProfileAnswer.Merged -> isMerged(answer)
    else -> false
}

/**
 * PS7: the Profile of [targetId] replaces this one over the same parent, which is read again. A Profile of the target
 * already in the chain is the one that shows, read again, as the back stack never holds two copies (HS9).
 */
private fun Screen.Profile.replacedBy(targetId: Long, at: Long): Screen {
    val reloadedParent = parent.reloaded()
    val isInChain = reloadedParent.profilesOfStack().any { it.profileId == targetId }
    val opened = reloadedParent.open(Screen.Profile(reloadedParent, targetId, ProfileState(now = at)))
    return if (isInChain) opened.mapProfile(targetId) { it.copy(profile = it.profile.reloaded()) } else opened
}

/**
 * PS7: back to the previous screen, read again. When that screen is the detail of a match the deletion erased (PM6), to
 * the History list, or to the nearest screen that is not a detail when the chain has no History list.
 */
private fun Screen.Profile.leftAfterDeletion(deleted: PlayerSummary): Screen {
    val previous = parent
    val erased = previous is Screen.MatchDetail && previous.isErasedBy(deleted)
    if (erased) return previous.parent.beforeDetails().reloaded()
    return previous.reloaded()
}

/** The History list of the chain, or else the first screen that is not a match detail (the detail was erased). */
private fun Screen.beforeDetails(): Screen =
    historyListOfStack() ?: if (this is Screen.MatchDetail) parent.beforeDetails() else this

/** PM6: the deletion of [deleted] erases this match when every one of its players is that profile or anonymous. */
private fun Screen.MatchDetail.isErasedBy(deleted: PlayerSummary): Boolean {
    val loaded = (detail.content as? MatchDetailContent.Loaded)?.detail ?: return false
    return loaded.isErasedBy(deleted)
}

private fun MatchDetail.isErasedBy(deleted: PlayerSummary): Boolean =
    players.all { it.isDeleted || PlayerListRules.nameKey(it.name) == deleted.nameKey }

/** The screen with a new read asked for, so that it shows what the action changed (PS7). Others are unchanged. */
internal fun Screen.reloaded(): Screen = when (this) {
    is Screen.Players -> copy(players = players.reload())
    is Screen.History -> copy(history = history.retry())
    is Screen.MatchDetail -> copy(detail = detail.reloaded())
    is Screen.Profile -> copy(profile = profile.reloaded())
    else -> this
}
