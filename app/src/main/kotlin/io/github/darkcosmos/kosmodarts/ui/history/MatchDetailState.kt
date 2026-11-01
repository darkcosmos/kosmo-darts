package io.github.darkcosmos.kosmodarts.ui.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMarks
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryPlayer
import io.github.darkcosmos.kosmodarts.history.MatchDetail

/** What the last read of the match gave (HS7): nothing yet, a failure, or the match. */
sealed interface MatchDetailContent {
    data object Loading : MatchDetailContent

    data object Failed : MatchDetailContent

    data class Loaded(val detail: MatchDetail) : MatchDetailContent
}

/**
 * State of the match detail screen (HS5, HS7). Pure: "Retry" asks for a new read by raising [loadId]; an answer to an
 * older read is ignored. Only [sessionId] has to survive a return after the process was killed (HS6).
 *
 * @property sessionId the session of the match, as [io.github.darkcosmos.kosmodarts.history.HistoryMatch.sessionId].
 * @property loadId number of the read this state waits for or shows.
 */
data class MatchDetailState(
    val sessionId: Long,
    val content: MatchDetailContent = MatchDetailContent.Loading,
    val loadId: Int = FIRST_LOAD,
) {
    /** The answer to read [id]; an older answer changes nothing. */
    fun loaded(id: Int, result: DbResult<MatchDetail>): MatchDetailState = when {
        id != loadId -> this
        result is DbResult.Ok -> copy(content = MatchDetailContent.Loaded(result.value))
        else -> copy(content = MatchDetailContent.Failed)
    }

    /** "Retry" (HS7): reads again; nothing happens unless the last read failed. */
    fun retry(): MatchDetailState = if (content == MatchDetailContent.Failed) {
        copy(content = MatchDetailContent.Loading, loadId = loadId + 1)
    } else {
        this
    }

    private companion object {
        const val FIRST_LOAD = 1
    }
}

/** A visit as the detail lists it (HS5): who threw, the darts in play, and the total or the bust. */
data class DetailVisit(
    val player: HistoryPlayer,
    val darts: List<Dart>,
    val isBust: Boolean,
    /** X01: the points of the darts (meaningless when [isBust]); Cricket: the marks (CR2, CR14). */
    val total: Int,
)

/**
 * A leg of the detail (HS5): its position in the match, who started and who won it, and its visits in order.
 *
 * @property hasSetNumber whether the heading names the set (the format has several sets, M19).
 */
data class DetailLeg(
    val setNumber: Int,
    val legNumber: Int,
    val hasSetNumber: Boolean,
    val starter: HistoryPlayer,
    val winner: HistoryPlayer,
    val visits: List<DetailVisit>,
)

/** Duration of a match in whole minutes, rounded down (HS5, owner Q8); never negative. */
fun MatchDetail.durationMinutes(): Long = ((endedAt - startedAt).coerceAtLeast(0)) / MILLIS_PER_MINUTE

/** The players by final rank, then by throwing order (HS5). */
fun MatchDetail.ranking(): List<HistoryPlayer> = players.sortedBy { it.finalRank }

/** Whether the legs and sets are worth showing: the format has more than one leg (HS2, HS5). */
val MatchDetail.hasScore: Boolean get() = !settings.format.isSingleGame

/**
 * The legs of the match with their visits (HS5). A visit is the non-voided darts of one visit number of a leg, in the
 * rank order (DJ3); a visit whose darts are all voided is not listed. X01 visits carry the sum of their darts, and a
 * bust visit is flagged; Cricket visits carry their marks and are never bust.
 */
fun MatchDetail.legs(): List<DetailLeg> {
    val isCricket = settings is ArchivedSettings.Cricket
    val isBullInPlay = (settings as? ArchivedSettings.Cricket)?.isBullInPlay ?: true
    val dartsByLeg = darts.filter { !it.isVoided }.groupBy { it.legOrdinal }
    return legs.map { leg ->
        val visits = dartsByLeg[leg.ordinal].orEmpty()
            .groupBy { it.visit }
            .toSortedMap()
            .values
            .map { visitDarts -> toVisit(visitDarts.sortedBy { it.rank }, isCricket, isBullInPlay) }
        DetailLeg(
            setNumber = leg.setNumber,
            legNumber = leg.legNumberInSet,
            hasSetNumber = settings.format.hasSets,
            starter = playerAt(leg.starterPosition),
            winner = playerAt(leg.winnerPosition),
            visits = visits,
        )
    }
}

private fun MatchDetail.toVisit(
    visitDarts: List<ArchivedDart>,
    isCricket: Boolean,
    isBullInPlay: Boolean,
): DetailVisit {
    val darts = visitDarts.map { Dart(it.segment, it.multiplier) }
    val total = if (isCricket) {
        darts.sumOf { CricketMarks.of(it, isBullInPlay).count }
    } else {
        darts.sumOf { it.value }
    }
    return DetailVisit(
        player = playerAt(visitDarts.first().playerPosition),
        darts = darts,
        isBust = !isCricket && visitDarts.any { it.isBust },
        total = total,
    )
}

private fun MatchDetail.playerAt(position: Int): HistoryPlayer =
    checkNotNull(players.getOrNull(position)) { "No player at this position" }

private const val MILLIS_PER_MINUTE = 60_000L
