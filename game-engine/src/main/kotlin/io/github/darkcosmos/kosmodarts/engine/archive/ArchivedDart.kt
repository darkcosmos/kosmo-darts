package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.board.BoardPoint
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/**
 * A dart of an archived match (AR5), a snapshot of the journal replay (AR10).
 *
 * @property legOrdinal [ArchivedLeg.ordinal] of its leg.
 * @property playerPosition [ArchivedPlayer.position] of the thrower.
 * @property visit visit number in the leg (DJ3), [rank] its rank in the visit.
 * @property segment 0 for a miss, 1 to 20, 25 or 50.
 * @property point board position in millimetres (BC1), or `null` when the position is unknown (D33); the type does not
 *   tie `null` to a miss, so a later source may leave a hit without position. Never read `null` as (0, 0).
 */
data class ArchivedDart(
    val legOrdinal: Int,
    val playerPosition: Int,
    val visit: Int,
    val rank: Int,
    val segment: Int,
    val multiplier: Multiplier,
    val isBust: Boolean,
    val isVoided: Boolean,
    val source: DartSource,
    val enteredAt: Long,
    val point: BoardPoint?,
)
