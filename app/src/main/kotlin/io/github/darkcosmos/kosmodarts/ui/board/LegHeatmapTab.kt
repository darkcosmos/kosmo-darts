package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.between.EndHeatmapState
import io.github.darkcosmos.kosmodarts.ui.between.HeatmapPlayerChip
import io.github.darkcosmos.kosmodarts.ui.between.heatmapState
import io.github.darkcosmos.kosmodarts.ui.between.legPosition
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.positionText
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapBand
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapBoard
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapMisses
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapSectionState
import io.github.darkcosmos.kosmodarts.ui.profile.HeatmapTags
import io.github.darkcosmos.kosmodarts.ui.profile.TopZones
import io.github.darkcosmos.kosmodarts.ui.profile.profileLocale
import io.github.darkcosmos.kosmodarts.ui.profile.profileResources
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * D11 (owner N3 A): whether a tap on a zone of the end-screen heatmap shows its band. The single switch: `false` (N3 B)
 * leaves the board without taps and the band out. The composables below take it as a parameter with this default, so
 * that tests exercise both values.
 */
const val END_SCREEN_ZONE_TAP_ENABLED: Boolean = true

private val BOARD_MIN_SIZE = 200.dp
private val CHIP_STRIP_WIDTH = 4.dp
private val CHIP_STRIP_HEIGHT = 24.dp
private val CHIP_NAME_MAX_WIDTH = 160.dp
private val PANE_PADDING = 12.dp
private val PANE_GAP = 12.dp

/**
 * HM9: the "Leg heatmap" tab in one column (portrait), to be placed in the `ScrollingScreen` content slot: the "LEG n"
 * title, the player chips (none in a solo game), then the board, band, misses line, legend and top 5, or the empty
 * state (HM7).
 */
@Composable
fun LegHeatmapPortrait(screen: EndHeatmapScreen, onEvent: (AppEvent) -> Unit, isZoneTapEnabled: Boolean) {
    val state = remember(screen) { screen.heatmapState() }
    val section = state.shown(isZoneTapEnabled)
    val resources = profileResources()
    val locale = profileLocale()
    LegHeatmapHeader(screen, state, onEvent)
    if (section.isEmpty) {
        LegHeatmapEmpty()
    } else {
        HeatmapBoard(section, { zoneTapped(it, isZoneTapEnabled, onEvent) }, resources, locale)
        LegHeatmapFigures(section, isZoneTapEnabled)
    }
}

/**
 * HM9, HM10: the "Leg heatmap" tab in landscape: the board at the left, as large as the height allows and never under
 * 200 dp; the right column scrolls on its own with the title, chips, band, misses line, legend and top 5. The main
 * [buttons] are pinned at the bottom of the right column when [isButtonPinned], else they follow the content (D10).
 */
@Composable
fun LegHeatmapLandscape(
    screen: EndHeatmapScreen,
    onEvent: (AppEvent) -> Unit,
    isZoneTapEnabled: Boolean,
    isButtonPinned: Boolean,
    buttons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(screen) { screen.heatmapState() }
    val section = state.shown(isZoneTapEnabled)
    val resources = profileResources()
    val locale = profileLocale()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = ScreenLayoutPolicy.TWO_COLUMNS_MAX_WIDTH.dp)
                .fillMaxSize()
                .padding(horizontal = PANE_PADDING, vertical = PANE_PADDING),
        ) {
            val side = maxOf(BOARD_MIN_SIZE, minOf(maxHeight, maxWidth / 2))
            Row(horizontalArrangement = Arrangement.spacedBy(PANE_GAP), modifier = Modifier.fillMaxSize()) {
                if (!section.isEmpty) {
                    HeatmapBoard(
                        section,
                        { zoneTapped(it, isZoneTapEnabled, onEvent) },
                        resources,
                        locale,
                        Modifier.size(side),
                    )
                }
                RightColumn(isButtonPinned, buttons, Modifier.weight(1f).fillMaxHeight()) {
                    LegHeatmapHeader(screen, state, onEvent)
                    if (section.isEmpty) LegHeatmapEmpty() else LegHeatmapFigures(section, isZoneTapEnabled)
                }
            }
        }
    }
}

