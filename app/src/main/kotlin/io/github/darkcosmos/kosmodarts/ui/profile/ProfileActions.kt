package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.RenameResult

/** What is wrong with the name typed in the rename panel (PM3). */
sealed interface RenameError {
    /** The name is not valid. */
    data class Invalid(val violation: PlayerListError) : RenameError

    /** The name key belongs to [target]: the screen offers to merge the shown profile into it (Q6). */
    data class Taken(val target: PlayerSummary) : RenameError
}

/** The inline panel open under the "Manage player" toggle (PM11, PS2): never a dialog. */
sealed interface ProfilePanel {
    data class Rename(val text: String, val error: RenameError? = null) : ProfilePanel

    /** "Merge Kevin into:" with the other profiles as rows. */
    data object MergePick : ProfilePanel

    data class MergeConfirm(val target: PlayerSummary) : ProfilePanel

    data object ResetConfirm : ProfilePanel

    /** [count] is the number of matches the deletion erases (PM6). */
    data class DeleteConfirm(val count: Int) : ProfilePanel
}

/** The short message shown in the panel or under the header (PM4, PM8, PM10, PM11). */
sealed interface ProfileMessage {
    /** PM8: [playerName] is in the game in progress; finish or abandon it first. */
    data class InProgress(val playerName: String) : ProfileMessage

    /** PM4: both profiles played a same match. */
    data object SharedMatch : ProfileMessage

    /** PM10: a database error; everything is unchanged and the action can be retried. */
    data object DatabaseError : ProfileMessage
}

/** The action waiting for the database. At most one at a time; the app queues it as an effect. */
sealed interface ProfileRequest {
    data class Rename(val profile: PlayerSummary, val name: String) : ProfileRequest

    data class Merge(val source: PlayerSummary, val target: PlayerSummary) : ProfileRequest

    /** [at] is the reset date (PM5). */
    data class Reset(val profile: PlayerSummary, val at: Long) : ProfileRequest

    /** Reads how many matches the deletion would erase, for the confirmation (PM6). */
    data class CountDeletion(val profile: PlayerSummary) : ProfileRequest

    /** [at] creates the anonymous profile when absent (PM7). */
    data class Delete(val profile: PlayerSummary, val at: Long) : ProfileRequest
}

/** The answer of the database to a [ProfileRequest]; [DbResult.Failed] is a database error (PM10). */
sealed interface ProfileAnswer {
    data class Renamed(val result: DbResult<RenameResult>) : ProfileAnswer

    data class Merged(val result: DbResult<MergeResult>) : ProfileAnswer

    data class ResetDone(val result: DbResult<Unit>) : ProfileAnswer

    data class Counted(val result: DbResult<Int>) : ProfileAnswer

    /** The result is the number of matches erased, counted in the transaction. */
    data class Deleted(val result: DbResult<Int>) : ProfileAnswer
}

/**
 * The profiles the actions need (PM3, PM4): the shown profile as the Players list gives it, and every other one as a
 * merge target. It is read with the profile, under the same read number.
 */
sealed interface ProfileRoster {
    data object Loading : ProfileRoster

    data object Failed : ProfileRoster

    /** [self] is `null` when the shown profile is no longer listed. */
    data class Loaded(val self: PlayerSummary?, val others: List<PlayerSummary>) : ProfileRoster
}

/** Runs [action] on the shown profile; nothing happens while a request is pending or before the roster is read. */
internal inline fun ProfileState.withSelf(action: (PlayerSummary) -> ProfileState): ProfileState {
    val profile = (roster as? ProfileRoster.Loaded)?.self
    return if (isBusy || profile == null) this else action(profile)
}

/** PM8: the first of [profiles] that the players of the game in progress ([savedNames]) lock, or `null`. */
internal fun lockedBy(savedNames: List<String>, vararg profiles: PlayerSummary): PlayerSummary? =
    profiles.firstOrNull { ProfileRules.isLocked(setOf(it.nameKey), savedNames) }

internal fun ProfileState.refused(locked: PlayerSummary): ProfileState =
    copy(message = ProfileMessage.InProgress(locked.name))

/** PS2: "Manage player" unfolds the four actions; a second tap folds them. Ignored while a request is pending. */
fun ProfileState.manageToggled(): ProfileState =
    if (isBusy) this else copy(isManageOpen = !isManageOpen, panel = null, message = null)

/** "Rename": PM8 can refuse; otherwise the panel opens with the current name. */
fun ProfileState.renameStarted(savedNames: List<String>): ProfileState = withSelf { profile ->
    lockedBy(savedNames, profile)?.let(::refused) ?: copy(panel = ProfilePanel.Rename(profile.name), message = null)
}

fun ProfileState.renameTextChanged(text: String): ProfileState =
    if (isBusy || panel !is ProfilePanel.Rename) this else copy(panel = ProfilePanel.Rename(text), message = null)

/** "Save" of the rename panel (PM3). */
fun ProfileState.renameSaved(): ProfileState = withSelf { profile ->
    val rename = panel as? ProfilePanel.Rename
    if (rename == null) this else copy(request = ProfileRequest.Rename(profile, rename.text), message = null)
}

fun ProfileState.resetStarted(): ProfileState = withSelf { copy(panel = ProfilePanel.ResetConfirm, message = null) }

/** "Delete": PM8 can refuse; otherwise the number of matches to erase is read for the confirmation (PM6). */
fun ProfileState.deleteStarted(savedNames: List<String>): ProfileState = withSelf { profile ->
    lockedBy(savedNames, profile)?.let(::refused)
        ?: copy(request = ProfileRequest.CountDeletion(profile), message = null)
}

/** The confirm button of a merge, reset or delete panel; [at] is the clock reading of the tap (PM5, PM7). */
fun ProfileState.confirmed(at: Long): ProfileState = withSelf { profile ->
    val request = when (val open = panel) {
        is ProfilePanel.MergeConfirm -> ProfileRequest.Merge(profile, open.target)
        ProfilePanel.ResetConfirm -> ProfileRequest.Reset(profile, at)
        is ProfilePanel.DeleteConfirm -> ProfileRequest.Delete(profile, at)
        else -> null
    }
    if (request == null) this else copy(request = request, message = null)
}

/** "Cancel" of any panel: closes the panel, with its message. */
fun ProfileState.cancelled(): ProfileState = if (isBusy) this else copy(panel = null, message = null)
