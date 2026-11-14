package io.github.darkcosmos.kosmodarts.ui.headtohead

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.ui.history.formatEndDate
import io.github.darkcosmos.kosmodarts.ui.profile.profileLocale
import io.github.darkcosmos.kosmodarts.ui.profile.profileResources
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States
import java.time.ZoneId
import java.util.Locale

/** HH7: the texts of one common match: its end date, its mode and variant, and who won (HH3). */
internal data class CommonMatchText(val date: String, val mode: String, val outcome: String) {
    val description: String
        get() = listOf(date, mode, outcome).joinToString(", ")
}

internal fun CommonMatchRow.toText(
    resources: Resources,
    locale: Locale,
    zone: ZoneId,
    nameA: String,
    nameB: String,
): CommonMatchText = CommonMatchText(
    date = formatEndDate(endedAt, locale, zone),
    mode = modeLine(resources),
    outcome = when (outcome) {
        DuelOutcome.A_WINS -> resources.getString(R.string.h2h_outcome_won, nameA)
        DuelOutcome.B_WINS -> resources.getString(R.string.h2h_outcome_won, nameB)
        DuelOutcome.DRAW -> resources.getString(R.string.h2h_outcome_draw)
    },
)

/** "X01 · 501" or "CRICKET · CUT-THROAT", like the format lines of the other screens. */
private fun CommonMatchRow.modeLine(resources: Resources): String {
    val separator = resources.getString(R.string.format_separator)
    return when (mode) {
        ArchivedMode.X01 -> resources.getString(R.string.mode_x01_title) + separator + variant
        ArchivedMode.CRICKET -> resources.getString(R.string.cricket_title) + separator + resources.getString(
            if (variant == CricketVariant.CUT_THROAT.name) {
                R.string.cricket_variant_cut_throat
            } else {
                R.string.cricket_variant_standard
            },
        )
    }
}

/**
 * HH7: one common match of the list; the whole row is one button for TalkBack, at least 48 dp high, and opens the match
 * detail.
 */
@Composable
internal fun CommonMatchCard(row: CommonMatchRow, nameA: String, nameB: String, onClick: () -> Unit) {
    val colors = KosmoTheme.colors
    val resources = profileResources()
    val locale = profileLocale()
    val text = remember(row, locale, nameA, nameB) {
        row.toText(resources, locale, ZoneId.systemDefault(), nameA, nameB)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HeadToHeadTags.matchRow(row.sessionId))
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.history_row_action),
                onClick = onClick,
            )
            .sizeIn(minHeight = States.minTouchTarget)
            .clearAndSetSemantics { contentDescription = text.description }
            .padding(16.dp),
    ) {
        Text(text.date, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted)
        Text(text.mode, style = KosmoTheme.typography.bodyLarge, color = colors.onBackground)
        Text(text.outcome, style = KosmoTheme.typography.body, color = colors.onBackground)
    }
}
