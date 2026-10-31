package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonArray
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonBoolean
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNull
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNumber
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonObject
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonString

private const val END = '\u0000'
private const val HEX_DIGITS = 4
private const val HEX_RADIX = 16

/**
 * Strict JSON reader (RFC 8259): one value, only JSON whitespace around it, no trailing text, no duplicate key,
 * no control character inside a string, no leading zero, no `NaN`. Nesting is limited to [MAX_DEPTH] levels so that
 * a hostile file cannot exhaust the stack.
 */
internal class JsonParser private constructor(private val text: String) {

    private var position = 0

    private fun parseDocument(): JsonValue {
        skipWhitespace()
        val value = readValue(depth = 1)
        skipWhitespace()
        if (position != text.length) fail("unexpected text after the end of the document")
        return value
    }

    private fun readValue(depth: Int): JsonValue {
        if (depth > MAX_DEPTH) fail("nesting deeper than $MAX_DEPTH levels")
        return when (val char = current()) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> JsonString(readString())
            't' -> readLiteral("true", JsonBoolean(true))
            'f' -> readLiteral("false", JsonBoolean(false))
            'n' -> readLiteral("null", JsonNull)
            '-', in '0'..'9' -> readNumber()
            END -> fail("unexpected end of text")
            else -> fail("unexpected character '$char'")
        }
    }

    private fun readObject(depth: Int): JsonObject {
        position++
        val members = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (current() == '}') {
            position++
        } else {
            do {
                skipWhitespace()
                if (current() != '"') fail("expected a key")
                val key = readString()
                if (key in members) fail("duplicate key \"$key\"")
                skipWhitespace()
                expect(':')
                skipWhitespace()
                members[key] = readValue(depth + 1)
                skipWhitespace()
            } while (separatorOrClose('}'))
        }
        return JsonObject(members)
    }

    private fun readArray(depth: Int): JsonArray {
        position++
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (current() == ']') {
            position++
        } else {
            do {
                skipWhitespace()
                items += readValue(depth + 1)
                skipWhitespace()
            } while (separatorOrClose(']'))
        }
        return JsonArray(items)
    }

    /** Consumes ',' (returns true: another element follows) or [close] (returns false); fails on anything else. */
    private fun separatorOrClose(close: Char): Boolean {
        val char = next()
        if (char != ',' && char != close) fail("expected ',' or '$close'")
        return char == ','
    }

    private fun readString(): String {
        position++
        val out = StringBuilder()
        while (true) {
            val char = next()
            when {
                char == '"' -> return out.toString()
                char == '\\' -> out.append(readEscape())
                char < ' ' -> fail("control character inside a string")
                else -> out.append(char)
            }
        }
    }

    private fun readEscape(): Char =
        when (val char = next()) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readHexChar()
            else -> fail("invalid escape '\\$char'")
        }

    private fun readHexChar(): Char {
        if (position + HEX_DIGITS > text.length) fail("unexpected end of text")
        val hex = text.substring(position, position + HEX_DIGITS)
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("invalid \\u escape")
        position += HEX_DIGITS
        return hex.toInt(HEX_RADIX).toChar()
    }

    private fun readLiteral(literal: String, value: JsonValue): JsonValue {
        if (!text.startsWith(literal, position)) fail("invalid literal")
        position += literal.length
        return value
    }

    private fun readNumber(): JsonNumber {
        val start = position
        if (current() == '-') position++
        when (current()) {
            '0' -> position++
            in '1'..'9' -> skipDigits()
            else -> fail("invalid number")
        }
        if (current() == '.') {
            position++
            requireDigits()
        }
        if (current() == 'e' || current() == 'E') {
            position++
            if (current() == '+' || current() == '-') position++
            requireDigits()
        }
        return JsonNumber(text.substring(start, position))
    }

    private fun requireDigits() {
        if (current() !in '0'..'9') fail("invalid number")
        skipDigits()
    }

    private fun skipDigits() {
        while (current() in '0'..'9') position++
    }

    private fun skipWhitespace() {
        while (current() == ' ' || current() == '\t' || current() == '\n' || current() == '\r') position++
    }

    private fun expect(char: Char) {
        if (next() != char) fail("expected '$char'")
    }

    /** The character at the current position, or [END] past the end (a raw NUL is never valid there anyway). */
    private fun current(): Char = if (position < text.length) text[position] else END

    private fun next(): Char {
        if (position >= text.length) fail("unexpected end of text")
        return text[position++]
    }

    private fun fail(reason: String): Nothing = throw TruthFormatException("Invalid JSON at offset $position: $reason")

    companion object {

        /** Deepest nesting accepted; the truth format needs 5 levels. */
        const val MAX_DEPTH: Int = 32

        /** Parses [text] as one JSON document, or throws [TruthFormatException]. */
        fun parse(text: String): JsonValue = JsonParser(text).parseDocument()
    }
}
