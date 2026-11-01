package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.archive.MatchArchiver
import io.github.darkcosmos.kosmodarts.engine.journal.GameProgress
import io.github.darkcosmos.kosmodarts.engine.journal.JournalReplayException
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.ui.game.ArchiveStatus
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.history.HistoryContent
import io.github.darkcosmos.kosmodarts.ui.setup.SetupResult
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import kotlin.random.Random

/**
 * Computes the next [AppState] for an [AppEvent]. Pure, except for the injected [random] used by Shuffle. Storage
 * follows from the states it returns (see [persistenceEffects]).
 */
class AppReducer(private val random: Random) {

    fun reduce(state: AppState, event: AppEvent, stamp: EventStamp): AppState = when (val screen = state.screen) {
        is Screen.Home -> onHome(state, screen, event)
        is Screen.Setup -> onSetup(state, screen, event, stamp)
        is Screen.Game -> onGame(state, screen, event, stamp.at)
        is Screen.BetweenLegs -> onBetweenLegs(state, screen, event)
        is Screen.End -> onEnd(state, screen.match, event, stamp)
        is Screen.Settings -> onSettings(state, screen, event)
        is Screen.History -> onHistory(state, screen, event)
        is Screen.CricketGame -> onCricketGame(state, screen, event, stamp.at)
        is Screen.CricketBetweenLegs -> onCricketBetweenLegs(state, screen, event)
        is Screen.CricketEnd -> onCricketEnd(state, screen, event, stamp)
    }

    /** Rules R2 and R3: "Resume game", and "New game" that asks before deleting the saved game. */
    private fun onHome(state: AppState, home: Screen.Home, event: AppEvent): AppState {
        val saved = home.resumableGame
        return when {
            home.newGameDialogMode != null -> when (event) {
                AppEvent.NewGameConfirmed -> state.toSetup(home.newGameDialogMode)
                AppEvent.NewGameDismissed, AppEvent.Back ->
                    state.copy(screen = home.copy(newGameDialogMode = null))
                else -> state
            }
            event == AppEvent.SettingsTapped -> state.copy(screen = Screen.Settings(home))
            event == AppEvent.HistoryTapped -> state.copy(screen = Screen.History(home))
            event is AppEvent.NewGame && saved == null -> state.toSetup(event.mode)
            // R3, R20: the confirmation remembers the mode of the "New game" that opened it.
            event is AppEvent.NewGame -> state.copy(screen = home.copy(newGameDialogMode = event.mode))
            // R14: a match saved between legs resumes on the between-legs screen.
            event == AppEvent.ResumeTapped && saved != null -> state.copy(screen = saved.toScreen())
            else -> state
        }
    }

    private fun onSetup(state: AppState, screen: Screen.Setup, event: AppEvent, stamp: EventStamp): AppState = when {
        // Rule R9: the message is modal; it only closes.
        screen.isRestoreFailedShown -> when (event) {
            AppEvent.RestoreFailedDismissed, AppEvent.Back ->
                state.copy(screen = screen.copy(isRestoreFailedShown = false))
            else -> state
        }
        event == AppEvent.Back -> state.copy(screen = Screen.Home())
        event == AppEvent.StartRequested -> when (val result = screen.setup.start()) {
            is SetupResult.Valid -> newMatchState(result.config, state.lastSetup, stamp)
            is SetupResult.ValidCricket -> newCricketMatchState(result.config, state.lastSetup, stamp)
            is SetupResult.Invalid -> state.copy(screen = Screen.Setup(result.setup))
        }
        else -> state.copy(screen = Screen.Setup(editSetup(screen.setup, event, random)))
    }

    private fun onGame(state: AppState, screen: Screen.Game, event: AppEvent, at: Long): AppState {
        val game = screen.game
        return when {
            // AR3: while the archive is written, only its answer counts; a second tap does nothing.
            game.archive is ArchiveStatus.Saving -> when (event) {
                AppEvent.ArchiveSucceeded -> state.copy(screen = Screen.End(game.match))
                AppEvent.ArchiveFailed ->
                    state.copy(screen = screen.copy(game = game.copy(archive = ArchiveStatus.Failed)))
                else -> state
            }
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
            event == AppEvent.FinishGameTapped && game.isCheckoutPending -> state.copy(screen = finish(screen, at))
            // M15, owner O1: system back opens "Abandon the game?" in every zone mode, a pending checkout included;
            // dismissing it leaves the checkout pending.
            else -> state.copy(screen = play(screen, event, at))
        }
    }

