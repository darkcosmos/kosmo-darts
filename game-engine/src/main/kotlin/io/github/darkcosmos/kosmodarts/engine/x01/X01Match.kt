package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import io.github.darkcosmos.kosmodarts.engine.match.MatchRanking
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot

/**
 * Figures of a player over a whole match (docs/rules/match.md, M9).
 *
 * @property threeDartAverage points scored in every leg / darts thrown in every leg x 3 (rule A1), or `null` if no
 *   dart was thrown.
 * @property dartsThrown darts thrown in every leg, bust darts included.
 */
data class X01MatchPlayerStats(val threeDartAverage: Double?, val dartsThrown: Int)

/**
 * An X01 match (docs/rules/match.md): the confirmed legs, kept as summaries (M8), and the leg in progress. Immutable:
 * every action returns a new match. A single game is a match with the format 1 and 1 (M5).
 *
 * The leg that decides the match is never confirmed into [completedLegs]: the finished match keeps it as
 * [currentLeg], whose remaining scores break ties (M10).
 *
 * @throws IllegalArgumentException if a leg does not use the configuration of [matchConfig], if the completed legs
 *   continue after the match is decided or decide it, or if [currentLeg] does not start with the next starter (M6).
 */
class X01Match(
    val matchConfig: X01MatchConfig,
    completedLegs: List<X01LegSummary> = emptyList(),
    val currentLeg: X01Game,
) {
    /** Confirmed legs, in play order. */
    val completedLegs: List<X01LegSummary> = completedLegs.toList()

    /** Sets and legs won before the current leg (M3, M4). */
    val score: MatchScore = MatchScore(format, config.players.size, this.completedLegs.map(X01LegSummary::winnerIndex))

    init {
        require(this.completedLegs.all { it.config == config }) { "Every leg uses the match configuration" }
        require(currentLeg.config == config) { "The current leg uses the match configuration" }
        require(!score.isFinished) { "The completed legs already decide the match" }
        require(currentLeg.startingPlayerIndex == score.starterOfNextLeg) { "Wrong starter for the current leg (M6)" }
    }

    /** Configuration of every leg. */
    val config: X01Config
        get() = matchConfig.config

    /** Format of the match. */
    val format: MatchFormat
        get() = matchConfig.format

    /** Color slot of each player, in configuration order; the same in every leg (docs/rules/players.md, PC6). */
    val colorSlots: List<ColorSlot>
        get() = matchConfig.colorSlots

    /** What confirming the current leg decides once it is won (M7), or `null` while it is in progress. */
    val pendingOutcome: LegOutcome? = currentLeg.state.winnerIndex?.let(score::outcomeIfWonBy)

    /** Whether the current leg is won and decides the match (M4). */
    val isFinished: Boolean
        get() = pendingOutcome == LegOutcome.MATCH

    /** The score with the deciding leg counted, once the match is finished; `null` before. */
    val finalScore: MatchScore? by lazy {
        currentLeg.state.winnerIndex?.takeIf { isFinished }?.let(score::withLegWonBy)
    }

    /** Per player figures over the whole match, in configuration order (M9). */
    val playerStats: List<X01MatchPlayerStats> by lazy {
        currentLeg.state.players.mapIndexed { player, current ->
            val points = completedLegs.sumOf { it.pointsScored[player] } + config.startScore - current.remaining
            val darts = completedLegs.sumOf { it.dartsThrown[player] } + current.dartsThrown
            X01MatchPlayerStats(
                threeDartAverage = if (darts == 0) null else points * DARTS_PER_TURN.toDouble() / darts,
                dartsThrown = darts,
            )
        }
    }

    /**
     * Final ranking (M10): sets, then legs over the match, then remaining score in the last leg (lower is better).
     * Empty while the match is not finished.
     */
    val ranking: List<MatchRank> by lazy {
        finalScore?.let { MatchRanking.rank(it, currentLeg.state.players.map(X01PlayerState::remaining)) }.orEmpty()
    }

    /** Records [dart] in the current leg (rules S1 to S5). @throws LegFinishedException if the leg is won. */
    fun throwDart(dart: Dart): X01Match = withCurrentLeg(currentLeg.throwDart(dart))

    /** Undoes the last dart of the current leg; never goes back into a confirmed leg (M8, U1 to U3). */
    fun undo(): X01Match = withCurrentLeg(currentLeg.undo())

    /** Whether the current leg has a dart to undo (U2, M8). */
    val canUndo: Boolean
        get() = currentLeg.canUndo

    /**
     * Confirms the won current leg and starts the next one with the next starter (M6, M8).
     *
     * @throws IllegalStateException unless the current leg is won with the outcome [LegOutcome.LEG] or
     *   [LegOutcome.SET]: a leg in progress cannot be confirmed, and the leg that decides the match stays current.
     */
    fun confirmLeg(): X01Match {
        check(pendingOutcome?.isConfirmable == true) {
            "Only a won leg that does not decide the match can be confirmed"
        }
        val legs = completedLegs + X01LegSummary.of(currentLeg)
        val next = X01Game(config, startingPlayerIndex = legs.size % config.players.size)
        return X01Match(matchConfig, legs, next)
    }

    private fun withCurrentLeg(leg: X01Game): X01Match =
        if (leg == currentLeg) this else X01Match(matchConfig, completedLegs, leg)

    override fun equals(other: Any?): Boolean = other is X01Match &&
        matchConfig == other.matchConfig &&
        completedLegs == other.completedLegs &&
        currentLeg == other.currentLeg

    override fun hashCode(): Int =
        (matchConfig.hashCode() * HASH_PRIME + completedLegs.hashCode()) * HASH_PRIME + currentLeg.hashCode()

    override fun toString(): String =
        "X01Match(matchConfig=$matchConfig, completedLegs=$completedLegs, currentLeg=$currentLeg)"

    companion object {
        private const val HASH_PRIME = 31

        /** A new match: no confirmed leg, the first leg started by the first player (M6). */
        fun start(matchConfig: X01MatchConfig): X01Match =
            X01Match(matchConfig, emptyList(), X01Game(matchConfig.config))
    }
}
