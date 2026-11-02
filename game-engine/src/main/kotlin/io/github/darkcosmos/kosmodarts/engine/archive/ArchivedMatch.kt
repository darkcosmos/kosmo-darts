package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule

/** Type of an archived session (AR5). */
enum class SessionType {
    MATCH,

    /** Reserved, unused (AR5). */
    TRAINING,
}

/** Game mode of an archived match, a queryable value (AR5). */
enum class ArchivedMode {
    X01,
    CRICKET,
}

/** The complete settings of an archived match (AR5), typed by mode. */
sealed interface ArchivedSettings {
    val format: MatchFormat

    /** The mode of the match. */
    val mode: ArchivedMode

    /** The variant as a queryable value: X01 `501` or `301`; Cricket `STANDARD` or `CUT_THROAT` (AR5). */
    val variant: String

    data class X01(val startScore: Int, val checkoutRule: CheckoutRule, override val format: MatchFormat) :
        ArchivedSettings {
        override val mode: ArchivedMode get() = ArchivedMode.X01
        override val variant: String get() = startScore.toString()
    }

    data class Cricket(
        val cricketVariant: CricketVariant,
        val isBullInPlay: Boolean,
        override val format: MatchFormat,
    ) : ArchivedSettings {
        override val mode: ArchivedMode get() = ArchivedMode.CRICKET
        override val variant: String get() = cricketVariant.name
    }
}

/**
 * A confirmed match, complete and ready to store (AR5, AR6): results come from the engine, not recomputed when read.
 * No field is nullable, except the position of a dart (D33).
 *
 * @property startedAt start of the match, milliseconds since the epoch.
 * @property endedAt confirmation of the match, milliseconds since the epoch.
 * @property rulesVersion [ENGINE_RULES_VERSION] at archive time (AR10).
 * @property players in throwing order.
 * @property legs in play order, the deciding leg last.
 * @property darts every dart of every leg, voided ones included, in leg then entry order.
 */
data class ArchivedMatch(
    val matchId: String,
    val sessionType: SessionType,
    val startedAt: Long,
    val endedAt: Long,
    val rulesVersion: Int,
    val settings: ArchivedSettings,
    val players: List<ArchivedPlayer>,
    val legs: List<ArchivedLeg>,
    val darts: List<ArchivedDart>,
) {
    /** Mode of the match. */
    val mode: ArchivedMode get() = settings.mode

    /** Variant of the match, see [ArchivedSettings.variant]. */
    val variant: String get() = settings.variant
}
