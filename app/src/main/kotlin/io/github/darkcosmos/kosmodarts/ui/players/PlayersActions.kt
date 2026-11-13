package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.history.PlayerSummary

/** "Rename": PM8 can refuse; otherwise the panel opens with the current name. */
fun PlayersState.renameStarted(savedNames: List<String>): PlayersState = withExpanded { profile ->
    lockedBy(savedNames, profile)?.let(::refused) ?: copy(panel = PlayersPanel.Rename(profile.name), message = null)
}

fun PlayersState.renameTextChanged(text: String): PlayersState =
    if (isBusy || panel !is PlayersPanel.Rename) this else copy(panel = PlayersPanel.Rename(text), message = null)

/** "Save" of the rename panel (PM3). */
fun PlayersState.renameSaved(): PlayersState = withExpanded { profile ->
    val rename = panel as? PlayersPanel.Rename
    if (rename == null) this else copy(request = PlayersRequest.Rename(profile, rename.text), message = null)
}

/** "Merge": PM8 can refuse; otherwise the list of targets opens. */
fun PlayersState.mergeStarted(savedNames: List<String>): PlayersState = withExpanded { profile ->
    lockedBy(savedNames, profile)?.let(::refused) ?: copy(panel = PlayersPanel.MergePick, message = null)
}

/** A target picked in the merge list: PM8 checks the target too, then the confirmation opens (PM4). */
fun PlayersState.mergeTargetPicked(id: Long, savedNames: List<String>): PlayersState = withExpanded { profile ->
    val target = players.find { it.id == id && it.id != profile.id }
    if (target == null || panel != PlayersPanel.MergePick) this else mergeConfirmation(profile, target, savedNames)
}

/** "Merge into Lea" of the rename panel when the name is taken (Q6): the renamed profile is the source. */
fun PlayersState.mergeIntoTakenTapped(savedNames: List<String>): PlayersState = withExpanded { profile ->
    val taken = ((panel as? PlayersPanel.Rename)?.error as? RenameError.Taken)?.target
    if (taken == null) this else mergeConfirmation(profile, taken, savedNames)
}

private fun PlayersState.mergeConfirmation(
    source: PlayerSummary,
    target: PlayerSummary,
    savedNames: List<String>,
): PlayersState = lockedBy(savedNames, source, target)?.let(::refused)
    ?: copy(panel = PlayersPanel.MergeConfirm(target), message = null)

fun PlayersState.resetStarted(): PlayersState =
    withExpanded { copy(panel = PlayersPanel.ResetConfirm, message = null) }

/** "Delete": PM8 can refuse; otherwise the number of matches to erase is read for the confirmation (PM6). */
fun PlayersState.deleteStarted(savedNames: List<String>): PlayersState = withExpanded { profile ->
    lockedBy(savedNames, profile)?.let(::refused)
        ?: copy(request = PlayersRequest.CountDeletion(profile), message = null)
}

/** The confirm button of a merge, reset or delete panel; [at] is the clock reading of the tap (PM5, PM7). */
fun PlayersState.confirmed(at: Long): PlayersState = withExpanded { profile ->
    val request = when (val open = panel) {
        is PlayersPanel.MergeConfirm -> PlayersRequest.Merge(profile, open.target)
        PlayersPanel.ResetConfirm -> PlayersRequest.Reset(profile, at)
        is PlayersPanel.DeleteConfirm -> PlayersRequest.Delete(profile, at)
        else -> null
    }
    if (request == null) this else copy(request = request, message = null)
}
