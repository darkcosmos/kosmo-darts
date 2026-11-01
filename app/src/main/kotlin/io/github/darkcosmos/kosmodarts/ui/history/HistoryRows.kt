package io.github.darkcosmos.kosmodarts.ui.history

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.history.HistoryMatch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A player of a row: the name and the color of the match (HS2). */
data class HistoryPlayerText(val name: String, val colorArgb: Int)

/**
 * The texts of one History row (HS2), in the player's language.
 *
 * @property winner the winner line (text, never color alone, PC9).
 * @property score the legs or sets score, or `null` when the format has a single leg.
 * @property description what TalkBack reads for the whole row: date, mode, players, winner and score.
 */
data class HistoryRowText(
    val date: String,
    val mode: String,
    val players: List<HistoryPlayerText>,
    val winner: String,
    val score: String?,
    val description: String,
)

/** The end date and time of a match in [locale] (HS2). */
fun formatEndDate(epochMillis: Long, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMillis))

/** The texts of the row of this match (HS2), dates in [locale] and [zone]. */
fun HistoryMatch.toRowText(resources: Resources, locale: Locale, zone: ZoneId): HistoryRowText {
    val date = formatEndDate(endedAt, locale, zone)
    val mode = modeText(resources, settings)
    val texts = players.map { HistoryPlayerText(it.name, it.colorArgb) }
    val winnerNames = winners.joinToString(", ") { it.name }
    val winner = resources.getQuantityString(R.plurals.history_winner, winners.size, winners.size, winnerNames)
    val score = scoreText(resources)
    val playersLine = resources.getQuantityString(
        R.plurals.history_players_description,
        players.size,
        players.size,
        players.joinToString(", ") { it.name },
    )
    val description = listOfNotNull(date, mode, playersLine, winner, score)
        .joinToString(resources.getString(R.string.history_description_separator))
    return HistoryRowText(date, mode, texts, winner, score, description)
}

/** "X01 · 501 · DOUBLE OUT" or "CRICKET · CUT-THROAT", like the format lines of the game screens. */
private fun modeText(resources: Resources, settings: ArchivedSettings): String {
    val parts = when (settings) {
        is ArchivedSettings.X01 -> listOf(
            resources.getString(R.string.mode_x01_title),
            settings.startScore.toString(),
            resources.getString(
                if (settings.checkoutRule == CheckoutRule.DOUBLE_OUT) R.string.setup_double_out else R.string.setup_single_out,
            ),
        )
        is ArchivedSettings.Cricket -> listOf(
            resources.getString(R.string.cricket_title),
            resources.getString(
                if (settings.cricketVariant == CricketVariant.CUT_THROAT) {
                    R.string.cricket_variant_cut_throat
                } else {
                    R.string.cricket_variant_standard
                },
            ),
        )
    }
    return parts.joinToString(resources.getString(R.string.format_separator))
}

/** "Sets 2–1" when the match has several sets, else "Legs 2–1"; `null` for a single game (HS2). */
private fun HistoryMatch.scoreText(resources: Resources): String? {
    if (!hasScore) return null
    val hasSets = settings.format.hasSets
    val values = players.joinToString(resources.getString(R.string.history_score_separator)) {
        (if (hasSets) it.setsWon else it.legsWon).toString()
    }
    return resources.getString(if (hasSets) R.string.history_score_sets else R.string.history_score_legs, values)
}
