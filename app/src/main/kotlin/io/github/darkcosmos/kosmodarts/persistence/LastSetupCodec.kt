package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config

/**
 * Encodes and decodes the last setup file, format version 1 (docs/adr/0001-save-format.md, rules R7, R10, R11): the
 * player names in throwing order, the start score and the checkout rule of the last game started.
 */
class LastSetupCodec {

    fun encode(config: X01Config): ByteArray = SaveFormat.encode(KIND, configLines(config))

    /** The last setup, or `null` for any invalid content (rule R10). Never throws. */
    fun decode(bytes: ByteArray): X01Config? = try {
        ensure(bytes.size <= MAX_FILE_BYTES, InvalidSaveReason.TOO_LARGE)
        val reader = SaveFormat.decode(bytes, KIND)
        readConfig(reader).also { reader.finish() }
    } catch (ignored: InvalidSaveException) {
        null
    }

    companion object {
        /** Larger files are rejected before parsing. A valid setup is always under 1 KB. */
        const val MAX_FILE_BYTES: Int = 4_096

        private const val KIND = "last-setup"
    }
}
