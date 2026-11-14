package io.github.darkcosmos.kosmodarts.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode

/** A dart of a batch of sessions, with its session id and leg ordinal; positions are not read (no D33 columns). */
data class StatsDartRow(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "leg_ordinal") val legOrdinal: Int,
    @ColumnInfo(name = "player_position") val playerPosition: Int,
    @ColumnInfo(name = "visit") val visit: Int,
    @ColumnInfo(name = "rank") val rank: Int,
    @ColumnInfo(name = "segment") val segment: Int,
    @ColumnInfo(name = "multiplier") val multiplier: Int,
    @ColumnInfo(name = "voided") val voided: Boolean,
)

/** The session facts the statistics need: dates from the session, mode and settings text from the match record. */
data class StatsSessionRow(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "mode") val mode: ArchivedMode,
    @ColumnInfo(name = "settings") val settings: String,
)

/**
 * Statistics reads (plan "Approach" 2b, ST10), all read-only and over the existing indices: the sessions of a
 * profile, then the rows of a batch of sessions. Darts keep their entry order inside a visit rank (`id`), so a voided
 * dart comes before the dart that replaced it.
 */
@Dao
interface StatsDao {

    /** The profile [id], or `null`. */
    @Query("SELECT * FROM profile WHERE id = :id")
    fun profile(id: Long): ProfileEntity?

    /** The ids of the archived matches [profileId] played, oldest end first (ties by id). */
    @Query(
        """
        SELECT DISTINCT s.id FROM session s
        JOIN match_record m ON m.session_id = s.id
        JOIN match_player p ON p.session_id = s.id
        WHERE p.profile_id = :profileId
        ORDER BY s.ended_at, s.id
        """,
    )
    fun sessionIds(profileId: Long): List<Long>

    @Query(
        """
        SELECT s.id AS session_id, s.started_at, s.ended_at, m.mode, m.settings FROM session s
        JOIN match_record m ON m.session_id = s.id
        WHERE s.id IN (:sessionIds)
        """,
    )
    fun sessions(sessionIds: List<Long>): List<StatsSessionRow>

    @Query("SELECT * FROM match_player WHERE session_id IN (:sessionIds) ORDER BY session_id, position")
    fun players(sessionIds: List<Long>): List<MatchPlayerEntity>

    @Query("SELECT * FROM leg WHERE session_id IN (:sessionIds) ORDER BY session_id, ordinal")
    fun legs(sessionIds: List<Long>): List<LegEntity>

    @Query(
        """
        SELECT l.session_id, l.ordinal AS leg_ordinal, d.player_position, d.visit, d.rank, d.segment, d.multiplier,
               d.voided
        FROM dart d JOIN leg l ON l.id = d.leg_id
        WHERE l.session_id IN (:sessionIds)
        ORDER BY l.session_id, l.ordinal, d.visit, d.rank, d.id
        """,
    )
    fun darts(sessionIds: List<Long>): List<StatsDartRow>
}
