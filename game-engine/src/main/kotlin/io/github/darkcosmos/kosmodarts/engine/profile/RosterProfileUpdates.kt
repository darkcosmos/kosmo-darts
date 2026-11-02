package io.github.darkcosmos.kosmodarts.engine.profile

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.engine.player.Roster
import io.github.darkcosmos.kosmodarts.engine.player.RosterEntry

private fun RosterEntry.hasKey(key: String): Boolean = PlayerListRules.nameKey(name) == key

/** PM9: the entry of the profile [profileKey] takes the spelling [newName]; its color is kept. */
fun Roster.withProfileRenamed(profileKey: String, newName: String): Roster =
    Roster(entries.map { if (it.hasKey(profileKey)) it.copy(name = newName) else it })

/**
 * PM9: the entry of the merged source takes [targetName], or is removed when the target is already in the roster.
 */
fun Roster.withProfileMerged(sourceKey: String, targetName: String): Roster {
    val targetKey = PlayerListRules.nameKey(targetName)
    return if (entries.any { it.hasKey(targetKey) }) {
        Roster(entries.filterNot { it.hasKey(sourceKey) })
    } else {
        withProfileRenamed(sourceKey, targetName)
    }
}

/** PM9: the entry of the deleted profile is removed; the only entry keeps its color with an empty name. */
fun Roster.withProfileDeleted(profileKey: String): Roster {
    val kept = entries.filterNot { it.hasKey(profileKey) }
    return when {
        kept.size == entries.size -> this
        kept.isEmpty() -> Roster(entries.map { it.copy(name = "") })
        else -> Roster(kept)
    }
}
