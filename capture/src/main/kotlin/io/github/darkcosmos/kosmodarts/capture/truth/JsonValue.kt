package io.github.darkcosmos.kosmodarts.capture.truth

/** Minimal JSON tree used by [TruthJson]; objects keep their key order. */
internal sealed interface JsonValue {

    data class JsonObject(val members: Map<String, JsonValue>) : JsonValue

    data class JsonArray(val items: List<JsonValue>) : JsonValue

    data class JsonString(val value: String) : JsonValue

    /** A number kept as its JSON text, so that integers and floats are read without loss. */
    data class JsonNumber(val text: String) : JsonValue

    data class JsonBoolean(val value: Boolean) : JsonValue

    data object JsonNull : JsonValue
}

/** A truth text that is not valid JSON or not a valid truth file; [message] says where and why. */
internal class TruthFormatException(override val message: String) : RuntimeException(message)
