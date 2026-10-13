package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private val START_SCORES = listOf(501, 301)
private val CHECKOUT_RULES = listOf(CheckoutRule.DOUBLE_OUT, CheckoutRule.SINGLE_OUT)
private val CONTENT_MAX_WIDTH = 560.dp
private val MIN_TOUCH_TARGET = 48.dp

@Composable
fun SetupScreen(setup: SetupState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                GameSettings(setup, onEvent)
                PlayersHeader(setup, onEvent)
                setup.names.forEachIndexed { index, name ->
                    NameField(
                        index = index,
                        name = name,
                        errors = setup.errorsFor(index),
                        isLast = index == setup.names.lastIndex,
                        onChange = { onEvent(AppEvent.NameChanged(index, it)) },
                    )
                }
                setup.screenErrors.forEach { error ->
                    Text(
                        text = errorText(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = { onEvent(AppEvent.StartRequested) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = MIN_TOUCH_TARGET),
                ) {
                    Text(stringResource(R.string.setup_start))
                }
            }
        }
    }
}

@Composable
private fun GameSettings(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    SectionLabel(stringResource(R.string.setup_game_label))
    ChoiceRow(
        options = START_SCORES,
        selected = setup.startScore,
        label = { stringResource(R.string.setup_start_score, it) },
        onSelect = { onEvent(AppEvent.StartScoreSelected(it)) },
    )
    SectionLabel(stringResource(R.string.setup_finish_label))
    ChoiceRow(
        options = CHECKOUT_RULES,
        selected = setup.checkoutRule,
        label = { checkoutRuleText(it) },
        onSelect = { onEvent(AppEvent.CheckoutRuleSelected(it)) },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label(option))
            }
        }
    }
}

@Composable
private fun PlayersHeader(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    val removeDescription = stringResource(R.string.setup_remove_player)
    val addDescription = stringResource(R.string.setup_add_player)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SectionLabel(stringResource(R.string.setup_players_label))
        }
        FilledTonalIconButton(
            onClick = { onEvent(AppEvent.PlayerRemoved) },
            enabled = setup.canRemovePlayer,
            modifier = Modifier.semantics { contentDescription = removeDescription },
        ) {
            SymbolText(stringResource(R.string.setup_remove_symbol))
        }
        FilledTonalIconButton(
            onClick = { onEvent(AppEvent.PlayerAdded) },
            enabled = setup.canAddPlayer,
            modifier = Modifier.semantics { contentDescription = addDescription },
        ) {
            SymbolText(stringResource(R.string.setup_add_symbol))
        }
        OutlinedButton(
            onClick = { onEvent(AppEvent.PlayersShuffled) },
            enabled = setup.names.size > 1,
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text(stringResource(R.string.setup_shuffle))
        }
    }
}

@Composable
private fun NameField(
    index: Int,
    name: String,
    errors: List<X01ConfigError>,
    isLast: Boolean,
    onChange: (String) -> Unit,
) {
    val errorTexts = errors.map { errorText(it) }
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.setup_player_name, index + 1)) },
        singleLine = true,
        isError = errors.isNotEmpty(),
        supportingText = if (errorTexts.isEmpty()) null else { { Text(errorTexts.joinToString("\n")) } },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SetupTags.nameField(index)),
    )
}

@Composable
private fun checkoutRuleText(rule: CheckoutRule): String = when (rule) {
    CheckoutRule.DOUBLE_OUT -> stringResource(R.string.setup_double_out)
    CheckoutRule.SINGLE_OUT -> stringResource(R.string.setup_single_out)
}

@Composable
private fun errorText(error: X01ConfigError): String = when (error) {
    X01ConfigError.INVALID_START_SCORE -> stringResource(R.string.setup_error_invalid_start_score)
    X01ConfigError.NO_PLAYERS -> stringResource(R.string.setup_error_no_players)
    X01ConfigError.TOO_MANY_PLAYERS -> stringResource(R.string.setup_error_too_many_players, X01Config.MAX_PLAYERS)
    X01ConfigError.BLANK_NAME -> stringResource(R.string.setup_error_blank_name)
    X01ConfigError.NAME_TOO_LONG -> stringResource(R.string.setup_error_name_too_long, X01Config.MAX_NAME_LENGTH)
    X01ConfigError.DUPLICATE_NAME -> stringResource(R.string.setup_error_duplicate_name)
}

@Preview
@Composable
private fun SetupScreenPreview() {
    KosmoDartsTheme {
        SetupScreen(setup = SetupState(names = listOf("Ann", "")), onEvent = {})
    }
}

/** The button content description already says what the symbol means, so the symbol itself is not read. */
@Composable
private fun SymbolText(symbol: String) {
    Text(text = symbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
}
