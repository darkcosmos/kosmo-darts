package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule

/** Days of the "Last 30 days" period (ST4). */
const val PERIOD_DAYS: Int = 30

/** Matches of the "Last 10 matches" period (ST4). */
const val LAST_MATCHES: Int = 10

private const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000

/** The three fixed periods (ST4). */
enum class Period { SINCE_ALWAYS, LAST_30_DAYS, LAST_10_MATCHES }

/** The mode and filters of a selection (ST3). */
sealed interface StatsFilter {
    /** Whether [settings] pass the filter. */
    fun accepts(settings: ArchivedSettings): Boolean

    /** Every match of every mode. */
    data object AllModes : StatsFilter {
        override fun accepts(settings: ArchivedSettings): Boolean = true
    }
}

/** X01 matches only; a `null` criterion means All (ST3). */
data class X01Filter(val startScore: Int? = null, val checkoutRule: CheckoutRule? = null) : StatsFilter {
    override fun accepts(settings: ArchivedSettings): Boolean = settings is ArchivedSettings.X01 &&
        (startScore == null || settings.startScore == startScore) &&
        (checkoutRule == null || settings.checkoutRule == checkoutRule)
}

/** Cricket matches only; a `null` variant means All variants (ST3). */
data class CricketFilter(val variant: CricketVariant? = null) : StatsFilter {
    override fun accepts(settings: ArchivedSettings): Boolean = settings is ArchivedSettings.Cricket &&
        (variant == null || settings.cricketVariant == variant)
}

/**
 * Selects the matches of [profileId] that count: started at or after [resetAt] (ST2), passing [filter] (ST3), then
 * the [period] (ST4: "Last 10" is taken after the filter and ST2, by end time). The order of [matches] is kept.
 *
 * @param now the current time, milliseconds since the epoch.
 */
fun select(
    matches: List<StatsMatch>,
    profileId: Long,
    resetAt: Long,
    filter: StatsFilter,
    period: Period,
    now: Long,
): List<StatsMatch> {
    val eligible = matches.filter {
        it.playerOf(profileId) != null && it.startedAt >= resetAt && filter.accepts(it.settings)
    }
    return when (period) {
        Period.SINCE_ALWAYS -> eligible
        Period.LAST_30_DAYS -> eligible.filter { it.endedAt >= now - PERIOD_DAYS * MILLIS_PER_DAY }
        Period.LAST_10_MATCHES -> {
            val byRecency = compareByDescending<StatsMatch> { it.endedAt }.thenByDescending { it.startedAt }
            val latest = eligible.sortedWith(byRecency).take(LAST_MATCHES)
            eligible.filter { match -> latest.any { it === match } }
        }
    }
}
