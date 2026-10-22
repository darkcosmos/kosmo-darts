package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The frame of the between-legs and end screens (plan of issue #49, section 4b): inside the system bars and the
 * cutout, the content scrolls; the main [buttons] stay pinned at the bottom when [ScreenLayoutPolicy] keeps enough
 * height above them, else they scroll at the end of the content, so nothing is ever hidden at a large font size.
 */
@Composable
fun ScrollingScreen(
    buttonCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    val horizontal = ScreenLayoutPolicy.HORIZONTAL_MARGIN.dp
    val vertical = ScreenLayoutPolicy.VERTICAL_MARGIN.dp
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(KosmoTheme.colors.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .safeDrawingPadding()
                .widthIn(max = ScreenLayoutPolicy.CONTENT_MAX_WIDTH.dp)
                .fillMaxWidth(),
        ) {
            val fontScale = LocalDensity.current.fontScale
            val layout = ScreenLayoutPolicy.of(maxHeight.value, fontScale, buttonCount)
            val gap = Arrangement.spacedBy(12.dp)
            if (layout.isMainButtonPinned) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = gap,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = horizontal, vertical = vertical),
                        content = content,
                    )
                    Column(
                        verticalArrangement = gap,
                        modifier = Modifier.padding(start = horizontal, end = horizontal, bottom = vertical),
                        content = buttons,
                    )
                }
            } else {
                Column(
                    verticalArrangement = gap,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontal, vertical = vertical),
                ) {
                    content()
                    Spacer(Modifier.padding(top = 4.dp))
                    buttons()
                }
            }
        }
    }
}
