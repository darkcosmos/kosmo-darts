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
    /**
     * @property remaining in the current leg.
     * @property setsWon sets won, or `null` in a single game or with one set (M18).
     * @property legsWon legs won in the current set, or `null` in a single game (M18), as on the cards (M14).
     */
    data class PlayerScore(val name: String, val remaining: Int, val setsWon: Int? = null, val legsWon: Int? = null)

    companion object {
        /**
         * Reads the player to throw from the resumed game screen, so that both always agree. Between legs, that is the
         * starter of the next leg (M18).
         */
        fun of(saved: SavedGame): ResumeSummary {
            val screen = saved.toGameScreenState()
            val state = screen.game.state
            // A saved game always has a highlighted player: a finished one waits in its checkout dialog.
            val highlighted = checkNotNull(screen.highlightedPlayerIndex)
            val indicators = screen.indicators
            return ResumeSummary(
                startScore = screen.game.config.startScore,
                checkoutRule = screen.game.config.checkoutRule,
                players = state.players.mapIndexed { index, player ->
                    val indicator = indicators?.get(index)
                    PlayerScore(player.name, player.remaining, indicator?.setsWon, indicator?.legsWon)
                },
                highlightedPlayer = state.players[highlighted].name,
                isCheckoutToConfirm = screen.isCheckoutDialogShown,
            )
        }
    }
}
