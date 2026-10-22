package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState

/** The screen on display, with its content. There is no navigation library: the screen is plain state. */
sealed interface Screen {
    /**
     * @property resumableGame the saved game shown in the summary, with "Resume game" (rule R2), or `null`.
     * @property isNewGameDialogShown whether "New game" asks to delete the saved game (rule R3).
     */
    data class Home(val resumableGame: SavedGame? = null, val isNewGameDialogShown: Boolean = false) : Screen

    /** @property isRestoreFailedShown whether the "saved game could not be resumed" message is open (rule R9). */
    data class Setup(val setup: SetupState, val isRestoreFailedShown: Boolean = false) : Screen

    /**
     * @property isNewGame whether "Start" or "Rematch" opened this screen, rather than a resume (AN4, AN16).
     * @property noVoiceNotice the open "no offline voice" message (AN16), or `null`. Neither is saved.
     */
    data class Game(
        val game: GameScreenState,
        val isNewGame: Boolean = false,
        val noVoiceNotice: NoVoiceNotice? = null,
    ) : Screen

    /** The "Settings" screen (AN13); Back returns to [home], with its saved game summary. */
    data class Settings(val home: Home) : Screen

    /**
     * Between two legs of a match, after "Finish leg" or "Finish set" (docs/rules/match.md, M16). [match] already has
     * the leg confirmed and its next leg started, with no dart.
     *
     * @property isAbandonDialogShown whether the "Abandon the game?" confirmation is open (owner Q6 i).
     * @property checkout the winning visit of the leg just confirmed, shown in the winner's zone; `null` after a resume
     *   on this screen (not saved).
     */
    data class BetweenLegs(
        val match: X01Match,
        val isAbandonDialogShown: Boolean = false,
        val checkout: CheckoutSummary? = null,
    ) : Screen

    /** End of a finished match (M17); a single game is a match of one leg. */
    data class End(val match: X01Match) : Screen {
        /** The end of a single game (M5): [game] is its only, finished, leg. */
        constructor(game: X01Game) : this(X01Match(X01MatchConfig(game.config), emptyList(), game))

        /** The last leg, the one that decided the match. */
        val game: X01Game
            get() = match.currentLeg
    }
}

/**
 * Whole UI state of the app.
 *
 * @property lastSetup configuration and match format of the last game started (Start or Rematch), to prefill the
 *   next setup (rules R7, R13).
 */
data class AppState(
    val screen: Screen = Screen.Home(),
    val lastSetup: X01MatchConfig? = null,
)

/** Everything the user can do. An event that does not apply to the current screen is ignored. */
sealed interface AppEvent {
    /** System back. */
    data object Back : AppEvent

    /** "New game" on the home and end screens. */
    data object NewGame : AppEvent

    /** "Resume game" on the home screen (rule R2). */
    data object ResumeTapped : AppEvent

    /** "Delete" in the new-game confirmation (rule R3). */
    data object NewGameConfirmed : AppEvent

    /** "Cancel" in the new-game confirmation, or a tap outside it (rule R3). */
    data object NewGameDismissed : AppEvent

    /** "OK" on the "saved game could not be resumed" message, or a tap outside it (rule R9). */
    data object RestoreFailedDismissed : AppEvent

    data class StartScoreSelected(val score: Int) : AppEvent

    data class CheckoutRuleSelected(val rule: CheckoutRule) : AppEvent

    data class NameChanged(val index: Int, val name: String) : AppEvent

    data object PlayerAdded : AppEvent

    /** The remove cross of the row at [index] (M13, PC3). */
    data class PlayerRemoved(val index: Int) : AppEvent

    data object PlayersShuffled : AppEvent

    /** − (delta -1) or + (delta 1) of "Legs per set" (M13). */
    data class LegsPerSetChanged(val delta: Int) : AppEvent

    /** − (delta -1) or + (delta 1) of "Sets" (M13). */
    data class SetsChanged(val delta: Int) : AppEvent

    data object StartRequested : AppEvent

    data class NumberTapped(val number: Int) : AppEvent

    /** Double or Triple key. */
    data class MultiplierTapped(val multiplier: Multiplier) : AppEvent

    data object OuterBullTapped : AppEvent

    data object BullTapped : AppEvent

    data object MissTapped : AppEvent

    /** "Undo" of the action bar, in every zone mode (VT13, M15). */
    data object UndoTapped : AppEvent

    /** "Next player" of the action bar. */
    data object NextPlayerTapped : AppEvent

    /** "Confirm leg", "Confirm set" or "Confirm match" of the action bar, in the CHECKOUT zone (M15). */
    data object FinishGameTapped : AppEvent

    /** "Next leg" on the between-legs screen (M16). */
    data object NextLegTapped : AppEvent

    data object AbandonConfirmed : AppEvent

    data object AbandonDismissed : AppEvent

    data object Rematch : AppEvent

    /** The gear on the home screen (AN13). */
    data object SettingsTapped : AppEvent

    /** The game screen found no usable voice (AN16). The ViewModel decides whether the message shows. */
    data class VoiceUnavailable(val canInstall: Boolean) : AppEvent

    /** "OK" or back on the "no offline voice" message (AN16). */
    data object NoVoiceNoticeDismissed : AppEvent

    /** "Install a voice" on the "no offline voice" message: it also closes it (AN16, AN17). */
    data object InstallVoiceTapped : AppEvent
}
