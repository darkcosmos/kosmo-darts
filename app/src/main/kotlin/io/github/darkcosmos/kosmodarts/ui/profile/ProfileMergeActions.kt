package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.history.PlayerSummary

/** "Merge": PM8 can refuse; otherwise the list of targets opens. */
fun ProfileState.mergeStarted(savedNames: List<String>): ProfileState = withSelf { profile ->
    lockedBy(savedNames, profile)?.let(::refused) ?: copy(panel = ProfilePanel.MergePick, message = null)
}

/** A target picked in the merge list: PM8 checks the target too, then the confirmation opens (PM4). */
fun ProfileState.mergeTargetPicked(id: Long, savedNames: List<String>): ProfileState = withSelf { profile ->
    val target = mergeTargets.find { it.id == id && it.id != profile.id }
    if (target == null || panel != ProfilePanel.MergePick) this else mergeConfirmation(profile, target, savedNames)
}

/** "Merge into Lea" of the rename panel when the name is taken (Q6): the shown profile is the source. */
fun ProfileState.mergeIntoTakenTapped(savedNames: List<String>): ProfileState = withSelf { profile ->
    val taken = ((panel as? ProfilePanel.Rename)?.error as? RenameError.Taken)?.target
    if (taken == null) this else mergeConfirmation(profile, taken, savedNames)
}

private fun ProfileState.mergeConfirmation(
    source: PlayerSummary,
    target: PlayerSummary,
    savedNames: List<String>,
): ProfileState = lockedBy(savedNames, source, target)?.let(::refused)
    ?: copy(panel = ProfilePanel.MergeConfirm(target), message = null)
