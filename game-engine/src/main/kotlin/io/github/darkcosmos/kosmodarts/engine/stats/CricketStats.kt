package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMarks
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant

/** Most useful marks of a visit: 3 darts of 3 marks (STC3). */
const val MAX_USEFUL_MARKS_PER_VISIT: Int = 9

private const val DARTS_PER_ROUND = 3

/**
 * The results of one profile in one Cricket variant (STC5). Solo matches are excluded (ST5).
 *
 * @property winRatio matches won / (won + lost), `null` without a result (ST6).
 */
data class CricketResults(
    val variant: CricketVariant,
    val matchesWon: Int,
    val matchesLost: Int,
    val winRatio: Double?,
    val legsWon: Int,
    val setsWon: Int,
)

/**
 * The Cricket figures of one profile over a selection of matches (STC2 to STC5). A figure that cannot be computed is
 * `null` (ST6).
 *
 * @property mpr marks per round: useful marks x 3 / darts (STC2).
 * @property bestVisit the most useful marks of a visit, at most [MAX_USEFUL_MARKS_PER_VISIT] (STC3).
 * @property marksPerTarget useful marks on each target, Bull covering 25 and Bull (STC3); every target is present.
 * @property pointsScored points scored in the Standard matches (STC4), `null` without a Standard leg.
 * @property pointsScoredPerLeg [pointsScored] per leg played (STC4).
 * @property pointsGiven points given in the Cut-throat matches (STC4), `null` without a Cut-throat leg.
 * @property pointsGivenPerLeg [pointsGiven] per leg played (STC4).
 * @property results one block per variant (STC5): both with "All variants", only the filtered one otherwise.
 */
data class CricketFigures(
    val mpr: Double?,
    val bestVisit: Int?,
    val marksPerTarget: Map<CricketTarget, Int>,
    val pointsScored: Int?,
    val pointsScoredPerLeg: Double?,
    val pointsGiven: Int?,
    val pointsGivenPerLeg: Double?,
    val results: List<CricketResults>,
)

/** Running sums of the Cricket figures of one profile. */
private class CricketTally {
    var useful = 0
    var darts = 0
    var bestVisit: Int? = null
    val perTarget = CricketTarget.entries.associateWith { 0 }.toMutableMap()
    var scored = 0
    var scoredLegs = 0
    var given = 0
    var givenLegs = 0
    val won = HashMap<CricketVariant, Int>()
    val lost = HashMap<CricketVariant, Int>()
    val legsWon = HashMap<CricketVariant, Int>()
    val setsWon = HashMap<CricketVariant, Int>()
}

/**
 * The Cricket figures of [profileId] over [matches], which the caller has already validated (ST10) and selected. X01
 * matches and matches without the profile are ignored. Reads the per-dart yields, never replays. [variant] is the
 * variant filter of the selection (`null` for All variants): it decides which result blocks are listed (STC5).
 */
fun cricketFigures(matches: List<StatsMatch>, profileId: Long, variant: CricketVariant? = null): CricketFigures {
    val tally = CricketTally()
    for (match in matches) tally.addMatch(match, profileId)
    return tally.toFigures(variant)
}

private fun CricketTally.addMatch(match: StatsMatch, profileId: Long) {
    val settings = match.settings as? ArchivedSettings.Cricket
    val player = match.playerOf(profileId)
    if (settings == null || player == null) return
    for (leg in match.legs) addLeg(leg, player.position, settings)
    if (!match.isSolo) addResult(settings.cricketVariant, player)
}

private fun CricketTally.addResult(variant: CricketVariant, player: StatsPlayer) {
    val counter = if (player.rank == 1) won else lost
    counter.merge(variant, 1, Int::plus)
    legsWon.merge(variant, player.legsWon, Int::plus)
    setsWon.merge(variant, player.setsWon, Int::plus)
}

private fun CricketTally.addLeg(leg: StatsLeg, position: Int, settings: ArchivedSettings.Cricket) {
    when (settings.cricketVariant) {
        CricketVariant.STANDARD -> scoredLegs++
        CricketVariant.CUT_THROAT -> givenLegs++
    }
    val darts = leg.darts.filter { it.position == position }
    for (dart in darts) addDart(dart, settings)
    for (visit in darts.groupBy { it.visit }.values) {
        val total = visit.sumOf { it.yield?.useful ?: 0 }
        bestVisit = maxOf(bestVisit ?: total, total)
    }
}

private fun CricketTally.addDart(dart: StatsDart, settings: ArchivedSettings.Cricket) {
    darts++
    val yield = dart.yield ?: return
    useful += yield.useful
    scored += yield.pointsScored
    given += yield.pointsGiven
    val target = CricketMarks.of(dart.dart, settings.isBullInPlay).target
    if (target != null) perTarget.merge(target, yield.useful, Int::plus)
}

private fun CricketTally.toFigures(variant: CricketVariant?): CricketFigures = CricketFigures(
    mpr = ratio(useful * DARTS_PER_ROUND, darts),
    bestVisit = bestVisit,
    marksPerTarget = perTarget.toMap(),
    pointsScored = scored.takeIf { scoredLegs > 0 },
    pointsScoredPerLeg = ratio(scored, scoredLegs),
    pointsGiven = given.takeIf { givenLegs > 0 },
    pointsGivenPerLeg = ratio(given, givenLegs),
    results = (variant?.let { listOf(it) } ?: CricketVariant.entries).map { resultsOf(it) },
)

private fun CricketTally.resultsOf(variant: CricketVariant): CricketResults {
    val wins = won[variant] ?: 0
    val losses = lost[variant] ?: 0
    return CricketResults(
        variant = variant,
        matchesWon = wins,
        matchesLost = losses,
        winRatio = ratio(wins, wins + losses),
        legsWon = legsWon[variant] ?: 0,
        setsWon = setsWon[variant] ?: 0,
    )
}

private fun ratio(numerator: Int, denominator: Int): Double? =
    if (denominator == 0) null else numerator.toDouble() / denominator
