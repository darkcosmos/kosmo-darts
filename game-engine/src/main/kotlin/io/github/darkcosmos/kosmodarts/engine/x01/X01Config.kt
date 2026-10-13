package io.github.darkcosmos.kosmodarts.engine.x01

/** How a player must reach exactly zero (rule C2). */
enum class CheckoutRule {
    /** The last dart must be a double or the bull. */
    DOUBLE_OUT,

    /** Any dart can finish. */
    SINGLE_OUT,
}

/** Reason why an [X01Config] was rejected (rule C5). */
enum class X01ConfigError {
    INVALID_START_SCORE,
    NO_PLAYERS,
    TOO_MANY_PLAYERS,
    BLANK_NAME,
    NAME_TOO_LONG,
    DUPLICATE_NAME,
}

/** Thrown when an [X01Config] is invalid (rule C5). [error] tells which rule is broken. */
class InvalidX01ConfigException(val error: X01ConfigError) :
    IllegalArgumentException("Invalid X01 configuration: $error")

/**
 * Configuration of an X01 leg (rules C1 to C5). Entry is always free (straight in, rule C3).
 *
 * @param playerNames player names in throwing order; the caller is responsible for any shuffling. Names are
 *   trimmed, then must be non-blank, at most [MAX_NAME_LENGTH] characters and unique ignoring case.
 * @param startScore 501 or 301.
 * @throws InvalidX01ConfigException if any rule is broken.
 */
class X01Config(
    playerNames: List<String>,
    val startScore: Int = DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
) {
    /** Trimmed player names, in throwing order. */
    val players: List<String> = playerNames.map(String::trim)

    init {
        validationError()?.let { throw InvalidX01ConfigException(it) }
    }

    private fun validationError(): X01ConfigError? = when {
        startScore !in START_SCORES -> X01ConfigError.INVALID_START_SCORE
        players.isEmpty() -> X01ConfigError.NO_PLAYERS
        players.size > MAX_PLAYERS -> X01ConfigError.TOO_MANY_PLAYERS
        players.any(String::isBlank) -> X01ConfigError.BLANK_NAME
        players.any { it.codePointCount(0, it.length) > MAX_NAME_LENGTH } -> X01ConfigError.NAME_TOO_LONG
        hasDuplicateNames() -> X01ConfigError.DUPLICATE_NAME
        else -> null
    }

    private fun hasDuplicateNames(): Boolean = players.withIndex().any { (index, name) ->
        players.drop(index + 1).any { other -> other.equals(name, ignoreCase = true) }
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
        const val MAX_PLAYERS: Int = 4

        /** Maximum length of a trimmed player name, in Unicode code points (rule C4). */
        const val MAX_NAME_LENGTH: Int = 20

        private const val HASH_PRIME = 31
    }
}
