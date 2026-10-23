package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules

/** How a player must reach exactly zero (rule C2). */
enum class CheckoutRule {
    /** The last dart must be a double or the bull. */
    DOUBLE_OUT,

    /** Any dart can finish. */
    SINGLE_OUT,
}

/** A broken configuration rule (rule C5). The declaration order is the order in which errors are reported. */
enum class X01ConfigError {
    INVALID_START_SCORE,
    NO_PLAYERS,
    TOO_MANY_PLAYERS,
    BLANK_NAME,
    NAME_TOO_LONG,
    DUPLICATE_NAME,
}

/**
 * One broken configuration rule (rule C5).
 *
 * @property playerIndices positions (0-based, in configuration order) of the players whose name breaks the rule.
 *   Empty for errors that are not about a name. A [X01ConfigError.DUPLICATE_NAME] violation carries all the positions
 *   of one duplicated name; each duplicated name has its own violation.
 */
data class X01ConfigViolation(val error: X01ConfigError, val playerIndices: List<Int> = emptyList())

/**
 * Thrown when an [X01Config] is invalid (rule C5). [violations] lists every broken rule in the declaration order of
 * [X01ConfigError]; duplicated names are ordered by their first position.
 */
class InvalidX01ConfigException(val violations: List<X01ConfigViolation>) :
    IllegalArgumentException("Invalid X01 configuration: $violations") {

    /** Broken rules, each listed once, in the declaration order of [X01ConfigError]. */
    val errors: List<X01ConfigError>
        get() = violations.map(X01ConfigViolation::error).distinct()
}

/**
 * Configuration of an X01 leg (rules C1 to C5). Entry is always free (straight in, rule C3).
 *
 * @param playerNames player names in throwing order; the caller is responsible for any shuffling. Names are
 *   trimmed and normalized to Unicode NFC, then must be non-blank, at most [MAX_NAME_LENGTH] code points and unique
 *   ignoring case (not accents), whatever the default locale.
 * @param startScore 501 or 301.
 * @throws InvalidX01ConfigException listing every broken rule, with the positions of the names concerned.
 */
class X01Config(
    playerNames: List<String>,
    val startScore: Int = DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
) {
    /** Trimmed, NFC-normalized player names, in throwing order. */
    val players: List<String> = PlayerListRules.normalize(playerNames)

    init {
        val startScoreViolations = if (startScore in START_SCORES) {
            emptyList()
        } else {
            listOf(X01ConfigViolation(X01ConfigError.INVALID_START_SCORE))
        }
        val playerViolations = PlayerListRules.violations(players).map {
            X01ConfigViolation(it.error.toX01ConfigError(), it.playerIndices)
        }
        val violations = startScoreViolations + playerViolations
        if (violations.isNotEmpty()) throw InvalidX01ConfigException(violations)
    }

    override fun equals(other: Any?): Boolean = other is X01Config &&
        players == other.players &&
        startScore == other.startScore &&
        checkoutRule == other.checkoutRule

    override fun hashCode(): Int = (players.hashCode() * HASH_PRIME + startScore) * HASH_PRIME + checkoutRule.hashCode()

    override fun toString(): String =
        "X01Config(players=$players, startScore=$startScore, checkoutRule=$checkoutRule)"

    companion object {
        /** Start score used when none is given (rule C1). */
        const val DEFAULT_START_SCORE: Int = 501

        /** Allowed start scores (rule C1). */
        val START_SCORES: Set<Int> = setOf(501, 301)

        /** Maximum number of players (rule C4). */
        const val MAX_PLAYERS: Int = PlayerListRules.MAX_PLAYERS

        /** Maximum length of a player name, in Unicode code points after trimming and NFC normalization (rule C4). */
        const val MAX_NAME_LENGTH: Int = PlayerListRules.MAX_NAME_LENGTH

        private const val HASH_PRIME = 31
    }
}

/** The X01 error of a shared player list rule (rule C5): same rule, same reporting order. */
private fun PlayerListError.toX01ConfigError(): X01ConfigError = when (this) {
    PlayerListError.NO_PLAYERS -> X01ConfigError.NO_PLAYERS
    PlayerListError.TOO_MANY_PLAYERS -> X01ConfigError.TOO_MANY_PLAYERS
    PlayerListError.BLANK_NAME -> X01ConfigError.BLANK_NAME
    PlayerListError.NAME_TOO_LONG -> X01ConfigError.NAME_TOO_LONG
    PlayerListError.DUPLICATE_NAME -> X01ConfigError.DUPLICATE_NAME
}
