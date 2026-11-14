package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndTab
import io.github.darkcosmos.kosmodarts.ui.components.ScrollingScreen
import io.github.darkcosmos.kosmodarts.ui.components.TwoColumnScreen
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val TAB_UNDERLINE = 3.dp
private val TAB_MIN_HEIGHT_LANDSCAPE = 42.dp
private val TAB_ROW_TOP = 8.dp

/** The height in dp that the tab row and its margin take off the pinning decision of the landscape heatmap tab. */
private const val TABS_ALLOWANCE = 50f

/** The three parts of the landscape Result tab of the between-legs screens: [top] over [start] and [end]. */
class TwoColumnParts(
    val top: @Composable ColumnScope.() -> Unit,
    val start: @Composable ColumnScope.() -> Unit,
    val end: @Composable ColumnScope.() -> Unit,
)

/**
 * The frame of the between-legs and end screens with their two tabs (HM9, HM10): the tab row fixed at the top under the
 * safe area, then the selected tab. "Result" is today's screen ([content] and [buttons] in the [ScrollingScreen] slot,
 * or [twoColumns] in landscape between legs); "Leg heatmap" is [LegHeatmapPortrait] or [LegHeatmapLandscape], with the
 * same [buttons] in reach (D10). Without a [heatmapScreen] there is no tab row and the screen is the Result one.
 * [isZoneTapEnabled] is D11's switch, passed on to the heatmap tab.
 */
@Composable
fun EndTabsFrame(
    heatmapScreen: EndHeatmapScreen?,
    onEvent: (AppEvent) -> Unit,
    buttonCount: Int,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    twoColumns: TwoColumnParts? = null,
    isZoneTapEnabled: Boolean = END_SCREEN_ZONE_TAP_ENABLED,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(KosmoTheme.colors.background)) {
        val windowHeight = maxHeight
        val isWide = ScreenLayoutPolicy.hasTwoColumns(maxWidth.value, windowHeight.value)
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (heatmapScreen != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    EndTabRow(
                        selected = heatmapScreen.endTab,
                        onSelect = { onEvent(AppEvent.EndTabSelected(it)) },
                        minHeight = if (isWide) TAB_MIN_HEIGHT_LANDSCAPE else States.minTouchTarget,
                        modifier = Modifier
                            .widthIn(max = ScreenLayoutPolicy.CONTENT_MAX_WIDTH.dp)
                            .padding(horizontal = ScreenLayoutPolicy.HORIZONTAL_MARGIN.dp)
                            .padding(top = TAB_ROW_TOP),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val showsHeatmap = heatmapScreen != null && heatmapScreen.endTab == EndTab.LEG_HEATMAP
                when {
                    showsHeatmap && isWide -> LegHeatmapLandscape(
                        screen = requireNotNull(heatmapScreen),
                        onEvent = onEvent,
                        isZoneTapEnabled = isZoneTapEnabled,
                        isButtonPinned = twoColumns != null ||
                            ScreenLayoutPolicy.of(
                                windowHeight.value - TABS_ALLOWANCE,
                                LocalDensity.current.fontScale,
                                buttonCount,
                            ).isMainButtonPinned,
                        buttons = buttons,
                    )
                    showsHeatmap -> ScrollingScreen(
                        buttonCount = buttonCount,
                        content = {
                            LegHeatmapPortrait(requireNotNull(heatmapScreen), onEvent, isZoneTapEnabled)
                        },
                        buttons = buttons,
                    )
                    twoColumns != null && isWide -> TwoColumnScreen(
                        top = twoColumns.top,
                        start = twoColumns.start,
                        end = twoColumns.end,
                    )
                    else -> ScrollingScreen(buttonCount = buttonCount, content = content, buttons = buttons)
                }
            }
        }
    }
}

/**
 * HM9: the two tabs, equal widths, at least [minHeight] high and growing with the text at a large font, the selected
 * one underlined in the accent colour. Labels wrap and are never cut. Each is a Tab for TalkBack with its selected
 * state.
 */
@Composable
fun EndTabRow(selected: EndTab, onSelect: (EndTab) -> Unit, minHeight: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup()
            .testTag(EndTabTags.ROW),
    ) {
        EndTab.entries.forEach { tab ->
            EndTabItem(
                label = stringResource(tabLabel(tab)),
                isSelected = tab == selected,
                minHeight = minHeight,
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(EndTabTags.tab(tab)),
            )
        }
    }
}

private fun tabLabel(tab: EndTab): Int = when (tab) {
    EndTab.RESULT -> R.string.end_tab_result
    EndTab.LEG_HEATMAP -> R.string.end_tab_heatmap
}

@Composable
private fun EndTabItem(label: String, isSelected: Boolean, minHeight: Dp, onClick: () -> Unit, modifier: Modifier) {
    val colors = KosmoTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = minHeight)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Text(
                text = label,
                // 14 sp: at font scale 2 the longest word, RÉSULTAT, still fits half of a Pixel 7 width.
                style = KosmoTheme.typography.button.copy(
                    fontSize = 14.sp,
                    letterSpacing = 0.02.em,
                    color = if (isSelected) colors.accent else colors.onBackgroundMuted,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TAB_UNDERLINE)
                .background(if (isSelected) colors.accent else colors.outline),
        )
    }
}

/** Test tags of the tab row. */
object EndTabTags {
    const val ROW = "end_tab_row"

    /** The tab [tab]. */
    fun tab(tab: EndTab): String = "end_tab_${tab.name.lowercase()}"
}
