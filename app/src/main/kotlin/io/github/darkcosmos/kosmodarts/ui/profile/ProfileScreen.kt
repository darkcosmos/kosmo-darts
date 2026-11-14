package io.github.darkcosmos.kosmodarts.ui.profile

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.stats.TrendPoint
import io.github.darkcosmos.kosmodarts.engine.stats.TrendSummary
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScaffold
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import java.util.Locale

/** One line of figures: its [key] for tests, its label and its value, the dash when it cannot be computed (ST6). */
internal data class FigureRow(val key: String, val label: String, val value: String)

/** A titled group of figures; [note] is a plain sentence under the title (the Heatmap placeholder). */
internal data class FigureBlock(
    val key: String,
    val title: String,
    val rows: List<FigureRow>,
    val note: String? = null,
    val trend: TrendDisplay? = null,
)

/** What the trend block shows (PS5): the points of the curve, their engine [summary] and the TalkBack [description]. */
internal data class TrendDisplay(
    val points: List<TrendPoint>,
    val summary: TrendSummary,
    val description: String,
)

@Composable
@ReadOnlyComposable
internal fun profileResources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

@Composable
@ReadOnlyComposable
internal fun profileLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * The Profile screen (PS2 to PS6, PS8): the header, the tabs X01 / Cricket / Heatmap, the filters and period of the
 * section, then its figures; or one message for the loading, empty, no-match-for-the-filters and failure states
 * (HS7). Portrait: one column, the tabs pinned at the top while the header and the figures scroll (D8). Landscape: the
 * tabs and filters at the left, the figures in two columns at the right. Everything shown comes from [state].
 */
@Composable
fun ProfileScreen(state: ProfileState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val isWide = ScreenLayoutPolicy.hasTwoColumns(
        configuration.screenWidthDp.toFloat(),
        configuration.screenHeightDp.toFloat(),
    )
    HistoryScaffold(
        title = stringResource(R.string.profile_title),
        onBack = { onEvent(AppEvent.Back) },
        modifier = modifier,
        backTag = ProfileTags.BACK,
        maxWidth = (if (isWide) ScreenLayoutPolicy.TWO_COLUMNS_MAX_WIDTH else ScreenLayoutPolicy.CONTENT_MAX_WIDTH).dp,
    ) {
        when {
            state.view != ProfileView.CONTENT -> MessageScreen(state, onEvent, Modifier.weight(1f))
            isWide -> LandscapeContent(state, onEvent, Modifier.weight(1f))
            else -> PortraitContent(state, onEvent, Modifier.weight(1f))
        }
    }
}

/** The blocks of the shown section for its current selection (PS4); the Heatmap tab is a placeholder until T22. */
@Composable
private fun sectionBlocks(state: ProfileState): List<FigureBlock> {
    val figures = state.figures ?: return emptyList()
    return when (state.section) {
        ProfileSection.X01 -> x01Blocks(figures.x01)
        ProfileSection.CRICKET -> cricketBlocks(figures.cricket, state.cricketFilter.variant)
        ProfileSection.HEATMAP -> listOf(
            FigureBlock(
                key = "heatmap",
                title = stringResource(R.string.profile_tab_heatmap),
                rows = emptyList(),
                note = stringResource(R.string.profile_heatmap_placeholder),
            ),
        )
    }
}

@Composable
private fun PortraitContent(state: ProfileState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    val blocks = sectionBlocks(state)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.testTag(ProfileTags.CONTENT),
    ) {
        item(key = "header") { ProfileHeader(state) }
        stickyHeader(key = "tabs") {
            ProfileTabs(state.section, { onEvent(ProfileEvent.SectionSelected(it)) })
        }
        item(key = "filters") { ProfileFilters(state, onEvent) }
        if (state.isSelectionEmpty) {
            item(key = "no_match") { NoMatchForFilters() }
        } else {
            items(blocks, key = { it.key }) { FigureBlockView(it) }
        }
    }
}

@Composable
private fun LandscapeContent(state: ProfileState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(LEFT_WEIGHT)
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileHeader(state)
            ProfileTabs(state.section, { onEvent(ProfileEvent.SectionSelected(it)) })
            ProfileFilters(state, onEvent)
        }
        Column(
            modifier = Modifier
                .weight(RIGHT_WEIGHT)
                .verticalScroll(rememberScrollState())
                .testTag(ProfileTags.CONTENT),
        ) {
            if (state.isSelectionEmpty) NoMatchForFilters() else TwoColumns(sectionBlocks(state))
        }
    }
}

/** The blocks side by side in two columns, taken in turn so that both stay about as long. */
@Composable
private fun TwoColumns(blocks: List<FigureBlock>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(blocks.filterIndexed { i, _ -> i % 2 == 0 }, blocks.filterIndexed { i, _ -> i % 2 == 1 }).forEach {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                it.forEach { block -> FigureBlockView(block) }
            }
        }
    }
}

@Composable
private fun FigureBlockView(block: FigureBlock, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProfileTags.block(block.key)),
    ) {
        SectionLabel(block.title)
        block.note?.let {
            Text(
                text = it,
                style = KosmoTheme.typography.body,
                color = colors.onBackgroundMuted,
                modifier = Modifier.testTag(
                    if (block.trend == null) ProfileTags.HEATMAP_PLACEHOLDER else ProfileTags.TREND_TOO_FEW,
                ),
            )
        }
        block.trend?.let { TrendCurve(it) }
        block.rows.forEach { FigureRowView(it) }
    }
}

/** A label and its value on one line; they wrap at a large font. One element for TalkBack: "label, value". */
@Composable
private fun FigureRowView(row: FigureRow) {
    val colors = KosmoTheme.colors
    // A row that does not fit (large font, long date) puts the value on its own line: a word is never cut.
    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(ProfileTags.figure(row.key)),
    ) {
        Text(
            text = row.label,
            style = KosmoTheme.typography.body,
            color = colors.onBackgroundMuted,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(text = row.value, style = KosmoTheme.typography.bodyLarge, color = colors.onBackground)
    }
}

private const val LEFT_WEIGHT = 0.42f
private const val RIGHT_WEIGHT = 0.58f
