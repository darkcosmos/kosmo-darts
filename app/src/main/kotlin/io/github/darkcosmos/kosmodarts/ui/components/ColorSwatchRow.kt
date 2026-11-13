package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val GAP = 8.dp

/** Test tag of the swatch [color] of a row tagged [rowTag]. */
fun swatchTag(rowTag: String, color: PlayerColor): String = "${rowTag}_%08X".format(color.argb)

/**
 * The swatches of a player (design system, `ColorSwatchRow`; PC14): 48 dp squares that wrap onto several lines instead
 * of scrolling, so all of them show at any font size. [selected] carries a check drawn in its PC13 text color, a 2 dp
 * ring and the TalkBack state "selected": the check alone never tells it. The row is brought into view when it shows.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ColorSwatchRow(
    colors: List<PlayerColor>,
    selected: PlayerColor,
    rowTag: String,
    onPick: (PlayerColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .testTag(rowTag),
    ) {
        colors.forEachIndexed { position, color ->
            Swatch(
                color = color,
                description = swatchDescription(color, position + 1, colors.size),
                isSelected = color == selected,
                tag = swatchTag(rowTag, color),
                onClick = { onPick(color) },
            )
        }
    }
}

@Composable
private fun swatchDescription(color: PlayerColor, position: Int, count: Int): String {
    return when (val name = swatchName(color, position, count)) {
        is SwatchName.Generated -> stringResource(R.string.color_generated, name.position, name.count)
        is SwatchName.Palette -> stringResource(paletteNameRes(name.color))
    }
}

private fun paletteNameRes(color: PlayerColor): Int = when (color) {
    PlayerColor.ORANGE -> R.string.color_orange
    PlayerColor.TEAL -> R.string.color_teal
    PlayerColor.LILAC -> R.string.color_lilac
    else -> R.string.color_blue
}

@Composable
private fun Swatch(color: PlayerColor, description: String, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    val selectedText = stringResource(R.string.color_selected)
    val ring = if (isSelected) KosmoTheme.colors.onBackground else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(States.minTouchTarget)
            .clip(KosmoShapes.control)
            .background(PlayerPalette.colorOf(color))
            .border(States.selectionBorder, ring, KosmoShapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = isSelected
                if (isSelected) stateDescription = selectedText
            }
            .testTag(tag),
    ) {
        if (isSelected) {
            Text(
                text = stringResource(R.string.color_check),
                style = KosmoTheme.typography.button,
                color = PlayerPalette.onColorOf(color),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
