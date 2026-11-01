package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedLeg
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings

/**
 * The two History filters (HS3): a `null` mode is "All", a `null` [profileId] is "All". They combine.
 *
 * @property profileId the database id of a profile, as [HistoryProfile.id] gives it.
 */
data class HistoryFilters(val mode: ArchivedMode? = null, val profileId: Long? = null) {
    /** Whether at least one filter narrows the list (HS4: "no match" rather than "no archived match"). */
    val isActive: Boolean
        get() = mode != null || profileId != null
}

/** A profile offered by the player filter (HS3). */
data class HistoryProfile(val id: Long, val name: String)

/** A player of a listed match, in throwing order, with the values stored at the confirmation (AR6). */
data class HistoryPlayer(
    val name: String,
    val colorArgb: Int,
    val finalRank: Int,
    val legsWon: Int,
    val setsWon: Int,
)

/** One archived match as History lists it (HS2). */
data class HistoryMatch(
    val sessionId: Long,
    val endedAt: Long,
    val settings: ArchivedSettings,
    val players: List<HistoryPlayer>,
) {
    /** The players ranked first, by throwing order: the winner of a match (HS2). */
    val winners: List<HistoryPlayer>
        get() = players.filter { it.finalRank == WINNER_RANK }

    /** Whether the score is worth showing: the format has more than one leg (HS2). */
    val hasScore: Boolean
        get() = !settings.format.isSingleGame

    private companion object {
        const val WINNER_RANK = 1
    }
}

/** What History shows: the matches matching the filters, most recent first, and every profile for the filter. */
data class HistoryList(val matches: List<HistoryMatch>, val profiles: List<HistoryProfile>)

/**
 * One archived match as its detail shows it (HS5): the stored values, not recomputed (AR10).
 *
 * @property startedAt start of the match, milliseconds since the epoch (D31: always present).
 * @property endedAt confirmation of the match, milliseconds since the epoch.
 * @property players in throwing order: the index of a player is its position.
 * @property legs in play order.
 * @property darts every dart of every leg, voided ones included, by leg then entry order.
 */
data class MatchDetail(
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val settings: ArchivedSettings,
    val players: List<HistoryPlayer>,
    val legs: List<ArchivedLeg>,
    val darts: List<ArchivedDart>,
)

/**
 * History reads (HS2, HS3, HS5). Blocking: runs on the read executor, never on the main thread. Never throws: a
 * database failure gives [DbResult.Failed] (ADR 0012, "Failure contract", AR9, HS7).
 */
interface HistoryRepository {
    /** The matches matching [filters] and every profile, read in one transaction. One light query per table. */
    fun list(filters: HistoryFilters): DbResult<HistoryList>

    /** The match of session [sessionId] with its legs and darts, read in one transaction (HS5). */
    fun match(sessionId: Long): DbResult<MatchDetail>
}

/** The Room implementation: summaries and players in two queries, joined in memory (no query per match). */
class RoomHistoryRepository(private val database: HistoryDatabase) : HistoryRepository {

    override fun match(sessionId: Long): DbResult<MatchDetail> = dbResult {
        val dao = database.historyDao()
        database.runInTransaction<MatchDetail> {
            // A match that is not there is a database fault for the screen (HS7), like a row it cannot read.
            val summary = checkNotNull(dao.matchSummary(sessionId)) { "Missing match" }
            MatchDetail(
                sessionId = summary.sessionId,
                startedAt = summary.startedAt,
                endedAt = summary.endedAt,
                settings = SettingsText.decode(summary.settings, summary.mode),
                players = dao.playersOf(sessionId).map { it.toHistoryPlayer() },
                legs = dao.legsOf(sessionId).map(ArchiveMapper::archivedLeg),
                darts = dao.dartsOf(sessionId).map(ArchiveMapper::archivedDart),
            ).also(::checkPositions)
        }
    }

    /** Every position a leg or a dart names must be a player of the match, or the rows are a database fault. */
    private fun checkPositions(match: MatchDetail) {
        val positions = match.players.indices
        check(match.players.isNotEmpty()) { "Match without players" }
        check(match.legs.all { it.starterPosition in positions && it.winnerPosition in positions }) { "Bad leg" }
        check(match.darts.all { it.playerPosition in positions }) { "Bad dart" }
    }

    override fun list(filters: HistoryFilters): DbResult<HistoryList> = dbResult {
        val dao = database.historyDao()
        database.runInTransaction<HistoryList> {
            val playersBySession = dao.matchPlayers(filters.mode, filters.profileId).groupBy { it.sessionId }
            val matches = dao.matchSummaries(filters.mode, filters.profileId).map { summary ->
                HistoryMatch(
                    sessionId = summary.sessionId,
                    endedAt = summary.endedAt,
                    settings = SettingsText.decode(summary.settings, summary.mode),
                    players = playersBySession[summary.sessionId].orEmpty().map { it.toHistoryPlayer() },
                )
            }
            HistoryList(matches, dao.profilesByRecency().map { HistoryProfile(it.id, it.name) })
        }
    }

    private fun MatchPlayerRow.toHistoryPlayer() = HistoryPlayer(name, colorArgb, finalRank, legsWon, setsWon)
}
