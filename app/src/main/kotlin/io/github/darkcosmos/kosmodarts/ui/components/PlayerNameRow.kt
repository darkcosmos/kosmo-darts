package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val STRIP = 6.dp
private val ORDER_WIDTH = 28.dp

/** What one row of the setup player list shows and does. */
class PlayerNameRowSpec(
    val order: Int,
    val name: String,
    val slot: ColorSlot,
    val placeholder: String,
    val errors: List<String>,
    val isLast: Boolean,
    /** TalkBack text of the remove cross, or `null` when the cross is hidden (a single player). */
    val removeDescription: String?,
    val fieldTag: String,
    val removeTag: String,
)

/**
 * A player of the setup (design system, `PlayerNameRow`; M13, PC9): a strip of his color, his order number, the name
 * field (placeholder "Player n name"), and a remove cross (48 dp) when more than one player is left. The focused row
 * has the 2 dp `accent` border. Errors go under the name.
 */
@Composable
fun PlayerNameRow(
    spec: PlayerNameRowSpec,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(KosmoShapes.control)
                .background(colors.surface)
                .border(
                    width = States.selectionBorder,
                    color = if (isFocused) colors.accent else Color.Transparent,
                    shape = KosmoShapes.control,
                ),
        ) {
            Box(
                Modifier
                    .width(STRIP)
                    .fillMaxHeight()
                    .background(PlayerPalette.colorOf(spec.slot, colors)),
            )
            Text(
                text = spec.order.toString(),
                style = KosmoTheme.typography.labelLarge,
                color = colors.onBackgroundMuted,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(ORDER_WIDTH)
                    .clearAndSetSemantics {},
            )
            NameField(spec, onChange, Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused })
            spec.removeDescription?.let { description ->
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(States.minTouchTarget)
                        .testTag(spec.removeTag),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = description,
                        tint = colors.onBackgroundMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun NameField(spec: PlayerNameRowSpec, onChange: (String) -> Unit, modifier: Modifier) {
    val colors = KosmoTheme.colors
    val transparent = Color.Transparent
    TextField(
        value = spec.name,
        onValueChange = onChange,
        placeholder = { Text(spec.placeholder, style = KosmoTheme.typography.bodyLarge) },
        singleLine = true,
        isError = spec.errors.isNotEmpty(),
        // Errors go under the name, inside the row (M13); words and the accent underline, never red (principle 4).
        supportingText = if (spec.errors.isEmpty()) null else { { Text(spec.errors.joinToString("\n")) } },
        textStyle = KosmoTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = if (spec.isLast) ImeAction.Done else ImeAction.Next,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = transparent,
            unfocusedContainerColor = transparent,
            errorContainerColor = transparent,
            focusedIndicatorColor = transparent,
            unfocusedIndicatorColor = transparent,
            // Red is for BUST only (design system, principle 4): an error is marked in accent and told in words.
            errorIndicatorColor = colors.accent,
            focusedTextColor = colors.onBackground,
            unfocusedTextColor = colors.onBackground,
            errorTextColor = colors.onBackground,
            cursorColor = colors.accent,
            focusedPlaceholderColor = colors.onBackgroundMuted,
            unfocusedPlaceholderColor = colors.onBackgroundMuted,
            errorPlaceholderColor = colors.onBackgroundMuted,
            errorSupportingTextColor = colors.onBackground,
        ),
        modifier = modifier.testTag(spec.fieldTag),
    )
}
