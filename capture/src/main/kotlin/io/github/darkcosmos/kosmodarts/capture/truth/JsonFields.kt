package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonArray
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonBoolean
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNull
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNumber
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonObject
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonString

private val INTEGER = Regex("-?(0|[1-9][0-9]*)")

/**
 * Strict typed access to the members of one JSON object at [path] (for example `$.volleys[0]`): every key read must
 * exist with the expected type (null only where the caller asks for a nullable value), and [finish] rejects any key
 * that was not read. Failures throw [TruthFormatException] with the path of the offending value.
 */
internal class JsonFields(private val members: Map<String, JsonValue>, val path: String) {

    private val readKeys = mutableSetOf<String>()

    fun string(key: String): String = (value(key) as? JsonString)?.value ?: wrongType(key, "a string")

    fun boolean(key: String): Boolean = (value(key) as? JsonBoolean)?.value ?: wrongType(key, "true or false")

    fun long(key: String): Long = integerText(key)?.toLongOrNull() ?: wrongType(key, "an integer")

    fun longOrNull(key: String): Long? = if (isNull(key)) null else long(key)

    fun int(key: String): Int = integerText(key)?.toIntOrNull() ?: wrongType(key, "a 32-bit integer")

    fun float(key: String): Float =
        (value(key) as? JsonNumber)?.text?.toFloatOrNull()?.takeIf { it.isFinite() } ?: wrongType(key, "a float")

    fun <E : Enum<E>> enum(key: String, entries: List<E>): E {
        val name = string(key)
        return entries.firstOrNull { it.name == name } ?: wrongType(key, "one of ${entries.joinToString()}")
    }

    fun <E : Enum<E>> enumOrNull(key: String, entries: List<E>): E? = if (isNull(key)) null else enum(key, entries)

    fun obj(key: String): JsonFields =
        (value(key) as? JsonObject)?.let { JsonFields(it.members, "$path.$key") } ?: wrongType(key, "an object")

    fun objOrNull(key: String): JsonFields? = if (isNull(key)) null else obj(key)

    fun objects(key: String): List<JsonFields> {
        val items = (value(key) as? JsonArray)?.items ?: wrongType(key, "an array")
        return items.mapIndexed { index, item ->
            val itemPath = "$path.$key[$index]"
            (item as? JsonObject)?.let { JsonFields(it.members, itemPath) } ?: fail(itemPath, "expected an object")
        }
    }

    /** Rejects the keys that no getter read: the format has no optional or unknown field. */
    fun finish() {
        val unknown = members.keys - readKeys
        if (unknown.isNotEmpty()) fail(path, "unknown field \"${unknown.first()}\"")
    }

    private fun isNull(key: String): Boolean = value(key) == JsonNull

    private fun integerText(key: String): String? =
        (value(key) as? JsonNumber)?.text?.takeIf { INTEGER.matches(it) }

    private fun value(key: String): JsonValue {
        readKeys += key
        return members[key] ?: fail("$path.$key", "missing field")
    }

    private fun wrongType(key: String, expected: String): Nothing = fail("$path.$key", "expected $expected")

    companion object {

        /** Fields of the root object of [value]. */
        fun root(value: JsonValue): JsonFields =
            (value as? JsonObject)?.let { JsonFields(it.members, "$") } ?: fail("$", "expected an object")

        fun fail(path: String, reason: String): Nothing = throw TruthFormatException("$path: $reason")
    }
}