@Composable
private fun RightColumn(
    isButtonPinned: Boolean,
    buttons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gap = Arrangement.spacedBy(PANE_GAP)
    Column(modifier = modifier) {
        Column(
            verticalArrangement = gap,
            modifier = Modifier
                .weight(1f, fill = isButtonPinned)
                .verticalScroll(rememberScrollState())
                .testTag(LegHeatmapTags.RIGHT_COLUMN),
        ) {
            content()
            if (!isButtonPinned) {
                Spacer(Modifier.height(4.dp))
                buttons()
            }
        }
        if (isButtonPinned) {
            Column(verticalArrangement = gap, modifier = Modifier.padding(top = PANE_GAP), content = buttons)
        }
    }
}

/** The state to draw: without zone taps (D11 N3 B) no zone is ever selected, whatever the screen remembers. */
private fun EndHeatmapState.shown(isZoneTapEnabled: Boolean): HeatmapSectionState =
    if (isZoneTapEnabled) section else section.copy(selections = section.selections.copy(selectedZone = null))

private fun zoneTapped(zone: BoardZone, isEnabled: Boolean, onEvent: (AppEvent) -> Unit) {
    if (isEnabled) onEvent(AppEvent.HeatmapZoneTapped(zone))
}

/** HM9: the "LEG n" title (M19 wording) and the chips of the players. */
@Composable
private fun LegHeatmapHeader(screen: EndHeatmapScreen, state: EndHeatmapState, onEvent: (AppEvent) -> Unit) {
    Text(
        text = positionText(screen.legPosition()) ?: stringResource(R.string.match_position_leg, 1),
        style = KosmoTheme.typography.labelLarge,
        color = KosmoTheme.colors.onBackgroundMuted,
        modifier = Modifier
            .semantics { heading() }
            .testTag(LegHeatmapTags.TITLE),
    )
    if (state.players.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .testTag(LegHeatmapTags.CHIPS),
        ) {
            state.players.forEach { chip ->
                PlayerChip(chip) { onEvent(AppEvent.HeatmapPlayerSelected(chip.index)) }
            }
        }
    }
}

/** One chip of the player switch: the colour strip and the name, at least 48 dp high, accent border when selected. */
@Composable
private fun PlayerChip(chip: HeatmapPlayerChip, onClick: () -> Unit) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = States.minTouchTarget)
            .clip(KosmoShapes.control)
            .background(colors.surface)
            .border(
                width = if (chip.isSelected) States.selectionBorder else 1.dp,
                color = if (chip.isSelected) colors.accent else colors.outline,
                shape = KosmoShapes.control,
            )
            .selectable(selected = chip.isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(LegHeatmapTags.chip(chip.index)),
    ) {
        Box(
            Modifier
                .width(CHIP_STRIP_WIDTH)
                .height(CHIP_STRIP_HEIGHT)
                .background(PlayerPalette.colorOf(chip.color)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = displayName(chip.name),
            style = KosmoTheme.typography.labelLarge,
            color = if (chip.isSelected) colors.onBackground else colors.onBackgroundMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = CHIP_NAME_MAX_WIDTH),
        )
    }
}

/** HM6, HM4: the band (unless D11 leaves it out), the misses line with its label, the legend and the top 5 list. */
@Composable
private fun LegHeatmapFigures(section: HeatmapSectionState, isZoneTapEnabled: Boolean) {
    val resources = profileResources()
    val locale = profileLocale()
    if (isZoneTapEnabled) HeatmapBand(section, resources, locale)
    HeatmapMisses(section, resources, locale)
    HeatmapLegend(maxShare = HeatmapScale.maxShare(section.zoneShares))
    TopZones(section, resources, locale)
}

/** HM7: no dart in that leg for that player. */
@Composable
private fun LegHeatmapEmpty() {
    Text(
        text = stringResource(R.string.heatmap_empty),
        style = KosmoTheme.typography.body,
        color = KosmoTheme.colors.onBackgroundMuted,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HeatmapTags.EMPTY),
    )
}

/** Test tags of the "Leg heatmap" tab. */
object LegHeatmapTags {
    const val TITLE = "leg_heatmap_title"
    const val CHIPS = "leg_heatmap_chips"
    const val RIGHT_COLUMN = "leg_heatmap_right_column"

    /** The chip of the player [index]. */
    fun chip(index: Int): String = "leg_heatmap_chip_$index"
}
