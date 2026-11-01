package io.github.darkcosmos.kosmodarts.engine.archive

/**
 * A leg of an archived match (AR5).
 *
 * @property ordinal index of the leg over the match, from 0.
 * @property setNumber number of its set, from 1 (M19).
 * @property legNumberInSet number of the leg inside its set, from 1 (M19).
 * @property starterPosition position of the player who started it (M6).
 * @property winnerPosition position of the player who won it.
 */
data class ArchivedLeg(
    val ordinal: Int,
    val setNumber: Int,
    val legNumberInSet: Int,
    val starterPosition: Int,
    val winnerPosition: Int,
)
