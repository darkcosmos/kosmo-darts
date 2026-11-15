package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule

/** Points of a maximum visit (STX5). */
const val BAND_180: Int = 180

/** Lower bound of the 140 to 179 band (STX5). */
const val BAND_140: Int = 140

/** Lower bound of the 100 to 139 band (STX5). */
const val BAND_100: Int = 100

/** Lower bound of the 60 to 99 band (STX5). */
const val BAND_60: Int = 60

/** Number of darts per leg of the first-9 average (STX2). */
const val FIRST_NINE_DARTS: Int = 9

/** Darts of a standard visit, the unit of the 3-dart average (STX1). */
const val DARTS_PER_VISIT: Int = 3

/** Highest even remaining that is a double-out attempt (STX3). */
const val DOUBLE_OUT_MAX: Int = 40

/** Remaining of the bull, a double-out attempt too (STX3). */
const val DOUBLE_OUT_BULL: Int = 50

private const val OUTER_BULL_VALUE: Int = 25
private const val NUMBER_MAX: Int = 20
private const val TRIPLE_FACTOR: Int = 3

/**
 * The X01 figures of one profile over a selection of matches (STX1 to STX8). A figure that cannot be computed is `null`
 * (ST6).
 *
 * @property average 3-dart average (STX1).
 * @property firstNineAverage average over the first 9 darts of each leg (STX2).
 * @property checkoutRatio successes / attempts (STX3), `null` without attempt.
 * @property bestCheckout the highest winning visit (STX4).
 * @property visits180 visits of 180 (STX5).
 * @property visits140 visits from 140 to 179 (STX5).
 * @property visits100 visits from 100 to 139 (STX5).
 * @property visits60 visits from 60 to 99 (STX5).
 * @property bestVisit the highest visit, busts excluded (STX6).
 * @property busts number of bust visits (STX6).
 * @property averageDartsPerLegWon average number of darts of the legs won (STX7).
 * @property bestLeg fewest darts of a leg won (STX7).
 * @property matchesWon matches ended at rank 1, solo excluded (STX8, ST5, ST8).
 * @property matchesLost matches ended at another rank, solo excluded (STX8, ST8).
 * @property winRatio matches won / (won + lost), `null` without a result (STX8).
 * @property legsWon stored legs won, solo excluded (STX8).
 * @property setsWon stored sets won, solo excluded (STX8).
 */
data class X01Figures(
    val average: Double?,
    val firstNineAverage: Double?,
    val checkoutSuccesses: Int,
    val checkoutAttempts: Int,
    val checkoutRatio: Double?,
    val bestCheckout: Int?,
    val visits180: Int,
    val visits140: Int,
    val visits100: Int,
    val visits60: Int,
    val bestVisit: Int?,
    val busts: Int,
    val averageDartsPerLegWon: Double?,
    val bestLeg: Int?,
    val matchesWon: Int,
    val matchesLost: Int,
    val winRatio: Double?,
    val legsWon: Int,
    val setsWon: Int,
)

/** Whether a dart thrown at [remaining] is a checkout attempt under [rule] (STX3). */
fun isCheckoutAttempt(remaining: Int, rule: CheckoutRule): Boolean = when (rule) {
    CheckoutRule.DOUBLE_OUT ->
        remaining == DOUBLE_OUT_BULL || (remaining in 2..DOUBLE_OUT_MAX && remaining % 2 == 0)
    CheckoutRule.SINGLE_OUT -> isSingleDartValue(remaining)
}

private fun isSingleDartValue(remaining: Int): Boolean = remaining in 1..NUMBER_MAX ||
    (remaining in 1..DOUBLE_OUT_MAX && remaining % 2 == 0) ||
    (remaining in 1..NUMBER_MAX * TRIPLE_FACTOR && remaining % TRIPLE_FACTOR == 0) ||
    remaining == OUTER_BULL_VALUE || remaining == DOUBLE_OUT_BULL

/** Running sums of the X01 figures of one profile. */
private class X01Tally {
    var points = 0
    var darts = 0
    var firstNinePoints = 0
    var firstNineDarts = 0
    var attempts = 0
    var successes = 0
    var bestCheckout: Int? = null
    var bestVisit: Int? = null
    var busts = 0
    var band180 = 0
    var band140 = 0
    var band100 = 0
    var band60 = 0
    val legWonDarts = ArrayList<Int>()
    var won = 0
    var lost = 0
    var legsWon = 0
    var setsWon = 0

