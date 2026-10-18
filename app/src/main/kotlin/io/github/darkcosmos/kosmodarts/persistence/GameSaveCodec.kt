package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.InvalidX01ConfigException
import io.github.darkcosmos.kosmodarts.engine.x01.LegFinishedException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game

/** Outcome of [GameSaveCodec.decode]. */
sealed interface GameDecodeResult {
    data class Valid(val saved: SavedGame) : GameDecodeResult

    data class Invalid(val reason: InvalidSaveReason) : GameDecodeResult
}

/**
 * Encodes and decodes the saved game file, format version 1 (docs/adr/0001-save-format.md, rules R9, R11, R12).
 *
 * Decoding never throws: any content is either a [GameDecodeResult.Valid] game that re-encodes to the same bytes, or
 * a [GameDecodeResult.Invalid] with the first broken check.
 *
 * @param gameFactory builds the leg from its configuration and every dart, in one replay. Injected so that tests can
 *   check that a save is replayed once, never dart by dart.
 */
class GameSaveCodec(
    private val gameFactory: (X01Config, List<Dart>) -> X01Game = ::X01Game,
) {

    /** The file content of [saved], or `null` if it has more than [MAX_DARTS] darts (rule R12). */
    fun encode(saved: SavedGame): ByteArray? {
        val game = saved.game
        if (game.darts.size > MAX_DARTS) return null
        val body = configLines(game.config) + listOf(
            DARTS_KEY to game.darts.joinToString(" ") { DartTokens.tokenOf(it) },
            AWAITING_KEY to saved.isAwaitingNextPlayer.toString(),
        )
        return SaveFormat.encode(KIND, body)
    }

    fun decode(bytes: ByteArray): GameDecodeResult = try {
        GameDecodeResult.Valid(decodeOrThrow(bytes))
    } catch (e: InvalidSaveException) {
        GameDecodeResult.Invalid(e.reason)
    }

    private fun decodeOrThrow(bytes: ByteArray): SavedGame {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val reader = SaveFormat.decode(bytes, KIND)
        val config = readConfig(reader)
        val darts = parseDarts(reader.value(DARTS_KEY))
        val isAwaiting = parseBoolean(reader.value(AWAITING_KEY))
        reader.finish()
        val game = try {
            gameFactory(config, darts)
        } catch (e: LegFinishedException) {
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        }
        ensure(SavedGame.isConsistent(game, isAwaiting), InvalidSaveReason.INCONSISTENT_WAITING_FLAG)
        return SavedGame(game, isAwaiting)
    }

    private fun parseDarts(value: String): List<Dart> {
        if (value.isEmpty()) return emptyList()
        // Count before splitting, so that an oversized list is rejected without building it.
        ensure(value.count { it == ' ' } < MAX_DARTS, InvalidSaveReason.OUT_OF_RANGE)
        return value.split(' ').map { token ->
            ensure(token.isNotEmpty(), InvalidSaveReason.MALFORMED)
            DartTokens.dartOf(token) ?: reject(InvalidSaveReason.OUT_OF_RANGE)
        }
    }

    private fun parseBoolean(value: String): Boolean = when (value) {
        "true" -> true
        "false" -> false
        else -> reject(InvalidSaveReason.OUT_OF_RANGE)
    }

    companion object {
        /** Largest number of darts that is saved (rule R12). */
        const val MAX_DARTS: Int = 10_000

        /** Larger files are rejected before parsing (rule R9). A valid save is always under 41 KB. */
        const val MAX_FILE_BYTES: Int = 65_536

        private const val KIND = "saved-game"
        private const val DARTS_KEY = "darts"
        private const val AWAITING_KEY = "awaiting-next-player"
    }
}

private const val GAME_KEY = "game"
private const val GAME_X01 = "x01"
private const val START_SCORE_KEY = "start-score"
private const val CHECKOUT_KEY = "checkout"
private const val PLAYERS_KEY = "players"
private const val PLAYER_KEY = "player"

private val CHECKOUT_TOKENS = mapOf(CheckoutRule.DOUBLE_OUT to "double-out", CheckoutRule.SINGLE_OUT to "single-out")

/** The configuration lines shared by the saved game and the last setup. */
internal fun configLines(config: X01Config): List<Pair<String, String>> = listOf(
    GAME_KEY to GAME_X01,
    START_SCORE_KEY to config.startScore.toString(),
    CHECKOUT_KEY to CHECKOUT_TOKENS.getValue(config.checkoutRule),
    PLAYERS_KEY to config.players.size.toString(),
) + config.players.map { PLAYER_KEY to SaveFormat.encodeName(it) }

/** Reads the lines written by [configLines] and validates them with the engine rules (C1 to C5). */
internal fun readConfig(reader: BodyReader): X01Config {
    ensure(reader.value(GAME_KEY) == GAME_X01, InvalidSaveReason.OUT_OF_RANGE)
    val startScore = SaveFormat.parseInteger(reader.value(START_SCORE_KEY))
    ensure(startScore in X01Config.START_SCORES, InvalidSaveReason.OUT_OF_RANGE)
    val checkoutToken = reader.value(CHECKOUT_KEY)
    val checkoutRule = CHECKOUT_TOKENS.entries.firstOrNull { it.value == checkoutToken }?.key
        ?: reject(InvalidSaveReason.OUT_OF_RANGE)
    val playerCount = SaveFormat.parseInteger(reader.value(PLAYERS_KEY))
    ensure(playerCount in 1..X01Config.MAX_PLAYERS, InvalidSaveReason.OUT_OF_RANGE)
    val names = List(playerCount) { SaveFormat.decodeName(reader.value(PLAYER_KEY)) }
    return try {
        X01Config(names, startScore, checkoutRule)
    } catch (e: InvalidX01ConfigException) {
        reject(InvalidSaveReason.INVALID_CONFIG, e)
    }
}

/**
 * Dart tokens of the format (docs/adr/0001-save-format.md): `1`..`20` single, `D1`..`D20`, `T1`..`T20`, `25`, `50` and
 * `0`. A fixed table, so that no token can reach the [Dart] constructor with invalid values.
 */
internal object DartTokens {

    private val byToken: Map<String, Dart> = buildMap {
        put("0", Dart.MISS)
        put("25", Dart.OUTER_BULL)
        put("50", Dart.BULL)
        Dart.NUMBERS.forEach { number ->
            put("$number", Dart.single(number))
            put("D$number", Dart.double(number))
            put("T$number", Dart.triple(number))
        }
    }

    private val byDart: Map<Dart, String> = byToken.entries.associate { (token, dart) -> dart to token }

    fun dartOf(token: String): Dart? = byToken[token]

    fun tokenOf(dart: Dart): String = byDart.getValue(dart)
}
