package io.github.darkcosmos.kosmodarts.capture.truth

/** Result of [TruthJson.decode]: the session, or a message saying where and why the text is invalid. */
sealed interface TruthDecodeResult {

    data class Success(val session: TruthSession) : TruthDecodeResult

    data class Failure(val message: String) : TruthDecodeResult
}

/**
 * Reads and writes `truth.json` (format `kosmo-capture-truth`, version [VERSION], described field by field in
 * `docs/camera/capture-format.md`). [encode] always writes the same text for the same session: fixed key order,
 * two-space indentation, no trailing newline, to be stored as UTF-8. [decode] is strict: it rejects another format
 * or version, a missing, unknown or mistyped field, an invalid dart, and any text over [MAX_TEXT_BYTES].
 */
object TruthJson {

    /** Value of the `format` field. */
    const val FORMAT: String = "kosmo-capture-truth"

    /**
     * Value of the `version` field; the only version this reader accepts. Version 2 added the `zoom` object (rule
     * CP17); version 1 files are rejected, not migrated (they were all recorded at zoom 1.0, see the format document).
     */
    const val VERSION: Int = 2

    /** Value of the `clock` field: the monotonic clock of every `monotonicNanos` (rule CP10). */
    const val CLOCK: String = "elapsedRealtimeNanos"

    /** Largest truth text accepted by [decode], in UTF-8 bytes (10 MiB). */
    const val MAX_TEXT_BYTES: Int = 10 * 1024 * 1024

    fun encode(session: TruthSession): String = JsonWriter.write(TruthEncoder.encode(session))

    fun decode(text: String): TruthDecodeResult {
        if (exceedsUtf8Bytes(text, MAX_TEXT_BYTES)) {
            return TruthDecodeResult.Failure("Truth text is larger than $MAX_TEXT_BYTES bytes")
        }
        return try {
            TruthDecodeResult.Success(TruthDecoder.decode(JsonParser.parse(text)))
        } catch (error: TruthFormatException) {
            TruthDecodeResult.Failure(error.message)
        }
    }

    /** Whether the UTF-8 encoding of [text] is longer than [limit] bytes, counted without encoding it. */
    private fun exceedsUtf8Bytes(text: String, limit: Int): Boolean {
        // Every UTF-16 unit takes at least one byte, so a longer text is over the limit without counting.
        if (text.length > limit) return true
        var bytes = 0L
        for (char in text) {
            bytes += utf8Bytes(char)
        }
        return bytes > limit
    }

    /** UTF-8 bytes of one UTF-16 unit; each half of a surrogate pair counts for 2 of the pair's 4 bytes. */
    private fun utf8Bytes(char: Char): Int =
        when {
            char.code < ONE_BYTE_LIMIT -> 1
            char.code < TWO_BYTES_LIMIT -> 2
            char.isSurrogate() -> 2
            else -> THREE_BYTES
        }

    private const val ONE_BYTE_LIMIT = 0x80
    private const val TWO_BYTES_LIMIT = 0x800
    private const val THREE_BYTES = 3
}
