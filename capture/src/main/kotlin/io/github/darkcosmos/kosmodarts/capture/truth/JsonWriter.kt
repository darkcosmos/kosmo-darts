package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonArray
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonBoolean
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNull
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNumber
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonObject
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonString

private const val INDENT = "  "
private const val HEX_WIDTH = 4
private const val HEX_RADIX = 16

/**
 * Pretty JSON writer: two-space indentation, keys in insertion order, `"key": value`, no trailing newline.
 * Non-ASCII text is written as is (the file is UTF-8); `"`, `\`, control characters and lone surrogates are escaped.
 */
internal object JsonWriter {

    fun write(value: JsonValue): String = StringBuilder().also { it.appendValue(value, depth = 0) }.toString()

    private fun StringBuilder.appendValue(value: JsonValue, depth: Int) {
        when (value) {
            is JsonObject -> appendObject(value, depth)
            is JsonArray -> appendArray(value, depth)
            is JsonString -> appendString(value.value)
            is JsonNumber -> append(value.text)
            is JsonBoolean -> append(value.value)
            JsonNull -> append("null")
        }
    }

    private fun StringBuilder.appendObject(value: JsonObject, depth: Int) {
        if (value.members.isEmpty()) {
            append("{}")
            return
        }
        append('{')
        value.members.entries.forEachIndexed { index, (key, member) ->
            if (index > 0) append(',')
            newLine(depth + 1)
            appendString(key)
            append(": ")
            appendValue(member, depth + 1)
        }
        newLine(depth)
        append('}')
    }

    private fun StringBuilder.appendArray(value: JsonArray, depth: Int) {
        if (value.items.isEmpty()) {
            append("[]")
            return
        }
        append('[')
        value.items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            newLine(depth + 1)
            appendValue(item, depth + 1)
        }
        newLine(depth)
        append(']')
    }

    private fun StringBuilder.newLine(depth: Int) {
        append('\n')
        repeat(depth) { append(INDENT) }
    }

    private fun StringBuilder.appendString(text: String) {
        append('"')
        text.forEachIndexed { index, char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' || isLoneSurrogate(text, index) -> appendUnicodeEscape(char)
                else -> append(char)
            }
        }
        append('"')
    }

    private fun StringBuilder.appendUnicodeEscape(char: Char) {
        append("\\u")
        append(char.code.toString(HEX_RADIX).padStart(HEX_WIDTH, '0'))
    }

    private fun isLoneSurrogate(text: String, index: Int): Boolean {
        val char = text[index]
        return when {
            char.isHighSurrogate() -> text.getOrNull(index + 1)?.isLowSurrogate() != true
            char.isLowSurrogate() -> text.getOrNull(index - 1)?.isHighSurrogate() != true
            else -> false
        }
    }
}
