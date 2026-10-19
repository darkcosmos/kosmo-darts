package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupResult
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import kotlin.random.Random

/**
 * Computes the next [AppState] for an [AppEvent]. Pure, except for the injected [random] used by Shuffle. Storage
 * follows from the states it returns (see [persistenceEffects]).
 */
class AppReducer(private val random: Random) {

    fun reduce(state: AppState, event: AppEvent): AppState = when (val screen = state.screen) {
        is Screen.Home -> onHome(state, screen, event)
        is Screen.Setup -> onSetup(state, screen, event)
        is Screen.Game -> onGame(state, screen, event)
        is Screen.End -> onEnd(state, screen.game, event)
        is Screen.Settings -> onSettings(state, screen, event)
    }

    /** Rules R2 and R3: "Resume game", and "New game" that asks before deleting the saved game. */
    private fun onHome(state: AppState, home: Screen.Home, event: AppEvent): AppState {
        val saved = home.resumableGame
        return when {
            home.isNewGameDialogShown -> when (event) {
                AppEvent.NewGameConfirmed -> state.toSetup()
                AppEvent.NewGameDismissed, AppEvent.Back ->
                    state.copy(screen = home.copy(isNewGameDialogShown = false))
                else -> state
            }
            event == AppEvent.SettingsTapped -> state.copy(screen = Screen.Settings(home))
            event == AppEvent.NewGame && saved == null -> state.toSetup()
            event == AppEvent.NewGame -> state.copy(screen = home.copy(isNewGameDialogShown = true))
            event == AppEvent.ResumeTapped && saved != null ->
                state.copy(screen = Screen.Game(saved.toGameScreenState()))
            else -> state
        }
    }

    private fun onSetup(state: AppState, screen: Screen.Setup, event: AppEvent): AppState = when {
        // Rule R9: the message is modal; it only closes.
        screen.isRestoreFailedShown -> when (event) {
            AppEvent.RestoreFailedDismissed, AppEvent.Back ->
                state.copy(screen = screen.copy(isRestoreFailedShown = false))
            else -> state
        }
        event == AppEvent.Back -> state.copy(screen = Screen.Home())
        event == AppEvent.StartRequested -> when (val result = screen.setup.start()) {
            is SetupResult.Valid -> AppState(
                screen = Screen.Game(GameScreenState(X01Game(result.config)), isNewGame = true),
                lastSetup = result.config,
            )
            is SetupResult.Invalid -> state.copy(screen = Screen.Setup(result.setup))
        }
        else -> state.copy(screen = Screen.Setup(editSetup(screen.setup, event)))
    }

    private fun editSetup(setup: SetupState, event: AppEvent): SetupState = when (event) {
        is AppEvent.StartScoreSelected -> setup.withStartScore(event.score)
        is AppEvent.CheckoutRuleSelected -> setup.withCheckoutRule(event.rule)
        is AppEvent.NameChanged -> setup.withName(event.index, event.name)
        AppEvent.PlayerAdded -> setup.withPlayerAdded()
        AppEvent.PlayerRemoved -> setup.withLastPlayerRemoved()
        AppEvent.PlayersShuffled -> setup.shuffled(random)
        else -> setup
    }

    private fun onGame(state: AppState, screen: Screen.Game, event: AppEvent): AppState {
        val game = screen.game
        return when {
            // AN16: the message is modal; "OK", back and "Install a voice" close it.
            screen.noVoiceNotice != null -> when (event) {
                AppEvent.NoVoiceNoticeDismissed, AppEvent.InstallVoiceTapped, AppEvent.Back ->
                    state.copy(screen = screen.copy(noVoiceNotice = null))
                else -> state
            }
            event is AppEvent.VoiceUnavailable ->
                state.copy(screen = screen.copy(noVoiceNotice = NoVoiceNotice(event.canInstall)))
            // Rule R6: leaving the game screen this way deletes the saved game.
            event == AppEvent.AbandonConfirmed -> state.copy(screen = Screen.Home())
            event == AppEvent.FinishGameTapped && game.isCheckoutDialogShown ->
                state.copy(screen = Screen.End(game.game))
            // The checkout dialog cannot be dismissed with system back.
            event == AppEvent.Back && game.isCheckoutDialogShown -> state
            else -> state.copy(screen = screen.copy(game = playGame(game, event)))
        }
    }

    /** AN13: back returns to the home screen it came from. */
    private fun onSettings(state: AppState, screen: Screen.Settings, event: AppEvent): AppState =
        if (event == AppEvent.Back) state.copy(screen = screen.home) else state

    private fun playGame(game: GameScreenState, event: AppEvent): GameScreenState = when (event) {
        AppEvent.Back -> game.withAbandonDialog(shown = true)
        AppEvent.AbandonDismissed -> game.withAbandonDialog(shown = false)
        is AppEvent.NumberTapped -> game.throwNumber(event.number)
        is AppEvent.MultiplierTapped -> game.toggleMultiplier(event.multiplier)
        AppEvent.OuterBullTapped -> game.throwOuterBull()
        AppEvent.BullTapped -> game.throwBull()
        AppEvent.MissTapped -> game.throwMiss()
        AppEvent.UndoTapped -> game.undo()
        AppEvent.NextPlayerTapped -> game.nextPlayer()
        else -> game
    }

    private fun onEnd(state: AppState, game: X01Game, event: AppEvent): AppState = when (event) {
        AppEvent.Back -> state.copy(screen = Screen.Home())
        AppEvent.NewGame -> state.toSetup()
        // Rule R7: a rematch is a game start, so its order becomes the last setup (owner decision, issue #30 Q3).
        AppEvent.Rematch -> rematchConfig(game.config).let { config ->
            AppState(screen = Screen.Game(GameScreenState(X01Game(config)), isNewGame = true), lastSetup = config)
        }
        else -> state
    }

    private fun AppState.toSetup(): AppState = copy(screen = Screen.Setup(SetupState.prefilled(lastSetup)))

    /** Same players and settings; the first player moves to the end, so the next one starts (A, B, C -> B, C, A). */
    private fun rematchConfig(config: X01Config): X01Config =
        X01Config(config.players.drop(1) + config.players.first(), config.startScore, config.checkoutRule)
}
