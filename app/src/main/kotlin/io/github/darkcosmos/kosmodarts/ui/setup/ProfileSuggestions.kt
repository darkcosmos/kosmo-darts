package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.Profile

/** PF3: at most this many profiles are suggested under the focused field. */
const val MAX_SUGGESTIONS = 5

/**
 * PF3, D18: the profiles to suggest under the name field at [focused], most recently used first, at most
 * [MAX_SUGGESTIONS]. A profile is kept when its name key contains the key of the typed text (all of them when the field
 * is empty), when no other field already holds its name key, and when its name is not exactly the text of the focused
 * field (the same key with another spelling stays, so that tapping it applies the profile's spelling). The matching is
 * done in memory on name keys, so `%` and `_` are plain characters and never wildcards.
 *
 * @param names the text of every field of the setup, in order.
 * @param focused the position of the focused field, or `null` when none has the focus.
 */
fun suggestions(
    profiles: List<Profile>,
    names: List<String>,
    focused: Int?,
    nameKey: (String) -> String = PlayerListRules::nameKey,
): List<Profile> {
    val typed = focused?.let(names::getOrNull) ?: return emptyList()
    val typedKey = nameKey(typed)
    val keysInOtherFields = names.filterIndexed { index, _ -> index != focused }.map(nameKey).toSet()
    return profiles
        .asSequence()
        .filter { it.nameKey.contains(typedKey) }
        .filter { it.nameKey !in keysInOtherFields }
        .filter { it.name != typed }
        .sortedByDescending { it.lastUsedAt }
        .take(MAX_SUGGESTIONS)
        .toList()
}
