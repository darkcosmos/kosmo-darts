package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState

/**
 * The first state of a new ViewModel, from the files found at launch (rules R1, R2, R8, R9, R10).
 *
 * @param wasOnGameScreen whether Android recreates the activity after the process was killed while the game screen
 *   was shown (ADR 0002, decision 4). `false` on a fresh launch.
 */
fun initialState(load: LoadResult, wasOnGameScreen: Boolean): AppState {
    val screen = when (val game = load.game) {
        GameLoad.Absent -> Screen.Home()
        is GameLoad.Valid -> if (wasOnGameScreen) {
            Screen.Game(game.saved.toGameScreenState())
        } else {
            Screen.Home(resumableGame = game.saved)
        }
        is GameLoad.Invalid -> Screen.Setup(SetupState.prefilled(load.lastSetup), isRestoreFailedShown = true)
    }
    return AppState(screen = screen, lastSetup = load.lastSetup)
}
