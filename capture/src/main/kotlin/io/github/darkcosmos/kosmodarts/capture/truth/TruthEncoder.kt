package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonArray
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonBoolean
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNull
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonNumber
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonObject
import io.github.darkcosmos.kosmodarts.capture.truth.JsonValue.JsonString

/** Builds the JSON tree of a [TruthSession]; the key order here is the key order of the file. */
internal object TruthEncoder {

    fun encode(session: TruthSession): JsonValue =
        obj(
            "format" to string(TruthJson.FORMAT),
            "version" to number(TruthJson.VERSION.toLong()),
            "session" to string(session.session),
            "app" to obj("id" to string(session.app.id), "version" to string(session.app.version)),
            "device" to obj(
                "manufacturer" to string(session.device.manufacturer),
                "model" to string(session.device.model),
                "sdk" to number(session.device.sdk.toLong()),
            ),
            "clock" to string(TruthJson.CLOCK),
            "startedAt" to stamp(session.startedAt, withPosition = false),
            "endedAt" to (session.endedAt?.let { stamp(it, withPosition = false) } ?: JsonNull),
            "endReason" to (session.endReason?.let { string(it.name) } ?: JsonNull),
            "video" to obj(
                "requestedQuality" to string(session.video.requestedQuality.name),
                "width" to number(session.video.resolution.width.toLong()),
                "height" to number(session.video.resolution.height.toLong()),
            ),
            "photo" to obj(
                "width" to number(session.photo.resolution.width.toLong()),
                "height" to number(session.photo.resolution.height.toLong()),
                "timestampSource" to string(session.photo.timestampSource.name),
            ),
            "volleys" to JsonArray(session.volleys.map(::volley)),
        )

    private fun volley(volley: TruthVolley): JsonValue =
        obj(
            "index" to number(volley.index.toLong()),
            "video" to string(volley.video),
            "start" to stamp(volley.start, withPosition = false),
            "end" to (volley.end?.let { stamp(it, withPosition = true) } ?: JsonNull),
            "empty" to JsonBoolean(volley.empty),
            "darts" to JsonArray(volley.darts.map(::dart)),
        )

    private fun dart(dart: TruthDart): JsonValue =
        obj(
            "number" to number(dart.number.toLong()),
            "segment" to number(dart.dart.segment.toLong()),
            "multiplier" to string(dart.dart.multiplier.name),
            "voided" to JsonBoolean(dart.voided),
            "entry" to stamp(dart.entry, withPosition = true),
            "voidedAt" to (dart.voidedAt?.let { stamp(it, withPosition = true) } ?: JsonNull),
            "photo" to (dart.photo?.let(::photo) ?: JsonNull),
            "gravity" to (dart.gravity?.let(::gravity) ?: JsonNull),
        )

    private fun photo(photo: TruthPhoto): JsonValue =
        JsonObject(linkedMapOf<String, JsonValue>("file" to string(photo.file)) + stampMembers(photo.taken, true))

    private fun gravity(gravity: Gravity): JsonValue =
        obj(
            "x" to JsonNumber(gravity.x.toString()),
            "y" to JsonNumber(gravity.y.toString()),
            "z" to JsonNumber(gravity.z.toString()),
        )

    private fun stamp(stamp: Stamp, withPosition: Boolean): JsonValue = JsonObject(stampMembers(stamp, withPosition))

    private fun stampMembers(stamp: Stamp, withPosition: Boolean): Map<String, JsonValue> {
        val members = linkedMapOf<String, JsonValue>(
            "utc" to string(UtcFormat.format(stamp.utc)),
            "monotonicNanos" to number(stamp.monotonicNanos),
        )
        if (withPosition) members["videoPositionMs"] = stamp.videoPositionMs?.let(::number) ?: JsonNull
        return members
    }

    private fun obj(vararg members: Pair<String, JsonValue>): JsonValue = JsonObject(linkedMapOf(*members))

    private fun string(value: String): JsonValue = JsonString(value)

    private fun number(value: Long): JsonValue = JsonNumber(value.toString())
}
