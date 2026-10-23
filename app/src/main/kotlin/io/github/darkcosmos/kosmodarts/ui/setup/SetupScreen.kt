package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.DialogAction
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.KosmoDialog
import io.github.darkcosmos.kosmodarts.ui.components.PlayerNameRow
import io.github.darkcosmos.kosmodarts.ui.components.PlayerNameRowSpec
import io.github.darkcosmos.kosmodarts.ui.components.ScreenTopBar
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.SegmentedChoice
import io.github.darkcosmos.kosmodarts.ui.format.ruleText
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private val START_SCORES = listOf(501, 301)
private val CHECKOUT_RULES = listOf(CheckoutRule.DOUBLE_OUT, CheckoutRule.SINGLE_OUT)
private val SECTION_GAP = 12.dp
private val START_MIN_HEIGHT = 58.dp
private const val DASH = 8f

/**
 * Setup screen (mockup 07; M13, PC2 to PC4). Everything above "START" scrolls; "START" stays pinned at the bottom,
 * above the keyboard when it is open, so it never covers the focused name field (U7). [isRestoreFailedShown] opens the
 * "saved game could not be resumed" message over it (rule R9).
 */
@Composable
fun SetupScreen(
    setup: SetupState,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    isRestoreFailedShown: Boolean = false,
) {
    if (isRestoreFailedShown) RestoreFailedDialog(onEvent)
    val horizontal = ScreenLayoutPolicy.HORIZONTAL_MARGIN.dp
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(KosmoTheme.colors.background),
    ) {
        // safeDrawingPadding includes the keyboard: the pinned button sits right above it.
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .widthIn(max = ScreenLayoutPolicy.CONTENT_MAX_WIDTH.dp)
                .fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag(SetupTags.CONTENT)
                    .padding(horizontal = horizontal, vertical = ScreenLayoutPolicy.VERTICAL_MARGIN.dp),
            ) {
                ScreenTopBar(
                    title = stringResource(R.string.setup_title),
                    accentSuffix = stringResource(R.string.setup_title_mode),
                    onBack = { onEvent(AppEvent.Back) },
                    backTag = SetupTags.BACK,
                )
                GameSettings(setup, onEvent)
                MatchSettings(setup, onEvent)
                Players(setup, onEvent)
            }
            KosmoButton(
                text = stringResource(R.string.setup_start),
                onClick = { onEvent(AppEvent.StartRequested) },
                minHeight = START_MIN_HEIGHT,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontal, vertical = 12.dp)
                    .testTag(SetupTags.START),
            )
        }
    }
}

/** A dialog rather than a snackbar: shown once, it must stay until read. Restyled (owner Q4 of issue #49). */
@Composable
private fun RestoreFailedDialog(onEvent: (AppEvent) -> Unit) {
    KosmoDialog(
        onDismissRequest = { onEvent(AppEvent.RestoreFailedDismissed) },
        text = stringResource(R.string.restore_failed_message),
        confirm = DialogAction(stringResource(R.string.restore_failed_ok)) { onEvent(AppEvent.RestoreFailedDismissed) },
    )
}

/** "GAME": 501 / 301, then double / single out. */
@Composable
private fun GameSettings(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    SectionLabel(stringResource(R.string.setup_game_label))
    SegmentedChoice(
        options = START_SCORES,
        selected = setup.startScore,
        label = { stringResource(R.string.format_start_score, it) },
        onSelect = { onEvent(AppEvent.StartScoreSelected(it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    SegmentedChoice(
        options = CHECKOUT_RULES,
        selected = setup.checkoutRule,
        label = { ruleText(it) },
        onSelect = { onEvent(AppEvent.CheckoutRuleSelected(it)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * "PLAYERS · THROWING ORDER" with "SHUFFLE", one row per player (color strip, order, name, remove cross), then
 * "+ ADD A PLAYER" while a color is free. The cross removes the player and his typed name at once (U7, PC3).
 */
@Composable
private fun Players(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.setup_players_label), Modifier.weight(1f))
        KosmoButton(
            text = stringResource(R.string.setup_shuffle),
            onClick = { onEvent(AppEvent.PlayersShuffled) },
            kind = ButtonKind.OUTLINED_ACCENT,
            enabled = setup.names.size > 1,
            modifier = Modifier.testTag(SetupTags.SHUFFLE),
        )
    }
    setup.roster.entries.forEachIndexed { index, entry ->
        val fallback = stringResource(R.string.setup_player_name, index + 1)
        val removeName = entry.name.trim().ifEmpty { fallback }
        PlayerNameRow(
            spec = PlayerNameRowSpec(
                order = index + 1,
                name = entry.name,
                slot = entry.slot,
                placeholder = stringResource(R.string.setup_player_placeholder, index + 1),
                errors = setup.errorsFor(index).map { errorText(it) },
                isLast = index == setup.roster.entries.lastIndex,
                removeDescription = if (setup.canRemovePlayer) {
                    stringResource(R.string.setup_remove_player, removeName)
                } else {
                    null
                },
                fieldTag = SetupTags.nameField(index),
                removeTag = SetupTags.remove(index),
            ),
            onChange = { onEvent(AppEvent.NameChanged(index, it)) },
            onRemove = { onEvent(AppEvent.PlayerRemoved(index)) },
        )
    }
    if (setup.canAddPlayer) AddPlayerButton(onEvent)
    setup.screenErrors.forEach { error ->
        Text(text = errorText(error), style = KosmoTheme.typography.body, color = KosmoTheme.colors.onBackground)
    }
}

/** "+ ADD A PLAYER": a dashed outline, read as "Add a player". */
@Composable
private fun AddPlayerButton(onEvent: (AppEvent) -> Unit) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(KosmoShapes.control)
            .drawBehind {
                drawRoundRect(
                    color = colors.outline,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
                    ),
                )
            }
            .clickable(role = Role.Button) { onEvent(AppEvent.PlayerAdded) }
            .testTag(SetupTags.ADD_PLAYER)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_add_symbol),
            style = KosmoTheme.typography.button,
            color = colors.onBackgroundMuted,
            modifier = Modifier
                .padding(end = 8.dp)
                .clearAndSetSemantics {},
        )
        Text(
            text = stringResource(R.string.setup_add_player),
            style = KosmoTheme.typography.button,
            color = colors.onBackgroundMuted,
            textAlign = TextAlign.Center,
        )
    }
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

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SetupScreenPreview() {
    KosmoDartsTheme {
        SetupScreen(setup = SetupState(names = listOf("Ann", "")), onEvent = {})
    }
}
