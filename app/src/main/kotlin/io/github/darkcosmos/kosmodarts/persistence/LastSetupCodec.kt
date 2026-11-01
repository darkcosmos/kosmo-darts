package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/**
 * Encodes the last setup file in format version 4 and decodes versions 1 to 4 (docs/adr/0001-save-format.md,
 * docs/adr/0005-save-format-v2-match.md, docs/adr/0006-save-format-v3-colors.md,
 * docs/adr/0008-save-format-v4-modes.md; rules R7, R10, R11, R13, R16 to R18, R22, R23, R25).
 *
 * Version 4: one section per mode started, each opened by its mode line (`x01` then `cricket`, at least one), then
 * the shared roster (`players`, one `player` per player, `colors`). Versions 1 to 3 hold an X01 setup only: a
 * version 1 file has the format 1 and 1, versions 1 and 2 have slots by position.
 */
class LastSetupCodec {

    fun encode(setups: LastSetups): ByteArray = SaveFormat.encode(KIND, buildList {
        setups.x01?.let { addAll(writeX01Section(it)) }
        setups.cricket?.let { addAll(writeCricketSection(it)) }
        add(SaveKey.PLAYERS.text to setups.roster.players.size.toString())
        setups.roster.players.forEach { add(SaveKey.PLAYER.text to SaveFormat.encodeName(it)) }
        add(SaveKey.COLORS.text to setups.roster.colorSlots.joinToString(" ") { it.number.toString() })
    }, VERSION_4)

    /** The last setups, or `null` for any invalid content (rules R10, R23). Never throws. */
    fun decode(bytes: ByteArray): LastSetups? = try {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val file = SaveFormat.decode(bytes, KIND, LAYOUTS.keys + VERSION_4)
        val setups = if (file.version == VERSION_4) {
            readVersion4(file.reader)
        } else {
            // R25: a version 1 to 3 file is an X01 setup; its layout starts with `game`, so a mode line is MALFORMED.
            val body = readBody(file.reader, LAYOUTS.getValue(file.version))
            LastSetups.afterX01(previous = null, X01MatchConfig(body.config, body.format, body.colorSlots))
        }
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
    private fun readVersion4(reader: BodyReader): LastSetups {
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

        // The last setup keeps its versions 1 to 4 until task T14 of issue #50 restarts it at version 1 (ADR 0011,
        // R30). The saved game no longer shares these numbers.
        private const val VERSION_1 = 1
        private const val VERSION_2 = 2
        private const val VERSION_3 = 3
        private const val VERSION_4 = 4

        /** Version 1 (ADR 0001). */
        private val V1_LAYOUT = listOf(
            SaveKey.GAME,
            SaveKey.START_SCORE,
            SaveKey.CHECKOUT,
            SaveKey.PLAYERS,
            SaveKey.PLAYER,
        )

        /** Version 2 (ADR 0005): version 1 plus the match format after the checkout rule. */
        private val V2_LAYOUT = listOf(
            SaveKey.GAME,
            SaveKey.START_SCORE,
            SaveKey.CHECKOUT,
            SaveKey.LEGS_PER_SET,
            SaveKey.SETS,
            SaveKey.PLAYERS,
            SaveKey.PLAYER,
        )

        /** Version 3 (ADR 0006): version 2 plus the color slots right after the player lines (R17). */
        private val V3_LAYOUT = V2_LAYOUT + SaveKey.COLORS

        /** The X01-only layouts of versions 1 to 3; version 4 has its own sections ([readVersion4]). */
        private val LAYOUTS = mapOf(
            VERSION_1 to V1_LAYOUT,
            VERSION_2 to V2_LAYOUT,
            VERSION_3 to V3_LAYOUT,
        )
    }
}
