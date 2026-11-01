package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot

/**
 * A player of an archived match (AR5).
 *
 * @property position index in throwing order, from 0.
 * @property rank final rank (M10); tied players share a rank.
 */
data class ArchivedPlayer(
    val position: Int,
    val name: String,
    val colorSlot: ColorSlot,
    val rank: Int,
    val legsWon: Int,
    val setsWon: Int,
)
