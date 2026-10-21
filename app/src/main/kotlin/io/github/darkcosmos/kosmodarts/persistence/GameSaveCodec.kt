package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.LegFinishedException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/** Outcome of [GameSaveCodec.decode]. */
sealed interface GameDecodeResult {
    data class Valid(val saved: SavedGame) : GameDecodeResult

    data class Invalid(val reason: InvalidSaveReason) : GameDecodeResult
}

/**
 * Encodes the saved game file in format version 2 and decodes versions 1 and 2 (docs/adr/0001-save-format.md,
 * docs/adr/0005-save-format-v2-match.md; rules R9, R11, R12, R13, R16).
 *
 * Decoding never throws: any content is either a [GameDecodeResult.Valid] game, which re-encodes to the same bytes
 * when it was read from version 2, or a [GameDecodeResult.Invalid] with the first broken check.
 *
 * @param gameFactory builds the current leg from its configuration, every dart and its starting player, in one
 *   replay. Injected so that tests can check that a save is replayed once, never dart by dart.
 */
class GameSaveCodec(
    private val gameFactory: (X01Config, List<Dart>, Int) -> X01Game = ::X01Game,
) {

    /**
     * The file content of [saved], or `null` if the current leg or a confirmed leg has more than [MAX_DARTS] darts
     * (rule R12): the previous file is then kept.
     */
    fun encode(saved: SavedGame): ByteArray? {
        val match = saved.match
        val tooLong = match.currentLeg.darts.size > MAX_DARTS ||
            match.completedLegs.any { leg -> leg.dartsThrown.any { it > MAX_DARTS } }
        if (tooLong) return null
        val body = SaveBody(
            config = match.config,
            format = match.format,
            legs = match.completedLegs,
            darts = match.currentLeg.darts,
            isAwaitingNextPlayer = saved.isAwaitingNextPlayer,
            isBetweenLegs = saved.isBetweenLegs,
        )
        return SaveFormat.encode(KIND, writeBody(V2_LAYOUT, body))
    }

    fun decode(bytes: ByteArray): GameDecodeResult = try {
        GameDecodeResult.Valid(decodeOrThrow(bytes))
    } catch (e: InvalidSaveException) {
        GameDecodeResult.Invalid(e.reason)
    }

    private fun decodeOrThrow(bytes: ByteArray): SavedGame {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val file = SaveFormat.decode(bytes, KIND, LAYOUTS.keys)
        val body = readBody(file.reader, LAYOUTS.getValue(file.version))
        file.reader.finish()
        val match = try {
            val currentLeg = gameFactory(body.config, body.darts, body.legs.size % body.config.players.size)
            X01Match(X01MatchConfig(body.config, body.format), body.legs, currentLeg)
        } catch (e: LegFinishedException) {
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        } catch (e: IllegalArgumentException) {
            // The confirmed legs go on after the match is decided, or decide it (docs/rules/match.md, M4).
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        }
        val saved = SavedGame(match, body.isAwaitingNextPlayer, body.isBetweenLegs)
        ensure(saved.isConsistent, InvalidSaveReason.INCONSISTENT_WAITING_FLAG)
        return saved
    }

    companion object {
        /** Largest number of darts that is saved, in the current leg and in each confirmed leg (rule R12). */
        const val MAX_DARTS: Int = MAX_SAVED_DARTS

        /**
         * Larger files are rejected before parsing (rule R9). A valid save is always under 92 KB: see ADR 0005,
         * "Size limit".
         */
        const val MAX_FILE_BYTES: Int = 131_072

        private const val KIND = "saved-game"

        /** Version 1 (ADR 0001): a single game. */
        private val V1_LAYOUT = listOf(
            SaveKey.GAME,
            SaveKey.START_SCORE,
            SaveKey.CHECKOUT,
            SaveKey.PLAYERS,
            SaveKey.PLAYER,
            SaveKey.DARTS,
            SaveKey.AWAITING,
        )

        /** Version 2 (ADR 0005): the match format, the confirmed legs and the between-legs flag. */
        private val V2_LAYOUT = listOf(
            SaveKey.GAME,
            SaveKey.START_SCORE,
            SaveKey.CHECKOUT,
            SaveKey.LEGS_PER_SET,
            SaveKey.SETS,
            SaveKey.PLAYERS,
            SaveKey.PLAYER,
            SaveKey.LEGS,
            SaveKey.LEG,
            SaveKey.DARTS,
            SaveKey.AWAITING,
            SaveKey.BETWEEN_LEGS,
        )

        private val LAYOUTS = mapOf(SaveFormat.VERSION_1 to V1_LAYOUT, SaveFormat.VERSION_2 to V2_LAYOUT)
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
