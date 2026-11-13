package io.github.darkcosmos.kosmodarts.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import io.github.darkcosmos.kosmodarts.engine.profile.DeletionPlan
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules

/** One player of a match: the session and the profile (PM6). */
data class SessionParticipant(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "profile_id") val profileId: Long,
)

/**
 * The only deletes of matches (PM6, the exception to AR8), used by [ProfileDeleter] in one transaction. Every foreign
 * key is NO ACTION, so a match is deleted child first: `dart`, `leg`, `match_player`, `match_record`, `session`.
 */
@Dao
interface ProfileDeletionDao {

    /** The profile of the anonymous key (PM7), or `null` when no deletion created it yet. */
    @Query("SELECT * FROM profile WHERE name_key = :anonymousKey")
    fun findAnonymous(anonymousKey: String): ProfileEntity?

    /** Every participation of every match the profile [id] played (PM6): the input of the deletion plan. */
    @Query(
        """
        SELECT session_id, profile_id FROM match_player
        WHERE session_id IN (SELECT session_id FROM match_player WHERE profile_id = :id)
        """,
    )
    fun participationsOfMatchesOf(id: Long): List<SessionParticipant>

    @Query("DELETE FROM dart WHERE leg_id IN (SELECT id FROM leg WHERE session_id IN (:sessionIds))")
    fun deleteDartsOf(sessionIds: List<Long>)

    @Query("DELETE FROM leg WHERE session_id IN (:sessionIds)")
    fun deleteLegsOf(sessionIds: List<Long>)

    @Query("DELETE FROM match_player WHERE session_id IN (:sessionIds)")
    fun deleteMatchPlayersOf(sessionIds: List<Long>)

    @Query("DELETE FROM match_record WHERE session_id IN (:sessionIds)")
    fun deleteMatchRecordsOf(sessionIds: List<Long>)

    @Query("DELETE FROM session WHERE id IN (:sessionIds)")
    fun deleteSessions(sessionIds: List<Long>)
}

/**
 * PM6 and PM7 on the database: computes the [DeletionPlan] of a profile and applies it. Call inside one transaction
 * (PM10): [RoomPlayersRepository] does.
 */
class ProfileDeleter(private val database: HistoryDatabase) {

    /** The PM6 plan of the profile [id], or `null` when it is unknown or the anonymous profile (PM7). */
    fun plan(id: Long): DeletionPlan? {
        val profile = database.playersDao().find(id)?.takeUnless { it.nameKey == ProfileRules.ANONYMOUS_NAME_KEY }
            ?: return null
        val deletion = database.profileDeletionDao()
        val anonymousId = deletion.findAnonymous(ProfileRules.ANONYMOUS_NAME_KEY)?.id
        val matches = deletion.participationsOfMatchesOf(profile.id).groupBy({ it.sessionId }, { it.profileId })
        return ProfileRules.deletionPlan(profile.id, anonymousId, matches)
    }

    /** Applies [plan] (from [plan]) to the profile [id]: the anonymous profile is created at [at] when needed. */
    fun apply(id: Long, plan: DeletionPlan, at: Long) {
        val deletion = database.profileDeletionDao()
        plan.toDelete.chunked(SESSIONS_PER_STATEMENT).forEach { sessions ->
            deletion.deleteDartsOf(sessions)
            deletion.deleteLegsOf(sessions)
            deletion.deleteMatchPlayersOf(sessions)
            deletion.deleteMatchRecordsOf(sessions)
            deletion.deleteSessions(sessions)
        }
        val players = database.playersDao()
        if (plan.toReassign.isNotEmpty()) {
            val anonymousId = deletion.findAnonymous(ProfileRules.ANONYMOUS_NAME_KEY)?.id
                ?: database.profileDao().upsertUsed(ANONYMOUS_NAME, ProfileRules.ANONYMOUS_NAME_KEY, at)
            players.moveParticipations(id, anonymousId)
        }
        players.deleteProfile(id)
    }

    private companion object {
        /** The stored name of the anonymous profile; screens show the "Deleted player" resource instead (ADR 0014). */
        const val ANONYMOUS_NAME = "Deleted player"

        /** Well under SQLite's limit of bound variables per statement. */
        const val SESSIONS_PER_STATEMENT = 500
    }
}
