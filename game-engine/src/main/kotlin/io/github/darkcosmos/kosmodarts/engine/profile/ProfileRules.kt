package io.github.darkcosmos.kosmodarts.engine.profile

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules

/** The dates of a profile as epoch milliseconds (history rule PF1). */
data class ProfileDates(val resetAt: Long, val createdAt: Long, val lastUsedAt: Long)

/** The result of a rename request (history rule PM3). */
sealed interface RenameOutcome {
    /** The new name breaks a [PlayerListRules] rule. */
    data class Invalid(val violation: PlayerListError) : RenameOutcome

    /** The profile takes [name] (trimmed, NFC) under [key], its name key (same as before, or a free one). */
    data class Renamed(val name: String, val key: String) : RenameOutcome

    /** The name key belongs to another profile [targetKey]: the screen offers to merge into it. */
    data class Clash(val targetKey: String) : RenameOutcome
}

/**
 * What deleting a profile does to the matches it played (history rule PM6).
 *
 * @property toDelete matches whose players are then all the anonymous profile.
 * @property toReassign matches whose participation of the profile moves to the anonymous profile.
 */
data class DeletionPlan(val toDelete: List<Long>, val toReassign: List<Long>)

/** The pure rules of the Players screen (history rules PF1, PM3, PM4, PM6, PM7, PM8). */
object ProfileRules {

    /** PM7: the reserved name key of the anonymous profile. */
    const val ANONYMOUS_NAME_KEY: String = ""

    /**
     * PM3: renames the profile whose name key is [profileKey]. [otherKeys] are the name keys of the other profiles.
     */
    fun rename(newName: String, profileKey: String, otherKeys: Set<String>): RenameOutcome {
        val violation = PlayerListRules.violations(listOf(newName)).firstOrNull()
        if (violation != null) return RenameOutcome.Invalid(violation.error)
        val name = PlayerListRules.normalize(listOf(newName)).single()
        val key = PlayerListRules.nameKey(name)
        return if (key != profileKey && key in otherKeys) RenameOutcome.Clash(key) else RenameOutcome.Renamed(name, key)
    }

    /** PM4: a merge is refused when both profiles played a same match. */
    fun canMerge(sourceSessions: Set<Long>, targetSessions: Set<Long>): Boolean =
        sourceSessions.none { it in targetSessions }

    /** PM4, decision D11: latest reset and last-used dates, earliest creation date. */
    fun mergedDates(source: ProfileDates, target: ProfileDates): ProfileDates = ProfileDates(
        resetAt = maxOf(source.resetAt, target.resetAt),
        createdAt = minOf(source.createdAt, target.createdAt),
        lastUsedAt = maxOf(source.lastUsedAt, target.lastUsedAt),
    )

    /**
     * PM6: [matches] maps each match id to the profile ids of its players. A match of [profileId] is deleted when
     * every other player is [anonymousId] (or there is none), otherwise reassigned. Other matches are ignored.
     */
    fun deletionPlan(profileId: Long, anonymousId: Long?, matches: Map<Long, List<Long>>): DeletionPlan {
        val (toDelete, toReassign) = matches.filterValues { profileId in it }.keys.partition { matchId ->
            matches.getValue(matchId).all { it == profileId || it == anonymousId }
        }
        return DeletionPlan(toDelete, toReassign)
    }

    /** PM8: whether a saved game player ([savedGameNames]) has one of the [nameKeys] of the profiles concerned. */
    fun isLocked(nameKeys: Set<String>, savedGameNames: List<String>): Boolean =
        savedGameNames.any { PlayerListRules.nameKey(it) in nameKeys }
}
