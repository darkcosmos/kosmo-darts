package io.github.darkcosmos.kosmodarts.engine.stats

/**
 * The matches both [a] and [b] played, started at or after both reset dates, so after the latest one (HH2, ST2). The
 * order of [matches] is kept. A profile compared with itself has no common match.
 */
fun commonMatches(matches: List<StatsMatch>, a: Long, resetA: Long, b: Long, resetB: Long): List<StatsMatch> {
    if (a == b) return emptyList()
    val since = maxOf(resetA, resetB)
    return matches.filter { it.startedAt >= since && it.playerOf(a) != null && it.playerOf(b) != null }
}

/**
 * The number of common matches of [a] with each profile of [resets] (profile id to its reset date), for the picker
 * (HH1). Every key of [resets] is in the result, with 0 when there is none.
 */
fun commonCounts(matches: List<StatsMatch>, a: Long, resetA: Long, resets: Map<Long, Long>): Map<Long, Int> =
    resets.mapValues { (b, resetB) -> commonMatches(matches, a, resetA, b, resetB).size }

/**
 * The head-to-head of two profiles over their selected common matches (HH3 to HH6).
 *
 * @property commonCount the selected common matches; it equals [winsA] + [winsB] + [draws] (HH3).
 * @property draws duels where both ended with the same final rank (HH3).
 * @property legsWonA legs won by A in the selected common matches, from the stored values (HH4).
 * @property x01A the X01 figures of A over the selected common matches (HH5); empty without an X01 match.
 * @property cricketA the Cricket figures of A over the selected common matches (HH5), MPR is `null` without one.
 */
data class HeadToHeadFigures(
    val commonCount: Int,
    val winsA: Int,
    val winsB: Int,
    val draws: Int,
    val legsWonA: Int,
    val legsWonB: Int,
    val x01A: X01Figures,
    val x01B: X01Figures,
    val cricketA: CricketFigures,
    val cricketB: CricketFigures,
)

/**
 * The common matches of [a] and [b] after the filter and the period (HH6): the filter (ST3) and the reset dates (HH2)
 * first, then the [period] (ST4) over the common matches only ("Last 10" = the last 10 common matches).
 *
 * @param now the current time, milliseconds since the epoch.
 */
fun selectCommon(
    matches: List<StatsMatch>,
    a: Long,
    resetA: Long,
    b: Long,
    resetB: Long,
    filter: StatsFilter,
    period: Period,
    now: Long,
): List<StatsMatch> {
    val common = commonMatches(matches, a, resetA, b, resetB)
    return select(common, a, maxOf(resetA, resetB), filter, period, now)
}

/** The head-to-head of [a] and [b]; [matches] are validated (ST10). Unknown profiles give empty figures (HH9). */
fun headToHead(
    matches: List<StatsMatch>,
    a: Long,
    resetA: Long,
    b: Long,
    resetB: Long,
    filter: StatsFilter,
    period: Period,
    now: Long,
): HeadToHeadFigures {
    val common = selectCommon(matches, a, resetA, b, resetB, filter, period, now)
    var winsA = 0
    var winsB = 0
    var draws = 0
    var legsA = 0
    var legsB = 0
    for (match in common) {
        val playerA = checkNotNull(match.playerOf(a))
        val playerB = checkNotNull(match.playerOf(b))
        when {
            playerA.rank < playerB.rank -> winsA++
            playerB.rank < playerA.rank -> winsB++
            else -> draws++
        }
        legsA += playerA.legsWon
        legsB += playerB.legsWon
    }
    val variant = (filter as? CricketFilter)?.variant
    return HeadToHeadFigures(
        commonCount = common.size,
        winsA = winsA,
        winsB = winsB,
        draws = draws,
        legsWonA = legsA,
        legsWonB = legsB,
        x01A = x01Figures(common, a),
        x01B = x01Figures(common, b),
        cricketA = cricketFigures(common, a, variant),
        cricketB = cricketFigures(common, b, variant),
    )
}
