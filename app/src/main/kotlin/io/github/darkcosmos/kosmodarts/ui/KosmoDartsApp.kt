package io.github.darkcosmos.kosmodarts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.AppState
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.between.BetweenLegsScreen
import io.github.darkcosmos.kosmodarts.ui.end.EndScreen
import io.github.darkcosmos.kosmodarts.ui.game.GameScreen
import io.github.darkcosmos.kosmodarts.ui.home.HomeScreen
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
        is Screen.Home -> HomeScreen(screen, onEvent)
        is Screen.Setup -> SetupScreen(screen.setup, onEvent, isRestoreFailedShown = screen.isRestoreFailedShown)
        is Screen.Game -> GameScreen(screen.game, onEvent, noVoiceNotice = screen.noVoiceNotice)
        is Screen.BetweenLegs ->
            BetweenLegsScreen(screen.match, onEvent, isAbandonDialogShown = screen.isAbandonDialogShown)
        is Screen.End -> EndScreen(screen.match, onEvent)
        is Screen.Settings -> SettingsScreen(settings, onSettingsEvent, onBack = { onEvent(AppEvent.Back) })
    }
}
