package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.cricket.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.history.HistorySnapshot
import io.github.darkcosmos.kosmodarts.ui.history.HistoryState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState

/**
 * The first state of a new ViewModel, from the files found at launch (rules R1, R2, R8, R9, R10, R14).
 *
 * @param wasOnGameScreen whether Android recreates the activity after the process was killed while the game screen
 *   or the between-legs screen was shown (ADR 0002, decision 4). `false` on a fresh launch.
 * @param history the History filters and scroll position stored when the process was killed on the History screen, or
 *   `null`; History then comes back over the home screen (HS6).
 */
fun initialState(load: LoadResult, wasOnGameScreen: Boolean, history: HistorySnapshot? = null): AppState {
    val screen = when (val game = load.game) {
        GameLoad.Absent -> Screen.Home().backToHistory(history)
        // R14, R21: after a process kill the game screen comes back, else the home screen offers the saved game.
        is GameLoad.Valid -> game.saved.let {
            if (wasOnGameScreen) it.toScreen() else Screen.Home(resumableGame = it).backToHistory(history)
        }
        is GameLoad.Invalid -> Screen.Setup(SetupState.prefilled(load.lastSetup?.x01Setup), isRestoreFailedShown = true)
    }
    return AppState(screen = screen, lastSetup = load.lastSetup)
}

/** HS6: the History screen over [this] home screen when a snapshot was stored, else [this]. */
private fun Screen.Home.backToHistory(snapshot: HistorySnapshot?): Screen =
    if (snapshot == null) this else Screen.History(this, HistoryState.restored(snapshot))

/** R14: the screen the saved game was on: the between-legs screen (owner Q6 ii), else the game screen. */
fun SavedGame.toScreen(): Screen =
    if (isBetweenLegs) Screen.BetweenLegs(match, journal) else Screen.Game(toGameScreenState(), journal)

/** R14, R21: the screen the saved Cricket game was on: between legs, else the Cricket game screen. */
fun SavedCricketGame.toScreen(): Screen =
    if (isBetweenLegs) {
        Screen.CricketBetweenLegs(match, journal)
    } else {
        Screen.CricketGame(toGameScreenState(), journal)
    }

/** R14, R21: the screen the saved game of either mode was on. */
fun StoredGame.toScreen(): Screen = when (this) {
    is SavedGame -> toScreen()
    is SavedCricketGame -> toScreen()
}
