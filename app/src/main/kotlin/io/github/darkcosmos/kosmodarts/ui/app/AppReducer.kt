package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupResult
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import kotlin.random.Random

/** Computes the next [AppState] for an [AppEvent]. Pure, except for the injected [random] used by Shuffle. */
class AppReducer(private val random: Random) {

    fun reduce(state: AppState, event: AppEvent): AppState = when (val screen = state.screen) {
        Screen.Home -> onHome(state, event)
        is Screen.Setup -> onSetup(state, screen.setup, event)
        is Screen.Game -> onGame(state, screen.game, event)
        is Screen.End -> onEnd(state, screen.game, event)
    }

    private fun onHome(state: AppState, event: AppEvent): AppState =
        if (event == AppEvent.NewGame) state.toSetup() else state

    private fun onSetup(state: AppState, setup: SetupState, event: AppEvent): AppState = when (event) {
        AppEvent.Back -> state.copy(screen = Screen.Home)
        AppEvent.StartRequested -> when (val result = setup.start()) {
            is SetupResult.Valid -> AppState(
                screen = Screen.Game(GameScreenState(X01Game(result.config))),
                lastNames = result.config.players,
            )
            is SetupResult.Invalid -> state.copy(screen = Screen.Setup(result.setup))
        }
        else -> state.copy(screen = Screen.Setup(editSetup(setup, event)))
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

    private fun onGame(state: AppState, game: GameScreenState, event: AppEvent): AppState {
        if (event == AppEvent.AbandonConfirmed) return state.copy(screen = Screen.Home)
        val next = playGame(game, event)
        val screen = if (next.game.state.isFinished) Screen.End(next.game) else Screen.Game(next)
        return state.copy(screen = screen)
    }

    private fun playGame(game: GameScreenState, event: AppEvent): GameScreenState = when (event) {
        AppEvent.Back -> game.withAbandonDialog(shown = true)
        AppEvent.AbandonDismissed -> game.withAbandonDialog(shown = false)
        is AppEvent.NumberTapped -> game.throwNumber(event.number)
        is AppEvent.MultiplierTapped -> game.toggleMultiplier(event.multiplier)
        AppEvent.OuterBullTapped -> game.throwOuterBull()
        AppEvent.BullTapped -> game.throwBull()
        AppEvent.MissTapped -> game.throwMiss()
        AppEvent.UndoTapped -> game.undo()
        else -> game
    }

    private fun onEnd(state: AppState, game: X01Game, event: AppEvent): AppState = when (event) {
        AppEvent.Back -> state.copy(screen = Screen.Home)
        AppEvent.NewGame -> state.toSetup()
        AppEvent.UndoTapped -> state.copy(screen = Screen.Game(GameScreenState(game.undo())))
        AppEvent.Rematch -> state.copy(screen = Screen.Game(GameScreenState(X01Game(rematchConfig(game.config)))))
        else -> state
    }

    private fun AppState.toSetup(): AppState = copy(screen = Screen.Setup(SetupState.prefilled(lastNames)))

    /** Same players and settings; the first player moves to the end, so the next one starts (A, B, C -> B, C, A). */
    private fun rematchConfig(config: X01Config): X01Config =
        X01Config(config.players.drop(1) + config.players.first(), config.startScore, config.checkoutRule)
}
