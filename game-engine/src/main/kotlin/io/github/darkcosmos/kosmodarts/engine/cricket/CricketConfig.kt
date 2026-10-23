package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListViolation

/**
 * Thrown when a [CricketConfig] is invalid (docs/rules/cricket.md, CR1; X01 rule C5). [violations] lists every broken
 * player list rule, in the order of [PlayerListRules.violations].
 */
class InvalidCricketConfigException(val violations: List<PlayerListViolation>) :
    IllegalArgumentException("Invalid Cricket configuration: $violations")

/**
 * Configuration of a Cricket leg (docs/rules/cricket.md, CR1).
 *
 * @param playerNames player names in throwing order; the caller is responsible for any shuffling. Names follow the
 *   shared [PlayerListRules] (X01 rule C4): trimmed, NFC-normalized, non-blank, at most
 *   [PlayerListRules.MAX_NAME_LENGTH] code points and unique ignoring case.
 * @param variant Standard (default) or Cut-throat.
 * @param isBullInPlay whether the Bull is a target ("25-Bull", on by default).
 * @throws InvalidCricketConfigException listing every broken rule, with the positions of the names concerned.
 */
class CricketConfig(
    playerNames: List<String>,
    val variant: CricketVariant = CricketVariant.STANDARD,
    val isBullInPlay: Boolean = true,
) {
    /** Trimmed, NFC-normalized player names, in throwing order. */
    val players: List<String> = PlayerListRules.normalize(playerNames)

    /** The targets in play, in board order: 20 down to 15, then the Bull when [isBullInPlay] (CR1). */
    val targets: List<CricketTarget> = if (isBullInPlay) CricketTarget.entries.toList() else CricketTarget.NUMBERS

    init {
        val violations = PlayerListRules.violations(players)
        if (violations.isNotEmpty()) throw InvalidCricketConfigException(violations)
    }

    override fun equals(other: Any?): Boolean = other is CricketConfig &&
        players == other.players &&
        variant == other.variant &&
        isBullInPlay == other.isBullInPlay

    override fun hashCode(): Int =
        (players.hashCode() * HASH_PRIME + variant.hashCode()) * HASH_PRIME + isBullInPlay.hashCode()

    override fun toString(): String =
        "CricketConfig(players=$players, variant=$variant, isBullInPlay=$isBullInPlay)"

    private companion object {
        const val HASH_PRIME = 31
    }
}
