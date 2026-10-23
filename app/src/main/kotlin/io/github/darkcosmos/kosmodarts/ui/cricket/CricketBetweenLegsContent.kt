package io.github.darkcosmos.kosmodarts.ui.cricket

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsContent
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsState

/**
 * Between two legs of a Cricket match (CR26, M16): the layout of the X01 screen, with "245 PTS" under the winner (none
 * in a solo game) and "MPR MATCH" in place of the average. System back opens "Abandon the game?"
 * ([isAbandonDialogShown]).
 */
@Composable
fun CricketBetweenLegsScreen(
    match: CricketMatch,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    isAbandonDialogShown: Boolean = false,
) {
    BetweenLegsContent(
        state = BetweenLegsState.of(match),
        formatLine = cricketFormatText(LocalResources.current, match.config, match.format),
        onEvent = onEvent,
        modifier = modifier,
        isAbandonDialogShown = isAbandonDialogShown,
    )
}

/**
 * The format line of the Cricket between-legs and end screens: "CRICKET · STANDARD · WITHOUT 25-BULL · FIRST TO 3
 * LEGS" (CR22, M14); the Bull part only when it is out of play, the format part only for a match.
 */
internal fun cricketFormatText(resources: Resources, config: CricketConfig, format: MatchFormat): String {
    val variant = when (config.variant) {
        CricketVariant.STANDARD -> R.string.cricket_variant_standard
        CricketVariant.CUT_THROAT -> R.string.cricket_variant_cut_throat
    }
    val parts = listOfNotNull(
        resources.getString(R.string.cricket_title),
        resources.getString(variant),
        resources.getString(R.string.cricket_header_without_bull).takeIf { !config.isBullInPlay },
        format.sets.takeIf { format.hasSets }
            ?.let { resources.getQuantityString(R.plurals.format_first_to_sets, it, it) },
        format.legsPerSet.takeUnless { format.isSingleGame }
            ?.let { resources.getQuantityString(R.plurals.format_first_to_legs, it, it) },
    )
    return parts.joinToString(resources.getString(R.string.format_separator))
}
