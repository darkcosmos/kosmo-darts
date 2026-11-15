package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/** Why a match was rejected (ST10). */
enum class InvalidReason {
    SETTINGS_UNDECODABLE,
    PLAYER_COUNT,
    PLAYER_POSITION,
    PLAYER_RANK,
    LEGS_WON,
    SETS_WON,
    NO_LEG,
    LEG_ORDINAL,
    LEG_STARTER,
    LEG_WITHOUT_DARTS,
    DART_LEG,
    UNKNOWN_DART,
    DART_REJECTED,
    DART_THROWER,
    LEG_WINNER,
}

/** The outcome of [StatsMatchValidator.validate]. */
sealed interface StatsValidation {
    data class Valid(val match: StatsMatch) : StatsValidation

    data class Invalid(val reason: InvalidReason) : StatsValidation
}

/** The valid matches of a list, in order, and how many were ignored (ST10). */
data class StatsMatches(val matches: List<StatsMatch>, val ignoredCount: Int)

/**
 * Accepts an archived match only if it obeys the rules (ST10, plan "Approach" 2b): the settings decode; positions,
 * ranks, legs won and sets won are in range; every leg has darts; every dart is known; and the replay of each leg
 * with the existing engines (voided darts left out, DJ2) accepts every dart, with the stored thrower, and ends with
 * the stored winner. A leg is never replayed past its first rejected dart. Never throws.
 */
object StatsMatchValidator {

    /** Validates [raw]. */
    fun validate(raw: RawStatsMatch): StatsValidation {
        val settings = raw.settings
        return if (settings == null) invalid(InvalidReason.SETTINGS_UNDECODABLE) else validateDecoded(raw, settings)
    }

    private fun validateDecoded(raw: RawStatsMatch, settings: ArchivedSettings): StatsValidation {
        val header = headerReason(raw, settings.format)
        val (legs, rejection) = if (header == null) replayLegs(raw, settings) else emptyList<StatsLeg>() to header
        val players = raw.players.sortedBy { it.position }
            .map { StatsPlayer(it.profileId, it.position, it.rank, it.legsWon, it.setsWon) }
        return if (rejection == null) {
            StatsValidation.Valid(StatsMatch(raw.sessionId, raw.startedAt, raw.endedAt, settings, players, legs))
        } else {
            invalid(rejection)
        }
    }

    /** Replays the legs in order and stops at the first rejected one: the accepted legs and the rejection, if any. */
    private fun replayLegs(raw: RawStatsMatch, settings: ArchivedSettings): Pair<List<StatsLeg>, InvalidReason?> {
        val darts = raw.darts.groupBy { it.legOrdinal }
        val accepted = ArrayList<StatsLeg>(raw.legs.size)
        var rejection: InvalidReason? = null
        for (leg in raw.legs) {
            val result = replayLeg(settings, raw.players.size, leg, darts[leg.ordinal].orEmpty())
            when (result) {
                is LegResult.Accepted -> accepted += result.leg
                is LegResult.Rejected -> rejection = result.reason
            }
            if (rejection != null) break
        }
        return accepted to rejection
    }

    /** Validates every match of [raws]: the valid ones unchanged and in order, plus the count of the others. */
    fun validateAll(raws: List<RawStatsMatch>): StatsMatches {
        val valid = raws.mapNotNull { (validate(it) as? StatsValidation.Valid)?.match }
        return StatsMatches(valid, raws.size - valid.size)
    }

    private fun invalid(reason: InvalidReason): StatsValidation = StatsValidation.Invalid(reason)

    private fun headerReason(raw: RawStatsMatch, format: MatchFormat): InvalidReason? {
        val count = raw.players.size
        val maxSets = count * (format.sets - 1) + 1
        return when {
            count !in 1..PlayerListRules.MAX_PLAYERS -> InvalidReason.PLAYER_COUNT
            raw.players.map { it.position }.sorted() != (0 until count).toList() -> InvalidReason.PLAYER_POSITION
            raw.players.any { it.rank !in 1..count } -> InvalidReason.PLAYER_RANK
            raw.players.any { it.legsWon !in 0..format.maxLegs(count) } -> InvalidReason.LEGS_WON
            raw.players.any { it.setsWon !in 0..maxSets } -> InvalidReason.SETS_WON
            raw.legs.isEmpty() -> InvalidReason.NO_LEG
            raw.legs.map { it.ordinal }.distinct().size != raw.legs.size -> InvalidReason.LEG_ORDINAL
            raw.legs.any { it.starterPosition !in 0 until count || it.winnerPosition !in 0 until count } ->
                InvalidReason.LEG_STARTER
            raw.darts.any { dart -> raw.legs.none { it.ordinal == dart.legOrdinal } } -> InvalidReason.DART_LEG
            else -> null
        }
    }

    private sealed interface LegResult {
        data class Accepted(val leg: StatsLeg) : LegResult

        data class Rejected(val reason: InvalidReason) : LegResult
    }

    private fun replayLeg(
        settings: ArchivedSettings,
        playerCount: Int,
        leg: RawStatsLeg,
        rawDarts: List<RawStatsDart>,
    ): LegResult {
        val inPlay = rawDarts.filterNot { it.isVoided }
        val known = inPlay.mapNotNull { toDart(it) }
        val names = List(playerCount) { "P${it + 1}" }
        val facts = when {
            inPlay.isEmpty() -> LegFacts(InvalidReason.LEG_WITHOUT_DARTS)
            known.size != inPlay.size -> LegFacts(InvalidReason.UNKNOWN_DART)
            settings is ArchivedSettings.X01 -> x01Facts(settings, names, leg, known)
            settings is ArchivedSettings.Cricket -> cricketFacts(settings, names, leg, known)
            else -> LegFacts(InvalidReason.SETTINGS_UNDECODABLE)
        }
        val reason = facts.reason ?: throwerReason(inPlay, facts.throwers)
        return if (reason == null) {
            val statsDarts = inPlay.indices.map { statsDart(inPlay[it], known[it], facts, it) }
            LegResult.Accepted(StatsLeg(leg.ordinal, leg.starterPosition, leg.winnerPosition, statsDarts))
        } else {
            LegResult.Rejected(reason)
        }
    }

    private fun statsDart(raw: RawStatsDart, dart: Dart, facts: LegFacts, index: Int) = StatsDart(
        position = raw.position,
        visit = raw.visit,
        rank = raw.rank,
        dart = dart,
        remainingBefore = facts.remaining?.get(index),
        isBust = facts.bust?.get(index) ?: false,
        yield = facts.yields?.get(index),
    )

    private fun throwerReason(inPlay: List<RawStatsDart>, throwers: List<Int>): InvalidReason? =
        if (inPlay.indices.all { inPlay[it].position == throwers[it] }) null else InvalidReason.DART_THROWER

    private val specialSegments = setOf(Dart.MISS_SEGMENT, Dart.OUTER_BULL_SEGMENT, Dart.BULL_SEGMENT)

    /** The dart for [raw], or `null` for an unknown segment or multiplier (D1, D2) without throwing. */
    private fun toDart(raw: RawStatsDart): Dart? {
        val multiplier = raw.multiplier ?: return null
        val isKnown = raw.segment in Dart.NUMBERS ||
            (raw.segment in specialSegments && multiplier == Multiplier.SINGLE)
        return if (isKnown) Dart(raw.segment, multiplier) else null
    }
}
