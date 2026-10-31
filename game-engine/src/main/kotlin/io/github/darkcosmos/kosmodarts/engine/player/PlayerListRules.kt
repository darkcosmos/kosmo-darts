package io.github.darkcosmos.kosmodarts.engine.player

import java.text.Normalizer

/**
 * A broken player list rule (X01 rules C4 and C5, Cricket rule CR1). The declaration order is the order in which
 * errors are reported.
 */
enum class PlayerListError {
    NO_PLAYERS,
    TOO_MANY_PLAYERS,
    BLANK_NAME,
    NAME_TOO_LONG,
    DUPLICATE_NAME,
}

/**
 * One broken player list rule (X01 rule C5).
 *
 * @property playerIndices positions (0-based, in configuration order) of the players whose name breaks the rule.
 *   Empty for errors that are not about a name. A [PlayerListError.DUPLICATE_NAME] violation carries all the positions
 *   of one duplicated name; each duplicated name has its own violation.
 */
data class PlayerListViolation(val error: PlayerListError, val playerIndices: List<Int> = emptyList())

/**
 * The player list rules shared by every game mode (X01 rules C4 and C5, Cricket rule CR1): 1 to [MAX_PLAYERS]
 * players; names trimmed and normalized to Unicode NFC, non-blank, at most [MAX_NAME_LENGTH] code points and unique
 * ignoring case (not accents), whatever the default locale.
 */
object PlayerListRules {

    /** Maximum number of players (X01 rule C4, Cricket rule CR1). */
    const val MAX_PLAYERS: Int = 4

    /** Maximum length of a player name, in Unicode code points after trimming and NFC normalization (rule C4). */
    const val MAX_NAME_LENGTH: Int = 20

    /** [names] trimmed and normalized to Unicode NFC, in the same order (rule C4). */
    fun normalize(names: List<String>): List<String> =
        names.map { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }

    /**
     * Every broken rule of [names] (normalized first with [normalize]), in the declaration order of [PlayerListError];
     * duplicated names are ordered by their first position. Empty when the list is valid.
     */
    fun violations(names: List<String>): List<PlayerListViolation> {
        val players = normalize(names)
        return PlayerListError.entries.flatMap { violationsOf(it, players) }
    }

    private fun violationsOf(rule: PlayerListError, players: List<String>): List<PlayerListViolation> = when (rule) {
        PlayerListError.NO_PLAYERS -> ruleViolation(rule, isBroken = players.isEmpty())
        PlayerListError.TOO_MANY_PLAYERS -> ruleViolation(rule, isBroken = players.size > MAX_PLAYERS)
        PlayerListError.BLANK_NAME -> nameViolation(rule, players) { it.isBlank() }
        PlayerListError.NAME_TOO_LONG ->
            nameViolation(rule, players) { it.codePointCount(0, it.length) > MAX_NAME_LENGTH }
        PlayerListError.DUPLICATE_NAME -> duplicateNameViolations(players)
    }

    private fun ruleViolation(rule: PlayerListError, isBroken: Boolean): List<PlayerListViolation> =
        if (isBroken) listOf(PlayerListViolation(rule)) else emptyList()

    private fun nameViolation(
        rule: PlayerListError,
        players: List<String>,
        breaks: (String) -> Boolean,
    ): List<PlayerListViolation> {
        val indices = players.indices.filter { breaks(players[it]) }
        return if (indices.isEmpty()) emptyList() else listOf(PlayerListViolation(rule, indices))
    }

    /** One violation per duplicated name. Blank names are reported as [PlayerListError.BLANK_NAME] only. */
    private fun duplicateNameViolations(players: List<String>): List<PlayerListViolation> = players.indices
        .filterNot { players[it].isBlank() }
        .groupBy { caseInsensitiveKey(players[it]) }
        .values
        .filter { it.size > 1 }
        .map { PlayerListViolation(PlayerListError.DUPLICATE_NAME, it) }

    /**
     * Locale-independent case folding, code point by code point (also correct for letters outside the BMP). Upper
     * then lower case maps every case variant to one form, like [String.equals] with `ignoreCase = true`.
     */
    private fun caseInsensitiveKey(name: String): List<Int> =
        name.codePoints().map { Character.toLowerCase(Character.toUpperCase(it)) }.toArray().toList()
}
