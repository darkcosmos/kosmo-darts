package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.player.requireValidColorSlots

/**
 * What the Cricket setup produces: the configuration of every leg, the match format (docs/rules/match.md, M1) and each
 * player's color slot (docs/rules/players.md, PC1, PC5, PC6). The default format is a single game (M5); the default
 * slots follow the player positions.
 *
 * @property colorSlots one slot per player, aligned with `config.players`; they never change during the match (PC6).
 * @throws io.github.darkcosmos.kosmodarts.engine.player.InvalidColorSlotsException if [colorSlots] has the wrong size
 *   or a repeated slot (PC5).
 */
data class CricketMatchConfig(
    val config: CricketConfig,
    val format: MatchFormat = MatchFormat(),
    val colorSlots: List<ColorSlot> = ColorSlot.byPosition(config.players.size),
) {
    init {
        requireValidColorSlots(colorSlots, config.players.size)
    }
}
