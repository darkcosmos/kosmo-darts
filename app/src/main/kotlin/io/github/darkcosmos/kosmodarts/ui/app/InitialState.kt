package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState

/**
 * The first state of a new ViewModel, from the files found at launch (rules R1, R2, R8, R9, R10, R14).
 *
 * @param wasOnGameScreen whether Android recreates the activity after the process was killed while the game screen
 *   or the between-legs screen was shown (ADR 0002, decision 4). `false` on a fresh launch.
 */
fun initialState(load: LoadResult, wasOnGameScreen: Boolean): AppState {
    val screen = when (val game = load.game) {
        GameLoad.Absent -> Screen.Home()
        is GameLoad.Valid -> if (wasOnGameScreen) game.saved.toScreen() else Screen.Home(resumableGame = game.saved)
        is GameLoad.Invalid -> Screen.Setup(SetupState.prefilled(load.lastSetup), isRestoreFailedShown = true)
    }
    return AppState(screen = screen, lastSetup = load.lastSetup)
}

/** R14: the screen the saved game was on: the between-legs screen (owner Q6 ii), else the game screen. */
fun SavedGame.toScreen(): Screen =
    if (isBetweenLegs) Screen.BetweenLegs(match) else Screen.Game(toGameScreenState())