    /** M15, M16, M17: "Confirm match" ends the match; "Confirm leg" and "Confirm set" confirm the leg. */
    private fun finish(screen: Screen.Game, at: Long): Screen {
        val game = screen.game
        return when (game.checkoutOutcome) {
            LegOutcome.LEG, LegOutcome.SET -> {
                val confirmed = game.match.confirmLeg()
                val journal = GameProgress.follow(screen.journal, game.match, confirmed, at)
                Screen.BetweenLegs(confirmed, journal, checkout = game.checkoutSummary)
            }
            // AR1, AR3: the End screen waits for the archive; the game stays on the CHECKOUT zone meanwhile.
            LegOutcome.MATCH, null -> screen.copy(game = game.copy(archive = archiveOf(game.match, screen.journal, at)))
        }
    }

    /** AR1, AR5: [ArchiveStatus.Saving] with the match to write, or [ArchiveStatus.Failed] if it cannot be built. */
    private fun archiveOf(match: X01Match, journal: MatchJournal, at: Long): ArchiveStatus = try {
        ArchiveStatus.Saving(MatchArchiver.archive(match, journal, at))
    } catch (ignored: IllegalArgumentException) {
        // A journal out of step with the match (DJ4) is a programming error; it must not crash a finished game.
        ArchiveStatus.Failed
    } catch (ignored: JournalReplayException) {
        ArchiveStatus.Failed
    }

    /** DJ1, DJ2: the screen after a play event, with the journal that goes with its match. */
    private fun play(screen: Screen.Game, event: AppEvent, at: Long): Screen.Game {
        val next = playGame(screen.game, event)
        val journal = GameProgress.follow(screen.journal, screen.game.match, next.match, at)
        return screen.copy(game = next, journal = journal)
    }

    /** M16: "Next leg", or back that asks "Abandon the game?" (owner Q6 i, Q7). */
    private fun onBetweenLegs(state: AppState, screen: Screen.BetweenLegs, event: AppEvent): AppState = when {
        screen.isAbandonDialogShown -> when (event) {
            // Rule R6: abandoning deletes the whole match (R14).
            AppEvent.AbandonConfirmed -> state.copy(screen = Screen.Home())
            AppEvent.AbandonDismissed, AppEvent.Back ->
                state.copy(screen = screen.copy(isAbandonDialogShown = false))
            else -> state
        }
        event == AppEvent.NextLegTapped ->
            state.copy(screen = Screen.Game(GameScreenState(screen.match), screen.journal))
        event == AppEvent.Back -> state.copy(screen = screen.copy(isAbandonDialogShown = true))
        else -> state
    }

    /** AN13: back returns to the home screen it came from. */
    private fun onSettings(state: AppState, screen: Screen.Settings, event: AppEvent): AppState =
        if (event == AppEvent.Back) state.copy(screen = screen.home) else state

    /** HS1 to HS4, HS6, HS7: back returns home; the other events change the filters, the scroll or the read. */
    private fun onHistory(state: AppState, screen: Screen.History, event: AppEvent): AppState {
        val history = screen.history
        val next = when (event) {
            AppEvent.Back -> return state.copy(screen = screen.home)
            is AppEvent.HistoryModeSelected -> history.withMode(event.mode)
            is AppEvent.HistoryProfileSelected -> history.withProfile(event.profileId)
            AppEvent.HistoryShowAllTapped -> history.showAll()
            AppEvent.HistoryRetryTapped -> if (history.content == HistoryContent.Failed) history.retry() else history
            is AppEvent.HistoryScrolled -> history.scrolled(event.index, event.offset)
            is AppEvent.HistoryLoaded -> history.loaded(event.loadId, event.result)
            else -> history
        }
        return if (next == history) state else state.copy(screen = screen.copy(history = next))
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
        AppEvent.NextPlayerTapped -> game.nextPlayer()
        else -> game
    }

    private fun onEnd(state: AppState, match: X01Match, event: AppEvent, stamp: EventStamp): AppState = when (event) {
        AppEvent.Back -> state.copy(screen = Screen.Home())
        is AppEvent.NewGame -> state.toSetup(event.mode)
        // Rule R7: a rematch is a game start, so its order becomes the last setup (owner decision, issue #30 Q3).
        // M17: same format (owner Q3).
        AppEvent.Rematch -> newMatchState(rematchSetup(match), state.lastSetup, stamp)
        else -> state
    }
}
