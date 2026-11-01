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
 * Encodes and decodes the saved game file, format version 1 and no other (docs/adr/0011-save-format-v1-journal.md;
 * rules R9, R11, R12, R13, R20, R21, R23, R24, R26 to R30).
 *
 * The body starts with the mode line, then the X01 body or the Cricket body ([CricketSaveBody]), then the match id, the
 * start time and the dart journal of every leg ([readJournal]). No earlier version is read: such a file is rejected by
 * the checks of R9 like any other (R30).
 *
 * Decoding never throws: any content is either a [GameDecodeResult.Valid] game, which re-encodes to the same bytes, or
 * a [GameDecodeResult.Invalid] with the first broken check.
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
     * Cricket leg has more than 99,999 points, when a leg journal has more than [MAX_JOURNAL_EVENTS] events or cannot
     * be read back ([writeJournal]), or when the file would exceed [MAX_FILE_BYTES] (R28).
     */
    fun encode(saved: StoredGame): ByteArray? {
        val body = when (saved) {
            is SavedGame -> x01Lines(saved)
            is SavedCricketGame -> cricketLines(saved)
        }
        val journal = writeJournal(saved.journal, confirmedLegs = saved.confirmedLegCount)
        if (body == null || journal == null) return null
        return SaveFormat.encode(KIND, body + journal, VERSION).takeIf { it.size <= MAX_FILE_BYTES }
    }

    private val StoredGame.confirmedLegCount: Int
        get() = when (this) {
            is SavedGame -> match.completedLegs.size
            is SavedCricketGame -> match.completedLegs.size
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
        return listOf(SaveMode.X01.line) + writeBody(X01_LAYOUT, body)
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
        val file = SaveFormat.decode(bytes, KIND, setOf(VERSION))
        // R23: the mode line comes first. A file of an earlier build has none (R30): it is malformed.
        val saved = when (SaveMode.read(file.reader)) {
            SaveMode.X01 -> decodeX01(file.reader)
            SaveMode.CRICKET -> decodeCricket(file.reader)
        }
        ensure(saved.isConsistent, InvalidSaveReason.INCONSISTENT_WAITING_FLAG)
        return saved
    }

    private fun decodeCricket(reader: BodyReader): SavedCricketGame {
        val body = readCricketBody(reader)
        val journal = readJournal(reader, body.legs.size)
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
        val engine = LegEngine { darts, starter -> CricketGame(body.config, darts, starter).state.winnerIndex }
        checkJournal(journal, engine, body.config.players.size, body.legs.map { it.winnerIndex }, body.darts)
        return SavedCricketGame(match, body.isAwaitingNextPlayer, body.isBetweenLegs, journal)
    }

    private fun decodeX01(reader: BodyReader): SavedGame {
        val body = readBody(reader, X01_LAYOUT)
        val journal = readJournal(reader, body.legs.size)
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
        val engine = LegEngine { darts, starter -> X01Game(body.config, darts, starter).state.winnerIndex }
        checkJournal(journal, engine, body.config.players.size, body.legs.map { it.winnerIndex }, body.darts)
        return SavedGame(match, body.isAwaitingNextPlayer, body.isBetweenLegs, journal)
    }

    companion object {
        /** The only version read or written (ADR 0011, R30). */
        private const val VERSION = 1

        /** Largest number of darts that is saved, in the current leg and in each confirmed leg (rule R12). */
        const val MAX_DARTS: Int = MAX_SAVED_DARTS

        /**
         * Larger files are rejected before parsing (rule R9), and never written (R28). A dart event takes at most 18
         * bytes, so 1 MiB holds about 58,000 events, far above any real match (ADR 0011, "Limits").
         */
        const val MAX_FILE_BYTES: Int = 1_048_576

        private const val KIND = "saved-game"

        /** The X01 body after `mode=x01` (ADR 0008, with the color slots of ADR 0006), then the journal lines. */
        private val X01_LAYOUT = listOf(
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
