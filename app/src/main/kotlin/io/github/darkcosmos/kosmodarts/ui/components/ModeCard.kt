package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val THUMBNAIL = 64.dp
private val THUMBNAIL_PADDING = 4.dp
private val THUMBNAIL_MIN_FONT = 10.sp
private val THUMBNAIL_MAX_FONT = 32.sp

/**
 * A game mode of the home screen (design system, `ModeCard`): a 64 × 64 thumbnail, the mode name, its description and
 * a chevron. The whole card is one button for TalkBack ("X01, 501 or 301, double or single out").
 */
@Composable
fun ModeCard(
    thumbnail: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .sizeIn(minHeight = States.minTouchTarget)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(THUMBNAIL)
                .clip(KosmoShapes.control)
                .background(colors.accent)
                .clearAndSetSemantics {},
        ) {
            // "15–20" is wider than "X01": the text shrinks until it fits the box, whatever the font scale (U7).
            Text(
                text = thumbnail,
                style = KosmoTheme.typography.title,
                color = colors.onAccent,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = THUMBNAIL_MIN_FONT, maxFontSize = THUMBNAIL_MAX_FONT),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = THUMBNAIL_PADDING),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = KosmoTheme.typography.title, color = colors.onBackground)
            Text(text = description, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.onBackgroundMuted,
        )
    }
}
