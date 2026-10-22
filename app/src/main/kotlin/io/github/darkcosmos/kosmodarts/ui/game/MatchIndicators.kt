package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R

/** Up to this many marks are drawn one per item; from one more, a group shows one mark and "×N" (U3). */
internal const val MAX_DRAWN_MARKS = 4

private val MARK_SIZE = 12.dp
private val MARK_SPACING = 4.dp
private val RING_STROKE = 2.dp
private val GROUP_SPACING = 12.dp

/**
 * One indicator line: "Sets ●●" then "Legs ○○○", each group with its visible label, so that the shapes need no
 * legend and color carries no meaning (U2). The groups wrap onto two lines when they do not fit, never truncated.
 * TalkBack reads one description, "1 set won, 2 legs won"; the visible labels are not read twice.
 */
@Composable
fun MatchIndicators(indicator: MatchIndicator, index: Int, modifier: Modifier = Modifier) {
    val legs = pluralStringResource(R.plurals.match_legs_won, indicator.legsWon, indicator.legsWon)
    val description = indicator.setsWon?.let { sets ->
        stringResource(
            R.string.match_indicators_description,
            pluralStringResource(R.plurals.match_sets_won, sets, sets),
            legs,
        )
    } ?: legs
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GROUP_SPACING),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag(GameTags.matchIndicators(index))
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        indicator.setsWon?.let { sets ->
            MarkGroup(stringResource(R.string.match_sets_label), sets, isFilled = true, GameTags.setMark(index))
        }
        val legsLabel = stringResource(R.string.match_legs_label)
        MarkGroup(legsLabel, indicator.legsWon, isFilled = false, GameTags.legMark(index))
    }
}

/**
 * A label, then [count] marks (filled discs for sets, rings for legs), or one mark and "×N" from 5. Its texts are
 * hidden from TalkBack: the line description already says it.
 */
@Composable
private fun MarkGroup(label: String, count: Int, isFilled: Boolean, markTag: String) {
    val color = LocalContentColor.current
    val hidden = Modifier.semantics { hideFromAccessibility() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MARK_SPACING),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = hidden)
        repeat(if (count > MAX_DRAWN_MARKS) 1 else count) { Mark(isFilled, color, markTag) }
        if (count > MAX_DRAWN_MARKS) {
            Text(
                text = stringResource(R.string.match_count_overflow, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = hidden,
            )
        }
    }
}

@Composable
private fun Mark(isFilled: Boolean, color: Color, tag: String) {
    Canvas(modifier = Modifier.size(MARK_SIZE).testTag(tag)) {
        if (isFilled) {
            drawCircle(color = color)
        } else {
            val stroke = RING_STROKE.toPx()
            drawCircle(color = color, radius = (size.minDimension - stroke) / 2, style = Stroke(width = stroke))
        }
    }
}
