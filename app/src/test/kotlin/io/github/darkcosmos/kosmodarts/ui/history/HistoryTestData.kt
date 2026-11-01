package io.github.darkcosmos.kosmodarts.ui.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.history.HistoryList
import io.github.darkcosmos.kosmodarts.history.HistoryMatch
import io.github.darkcosmos.kosmodarts.history.HistoryPlayer
import io.github.darkcosmos.kosmodarts.history.HistoryProfile
import io.github.darkcosmos.kosmodarts.history.HistoryRepository

const val BLUE: Int = 0xFF2196F3.toInt()
const val ORANGE: Int = 0xFFFF9800.toInt()
const val GREEN: Int = 0xFF4CAF50.toInt()
const val PINK: Int = 0xFFE91E63.toInt()

/** 2026-11-01 12:00:00 UTC, in milliseconds since the epoch. */
const val NOON_MILLIS: Long = 1_793_534_400_000L

/** The players of a test match: [names] in throwing order, the first one ranked first, in the four palette colors. */
fun historyPlayers(vararg names: String, legs: List<Int> = emptyList(), sets: List<Int> = emptyList()) =
    names.mapIndexed { position, name ->
        HistoryPlayer(
            name = name,
            colorArgb = listOf(BLUE, ORANGE, GREEN, PINK)[position],
            finalRank = position + 1,
            legsWon = legs.getOrElse(position) { 0 },
            setsWon = sets.getOrElse(position) { 0 },
        )
    }

/** An X01 double-out match ended at [endedAt]; a single game unless [format] says otherwise. */
fun x01HistoryMatch(
    sessionId: Long,
    vararg names: String,
    endedAt: Long = NOON_MILLIS + sessionId * 60_000L,
    format: MatchFormat = MatchFormat(),
    legs: List<Int> = emptyList(),
    sets: List<Int> = emptyList(),
) = HistoryMatch(
    sessionId = sessionId,
    endedAt = endedAt,
    settings = ArchivedSettings.X01(501, CheckoutRule.DOUBLE_OUT, format),
    players = historyPlayers(*names, legs = legs, sets = sets),
)

/** A cut-throat Cricket single game. */
fun cricketHistoryMatch(sessionId: Long, vararg names: String, endedAt: Long = NOON_MILLIS + sessionId * 60_000L) =
    HistoryMatch(
        sessionId = sessionId,
        endedAt = endedAt,
        settings = ArchivedSettings.Cricket(CricketVariant.CUT_THROAT, isBullInPlay = false, format = MatchFormat()),
        players = historyPlayers(*names),
    )

fun profilesOf(vararg names: String): List<HistoryProfile> =
    names.mapIndexed { index, name -> HistoryProfile(id = index + 1L, name = name) }

fun okList(matches: List<HistoryMatch>, profiles: List<HistoryProfile> = emptyList()): DbResult<HistoryList> =
    DbResult.Ok(HistoryList(matches, profiles))

/** A repository that answers [answer] to every read and records the filters asked for. */
class FakeHistoryRepository(var answer: DbResult<HistoryList> = okList(emptyList())) : HistoryRepository {
    val asked = mutableListOf<HistoryFilters>()

    override fun list(filters: HistoryFilters): DbResult<HistoryList> {
        asked += filters
        return answer
    }
}
