package io.github.darkcosmos.kosmodarts.ui.history

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.history.HistoryPlayer
import io.github.darkcosmos.kosmodarts.history.MatchDetail
import io.github.darkcosmos.kosmodarts.text.dartWords
import io.github.darkcosmos.kosmodarts.ui.format.dartText

// The texts of the match detail (HS5), from [Resources] so that tests check them outside composition. Everything comes
// from the stored columns (mode, variant, settings, times) mapped to string resources: no settings text is parsed here.

/**
 * The settings of the match, one line each: "X01 · 501 · DOUBLE OUT" or "CRICKET · CUT-THROAT" (with "WITHOUT 25-BULL"
 * when the Bull is out), then "FIRST TO 2 SETS · FIRST TO 3 LEGS" when the format has more than one leg.
 */
fun settingsLines(resources: Resources, settings: ArchivedSettings): List<String> {
    val separator = resources.getString(R.string.format_separator)
    val modeLine = listOfNotNull(
        modeText(resources, settings),
        resources.getString(R.string.cricket_header_without_bull)
            .takeIf { settings is ArchivedSettings.Cricket && !settings.isBullInPlay },
    ).joinToString(separator)
    val format = settings.format
    val formatLine = if (format.isSingleGame) {
        null
    } else {
        listOfNotNull(
            format.sets.takeIf { format.hasSets }
                ?.let { resources.getQuantityString(R.plurals.format_first_to_sets, it, it) },
            resources.getQuantityString(R.plurals.format_first_to_legs, format.legsPerSet, format.legsPerSet),
        ).joinToString(separator)
    }
    return listOfNotNull(modeLine, formatLine)
}

/** "Duration: 1 hour 5 minutes", "Duration: 42 minutes" or "Duration: less than a minute" (HS5, owner Q8). */
fun durationText(resources: Resources, minutes: Long): String {
    val hours = (minutes / MINUTES_PER_HOUR).toInt()
    val rest = (minutes % MINUTES_PER_HOUR).toInt()
    val value = when {
        minutes <= 0 -> resources.getString(R.string.detail_duration_under_a_minute)
        else -> listOfNotNull(
            resources.getQuantityString(R.plurals.detail_duration_hours, hours, hours).takeIf { hours > 0 },
            resources.getQuantityString(R.plurals.detail_duration_minutes, rest, rest).takeIf { rest > 0 },
        ).joinToString(resources.getString(R.string.detail_duration_separator))
    }
    return resources.getString(R.string.detail_duration, value)
}

/** A line of the ranking: [rank] is "1. Anna", [score] "2 sets won · 6 legs won" for a match of several legs (HS5). */
data class RankText(val rank: String, val score: String?)

/** The ranking line of [player] (rank by text, never by color, PC9). */
fun rankText(resources: Resources, player: HistoryPlayer, detail: MatchDetail): RankText {
    val rank = resources.getString(R.string.detail_rank, player.finalRank, player.name)
    val score = if (detail.hasScore) {
        listOfNotNull(
            resources.getQuantityString(R.plurals.match_sets_won, player.setsWon, player.setsWon)
                .takeIf { detail.settings.format.hasSets },
            resources.getQuantityString(R.plurals.match_legs_won, player.legsWon, player.legsWon),
        ).joinToString(resources.getString(R.string.format_separator))
    } else {
        null
    }
    return RankText(rank, score)
}

/** The heading of a leg: "SET 2 · LEG 3", or "LEG 3" when the match has a single set (M19). */
fun legHeading(resources: Resources, leg: DetailLeg): String = if (leg.hasSetNumber) {
    resources.getString(R.string.match_position_set_leg, leg.setNumber, leg.legNumber)
} else {
    resources.getString(R.string.match_position_leg, leg.legNumber)
}

/** The darts of a visit as written on the game screen: "T20 D16 BULL 25 MISS". */
fun visitDartsText(resources: Resources, visit: DetailVisit): String =
    visit.darts.joinToString(" ") { dartText(resources, it) }

/** The result of a visit by text: "BUST", the X01 points, or the Cricket marks ("3 marks"). */
fun visitTotalText(resources: Resources, visit: DetailVisit, settings: ArchivedSettings): String = when {
    visit.isBust -> resources.getString(R.string.game_bust)
    settings is ArchivedSettings.Cricket ->
        resources.getQuantityString(R.plurals.detail_visit_marks, visit.total, visit.total)
    else -> resources.getString(R.string.game_visit_value, visit.total)
}

/** What TalkBack reads for a visit: the player, the darts in words, then the result (U8). */
fun visitDescription(resources: Resources, visit: DetailVisit, settings: ArchivedSettings): String {
    val darts = visit.darts.joinToString(resources.getString(R.string.detail_dart_spoken_separator)) {
        dartWords(resources, it) ?: resources.getString(R.string.detail_dart_miss_spoken)
    }
    val result = when {
        visit.isBust -> resources.getString(R.string.game_bust)
        settings is ArchivedSettings.Cricket -> visitTotalText(resources, visit, settings)
        else -> resources.getString(R.string.detail_visit_total_description, visit.total)
    }
    return resources.getString(R.string.detail_visit_description, visit.player.name, darts, result)
}

private const val MINUTES_PER_HOUR = 60
