package io.github.darkcosmos.kosmodarts.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.components.DialogAction
import io.github.darkcosmos.kosmodarts.ui.components.KosmoDialog
import io.github.darkcosmos.kosmodarts.ui.components.ModeCard
import io.github.darkcosmos.kosmodarts.ui.components.ResumeCard
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * Home screen (mockup 06; R2, R3, M18): the title and the settings button, the "GAME IN PROGRESS" card when a game is
 * saved, then "NEW GAME" with the X01 and Cricket mode cards. Everything scrolls.
 */
@Composable
fun HomeScreen(home: Screen.Home, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .widthIn(max = ScreenLayoutPolicy.CONTENT_MAX_WIDTH.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = ScreenLayoutPolicy.HORIZONTAL_MARGIN.dp,
                    vertical = ScreenLayoutPolicy.VERTICAL_MARGIN.dp,
                ),
        ) {
            TitleRow(onEvent)
            home.resumableGame?.let { saved ->
                ResumeCard(
                    summary = ResumeSummary.of(saved),
                    onResume = { onEvent(AppEvent.ResumeTapped) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SectionLabel(stringResource(R.string.home_new_game_section), Modifier.padding(top = 8.dp))
            // CR20: X01, then Cricket (mockup 06).
            ModeCard(
                thumbnail = stringResource(R.string.mode_x01_title),
                title = stringResource(R.string.mode_x01_title),
                description = stringResource(R.string.mode_x01_description),
                onClick = { onEvent(AppEvent.NewGame(GameMode.X01)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTags.MODE_X01),
            )
            ModeCard(
                thumbnail = stringResource(R.string.mode_cricket_thumbnail),
                title = stringResource(R.string.cricket_title),
                description = stringResource(R.string.mode_cricket_description),
                onClick = { onEvent(AppEvent.NewGame(GameMode.CRICKET)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTags.MODE_CRICKET),
            )
        }
    }
    if (home.isNewGameDialogShown) NewGameDialog(onEvent)
}

/** "KOSMO DARTS" ("DARTS" in `accent`), read as the app name; the settings button on the right (AN13). */
@Composable
private fun TitleRow(onEvent: (AppEvent) -> Unit) {
    val colors = KosmoTheme.colors
    val appName = stringResource(R.string.home_title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.home_title_first))
                append(" ")
                withStyle(SpanStyle(color = colors.accent)) { append(stringResource(R.string.home_title_second)) }
            },
            style = KosmoTheme.typography.title,
            color = colors.onBackground,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    contentDescription = appName
                },
        )
        IconButton(
            onClick = { onEvent(AppEvent.SettingsTapped) },
            colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surface),
            shape = KosmoShapes.control,
            modifier = Modifier
                .size(States.minTouchTarget)
                .testTag(HomeTags.SETTINGS),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.home_settings),
                tint = colors.onBackground,
            )
        }
    }
}

/** Rule R3: "Cancel", back or a tap outside keep the saved game; "Delete" deletes it and opens the setup. */
@Composable
private fun NewGameDialog(onEvent: (AppEvent) -> Unit) {
    KosmoDialog(
        onDismissRequest = { onEvent(AppEvent.NewGameDismissed) },
        title = stringResource(R.string.new_game_confirm_title),
        text = stringResource(R.string.new_game_confirm_text),
        confirm = DialogAction(stringResource(R.string.new_game_confirm_delete)) {
            onEvent(AppEvent.NewGameConfirmed)
        },
        dismiss = DialogAction(stringResource(R.string.new_game_confirm_cancel)) {
            onEvent(AppEvent.NewGameDismissed)
        },
    )
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun HomeScreenPreview() {
    KosmoDartsTheme {
        HomeScreen(
            home = Screen.Home(), onEvent = {})
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun HomeScreenWithSavedGamePreview() {
    val darts = listOf(Dart.triple(20), Dart.single(5))
    val game = X01Game(X01Config(listOf("Ann", "Bob", "Cid")), darts)
    val empty = MatchJournal("00000000-0000-4000-8000-000000000000", 0L)
    val journal = darts.foldIndexed(empty) { i, acc, dart -> acc.enter(dart, i.toLong()) }
    KosmoDartsTheme {
        HomeScreen(
            home = Screen.Home(resumableGame = SavedGame(game, isAwaitingNextPlayer = false, journal = journal)),
            onEvent = {},
        )
    }
}
