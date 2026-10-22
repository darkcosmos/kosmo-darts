package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private val CONTENT_MAX_WIDTH = 560.dp
private val MIN_TOUCH_TARGET = 48.dp
private val GEAR_CLEARANCE = 56.dp

/** Home screen; with a saved game, its summary, "New game" and "Resume game" under it (rules R2, R3). */
@Composable
fun HomeScreen(home: Screen.Home, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    // The top padding keeps scrolled content clear of the gear (AN13).
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = GEAR_CLEARANCE),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(40.dp))
                val saved = home.resumableGame
                if (saved == null) {
                    Button(onClick = { onEvent(AppEvent.NewGame) }) {
                        Text(text = stringResource(R.string.home_new_game))
                    }
                } else {
                    SavedGameActions(ResumeSummary.of(saved), onEvent)
                }
            }
            SettingsButton(onEvent, Modifier.align(Alignment.TopEnd))
        }
    }
    if (home.isNewGameDialogShown) NewGameDialog(onEvent)
}

/** AN13: the gear opens the Settings screen. It takes the status bar and cutout insets, like the content. */
@Composable
private fun SettingsButton(onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    IconButton(
        onClick = { onEvent(AppEvent.SettingsTapped) },
        modifier = modifier
            .safeDrawingPadding()
            .padding(4.dp)
            .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            .testTag(HomeTags.SETTINGS),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.home_settings),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** "Resume game" is the main action while a game is saved, but stays under "New game" (owner decision, Q1). */
@Composable
private fun SavedGameActions(summary: ResumeSummary, onEvent: (AppEvent) -> Unit) {
    val actionModifier = Modifier
        .widthIn(max = CONTENT_MAX_WIDTH)
        .fillMaxWidth()
        .sizeIn(minHeight = MIN_TOUCH_TARGET)
    Column(
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(summary)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { onEvent(AppEvent.NewGame) }, modifier = actionModifier) {
            Text(text = stringResource(R.string.home_new_game))
        }
        Button(onClick = { onEvent(AppEvent.ResumeTapped) }, modifier = actionModifier) {
            Text(text = stringResource(R.string.home_resume_game))
        }
    }
}

/**
 * Names wrap and scores never do, so a 20-character name never pushes a score out of the card. Every player row is
 * one TalkBack item ("name, score").
 */
@Composable
private fun SummaryCard(summary: ResumeSummary) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTags.SUMMARY),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_saved_game_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val rule = ruleText(summary.checkoutRule)
            Text(
                text = stringResource(R.string.home_saved_game_mode, summary.startScore, rule),
                style = MaterialTheme.typography.bodyLarge,
            )
            summary.players.forEachIndexed { index, player ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HomeTags.player(index))
                        .semantics(mergeDescendants = true) {},
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = player.name, style = MaterialTheme.typography.bodyLarge)
                        // M18: the match score, with the same labels as the cards; it wraps, the remaining never does.
                        matchScoreText(player)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag(HomeTags.matchScore(index)),
                            )
                        }
                    }
                    Text(
                        text = player.remaining.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.testTag(HomeTags.score(index)),
                    )
                }
            }
            Text(
                text = if (summary.isCheckoutToConfirm) {
                    stringResource(R.string.home_saved_game_checkout, summary.highlightedPlayer)
                } else {
                    stringResource(R.string.game_to_throw, summary.highlightedPlayer)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** M18: "Sets 1 · Legs 2" (the sets only with more than one set), or `null` for a single game. */
@Composable
private fun matchScoreText(player: ResumeSummary.PlayerScore): String? = player.legsWon?.let { legs ->
    listOfNotNull(
        player.setsWon?.let { stringResource(R.string.match_score_sets, it) },
        stringResource(R.string.match_score_legs, legs),
    ).joinToString(stringResource(R.string.match_score_separator))
}

@Composable
private fun ruleText(rule: CheckoutRule): String = when (rule) {
    CheckoutRule.DOUBLE_OUT -> stringResource(R.string.setup_double_out)
    CheckoutRule.SINGLE_OUT -> stringResource(R.string.setup_single_out)
}

/** Rule R3: "Cancel", back or a tap outside keep the saved game; "Delete" deletes it and opens the setup. */
@Composable
private fun NewGameDialog(onEvent: (AppEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AppEvent.NewGameDismissed) },
        title = { Text(stringResource(R.string.new_game_confirm_title)) },
        text = { Text(stringResource(R.string.new_game_confirm_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(AppEvent.NewGameConfirmed) }) {
                Text(stringResource(R.string.new_game_confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AppEvent.NewGameDismissed) }) {
                Text(stringResource(R.string.new_game_confirm_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun HomeScreenPreview() {
    KosmoDartsTheme {
        HomeScreen(home = Screen.Home(), onEvent = {})
    }
}

@Preview
@Composable
private fun HomeScreenWithSavedGamePreview() {
    val game = X01Game(X01Config(listOf("Ann", "Bob", "Cid")), listOf(Dart.triple(20), Dart.single(5)))
    KosmoDartsTheme {
        HomeScreen(home = Screen.Home(resumableGame = SavedGame(game, isAwaitingNextPlayer = false)), onEvent = {})
    }
}
