package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.components.ScreenTopBar
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The frame both History screens share (HS6; list here, detail in T12): `background`, the system bars and the display
 * cutout kept clear (edge to edge), a single column that never gets wider than [ScreenLayoutPolicy.CONTENT_MAX_WIDTH]
 * (portrait, landscape and tablets), and the top bar with its Back action and the description « Retour ».
 *
 * [content] fills the column under the top bar; give a scrolling child `Modifier.weight(1f)`.
 */
@Composable
fun HistoryScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTag: String = HistoryTags.BACK,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(KosmoTheme.colors.background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .fillMaxHeight()
                .widthIn(max = ScreenLayoutPolicy.CONTENT_MAX_WIDTH.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = ScreenLayoutPolicy.HORIZONTAL_MARGIN.dp,
                    vertical = ScreenLayoutPolicy.VERTICAL_MARGIN.dp,
                ),
        ) {
            ScreenTopBar(title, onBack = onBack, backTag = backTag)
            content()
        }
    }
}
