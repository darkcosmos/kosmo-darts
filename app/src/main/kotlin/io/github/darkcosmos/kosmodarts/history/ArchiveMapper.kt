package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedLeg
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedPlayer
import io.github.darkcosmos.kosmodarts.engine.board.BoardPoint
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/**
 * The one mapping between the engine's archive types and the entities (ADR 0012, "Tables"). It alone splits a dart's
 * `BoardPoint?` into `x_mm`/`y_mm` and joins them back (D33, review A11), and it alone stores the bull as segment 25
 * with multiplier 2 (the engine's bull is segment 50, single).
 */
object ArchiveMapper {
    private const val BULL_MULTIPLIER = 2

    /** The session row of [archived], with the build's [appVersion] (AR5). */
    fun session(archived: ArchivedMatch, appVersion: String): SessionEntity = SessionEntity(
        id = 0,
        type = archived.sessionType,
        startedAt = archived.startedAt,
        endedAt = archived.endedAt,
        rulesVersion = archived.rulesVersion,
        appVersion = appVersion,
    )

    /** The match row of [archived] in session [sessionId]. */
    fun matchRecord(archived: ArchivedMatch, sessionId: Long): MatchRecordEntity = MatchRecordEntity(
        sessionId = sessionId,
        matchUuid = archived.matchId,
        mode = archived.mode,
        variant = archived.variant,
        settings = SettingsText.encode(archived.settings),
        settingsVersion = SettingsText.VERSION,
    )

    /** The row of [player] in session [sessionId], with its profile and its color. */
    fun player(player: ArchivedPlayer, sessionId: Long, profileId: Long, colorArgb: Int): MatchPlayerEntity =
        MatchPlayerEntity(
            sessionId = sessionId,
            position = player.position,
            profileId = profileId,
            colorArgb = colorArgb,
            finalRank = player.rank,
            legsWon = player.legsWon,
            setsWon = player.setsWon,
        )

    /** The row of [leg] in session [sessionId]. */
    fun leg(leg: ArchivedLeg, sessionId: Long): LegEntity = LegEntity(
        id = 0,
        sessionId = sessionId,
        ordinal = leg.ordinal,
        setNumber = leg.setNumber,
        legNumber = leg.legNumberInSet,
        starterPosition = leg.starterPosition,
        winnerPosition = leg.winnerPosition,
    )

    /** The row of [dart] in leg [legId]; its position goes to both coordinates or to neither (D33). */
    fun dart(dart: ArchivedDart, legId: Long): DartEntity {
        val isBull = dart.segment == Dart.BULL_SEGMENT
        return DartEntity(
            id = 0,
            legId = legId,
            playerPosition = dart.playerPosition,
            visit = dart.visit,
            rank = dart.rank,
            segment = if (isBull) Dart.OUTER_BULL_SEGMENT else dart.segment,
            multiplier = if (isBull) BULL_MULTIPLIER else dart.multiplier.factor,
            bust = dart.isBust,
            voided = dart.isVoided,
            source = dart.source,
            xMm = dart.point?.xMm,
            yMm = dart.point?.yMm,
            enteredAt = dart.enteredAt,
        )
    }

    /**
     * The archived dart of [row]: a position only when both coordinates are set, never (0, 0) for an unknown one.
     *
     * @throws IllegalStateException when the stored segment and multiplier are no dart (a database fault, mapped to
     *   [DbResult.Failed] by [dbResult]).
     */
    fun archivedDart(row: DartWithLegRow): ArchivedDart {
        val entity = row.dart
        val engineDart = engineDart(entity.segment, entity.multiplier)
        val x = entity.xMm
        val y = entity.yMm
        return ArchivedDart(
            legOrdinal = row.legOrdinal,
            playerPosition = entity.playerPosition,
            visit = entity.visit,
            rank = entity.rank,
            segment = engineDart.segment,
            multiplier = engineDart.multiplier,
            isBust = entity.bust,
            isVoided = entity.voided,
            source = entity.source,
            enteredAt = entity.enteredAt,
            point = if (x != null && y != null) BoardPoint(x, y) else null,
        )
    }

    private fun engineDart(segment: Int, factor: Int): Dart {
        if (segment == Dart.OUTER_BULL_SEGMENT && factor == BULL_MULTIPLIER) return Dart.BULL
        val multiplier = checkNotNull(Multiplier.entries.firstOrNull { it.factor == factor }) { "Invalid multiplier" }
        return try {
            Dart(segment, multiplier)
        } catch (invalid: IllegalArgumentException) {
            throw IllegalStateException("Invalid dart", invalid)
        }
    }
}
