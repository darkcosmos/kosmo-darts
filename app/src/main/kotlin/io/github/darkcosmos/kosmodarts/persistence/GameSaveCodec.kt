package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketLegFinishedException
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.LegFinishedException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/** Outcome of [GameSaveCodec.decode]. */
sealed interface GameDecodeResult {
    data class Valid(val saved: StoredGame) : GameDecodeResult

    data class Invalid(val reason: InvalidSaveReason) : GameDecodeResult
}

/**
 * Encodes the saved game file in format version 4 and decodes versions 1 to 4 (docs/adr/0001-save-format.md,
 * docs/adr/0005-save-format-v2-match.md, docs/adr/0006-save-format-v3-colors.md, docs/adr/0008-save-format-v4-modes.md;
 * rules R9, R11, R12, R13, R16 to R18, R20, R21, R23 to R25).
 *
 * A version 4 file starts its body with the mode line, then the X01 body of version 3 or the Cricket body
 * ([CricketSaveBody]). Versions 1 to 3 have no mode line and are X01 files (R25).
 *
 * Decoding never throws: any content is either a [GameDecodeResult.Valid] game, which re-encodes to the same bytes
 * when it was read from version 4, or a [GameDecodeResult.Invalid] with the first broken check.
 *
 * @param cricketGameFactory builds the current Cricket leg from its configuration, every dart and its starting player,
 *   in one replay. Injected so that tests can check that a save is replayed once, never dart by dart.
 * @param gameFactory the same for an X01 leg. Last, so that a trailing lambda still means the X01 factory.
 */
class GameSaveCodec(
    private val cricketGameFactory: (CricketConfig, List<Dart>, Int) -> CricketGame = ::CricketGame,
    private val gameFactory: (X01Config, List<Dart>, Int) -> X01Game = ::X01Game,
) {

    /**
     * The file content of [saved], or `null` if it cannot be written (rules R12, R24): the previous file is then
     * kept. That is the case when the current leg or a confirmed leg has more than [MAX_DARTS] darts, when a confirmed
     * Cricket leg has more than 99,999 points, or when the file would exceed [MAX_FILE_BYTES] (ADR 0008, "Size limit").
     */
    fun encode(saved: StoredGame): ByteArray? {
        val body = when (saved) {
            is SavedGame -> x01Lines(saved)
            is SavedCricketGame -> cricketLines(saved)
        } ?: return null
        return SaveFormat.encode(KIND, body).takeIf { it.size <= MAX_FILE_BYTES }
    }

    private fun x01Lines(saved: SavedGame): List<Pair<String, String>>? {
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
            colorSlots = match.colorSlots,
        )
        return listOf(SaveMode.X01.line) + writeBody(V3_LAYOUT, body)
    }

    private fun cricketLines(saved: SavedCricketGame): List<Pair<String, String>>? {
        val match = saved.match
        val body = CricketSaveBody(
            config = match.config,
            format = match.format,
            colorSlots = match.colorSlots,
            legs = match.completedLegs,
            darts = match.currentLeg.darts,
            isAwaitingNextPlayer = saved.isAwaitingNextPlayer,
            isBetweenLegs = saved.isBetweenLegs,
        )
        return if (isWritable(body)) listOf(SaveMode.CRICKET.line) + writeCricketBody(body) else null
    }

    fun decode(bytes: ByteArray): GameDecodeResult = try {
        GameDecodeResult.Valid(decodeOrThrow(bytes))
    } catch (e: InvalidSaveException) {
        GameDecodeResult.Invalid(e.reason)
    }

    private fun decodeOrThrow(bytes: ByteArray): StoredGame {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val file = SaveFormat.decode(bytes, KIND, LAYOUTS.keys)
        // R23, R25: only version 4 has a mode line; an older file is X01 and its layout rejects a mode line.
        val mode = if (file.version == SaveFormat.VERSION_4) SaveMode.read(file.reader) else SaveMode.X01
        val saved = when (mode) {
            SaveMode.X01 -> decodeX01(file.reader, LAYOUTS.getValue(file.version))
            SaveMode.CRICKET -> decodeCricket(file.reader)
        }
        ensure(saved.isConsistent, InvalidSaveReason.INCONSISTENT_WAITING_FLAG)
        return saved
    }

    private fun decodeCricket(reader: BodyReader): SavedCricketGame {
        val body = readCricketBody(reader)
        reader.finish()
        val match = try {
            val starter = body.legs.size % body.config.players.size
            val currentLeg = cricketGameFactory(body.config, body.darts, starter)
            CricketMatch(CricketMatchConfig(body.config, body.format, body.colorSlots), body.legs, currentLeg)
        } catch (e: CricketLegFinishedException) {
            // A dart after the winning one (CR9).
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        } catch (e: IllegalArgumentException) {
            // The confirmed legs go on after the match is decided, or decide it (docs/rules/match.md, M4; R24).
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        }
        return SavedCricketGame(match, body.isAwaitingNextPlayer, body.isBetweenLegs)
    }

    private fun decodeX01(reader: BodyReader, layout: List<SaveKey>): SavedGame {
        val body = readBody(reader, layout)
        reader.finish()
        val match = try {
            val currentLeg = gameFactory(body.config, body.darts, body.legs.size % body.config.players.size)
            X01Match(X01MatchConfig(body.config, body.format, body.colorSlots), body.legs, currentLeg)
        } catch (e: LegFinishedException) {
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        } catch (e: IllegalArgumentException) {
            // The confirmed legs go on after the match is decided, or decide it (docs/rules/match.md, M4).
            reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
        }
        return SavedGame(match, body.isAwaitingNextPlayer, body.isBetweenLegs)
    }

    companion object {
        /** Largest number of darts that is saved, in the current leg and in each confirmed leg (rule R12). */
        const val MAX_DARTS: Int = MAX_SAVED_DARTS

        /**
         * Larger files are rejected before parsing (rule R9), and never written. A valid X01 save is always under
         * 92 KB (ADR 0005, "Size limit"); a Cricket save can only reach it with legs of thousands of darts (ADR 0008,
         * "Size limit").
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

        /** Version 3 (ADR 0006): version 2 plus the color slots right after the player lines (R17). */
        private val V3_LAYOUT = listOf(
            SaveKey.GAME,
            SaveKey.START_SCORE,
            SaveKey.CHECKOUT,
            SaveKey.LEGS_PER_SET,
            SaveKey.SETS,
            SaveKey.PLAYERS,
            SaveKey.PLAYER,
            SaveKey.COLORS,
            SaveKey.LEGS,
            SaveKey.LEG,
            SaveKey.DARTS,
            SaveKey.AWAITING,
            SaveKey.BETWEEN_LEGS,
        )

        private val LAYOUTS = mapOf(
            SaveFormat.VERSION_1 to V1_LAYOUT,
            SaveFormat.VERSION_2 to V2_LAYOUT,
            SaveFormat.VERSION_3 to V3_LAYOUT,
            // Version 4 (ADR 0008): the X01 body after `mode=x01` is the version 3 layout, unchanged.
            SaveFormat.VERSION_4 to V3_LAYOUT,
        )
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
