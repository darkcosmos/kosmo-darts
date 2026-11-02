package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * A dart of a replayed leg journal (DJ3).
 *
 * @property playerIndex player who threw it.
 * @property visit visit number in the leg: 1 for the starter's first visit, counting every player's visits in order.
 * @property rank rank in the visit, 1 to 3.
 * @property isBust X01: whether its visit is a bust (for a voided dart, as of the moment it was voided); Cricket:
 *   always false.
 * @property isVoided whether an undo voided it (DJ2).
 */
data class JournalDart(
    val dart: Dart,
    val enteredAt: Long,
    val source: DartSource,
    val playerIndex: Int,
    val visit: Int,
    val rank: Int,
    val isBust: Boolean,
    val isVoided: Boolean,
)
