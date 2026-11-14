package io.github.darkcosmos.kosmodarts.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** A profile row of the Players screen: its match count and the end of its latest match (PM2). */
data class PlayerRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "match_count") val matchCount: Int,
    @ColumnInfo(name = "last_ended_at") val lastEndedAt: Long?,
)

/**
 * Reads and writes behind the Players screen (PM2 to PM5); the deletion of PM6 is [ProfileDeletionDao]. The caller,
 * [RoomPlayersRepository], runs the multi-statement actions in one transaction (PM10).
 */
@Dao
interface PlayersDao {

    /** Every profile except the anonymous one (PM7), most recently used first (PM2). */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.name_key AS name_key,
               COUNT(DISTINCT mp.session_id) AS match_count, MAX(s.ended_at) AS last_ended_at
        FROM profile p
        LEFT JOIN match_player mp ON mp.profile_id = p.id
        LEFT JOIN session s ON s.id = mp.session_id
        WHERE p.name_key <> :anonymousKey
        GROUP BY p.id
        ORDER BY p.last_used_at DESC, p.id DESC
        """,
    )
    fun rows(anonymousKey: String): List<PlayerRow>

    /** The profile [id], or `null` when there is none. */
    @Query("SELECT * FROM profile WHERE id = :id")
    fun find(id: Long): ProfileEntity?

    /** Every profile (name keys for the clash check of PM3). */
    @Query("SELECT * FROM profile")
    fun all(): List<ProfileEntity>

    /** The ids of the sessions the profile [id] played (PM4). */
    @Query("SELECT session_id FROM match_player WHERE profile_id = :id")
    fun sessionIdsOf(id: Long): List<Long>

    @Query("UPDATE profile SET name = :name, name_key = :nameKey WHERE id = :id")
    fun updateName(id: Long, name: String, nameKey: String)

    @Query("UPDATE profile SET reset_at = :at WHERE id = :id")
    fun updateResetAt(id: Long, at: Long)

    @Query(
        "UPDATE profile SET created_at = :createdAt, last_used_at = :lastUsedAt, reset_at = :resetAt WHERE id = :id",
    )
    fun updateDates(id: Long, createdAt: Long, lastUsedAt: Long, resetAt: Long)

    /** Moves every participation of [fromId] to [toId] (PM4, PM6). */
    @Query("UPDATE match_player SET profile_id = :toId WHERE profile_id = :fromId")
    fun moveParticipations(fromId: Long, toId: Long)

    @Query("DELETE FROM profile WHERE id = :id")
    fun deleteProfile(id: Long)
}
