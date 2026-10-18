package io.github.darkcosmos.kosmodarts.persistence

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Base64
import java.util.zip.CRC32

/** Why a saved file was rejected (docs/adr/0001-save-format.md, "Validation order"; rule R9). */
enum class InvalidSaveReason {
    READ_FAILED,
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_VERSION,
    CHECKSUM_MISMATCH,
    OUT_OF_RANGE,
    INVALID_CONFIG,
    REJECTED_BY_ENGINE,
    INCONSISTENT_WAITING_FLAG,

    /** The loader failed in an unforeseen way: both files are deleted, so it cannot recur at every launch (R11). */
    UNEXPECTED_ERROR,
}

/**
 * Thrown by the format helpers to reject a file. The message only holds the [reason], never any file content (which
 * may contain player names); a [cause] from the JDK decoders carries at most a byte value or an offset.
 */
internal class InvalidSaveException(val reason: InvalidSaveReason, cause: Throwable? = null) :
    Exception(reason.name, cause)

/** Rejects the file for [reason]. */
internal fun reject(reason: InvalidSaveReason, cause: Throwable? = null): Nothing =
    throw InvalidSaveException(reason, cause)

/** Rejects the file for [reason] unless [condition] holds. */
internal fun ensure(condition: Boolean, reason: InvalidSaveReason) {
    if (!condition) reject(reason)
}

/**
 * Line-based text format shared by the saved game and the last setup, version 1 (docs/adr/0001-save-format.md).
 *
 * A file is: a kind line, a version line, body lines in a fixed order, a CRC-32 line covering every byte before it,
 * and an end line. Every value has a single canonical form, so that `decode(encode(x)) == x` and any other content is
 * rejected.
 */
internal object SaveFormat {

    const val VERSION: Int = 1

    private const val KIND_KEY = "kosmodarts"
    private const val VERSION_KEY = "version"
    private const val CRC_KEY = "crc32"
    private const val END_LINE = "end"
    private const val HEX_RADIX = 16
    private const val CRC_HEX_DIGITS = 8

    /** Longest accepted integer, in digits. */
    private const val MAX_INTEGER_DIGITS = 5

    private val CANONICAL_INTEGER = Regex("0|[1-9][0-9]{0,${MAX_INTEGER_DIGITS - 1}}")
    private val CRC_LINE = Regex("$CRC_KEY=[0-9a-f]{$CRC_HEX_DIGITS}")

    /** Encodes a file of the given [kind] with the [body] lines, in order. */
    fun encode(kind: String, body: List<Pair<String, String>>): ByteArray {
        val covered = buildString {
            appendLine(KIND_KEY, kind)
            appendLine(VERSION_KEY, VERSION.toString())
            body.forEach { (key, value) -> appendLine(key, value) }
        }.toByteArray(StandardCharsets.UTF_8)
        val trailer = "$CRC_KEY=${crc32Hex(covered)}\n$END_LINE\n".toByteArray(StandardCharsets.UTF_8)
        return covered + trailer
    }

    /**
     * Checks the encoding, the kind, the version, the trailer and the checksum of [bytes], then returns a reader over
     * the body lines.
     *
     * @throws InvalidSaveException if any of these checks fails.
     */
    fun decode(bytes: ByteArray, kind: String): BodyReader {
        val text = strictUtf8(bytes, InvalidSaveReason.MALFORMED)
        ensure('\r' !in text, InvalidSaveReason.MALFORMED)
        val lines = text.split('\n')
        ensure(lines.first() == "$KIND_KEY=$kind", InvalidSaveReason.MALFORMED)
        val versionLine = lines.getOrNull(1).orEmpty()
        ensure(versionLine.startsWith("$VERSION_KEY="), InvalidSaveReason.MALFORMED)
        ensure(versionLine == "$VERSION_KEY=$VERSION", InvalidSaveReason.UNSUPPORTED_VERSION)
        // ..., crc line, end line, then the empty string after the final line feed.
        val crcLine = lines.getOrNull(lines.size - TRAILER_LINES).orEmpty()
        val hasTrailer = lines.size >= HEADER_LINES + TRAILER_LINES &&
            lines.last().isEmpty() &&
            lines[lines.size - 2] == END_LINE &&
            CRC_LINE.matches(crcLine)
        ensure(hasTrailer, InvalidSaveReason.MALFORMED)
        // The trailer is ASCII: one byte per character.
        val trailerBytes = crcLine.length + 1 + END_LINE.length + 1
        val covered = bytes.copyOfRange(0, bytes.size - trailerBytes)
        ensure(crc32Hex(covered) == crcLine.substringAfter('='), InvalidSaveReason.CHECKSUM_MISMATCH)
        return BodyReader(lines.subList(HEADER_LINES, lines.size - TRAILER_LINES))
    }

    /** Canonical decimal: digits only, no sign, no leading zero, at most [MAX_INTEGER_DIGITS] digits. */
    fun parseInteger(value: String): Int {
        ensure(CANONICAL_INTEGER.matches(value), InvalidSaveReason.OUT_OF_RANGE)
        return value.toInt()
    }

    /** Standard Base64 of the UTF-8 bytes of [name]. */
    fun encodeName(name: String): String =
        Base64.getEncoder().encodeToString(name.toByteArray(StandardCharsets.UTF_8))

    /**
     * Decodes a name written by [encodeName]. The Base64 must be canonical, the bytes valid UTF-8 and the name already
     * trimmed and NFC-normalized, as `X01Config.players` stores it.
     */
    fun decodeName(value: String): String {
        ensure(value.length <= MAX_ENCODED_NAME_LENGTH, InvalidSaveReason.OUT_OF_RANGE)
        val bytes = try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            reject(InvalidSaveReason.OUT_OF_RANGE, e)
        }
        ensure(Base64.getEncoder().encodeToString(bytes) == value, InvalidSaveReason.OUT_OF_RANGE)
        val name = strictUtf8(bytes, InvalidSaveReason.OUT_OF_RANGE)
        ensure(Normalizer.normalize(name.trim(), Normalizer.Form.NFC) == name, InvalidSaveReason.OUT_OF_RANGE)
        return name
    }

    private fun strictUtf8(bytes: ByteArray, reason: InvalidSaveReason): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        reject(reason, e)
    }

    private fun crc32Hex(bytes: ByteArray): String =
        CRC32().apply { update(bytes) }.value.toString(HEX_RADIX).padStart(CRC_HEX_DIGITS, '0')

    private fun StringBuilder.appendLine(key: String, value: String) {
        append(key).append('=').append(value).append('\n')
    }

    /** Kind and version lines. */
    private const val HEADER_LINES = 2

    /** CRC line, end line and the empty string after the final line feed. */
    private const val TRAILER_LINES = 3

    /**
     * Longest Base64 text of an accepted name: 20 code points of at most 4 UTF-8 bytes each, rounded up to whole
     * Base64 groups. Longer values are rejected before decoding.
     */
    private const val MAX_ENCODED_NAME_LENGTH = 108
}

/** Reads body lines strictly in order: each [value] call consumes exactly one `key=value` line. */
internal class BodyReader(private val lines: List<String>) {

    private var next = 0

    /** Value of the next line, which must have the given [key]. */
    fun value(key: String): String {
        val line = lines.getOrNull(next) ?: reject(InvalidSaveReason.MALFORMED)
        ensure(line.startsWith("$key="), InvalidSaveReason.MALFORMED)
        next++
        return line.substring(key.length + 1)
    }

    /** Checks that every line was read. */
    fun finish() {
        ensure(next == lines.size, InvalidSaveReason.MALFORMED)
    }
}
