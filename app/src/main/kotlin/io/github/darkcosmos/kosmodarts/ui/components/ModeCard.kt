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
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val THUMBNAIL = 64.dp

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
            Text(text = thumbnail, style = KosmoTheme.typography.title, color = colors.onAccent, maxLines = 1)
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
