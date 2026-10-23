package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/**
 * Encodes the last setup file in format version 3 and decodes versions 1 to 3 (docs/adr/0001-save-format.md,
 * docs/adr/0005-save-format-v2-match.md, docs/adr/0006-save-format-v3-colors.md; rules R7, R10, R11, R13, R16 to
 * R18): the player names in throwing order with their color slots, the start score, the checkout rule and the match
 * format of the last game started. A version 1 file has the format 1 and 1; versions 1 and 2 have slots by position.
 */
class LastSetupCodec {

    fun encode(setup: X01MatchConfig): ByteArray = SaveFormat.encode(
        KIND,
        writeBody(V3_LAYOUT, SaveBody(setup.config, setup.format, colorSlots = setup.colorSlots)),
    )

    /** The last setup, or `null` for any invalid content (rule R10). Never throws. */
    fun decode(bytes: ByteArray): X01MatchConfig? = try {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val file = SaveFormat.decode(bytes, KIND, LAYOUTS.keys)
        val body = readBody(file.reader, LAYOUTS.getValue(file.version))
        file.reader.finish()
        X01MatchConfig(body.config, body.format, body.colorSlots)
    } catch (ignored: InvalidSaveException) {
        null
    }

    companion object {
        /** Larger files are rejected before parsing. A valid setup is always under 1 KB. */
        const val MAX_FILE_BYTES: Int = 4_096

        private const val KIND = "last-setup"

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

        private val LAYOUTS = mapOf(
            SaveFormat.VERSION_1 to V1_LAYOUT,
            SaveFormat.VERSION_2 to V2_LAYOUT,
            SaveFormat.VERSION_3 to V3_LAYOUT,
        )
    }
}
