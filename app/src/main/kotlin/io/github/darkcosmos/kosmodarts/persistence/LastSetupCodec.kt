package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config

/**
 * Encodes and decodes the last setup file, format version 1 (docs/adr/0011-save-format-v1-journal.md, "Clean slate";
 * layout of docs/adr/0008-save-format-v4-modes.md; rules R7, R10, R11, R13, R17, R22, R23, R30). A file of any other
 * version, including a file of an earlier build, is invalid (R10) and never converted.
 *
 * Version 1: one section per mode started, each opened by its mode line (`x01` then `cricket`, at least one), then
 * the shared roster (`players`, one `player` per player, `colors`).
 */
class LastSetupCodec {

    fun encode(setups: LastSetups): ByteArray = SaveFormat.encode(KIND, buildList {
        setups.x01?.let { addAll(writeX01Section(it)) }
        setups.cricket?.let { addAll(writeCricketSection(it)) }
        add(SaveKey.PLAYERS.text to setups.roster.players.size.toString())
        setups.roster.players.forEach { add(SaveKey.PLAYER.text to SaveFormat.encodeName(it)) }
        add(SaveKey.COLORS.text to setups.roster.colorSlots.joinToString(" ") { it.number.toString() })
    }, VERSION)

    /** The last setups, or `null` for any invalid content (rules R10, R23). Never throws. */
    fun decode(bytes: ByteArray): LastSetups? = try {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val file = SaveFormat.decode(bytes, KIND, setOf(VERSION))
        val setups = readSetups(file.reader)
        file.reader.finish()
        setups
    } catch (ignored: InvalidSaveException) {
        null
    }

    private fun writeX01Section(settings: X01Settings): List<Pair<String, String>> = listOf(
        SaveMode.X01.line,
        SaveKey.GAME.text to GAME_X01,
        SaveKey.START_SCORE.text to settings.startScore.toString(),
        SaveKey.CHECKOUT.text to CHECKOUT_TOKENS.getValue(settings.checkoutRule),
    ) + formatLines(settings.format)

    private fun writeCricketSection(settings: CricketSettings): List<Pair<String, String>> = listOf(
        SaveMode.CRICKET.line,
        VARIANT_KEY to CRICKET_VARIANT_TOKENS.getValue(settings.variant),
        BULL_KEY to CRICKET_BULL_TOKENS.getValue(settings.isBullInPlay),
    ) + formatLines(settings.format)

    private fun formatLines(format: MatchFormat) = listOf(
        SaveKey.LEGS_PER_SET.text to format.legsPerSet.toString(),
        SaveKey.SETS.text to format.sets.toString(),
    )

    /**
     * R23: the first body line is a mode line (missing: MALFORMED; unknown value: OUT_OF_RANGE). The X01 section, if
     * any, comes before the Cricket one; a repeated or out-of-order section is MALFORMED.
     */
    private fun readSetups(reader: BodyReader): LastSetups {
        var mode: SaveMode? = SaveMode.read(reader)
        var x01: X01Settings? = null
        var cricket: CricketSettings? = null
        if (mode == SaveMode.X01) {
            x01 = readX01Section(reader)
            mode = if (reader.isNext(SaveMode.KEY)) SaveMode.read(reader) else null
            ensure(mode != SaveMode.X01, InvalidSaveReason.MALFORMED)
        }
        if (mode == SaveMode.CRICKET) cricket = readCricketSection(reader)
        val playerCount = parsePlayerCount(reader.value(SaveKey.PLAYERS.text))
        val names = List(playerCount) { SaveFormat.decodeName(reader.value(SaveKey.PLAYER.text)) }
        val colorSlots = parseColorSlots(reader.value(SaveKey.COLORS.text), playerCount)
        return LastSetups(rosterOf(names, colorSlots, x01, cricket), x01, cricket)
    }

    private fun readX01Section(reader: BodyReader): X01Settings {
        ensure(reader.value(SaveKey.GAME.text) == GAME_X01, InvalidSaveReason.OUT_OF_RANGE)
        val startScore = parseStartScore(reader.value(SaveKey.START_SCORE.text))
        val checkoutRule = parseCheckout(reader.value(SaveKey.CHECKOUT.text))
        return X01Settings(startScore, checkoutRule, readFormat(reader))
    }

    /** R24: the variant and 25-Bull tokens come from a closed set (OUT_OF_RANGE otherwise). */
    private fun readCricketSection(reader: BodyReader): CricketSettings {
        val variant = parseToken(reader.value(VARIANT_KEY), CRICKET_VARIANT_TOKENS)
        val isBullInPlay = parseToken(reader.value(BULL_KEY), CRICKET_BULL_TOKENS)
        return CricketSettings(variant, isBullInPlay, readFormat(reader))
    }

    private fun readFormat(reader: BodyReader): MatchFormat {
        val legsPerSet = parseFormatValue(reader.value(SaveKey.LEGS_PER_SET.text))
        return MatchFormat(legsPerSet, parseFormatValue(reader.value(SaveKey.SETS.text)))
    }

    /**
     * The roster, with the names checked by the engine configuration of every mode in the file: INVALID_CONFIG, so
     * that neither `InvalidX01ConfigException` nor `InvalidCricketConfigException` escapes (R24).
     */
    private fun rosterOf(
        names: List<String>,
        colorSlots: List<ColorSlot>,
        x01: X01Settings?,
        cricket: CricketSettings?,
    ): SharedRoster = try {
        x01?.let { X01Config(names, it.startScore, it.checkoutRule) }
        cricket?.let { CricketConfig(names, it.variant, it.isBullInPlay) }
        SharedRoster(names, colorSlots)
    } catch (e: IllegalArgumentException) {
        // Both configuration exceptions are IllegalArgumentExceptions, like the SharedRoster checks.
        reject(InvalidSaveReason.INVALID_CONFIG, e)
    }

    companion object {
        /** Larger files are rejected before parsing. A valid setup is always under 1 KB. */
        const val MAX_FILE_BYTES: Int = 4_096

        private const val KIND = "last-setup"

        /** The only version written and read; the saved game numbers its own versions (ADR 0011, R30). */
        private const val VERSION = 1
    }
}
