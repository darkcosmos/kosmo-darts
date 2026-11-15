package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.journal.JournalReplayException
import io.github.darkcosmos.kosmodarts.engine.journal.LegReplay
import io.github.darkcosmos.kosmodarts.engine.journal.LegSetup
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** How many zones [Heatmap.topZones] is asked for by the screens (HM6). */
const val TOP_ZONES: Int = 5

/** A zone with its dart count and its share (ratio 0 to 1) of all darts, misses included (HM2). */
data class ZoneShare(val zone: BoardZone, val count: Int, val ratio: Double)

/**
 * The darts of a selection counted per zone (HM1, HM2).
 *
 * @property counts darts per zone; a zone without a dart is absent.
 * @property misses darts that hit no zone: the darts recorded as a miss (in Cricket this includes a dart outside the
 *   Cricket numbers, which the input records as a miss).
 * @property total all counted darts, misses included, voided darts excluded.
 * @property includesCricket whether at least one counted dart was thrown in Cricket: the miss label is then
 *   "Miss or outside Cricket numbers" (HM4).
 */
data class Heatmap(
    val counts: Map<BoardZone, Int>,
    val misses: Int,
    val total: Int,
    val includesCricket: Boolean,
) {
    /** Ratio (0 to 1, unrounded: the screens round and show a percentage) of [zone] over [total], `null` if 0. */
    fun share(zone: BoardZone): Double? = if (total == 0) null else (counts[zone] ?: 0).toDouble() / total

    /** Ratio (0 to 1, unrounded) of the misses over [total], or `null` when [total] is 0 (HM7). */
    val missShare: Double? get() = if (total == 0) null else misses.toDouble() / total

    /**
     * The [limit] most hit zones, most first. Ties are broken by the order of [BoardZone.ALL], so the result is
     * stable. Zones without a dart are never listed.
     */
    fun topZones(limit: Int = TOP_ZONES): List<ZoneShare> = BoardZone.ALL
        .filter { (counts[it] ?: 0) > 0 }
        .sortedByDescending { counts.getValue(it) } // stable sort keeps the BoardZone.ALL order on ties
        .take(limit)
        .map { ZoneShare(it, counts.getValue(it), counts.getValue(it).toDouble() / total) }

    companion object {
        /** The heatmap of no dart (HM7). */
        val EMPTY: Heatmap = Heatmap(emptyMap(), misses = 0, total = 0, includesCricket = false)

        internal fun of(darts: List<Dart>, isCricket: Boolean): Heatmap {
            if (darts.isEmpty()) return EMPTY
            val zones = darts.mapNotNull { BoardZone.of(it) }
            return Heatmap(zones.groupingBy { it }.eachCount(), darts.size - zones.size, darts.size, isCricket)
        }
    }
}

/**
 * The heatmap of [profileId] over [matches] (HM1 to HM3): the darts in play of the profile in the matches that pass
 * [modeFilter]. [matches] is normally the result of [select]; its validity was checked by [StatsMatchValidator] (ST10),
 * so a corrupt match never reaches it. Never throws.
 */
fun heatmapOf(matches: List<StatsMatch>, profileId: Long, modeFilter: StatsFilter): Heatmap {
    val counted = matches.filter { modeFilter.accepts(it.settings) }.mapNotNull { match ->
        match.playerOf(profileId)?.let { match to match.dartsOf(it.position).map(StatsDart::dart) }
    }
    val isCricket = counted.any { (match, darts) -> match.settings is ArchivedSettings.Cricket && darts.isNotEmpty() }
    return Heatmap.of(counted.flatMap { it.second }, isCricket)
}

/** Which leg of the journal [lastLegHeatmap] reads (HM9). */
enum class LastLeg {
    /** The last completed leg: between legs, where the current leg is the empty next one. */
    COMPLETED,

    /** The current leg: on the end screens, where it is the finished last leg. */
    CURRENT,
}

/**
 * The heatmap of the darts in play of the player at [playerIndex] in one leg of [journal] (HM9). [setup] gives the
 * game and its configuration; its starting player is ignored, since the starter of leg n is player n modulo the
 * number of players (M6). A missing leg, an out-of-range player or a journal that cannot be replayed give
 * [Heatmap.EMPTY]; it never throws.
 */
fun lastLegHeatmap(journal: MatchJournal, setup: LegSetup, playerIndex: Int, lastLeg: LastLeg): Heatmap {
    val legIndex = when (lastLeg) {
        LastLeg.COMPLETED -> journal.legs.size - 2
        LastLeg.CURRENT -> journal.legs.size - 1
    }
    val darts = legDartsOf(journal, setup, playerIndex, legIndex) ?: return Heatmap.EMPTY
    return Heatmap.of(darts, isCricket = setup is LegSetup.Cricket)
}

/** The darts in play of [playerIndex] in the leg [legIndex], or `null` when the leg or the player does not exist. */
private fun legDartsOf(journal: MatchJournal, setup: LegSetup, playerIndex: Int, legIndex: Int): List<Dart>? {
    val leg = journal.legs.getOrNull(legIndex)
    val playerCount = when (setup) {
        is LegSetup.X01 -> setup.config.players.size
        is LegSetup.Cricket -> setup.config.players.size
    }
    if (leg == null || playerIndex !in 0 until playerCount) return null
    val starter = legIndex % playerCount
    val legSetup = when (setup) {
        is LegSetup.X01 -> setup.copy(startingPlayerIndex = starter)
        is LegSetup.Cricket -> setup.copy(startingPlayerIndex = starter)
    }
    return try {
        LegReplay.replay(leg, legSetup).darts.filter { it.playerIndex == playerIndex && !it.isVoided }.map { it.dart }
    } catch (ignored: JournalReplayException) {
        null
    }
}
