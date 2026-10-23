package io.github.darkcosmos.kosmodarts.ui.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule

// Texts of the game format and of the checkout, from [Resources] so that tests check them outside composition; each
// has a composable shorthand that reads the current resources.

/** "DOUBLE OUT" or "SINGLE OUT". */
fun ruleText(resources: Resources, rule: CheckoutRule): String = when (rule) {
    CheckoutRule.DOUBLE_OUT -> resources.getString(R.string.setup_double_out)
    CheckoutRule.SINGLE_OUT -> resources.getString(R.string.setup_single_out)
}

@Composable
fun ruleText(rule: CheckoutRule): String = ruleText(LocalResources.current, rule)

/** The game header: "501 · DOUBLE OUT" (design system, `MatchHeader`). */
fun modeText(resources: Resources, startScore: Int, rule: CheckoutRule): String =
    listOf(resources.getString(R.string.format_start_score, startScore), ruleText(resources, rule))
        .joinToString(resources.getString(R.string.format_separator))

@Composable
fun modeText(startScore: Int, rule: CheckoutRule): String = modeText(LocalResources.current, startScore, rule)

/**
 * The format line of the home card, the between-legs and the end screens: "501 · FIRST TO 2 SETS · FIRST TO 3 LEGS",
 * the sets only with more than one set; a single game reads like the header, "501 · DOUBLE OUT".
 */
fun formatText(resources: Resources, startScore: Int, rule: CheckoutRule, format: MatchFormat): String {
    if (format.isSingleGame) return modeText(resources, startScore, rule)
    val parts = listOfNotNull(
        resources.getString(R.string.format_start_score, startScore),
        format.sets.takeIf { format.hasSets }
            ?.let { resources.getQuantityString(R.plurals.format_first_to_sets, it, it) },
        resources.getQuantityString(R.plurals.format_first_to_legs, format.legsPerSet, format.legsPerSet),
    )
    return parts.joinToString(resources.getString(R.string.format_separator))
}

@Composable
fun formatText(startScore: Int, rule: CheckoutRule, format: MatchFormat): String =
    formatText(LocalResources.current, startScore, rule, format)

/** U2: "141 IN 3 DARTS" / « 141 EN 1 FLÉCHETTE », the plural on the number of darts. */
fun checkoutDartsText(resources: Resources, points: Int, darts: Int): String =
    resources.getQuantityString(R.plurals.checkout_darts, darts, points, darts)

/** U5: what TalkBack announces for a pending checkout, "Checkout, 141 in 3 darts". */
fun checkoutAnnouncement(resources: Resources, points: Int, darts: Int): String = resources.getString(
    R.string.checkout_live,
    resources.getQuantityString(R.plurals.checkout_darts_spoken, darts, points, darts),
)

/** U4: the TalkBack description of the first tile, "Next player: Anna, 301 points left". */
fun nextPlayerDescription(resources: Resources, name: String, remaining: Int): String =
    resources.getQuantityString(R.plurals.next_player_tile_description, remaining, name, remaining)
