package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.history.MatchDetail
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import java.time.ZoneId

/**
 * The read-only detail of an archived match (HS5, HS6, HS7), in the shared [HistoryScaffold]: the summary card
 * (settings, date, duration, ranking, legs and sets), then each leg under its heading with one row per visit. No
 * delete button and no popup. The content scrolls under the top bar, so that Back stays reachable in landscape and at
 * the largest font.
 */
@Composable
fun MatchDetailScreen(state: MatchDetailState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    HistoryScaffold(
        title = stringResource(R.string.detail_title),
        onBack = { onEvent(AppEvent.Back) },
        modifier = modifier,
        backTag = HistoryTags.DETAIL_BACK,
    ) {
        when (val content = state.content) {
            MatchDetailContent.Loading -> LoadingIndicator(R.string.detail_loading)
            MatchDetailContent.Failed -> DetailFailed(onEvent)
            is MatchDetailContent.Loaded -> DetailContent(content.detail, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailContent(detail: MatchDetail, modifier: Modifier) {
    val legs = remember(detail) { detail.legs() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag(HistoryTags.DETAIL_LIST),
    ) {
        item(key = SUMMARY_KEY) { SummaryCard(detail) }
        legs.forEachIndexed { ordinal, leg -> legSection(detail, ordinal, leg) }
    }
}

private fun LazyListScope.legSection(detail: MatchDetail, ordinal: Int, leg: DetailLeg) {
    item(key = "leg_$ordinal") { LegHeader(ordinal, leg) }
    items(leg.visits.size, key = { "visit_${ordinal}_$it" }) { index ->
        VisitRow(ordinal, index, leg.visits[index], detail)
    }
}

@Composable
private fun SummaryCard(detail: MatchDetail) {
    val colors = KosmoTheme.colors
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(detail, locale) { formatEndDate(detail.endedAt, locale, ZoneId.systemDefault()) }
    val settings = remember(detail, locale) { settingsLines(resources, detail.settings) }
    val duration = remember(detail, locale) { durationText(resources, detail.durationMinutes()) }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTags.DETAIL_SUMMARY)
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Text(date, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted)
        settings.forEach { Text(it, style = KosmoTheme.typography.bodyLarge, color = colors.onBackground) }
        Text(duration, style = KosmoTheme.typography.body, color = colors.onBackground)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 8.dp)) {
            detail.ranking().forEach { player ->
                val rank = rankText(resources, player, detail)
                Text(rank.rank, style = KosmoTheme.typography.bodyLarge, color = Color(player.colorArgb))
                rank.score?.let { Text(it, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted) }
            }
        }
    }
}

/** « SET 2 · LEG 3 » as a heading (not pinned), then who started and who won the leg (HS5). */
@Composable
private fun LegHeader(ordinal: Int, leg: DetailLeg) {
    val colors = KosmoTheme.colors
    val resources = LocalContext.current.resources
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(HistoryTags.legHeading(ordinal)),
    ) {
        SectionLabel(legHeading(resources, leg))
        Text(
            text = stringResource(R.string.detail_leg_starter, leg.starter.name),
            style = KosmoTheme.typography.body,
            color = colors.onBackgroundMuted,
        )
        Text(
            text = stringResource(R.string.detail_leg_winner, leg.winner.name),
            style = KosmoTheme.typography.body,
            color = colors.onBackgroundMuted,
        )
    }
}

/**
 * One visit (HS5): the player's name in his color, the total or « BUST » on the right, the darts as text under them.
 * TalkBack reads one description for the row (U8).
 */
@Composable
private fun VisitRow(ordinal: Int, index: Int, visit: DetailVisit, detail: MatchDetail) {
    val colors = KosmoTheme.colors
    val resources = LocalContext.current.resources
    val darts = remember(visit) { visitDartsText(resources, visit) }
    val total = remember(visit) { visitTotalText(resources, visit, detail.settings) }
    val description = remember(visit) { visitDescription(resources, visit, detail.settings) }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTags.visit(ordinal, index))
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = visit.player.name,
                style = KosmoTheme.typography.bodyLarge,
                color = Color(visit.player.colorArgb),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = total,
                style = KosmoTheme.typography.bodyLarge,
                color = if (visit.isBust) colors.dangerText else colors.onBackground,
            )
        }
        Text(darts, style = KosmoTheme.typography.body, color = colors.onBackground)
    }
}

/** HS7: a short message in the player's language and "Retry"; the Back action stays in the top bar. */
@Composable
private fun DetailFailed(onEvent: (AppEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_failed),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(HistoryTags.DETAIL_FAILED)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        KosmoButton(
            text = stringResource(R.string.history_retry),
            onClick = { onEvent(AppEvent.MatchDetailRetryTapped) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HistoryTags.DETAIL_RETRY),
        )
    }
}

private const val SUMMARY_KEY = "summary"
