package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
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

    /** End of a finished leg. */
    data class End(val game: X01Game) : Screen
}

/**
 * Whole UI state of the app.
 *
 * @property lastSetup configuration of the last game started (Start or Rematch), to prefill the next setup (rule R7).
 */
data class AppState(
    val screen: Screen = Screen.Home(),
    val lastSetup: X01Config? = null,
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

    data object PlayerRemoved : AppEvent

    data object PlayersShuffled : AppEvent

    data object StartRequested : AppEvent

    data class NumberTapped(val number: Int) : AppEvent

    /** Double or Triple key. */
    data class MultiplierTapped(val multiplier: Multiplier) : AppEvent

    data object OuterBullTapped : AppEvent

    data object BullTapped : AppEvent

    data object MissTapped : AppEvent

    /** Undo key of the game screen, or Undo in the checkout dialog. */
    data object UndoTapped : AppEvent

    /** "Next player", next to the Undo key. */
    data object NextPlayerTapped : AppEvent

    /** "Finish game" in the checkout dialog. */
    data object FinishGameTapped : AppEvent

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
