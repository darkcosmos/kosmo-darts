package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import io.github.darkcosmos.kosmodarts.engine.match.MatchRanking
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * Figures of a player over a whole Cricket match (docs/rules/cricket.md, CR13; docs/rules/match.md, M9).
 *
 * @property marksPerRound marks hit in every leg / darts thrown in every leg x 3 (MPR), exact, or `null` if no dart
 *   was thrown.
 * @property dartsThrown darts thrown in every leg, misses included.
 */
data class CricketMatchPlayerStats(val marksPerRound: Double?, val dartsThrown: Int)

/**
 * A Cricket match (docs/rules/match.md, M1 to M12, M19, with the Cricket leg of M2): the confirmed legs, kept as
 * summaries (M8), and the leg in progress. Immutable: every action returns a new match. A single game is a match with
 * the format 1 and 1 (M5).
 *
 * The leg that decides the match is never confirmed into [completedLegs]: the finished match keeps it as
 * [currentLeg], whose points and board marks break ties (M10, CR16).
 *
 * @throws IllegalArgumentException if a leg does not use the configuration of [matchConfig], if the completed legs
 *   continue after the match is decided or decide it, or if [currentLeg] does not start with the next starter (M6).
 */
class CricketMatch(
    val matchConfig: CricketMatchConfig,
    completedLegs: List<CricketLegSummary> = emptyList(),
    val currentLeg: CricketGame,
) {
    /** Confirmed legs, in play order. */
    val completedLegs: List<CricketLegSummary> = completedLegs.toList()

    /** Sets and legs won before the current leg (M3, M4, M19). */
    val score: MatchScore =
        MatchScore(format, config.players.size, this.completedLegs.map(CricketLegSummary::winnerIndex))

    init {
        require(this.completedLegs.all { it.config == config }) { "Every leg uses the match configuration" }
        require(currentLeg.config == config) { "The current leg uses the match configuration" }
        require(!score.isFinished) { "The completed legs already decide the match" }
        require(currentLeg.startingPlayerIndex == score.starterOfNextLeg) { "Wrong starter for the current leg (M6)" }
    }

    /** Configuration of every leg. */
    val config: CricketConfig
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

    /** Per player figures over the whole match, completed legs and current leg, in configuration order (CR13). */
    val playerStats: List<CricketMatchPlayerStats> by lazy {
        currentLeg.state.players.mapIndexed { player, current ->
            val marks = completedLegs.sumOf { it.marksHit[player] } + current.marksHit
            val darts = completedLegs.sumOf { it.dartsThrown[player] } + current.dartsThrown
            CricketMatchPlayerStats(
                marksPerRound = if (darts == 0) null else marks * DARTS_PER_ROUND.toDouble() / darts,
                dartsThrown = darts,
            )
        }
    }

    /**
     * Final ranking (M10): sets, then legs over the match, then the Cricket tie-break of the last leg (CR16): points in
     * the CR12 direction, then board marks (more is better). Empty while the match is not finished.
     */
    val ranking: List<MatchRank> by lazy {
        finalScore?.let { MatchRanking.rank(it, lastLegTieBreak()) }.orEmpty()
    }

    /** Records [dart] in the current leg (CR3 to CR10). @throws CricketLegFinishedException if the leg is won. */
    fun throwDart(dart: Dart): CricketMatch = withCurrentLeg(currentLeg.throwDart(dart))

    /** Undoes the last dart of the current leg; never goes back into a confirmed leg (M8, CR11). */
    fun undo(): CricketMatch = withCurrentLeg(currentLeg.undo())

    /** Whether the current leg has a dart to undo (CR11, M8). */
    val canUndo: Boolean
        get() = currentLeg.canUndo

    /**
     * Confirms the won current leg and starts the next one with the next starter (M6, M8).
     *
     * @throws IllegalStateException unless the current leg is won with a confirmable outcome
     *   ([LegOutcome.isConfirmable]): a leg in progress cannot be confirmed, and the leg that decides the match stays
     *   current.
     */
    fun confirmLeg(): CricketMatch {
        check(pendingOutcome?.isConfirmable == true) {
            "Only a won leg that does not decide the match can be confirmed"
        }
        val summary = CricketLegSummary.of(currentLeg)
        val starter = score.withLegWonBy(summary.winnerIndex).starterOfNextLeg
        return CricketMatch(matchConfig, completedLegs + summary, CricketGame(config, startingPlayerIndex = starter))
    }

    /** CR16: player indices by points in the last leg in the CR12 direction, then by board marks, more first. */
    private fun lastLegTieBreak(): Comparator<Int> {
        val players = currentLeg.state.players
        val byPoints = when (config.variant) {
            CricketVariant.STANDARD -> compareByDescending<Int> { players[it].points }
            CricketVariant.CUT_THROAT -> compareBy { players[it].points }
        }
        return byPoints.thenByDescending { players[it].boardMarks }
    }

    private fun withCurrentLeg(leg: CricketGame): CricketMatch =
        if (leg == currentLeg) this else CricketMatch(matchConfig, completedLegs, leg)

    override fun equals(other: Any?): Boolean = other is CricketMatch &&
        matchConfig == other.matchConfig &&
        completedLegs == other.completedLegs &&
        currentLeg == other.currentLeg

    override fun hashCode(): Int =
        (matchConfig.hashCode() * HASH_PRIME + completedLegs.hashCode()) * HASH_PRIME + currentLeg.hashCode()

    override fun toString(): String =
        "CricketMatch(matchConfig=$matchConfig, completedLegs=$completedLegs, currentLeg=$currentLeg)"

    companion object {
        private const val HASH_PRIME = 31

        /** Darts of a full round, the unit of the MPR (CR13). */
        private const val DARTS_PER_ROUND = 3

        /** A new match: no confirmed leg, the first leg started by the first player (M6). */
        fun start(matchConfig: CricketMatchConfig): CricketMatch =
            CricketMatch(matchConfig, emptyList(), CricketGame(matchConfig.config))
    }
}
