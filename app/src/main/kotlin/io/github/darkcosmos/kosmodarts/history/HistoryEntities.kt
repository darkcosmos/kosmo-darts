package io.github.darkcosmos.kosmodarts.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.SessionType
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource

// Schema version 1 of the archive database (ADR 0012, "Tables"). Every column is NOT NULL (plan D31) except
// dart.x_mm and dart.y_mm (D33). Times are epoch milliseconds. Every foreign key is NO ACTION: nothing is deleted
// (AR8). Enum columns are stored as text (their names).

/** A player profile (PF1). [resetAt] equals [createdAt] at creation and never changes in this mission (D32). */
@Entity(
    tableName = "profile",
    indices = [
        Index(value = ["name_key"], unique = true),
        Index(value = ["last_used_at"]),
    ],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "reset_at") val resetAt: Long,
)

/** A session (AR5): one archived match; `TRAINING` is reserved. */
@Entity(tableName = "session", indices = [Index(value = ["ended_at"])])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "type") val type: SessionType,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "rules_version") val rulesVersion: Int,
    @ColumnInfo(name = "app_version") val appVersion: String,
)

/** The match of a session (AR5); named `match_record` because `MATCH` is an SQLite keyword (review A6). */
@Entity(
    tableName = "match_record",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"]),
    ],
    indices = [
        Index(value = ["match_uuid"], unique = true),
        Index(value = ["mode"]),
    ],
)
data class MatchRecordEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "match_uuid") val matchUuid: String,
    @ColumnInfo(name = "mode") val mode: ArchivedMode,
    @ColumnInfo(name = "variant") val variant: String,
    @ColumnInfo(name = "settings") val settings: String,
    @ColumnInfo(name = "settings_version") val settingsVersion: Int,
)

/** A player of an archived match, in throwing order (AR5, AR6). */
@Entity(
    tableName = "match_player",
    primaryKeys = ["session_id", "position"],
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"]),
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"]),
    ],
    indices = [Index(value = ["profile_id"])],
)
data class MatchPlayerEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    @ColumnInfo(name = "final_rank") val finalRank: Int,
    @ColumnInfo(name = "legs_won") val legsWon: Int,
    @ColumnInfo(name = "sets_won") val setsWon: Int,
)

/** A leg of an archived match (AR5); [ordinal] counts from 0 over the match. */
@Entity(
    tableName = "leg",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"]),
    ],
    indices = [Index(value = ["session_id", "ordinal"], unique = true)],
)
data class LegEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "ordinal") val ordinal: Int,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "leg_number") val legNumber: Int,
    @ColumnInfo(name = "starter_position") val starterPosition: Int,
    @ColumnInfo(name = "winner_position") val winnerPosition: Int,
)

/**
 * A dart of an archived match (AR5, AR10). [segment] is 0 (miss), 1 to 20 or 25 (outer bull with [multiplier] 1,
 * bull with 2); [multiplier] is 1 to 3. [xMm] and [yMm] are both null or both set: only [ArchiveMapper] reads or
 * writes them, every other type holds one `BoardPoint?` (D33, review A11).
 */
@Entity(
    tableName = "dart",
    foreignKeys = [
        ForeignKey(entity = LegEntity::class, parentColumns = ["id"], childColumns = ["leg_id"]),
    ],
    indices = [Index(value = ["leg_id", "visit", "rank"])],
)
data class DartEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "leg_id") val legId: Long,
    @ColumnInfo(name = "player_position") val playerPosition: Int,
    @ColumnInfo(name = "visit") val visit: Int,
    @ColumnInfo(name = "rank") val rank: Int,
    @ColumnInfo(name = "segment") val segment: Int,
    @ColumnInfo(name = "multiplier") val multiplier: Int,
    @ColumnInfo(name = "bust") val bust: Boolean,
    @ColumnInfo(name = "voided") val voided: Boolean,
    @ColumnInfo(name = "source") val source: DartSource,
    @ColumnInfo(name = "x_mm") val xMm: Double?,
    @ColumnInfo(name = "y_mm") val yMm: Double?,
    @ColumnInfo(name = "entered_at") val enteredAt: Long,
)
