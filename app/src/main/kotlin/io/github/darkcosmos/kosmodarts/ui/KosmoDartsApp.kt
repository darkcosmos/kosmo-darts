package io.github.darkcosmos.kosmodarts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import io.github.darkcosmos.kosmodarts.BuildConfig
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.AppState
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketBetweenLegsScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketEndScreen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreen
import io.github.darkcosmos.kosmodarts.ui.end.EndScreen
import io.github.darkcosmos.kosmodarts.ui.game.GameScreen
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScreen
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailScreen
import io.github.darkcosmos.kosmodarts.ui.home.HomeScreen
import io.github.darkcosmos.kosmodarts.ui.players.PlayersScreen
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileScreen
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsEvent
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsScreen
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsUiState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupScreen

/**
 * Shows the current screen. System back is handled explicitly on every screen but home, which closes the app; open
 * dialogs handle back themselves.
 */
@Composable
fun KosmoDartsApp(
    state: AppState,
    settings: SettingsUiState,
    onEvent: (AppEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
) {
    BackHandler(enabled = state.screen !is Screen.Home) { onEvent(AppEvent.Back) }
    when (val screen = state.screen) {
        is Screen.Home -> HomeScreen(
            screen,
            onEvent,
            versionName = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.BUILD_NUMBER,
        )
        is Screen.Setup -> SetupScreen(screen.setup, onEvent, isRestoreFailedShown = screen.isRestoreFailedShown)
        is Screen.Game -> GameScreen(screen.game, onEvent, noVoiceNotice = screen.noVoiceNotice)
        is Screen.BetweenLegs ->
            BetweenLegsScreen(
                screen.match,
                onEvent,
                isAbandonDialogShown = screen.isAbandonDialogShown,
                checkout = screen.checkout,
            )
        is Screen.End -> EndScreen(screen.match, onEvent)
        is Screen.Settings -> SettingsScreen(settings, onSettingsEvent, onBack = { onEvent(AppEvent.Back) })
        is Screen.History -> HistoryScreen(screen.history, onEvent)
        is Screen.MatchDetail -> MatchDetailScreen(screen.detail, onEvent)
        is Screen.Profile -> ProfileScreen(screen.profile, onEvent)
        // T25 adds the head-to-head screen; its stack and Back already work (HS9).
        is Screen.HeadToHead -> Unit
        is Screen.Players -> PlayersScreen(screen.players, onEvent)
        is Screen.CricketGame,
        is Screen.CricketBetweenLegs,
        is Screen.CricketEnd,
        -> CricketScreens(screen, onEvent)
    }
}

/** The Cricket screens, apart so that [KosmoDartsApp] stays a flat dispatch. */
@Composable
private fun CricketScreens(screen: Screen, onEvent: (AppEvent) -> Unit) {
    when (screen) {
        is Screen.CricketGame ->
            CricketGameScreen(screen.game, screen.journal, onEvent, noVoiceNotice = screen.noVoiceNotice)
        is Screen.CricketBetweenLegs ->
            CricketBetweenLegsScreen(
                screen.match,
                screen.journal,
                onEvent,
                isAbandonDialogShown = screen.isAbandonDialogShown,
            )
        is Screen.CricketEnd -> CricketEndScreen(screen.match, screen.journal, onEvent)
        else -> Unit
    }
}
