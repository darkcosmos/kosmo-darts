package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState

/**
 * Content of the "Game in progress" summary on the home screen (rule R2). No layout decision here.
 *
 * @property players every player in throwing order, with their remaining score.
 * @property highlightedPlayer the player the game screen shows as throwing after resuming: the one whose turn waits,
 *   else the player to throw. With [isCheckoutToConfirm], the winner (owner decision, issue #30 Q4).
 */
data class ResumeSummary(
    val startScore: Int,
    val checkoutRule: CheckoutRule,
    val players: List<PlayerScore>,
    val highlightedPlayer: String,
    val isCheckoutToConfirm: Boolean,
) {
    data class PlayerScore(val name: String, val remaining: Int)

    companion object {
        /** Reads the player to throw from the resumed game screen, so that both always agree. */
        fun of(saved: SavedGame): ResumeSummary {
            val screen = saved.toGameScreenState()
            val state = screen.game.state
            // A saved game always has a highlighted player: a finished one waits in its checkout dialog.
            val highlighted = checkNotNull(screen.highlightedPlayerIndex)
            return ResumeSummary(
                startScore = screen.game.config.startScore,
                checkoutRule = screen.game.config.checkoutRule,
                players = state.players.map { PlayerScore(it.name, it.remaining) },
                highlightedPlayer = state.players[highlighted].name,
                isCheckoutToConfirm = screen.isCheckoutDialogShown,
            )
        }
    }
}
