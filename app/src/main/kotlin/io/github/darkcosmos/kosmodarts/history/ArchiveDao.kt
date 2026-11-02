package io.github.darkcosmos.kosmodarts.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Archive writes (AR2, AR4). Every insert aborts on a conflict; there is no update and no delete (AR8). The caller,
 * [ArchiveRepository], runs them in one transaction.
 */
@Dao
interface ArchiveDao {

    /** Whether the match [matchUuid] is already archived (AR4). */
    @Query("SELECT EXISTS(SELECT 1 FROM match_record WHERE match_uuid = :matchUuid)")
    fun isArchived(matchUuid: String): Boolean

    @Insert
    fun insertSession(session: SessionEntity): Long

    @Insert
    fun insertMatchRecord(match: MatchRecordEntity)

    @Insert
    fun insertPlayers(players: List<MatchPlayerEntity>)

    /** Inserts [leg] and returns its id. */
    @Insert
    fun insertLeg(leg: LegEntity): Long

    @Insert
    fun insertDarts(darts: List<DartEntity>)
}
