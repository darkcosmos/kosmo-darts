package io.github.darkcosmos.kosmodarts.capture.ui.keypad

// Copied from :app (issue #41, decision Q11); not kept in sync.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.capture.R
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.capture.ui.theme.States
import io.github.darkcosmos.kosmodarts.capture.ui.theme.fixedSp

private val ACTIVE_BORDER = 2.dp

/** Least size (dp) of a key label: small keys on short landscape screens shrink their label rather than cut it. */
private const val KEY_MIN_FONT = 8f

/** Double or Triple (owner X1, U8): the active one shows a check mark and an inner border, and is "selected". */
@Composable
internal fun RowScope.ModifierKey(
    isActive: Boolean,
    isEnabled: Boolean,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val mark = stringResource(R.string.key_selected_mark)
    Key(
        text = if (isActive) "$mark $label" else label,
        tag = tag,
        style = KosmoTheme.typography.keypadLabel,
        enabled = isEnabled,
        isLocked = !isEnabled,
        isActive = isActive,
        modifier = Modifier.semantics {
            selected = isActive
            // TalkBack reads the label, not the check mark: the selected state already says it.
            contentDescription = label
        },
        onClick = onClick,
    )
}

/**
 * One key of a keypad, shared by the X01 and Cricket keypads. A key disabled inside an unlocked keypad is at 40 %; a
 * locked keypad is dimmed as a whole, so its keys keep their colors under the keypad alpha.
 *
 * @param weight the share of the row width.
 */
@Composable
internal fun RowScope.Key(
    text: String,
    tag: String,
    style: TextStyle,
    enabled: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isOutlined: Boolean = false,
    weight: Float = 1f,
) {
    val colors = KosmoTheme.colors
    val container = when {
        isActive -> colors.accent
        isOutlined -> Color.Transparent
        else -> colors.surfaceHigh
    }
    val content = when {
        isActive -> colors.onAccent
        isOutlined -> colors.onBackgroundMuted
        else -> colors.onBackground
    }
    val disabledAlpha = if (isLocked) 1f else States.DISABLED_ALPHA
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = KosmoShapes.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * disabledAlpha),
            disabledContentColor = content.copy(alpha = disabledAlpha),
        ),
        border = when {
            isActive -> BorderStroke(ACTIVE_BORDER, colors.onBackground)
            isOutlined -> BorderStroke(1.dp, colors.outline)
            else -> null
        },
        // Clear of the 2 dp border of the active key (U11).
        contentPadding = PaddingValues(horizontal = ACTIVE_BORDER + 2.dp, vertical = 2.dp),
        modifier = modifier
            .weight(weight)
            .fillMaxHeight()
            .testTag(tag),
    ) {
        // U11: the whole word is always shown ("DOUBLE", never "DOUB"); "✓ DOUBLE" may only wrap at its space.
        WordSafeText(
            text = text,
            style = style,
            maxLines = wordCount(text),
            minFontSize = LocalDensity.current.fixedSp(KEY_MIN_FONT),
        )
    }
}
