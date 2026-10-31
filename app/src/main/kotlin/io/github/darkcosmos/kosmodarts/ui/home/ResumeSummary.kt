package io.github.darkcosmos.kosmodarts.ui.home

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.cricket.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState

/** The game of the "Game in progress" card: what its format line shows (rules M18, CR20). */
sealed interface ResumeGame {
    /** An X01 game: "501 · DOUBLE OUT". */
    data class X01(val startScore: Int, val checkoutRule: CheckoutRule) : ResumeGame

    /** A Cricket game: "CRICKET · CUT-THROAT · SANS 25-BULL". */
    data class Cricket(val variant: CricketVariant, val isBullInPlay: Boolean) : ResumeGame
}

/**
 * Content of the "Game in progress" card on the home screen (rules R2, M18, CR20). No layout decision here.
 *
 * @property game the mode of the saved game, with the settings its format line shows.
 * @property format the match format, for the format line of the card (M18).
 * @property players every player in throwing order, with his remaining score and color slot.
 * @property highlightedPlayer the player the game screen shows as throwing after resuming: the one whose turn waits,
 *   else the player to throw; with a pending checkout, the winner. The card always reads "Resume · <player> to throw"
 *   (owner Q11 of issue #49).
 */
data class ResumeSummary(
    val game: ResumeGame,
    val format: MatchFormat,
    val players: List<PlayerScore>,
    val highlightedPlayer: String,
) {
    /** The start score of an X01 game, or `null` for another mode. */
    val startScore: Int?
        get() = (game as? ResumeGame.X01)?.startScore

    /** The checkout rule of an X01 game, or `null` for another mode. */
    val checkoutRule: CheckoutRule?
        get() = (game as? ResumeGame.X01)?.checkoutRule

    /**
     * @property remaining in the current leg for X01; the points scored in Cricket (CR20); `null` in a solo Cricket
     *   game, whose tile shows no score (CR20, Q6 A).
     * @property slot the player's color slot (PC9).
     * @property setsWon sets won, or `null` in a single game or with one set (M18).
     * @property legsWon legs won in the current set, or `null` in a single game (M18), as on the tiles (M14).
     */
    data class PlayerScore(
        val name: String,
        val remaining: Int?,
        val slot: ColorSlot,
        val setsWon: Int? = null,
        val legsWon: Int? = null,
    )

    companion object {
        /**
         * Reads the player to throw from the resumed game screen, so that both always agree. Between legs, that is the
         * starter of the next leg (M18).
         */
        fun of(saved: StoredGame): ResumeSummary = when (saved) {
            is SavedGame -> of(saved)
            is SavedCricketGame -> of(saved)
        }

        /** X01: reads the same screen state as the resume does. */
        fun of(saved: SavedGame): ResumeSummary {
            val screen = saved.toGameScreenState()
            val state = screen.game.state
            // A saved game always has a highlighted player: a finished leg waits for its checkout confirmation.
            val highlighted = checkNotNull(screen.highlightedPlayerIndex)
            val indicators = screen.indicators
            return ResumeSummary(
                game = ResumeGame.X01(screen.game.config.startScore, screen.game.config.checkoutRule),
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

        /** Cricket: reads the Cricket game screen the same way, so that the card and the resume agree (CR20, M18). */
        fun of(saved: SavedCricketGame): ResumeSummary {
            val screen = saved.toGameScreenState()
            val config = screen.game.config
            val highlighted = checkNotNull(screen.highlightedPlayerIndex)
            val indicators = screen.indicators
            return ResumeSummary(
                game = ResumeGame.Cricket(config.variant, config.isBullInPlay),
                format = screen.match.format,
                players = config.players.mapIndexed { index, name ->
                    val indicator = indicators?.get(index)
                    PlayerScore(
                        name = name,
                        // A solo game shows no score on its tile (CR20, Q6 A).
                        remaining = if (config.players.size == 1) null else screen.game.state.players[index].points,
                        slot = screen.colorSlots[index],
                        setsWon = indicator?.setsWon,
                        legsWon = indicator?.legsWon,
                    )
                },
                highlightedPlayer = config.players[highlighted],
            )
        }
    }
}
