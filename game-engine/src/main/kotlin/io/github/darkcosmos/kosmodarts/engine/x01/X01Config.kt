package io.github.darkcosmos.kosmodarts.engine.x01

import java.text.Normalizer

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
 * Thrown when an [X01Config] is invalid (rule C5). [errors] lists every broken rule once, in the declaration order
 * of [X01ConfigError].
 */
class InvalidX01ConfigException(val errors: List<X01ConfigError>) :
    IllegalArgumentException("Invalid X01 configuration: $errors")

/**
 * Configuration of an X01 leg (rules C1 to C5). Entry is always free (straight in, rule C3).
 *
 * @param playerNames player names in throwing order; the caller is responsible for any shuffling. Names are
 *   trimmed and normalized to Unicode NFC, then must be non-blank, at most [MAX_NAME_LENGTH] code points and unique
 *   ignoring case (not accents), whatever the default locale.
 * @param startScore 501 or 301.
 * @throws InvalidX01ConfigException listing every broken rule.
 */
class X01Config(
    playerNames: List<String>,
    val startScore: Int = DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
) {
    /** Trimmed, NFC-normalized player names, in throwing order. */
    val players: List<String> = playerNames.map { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }

    init {
        val errors = X01ConfigError.entries.filter(::isBroken)
        if (errors.isNotEmpty()) throw InvalidX01ConfigException(errors)
    }

    private fun isBroken(rule: X01ConfigError): Boolean = when (rule) {
        X01ConfigError.INVALID_START_SCORE -> startScore !in START_SCORES
        X01ConfigError.NO_PLAYERS -> players.isEmpty()
        X01ConfigError.TOO_MANY_PLAYERS -> players.size > MAX_PLAYERS
        X01ConfigError.BLANK_NAME -> players.any(String::isBlank)
        X01ConfigError.NAME_TOO_LONG -> players.any { it.codePointCount(0, it.length) > MAX_NAME_LENGTH }
        X01ConfigError.DUPLICATE_NAME -> hasDuplicateNames()
    }

    /** Blank names are reported as [X01ConfigError.BLANK_NAME] only, so they are left out of this check. */
    private fun hasDuplicateNames(): Boolean {
        val keys = players.filterNot(String::isBlank).map(::caseInsensitiveKey)
        return keys.size != keys.toSet().size
    }

    /**
     * Locale-independent case folding, code point by code point (also correct for letters outside the BMP). Upper
     * then lower case maps every case variant to one form, like [String.equals] with `ignoreCase = true`.
     */
    private fun caseInsensitiveKey(name: String): List<Int> =
        name.codePoints().map { Character.toLowerCase(Character.toUpperCase(it)) }.toArray().toList()

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
        const val MAX_PLAYERS: Int = 4

        /** Maximum length of a player name, in Unicode code points after trimming and NFC normalization (rule C4). */
        const val MAX_NAME_LENGTH: Int = 20

        private const val HASH_PRIME = 31
    }
}
