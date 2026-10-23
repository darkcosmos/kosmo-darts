package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Thrown when a dart is recorded after the Cricket leg has been won (docs/rules/cricket.md, CR9). */
class CricketLegFinishedException :
    IllegalStateException("The Cricket leg is finished: no further dart can be recorded")

/**
 * A Cricket leg: a configuration, the player who starts it, plus the ordered list of darts thrown (X01 rule P1, applied
 * to Cricket). Immutable: every action returns a new game. The [state] is derived by replaying [darts] from [config],
 * so the same input always yields the same state.
 *
 * @param startingPlayerIndex the player who throws first (CR7): the first player unless a match says otherwise
 *   (docs/rules/match.md, M6).
 * @throws IllegalArgumentException if [startingPlayerIndex] is not a player index.
 * @throws CricketLegFinishedException if [darts] contains a dart after the winning one.
 */
class CricketGame(val config: CricketConfig, darts: List<Dart> = emptyList(), val startingPlayerIndex: Int = 0) {

    init {
        require(startingPlayerIndex in config.players.indices) { "Starting player out of range" }
    }

    /** All recorded darts, in throw order, the winning dart included. */
    val darts: List<Dart> = darts.toList()

    /** Current marks, points, turn and result. */
    val state: CricketState = CricketReplay(config, startingPlayerIndex).replay(this.darts)

    /** Whether there is a dart to undo (CR11; X01 rule U2). */
    val canUndo: Boolean
        get() = darts.isNotEmpty()

    /**
     * Records [dart] for the current player (CR3 to CR10).
     *
     * @throws CricketLegFinishedException if the leg is already won (CR9).
     */
    fun throwDart(dart: Dart): CricketGame {
        if (state.isFinished) throw CricketLegFinishedException()
        return CricketGame(config, darts + dart, startingPlayerIndex)
    }

    /** Removes the last recorded dart, whoever threw it (CR11). Returns this game if there is none. */
    fun undo(): CricketGame = if (canUndo) CricketGame(config, darts.dropLast(1), startingPlayerIndex) else this

    /**
     * Whether [dart], thrown now, would be a closed-sector hit (CR15): it gives at least one mark on a target that is
     * already dead in this game, before the dart.
     */
    fun isClosedSectorHit(dart: Dart): Boolean =
        CricketMarks.of(dart, config).target?.let { it in state.deadTargets } ?: false

    override fun equals(other: Any?): Boolean = other is CricketGame &&
        config == other.config &&
        darts == other.darts &&
        startingPlayerIndex == other.startingPlayerIndex

    override fun hashCode(): Int =
        (config.hashCode() * HASH_PRIME + darts.hashCode()) * HASH_PRIME + startingPlayerIndex

    override fun toString(): String =
        "CricketGame(config=$config, darts=$darts, startingPlayerIndex=$startingPlayerIndex)"

    private companion object {
        const val HASH_PRIME = 31
    }
}
