package io.github.darkcosmos.kosmodarts.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode

/** One archived match as listed by History (HS2), with its session values (AR5). */
data class MatchSummaryRow(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "rules_version") val rulesVersion: Int,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "mode") val mode: ArchivedMode,
    @ColumnInfo(name = "variant") val variant: String,
    @ColumnInfo(name = "settings") val settings: String,
    @ColumnInfo(name = "settings_version") val settingsVersion: Int,
)

/** A player of an archived match with its profile name (HS2, AR6). */
data class MatchPlayerRow(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    @ColumnInfo(name = "final_rank") val finalRank: Int,
    @ColumnInfo(name = "legs_won") val legsWon: Int,
    @ColumnInfo(name = "sets_won") val setsWon: Int,
)

/** A dart with the ordinal of its leg; read through [ArchiveMapper] only (positions, D33). */
data class DartWithLegRow(
    @Embedded val dart: DartEntity,
    @ColumnInfo(name = "leg_ordinal") val legOrdinal: Int,
)

/**
 * History reads (HS2, HS3, HS5), all read-only. A `null` [ArchivedMode] or profile id in a filter means "All" (HS3);
 * both filters combine.
 */
@Dao
interface HistoryDao {

    /** Archived matches matching the filters, from the most recent end to the oldest (HS2, HS3). */
    @Query(
        """
        SELECT s.id AS session_id, s.started_at, s.ended_at, s.rules_version, s.app_version,
            m.mode, m.variant, m.settings, m.settings_version
        FROM session s JOIN match_record m ON m.session_id = s.id
        WHERE (:mode IS NULL OR m.mode = :mode)
            AND (:profileId IS NULL
                OR EXISTS (SELECT 1 FROM match_player f WHERE f.session_id = s.id AND f.profile_id = :profileId))
        ORDER BY s.ended_at DESC, s.id DESC
        """,
    )
    fun matchSummaries(mode: ArchivedMode?, profileId: Long?): List<MatchSummaryRow>

    /** Every player of the matches [matchSummaries] returns for the same filters, by match then position. */
    @Query(
        """
        SELECT p.session_id, p.position, p.profile_id, r.name, p.color_argb, p.final_rank, p.legs_won, p.sets_won
        FROM match_player p
            JOIN profile r ON r.id = p.profile_id
            JOIN match_record m ON m.session_id = p.session_id
        WHERE (:mode IS NULL OR m.mode = :mode)
            AND (:profileId IS NULL
                OR EXISTS (SELECT 1 FROM match_player f WHERE f.session_id = p.session_id AND f.profile_id = :profileId))
        ORDER BY p.session_id, p.position
        """,
    )
    fun matchPlayers(mode: ArchivedMode?, profileId: Long?): List<MatchPlayerRow>

    /** Every profile, most recently used first (PF3, HS3). */
    @Query("SELECT * FROM profile ORDER BY last_used_at DESC, id DESC")
    fun profilesByRecency(): List<ProfileEntity>

    /** The match of session [sessionId], or `null` when there is none (HS5). */
    @Query(
        """
        SELECT s.id AS session_id, s.started_at, s.ended_at, s.rules_version, s.app_version,
            m.mode, m.variant, m.settings, m.settings_version
        FROM session s JOIN match_record m ON m.session_id = s.id
        WHERE s.id = :sessionId
        """,
    )
    fun matchSummary(sessionId: Long): MatchSummaryRow?

    /** The players of session [sessionId], in throwing order. */
    @Query(
        """
        SELECT p.session_id, p.position, p.profile_id, r.name, p.color_argb, p.final_rank, p.legs_won, p.sets_won
        FROM match_player p JOIN profile r ON r.id = p.profile_id
        WHERE p.session_id = :sessionId
        ORDER BY p.position
        """,
    )
    fun playersOf(sessionId: Long): List<MatchPlayerRow>

    /** The legs of session [sessionId], in play order. */
    @Query("SELECT * FROM leg WHERE session_id = :sessionId ORDER BY ordinal")
    fun legsOf(sessionId: Long): List<LegEntity>

    /** Every dart of session [sessionId], voided ones included, by leg then entry order. */
    @Query(
        """
        SELECT d.*, l.ordinal AS leg_ordinal
        FROM dart d JOIN leg l ON l.id = d.leg_id
        WHERE l.session_id = :sessionId
        ORDER BY l.ordinal, d.id
        """,
    )
    fun dartsOf(sessionId: Long): List<DartWithLegRow>
}
