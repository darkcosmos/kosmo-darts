package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState

/**
 * Content of the "Game in progress" card on the home screen (rules R2, M18). No layout decision here.
 *
 * @property format the match format, for the format line of the card (M18).
 * @property players every player in throwing order, with his remaining score and color slot.
 * @property highlightedPlayer the player the game screen shows as throwing after resuming: the one whose turn waits,
 *   else the player to throw; with a pending checkout, the winner. The card always reads "Resume · <player> to throw"
 *   (owner Q11 of issue #49).
 */
data class ResumeSummary(
    val startScore: Int,
    val checkoutRule: CheckoutRule,
    val format: MatchFormat,
    val players: List<PlayerScore>,
    val highlightedPlayer: String,
) {
    /**
     * @property remaining in the current leg.
     * @property slot the player's color slot (PC9).
     * @property setsWon sets won, or `null` in a single game or with one set (M18).
     * @property legsWon legs won in the current set, or `null` in a single game (M18), as on the tiles (M14).
     */
    data class PlayerScore(
        val name: String,
        val remaining: Int,
        val slot: ColorSlot,
        val setsWon: Int? = null,
        val legsWon: Int? = null,
    )

    companion object {
        /**
         * Reads the player to throw from the resumed game screen, so that both always agree. Between legs, that is the
         * starter of the next leg (M18).
         */
        fun of(saved: SavedGame): ResumeSummary {
            val screen = saved.toGameScreenState()
            val state = screen.game.state
            // A saved game always has a highlighted player: a finished leg waits for its checkout confirmation.
            val highlighted = checkNotNull(screen.highlightedPlayerIndex)
            val indicators = screen.indicators
            return ResumeSummary(
                startScore = screen.game.config.startScore,
                checkoutRule = screen.game.config.checkoutRule,
                format = screen.match.format,
                players = state.players.mapIndexed { index, player ->
                    val indicator = indicators?.get(index)
                    PlayerScore(
                        player.name,
                        player.remaining,
                        screen.colorSlotOf(index),
                        indicator?.setsWon,
                        indicator?.legsWon,
                    )
                },
                highlightedPlayer = state.players[highlighted].name,
            )
        }
    }
}
