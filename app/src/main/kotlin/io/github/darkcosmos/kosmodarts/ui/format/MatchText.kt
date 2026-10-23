package io.github.darkcosmos.kosmodarts.ui.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition
import java.util.Locale

// Texts of the match and its format, from [Resources] so that tests check them outside composition; each has a
// composable shorthand that reads the current resources.

/** A player name as displayed by the design system: in capitals (section 3, `playerName`), in [locale]. */
fun displayName(name: String, locale: Locale): String = name.uppercase(locale)

/** [displayName] in the current language. */
@Composable
fun displayName(name: String): String = displayName(name, LocalConfiguration.current.locales[0])

/** M14, M19: "SET 2 · LEG 3", "LEG 3" with one set, or `null` in a single game. */
fun positionText(resources: Resources, position: MatchPosition): String? {
    val leg = position.legNumber ?: return null
    return position.setNumber?.let { resources.getString(R.string.match_position_set_leg, it, leg) }
        ?: resources.getString(R.string.match_position_leg, leg)
}

@Composable
fun positionText(position: MatchPosition): String? = positionText(LocalResources.current, position)

/** M14: the chips of the zone, "S1" (only with sets) and "L2". */
fun chipTexts(resources: Resources, indicator: MatchIndicator): List<String> = listOfNotNull(
    indicator.setsWon?.let { resources.getString(R.string.chip_sets, it) },
    resources.getString(R.string.chip_legs, indicator.legsWon),
)

@Composable
fun chipTexts(indicator: MatchIndicator): List<String> = chipTexts(LocalResources.current, indicator)

/** M14: the match line of a tile, "S0 · L1", or "L1" with one set. */
fun tileMatchText(resources: Resources, indicator: MatchIndicator): String = indicator.setsWon
    ?.let { resources.getString(R.string.tile_score_sets_legs, it, indicator.legsWon) }
    ?: resources.getString(R.string.chip_legs, indicator.legsWon)

@Composable
fun tileMatchText(indicator: MatchIndicator): String = tileMatchText(LocalResources.current, indicator)

/** M14: what the chips or the match line say, for TalkBack: "1 set won, 2 legs won". */
fun indicatorDescription(resources: Resources, indicator: MatchIndicator): String {
    val legs = resources.getQuantityString(R.plurals.match_legs_won, indicator.legsWon, indicator.legsWon)
    return indicator.setsWon?.let { sets ->
        resources.getString(
            R.string.match_indicators_description,
            resources.getQuantityString(R.plurals.match_sets_won, sets, sets),
            legs,
        )
    } ?: legs
}

@Composable
fun indicatorDescription(indicator: MatchIndicator): String = indicatorDescription(LocalResources.current, indicator)
