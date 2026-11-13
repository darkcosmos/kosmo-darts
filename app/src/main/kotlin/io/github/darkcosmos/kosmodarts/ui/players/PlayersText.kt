package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.history.PlayerSummary

/** PM2: what a card says of its profile. The reset date is not shown (Q7). */
sealed interface PlayerSummaryText {
    /** "No match yet". */
    data object NoMatch : PlayerSummaryText

    /** "N matches", when no end date is known. */
    data class Matches(val count: Int) : PlayerSummaryText

    /** "N matches · Last match: date"; [lastEndedAt] is the end date of the last match. */
    data class MatchesAndLast(val count: Int, val lastEndedAt: Long) : PlayerSummaryText
}

fun PlayerSummary.summaryText(): PlayerSummaryText = when {
    matchCount == 0 -> PlayerSummaryText.NoMatch
    lastEndedAt == null -> PlayerSummaryText.Matches(matchCount)
    else -> PlayerSummaryText.MatchesAndLast(matchCount, lastEndedAt)
}

/** PM6: the number of matches a deletion erases, as the confirmation says it. */
sealed interface ErasedText {
    /** "No match will be erased". */
    data object None : ErasedText

    data class Matches(val count: Int) : ErasedText
}

fun erasedText(count: Int): ErasedText = if (count == 0) ErasedText.None else ErasedText.Matches(count)
