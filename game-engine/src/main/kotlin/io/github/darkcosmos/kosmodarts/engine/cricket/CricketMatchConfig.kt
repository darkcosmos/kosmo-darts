package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.player.requireValidColors

/**
 * What the Cricket setup produces: the configuration of every leg, the match format (docs/rules/match.md, M1) and each
 * player's color (docs/rules/players.md, PC1, PC5, PC6). The default format is a single game (M5); the default
 * colors are the palette colors of the player positions.
 *
 * @property colors one color per player, aligned with `config.players`; they never change during the match (PC6).
 * @throws io.github.darkcosmos.kosmodarts.engine.player.InvalidPlayerColorsException if [colors] has the wrong size
 *   or a repeated color (PC5).
 */
data class CricketMatchConfig(
    val config: CricketConfig,
    val format: MatchFormat = MatchFormat(),
    val colors: List<PlayerColor> = PlayerColor.byPosition(config.players.size),
) {
    init {
        requireValidColors(colors, config.players.size)
    }
}
