package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/** A section header: `label` style, capitals, `onBackgroundMuted` (mockups 06, 07, 10). Read as a heading. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = KosmoTheme.colors.onBackgroundMuted) {
    Text(
        text = text,
        style = KosmoTheme.typography.label,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The top bar of setup and settings: a back arrow (48 dp, "Back" for TalkBack) and the screen title in `title`
 * style, its optional [accentSuffix] in `accent` ("NEW GAME · X01").
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    modifier: Modifier = Modifier,
    accentSuffix: String? = null,
) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .sizeIn(minWidth = States.minTouchTarget, minHeight = States.minTouchTarget)
                .testTag(backTag),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.onBackground,
            )
        }
        Text(
            text = buildAnnotatedString {
                append(title)
                accentSuffix?.let {
                    append(stringResource(R.string.format_separator))
                    withStyle(SpanStyle(color = colors.accent)) { append(it) }
                }
            },
            style = KosmoTheme.typography.title,
            color = colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}