    fun addVisitBand(total: Int) {
        when {
            total >= BAND_180 -> band180++
            total >= BAND_140 -> band140++
            total >= BAND_100 -> band100++
            total >= BAND_60 -> band60++
        }
    }
}

/**
 * The X01 figures of [profileId] over [matches], which the caller has already validated (ST10) and selected. Cricket
 * matches and matches without the profile are ignored. Reads the per-dart facts, never replays (STX3).
 */
fun x01Figures(matches: List<StatsMatch>, profileId: Long): X01Figures {
    val tally = X01Tally()
    for (match in matches) tally.addMatch(match, profileId)
    return tally.toFigures()
}

private fun X01Tally.addMatch(match: StatsMatch, profileId: Long) {
    val settings = match.settings as? ArchivedSettings.X01
    val player = match.playerOf(profileId)
    if (settings == null || player == null) return
    for (leg in match.legs) addLeg(leg, player.position, settings.checkoutRule)
    if (!match.isSolo) addResult(player)
}

private fun X01Tally.addResult(player: StatsPlayer) {
    if (player.rank == 1) won++ else lost++
    legsWon += player.legsWon
    setsWon += player.setsWon
}

private fun X01Tally.addLeg(leg: StatsLeg, position: Int, rule: CheckoutRule) {
    val darts = leg.darts.filter { it.position == position }
    if (darts.isEmpty()) return
    val visits = darts.groupBy { it.visit }.values
    val scoring = HashMap<Int, Int>()
    for (visit in visits) {
        val isBust = visit.any { it.isBust }
        val total = if (isBust) 0 else visit.sumOf { it.dart.value }
        scoring[visit.first().visit] = total
        this.points += total
        this.darts += visit.size
        if (isBust) busts++ else addScoringVisit(total)
    }
    addFirstNine(darts, visits.associate { it.first().visit to it.any { dart -> dart.isBust } })
    for (dart in darts) {
        if (dart.remainingBefore?.let { isCheckoutAttempt(it, rule) } == true) attempts++
    }
    if (leg.winnerPosition == position) addLegWon(darts, visits.last(), scoring)
}

private fun X01Tally.addScoringVisit(total: Int) {
    bestVisit = maxOf(bestVisit ?: total, total)
    addVisitBand(total)
}

private fun X01Tally.addFirstNine(darts: List<StatsDart>, bustByVisit: Map<Int, Boolean>) {
    for (dart in darts.take(FIRST_NINE_DARTS)) {
        firstNineDarts++
        if (bustByVisit[dart.visit] != true) firstNinePoints += dart.dart.value
    }
}

private fun X01Tally.addLegWon(darts: List<StatsDart>, lastVisit: List<StatsDart>, scoring: Map<Int, Int>) {
    successes++
    legWonDarts += darts.size
    val total = scoring.getValue(lastVisit.first().visit)
    bestCheckout = maxOf(bestCheckout ?: total, total)
}

private fun X01Tally.toFigures(): X01Figures = X01Figures(
    average = ratio(points * DARTS_PER_VISIT, darts),
    firstNineAverage = ratio(firstNinePoints * DARTS_PER_VISIT, firstNineDarts),
    checkoutSuccesses = successes,
    checkoutAttempts = attempts,
    checkoutRatio = ratio(successes, attempts),
    bestCheckout = bestCheckout,
    visits180 = band180,
    visits140 = band140,
    visits100 = band100,
    visits60 = band60,
    bestVisit = bestVisit,
    busts = busts,
    averageDartsPerLegWon = ratio(legWonDarts.sum(), legWonDarts.size),
    bestLeg = legWonDarts.minOrNull(),
    matchesWon = won,
    matchesLost = lost,
    winRatio = ratio(won, won + lost),
    legsWon = legsWon,
    setsWon = setsWon,
)

private fun ratio(numerator: Int, denominator: Int): Double? =
    if (denominator == 0) null else numerator.toDouble() / denominator
