package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/**
 * Builds a [TruthSession] from its JSON tree, strictly: `format` and `version` are checked before anything else,
 * every field must be present with its type, unknown fields are rejected, the derived `voided` and `empty` flags must
 * match the data, and every value goes through the model constructors (the engine [Dart] for segment and
 * multiplier, rule D2). Every failure throws [TruthFormatException] with the path of the value.
 */
internal object TruthDecoder {

    fun decode(document: JsonValue): TruthSession {
        val root = JsonFields.root(document)
        val format = root.string("format")
        if (format != TruthJson.FORMAT) JsonFields.fail("$.format", "unknown format \"$format\"")
        val version = root.long("version")
        if (version != TruthJson.VERSION.toLong()) JsonFields.fail("$.version", "unsupported version $version")
        val clock = root.string("clock")
        if (clock != TruthJson.CLOCK) JsonFields.fail("$.clock", "unknown clock \"$clock\"")
        val session = build(root.path) {
            TruthSession(
                session = root.string("session"),
                app = app(root.obj("app")),
                device = device(root.obj("device")),
                startedAt = stamp(root.obj("startedAt"), withPosition = false),
                endedAt = root.objOrNull("endedAt")?.let { stamp(it, withPosition = false) },
                endReason = root.enumOrNull("endReason", EndReason.entries),
                video = video(root.obj("video")),
                photo = photoSettings(root.obj("photo")),
                volleys = root.objects("volleys").map(::volley),
            )
        }
        root.finish()
        return session
    }

    private fun app(fields: JsonFields): AppInfo =
        AppInfo(id = fields.string("id"), version = fields.string("version")).also { fields.finish() }

    private fun device(fields: JsonFields): DeviceInfo =
        DeviceInfo(
            manufacturer = fields.string("manufacturer"),
            model = fields.string("model"),
            sdk = fields.int("sdk"),
        ).also { fields.finish() }

    private fun video(fields: JsonFields): VideoSettings =
        build(fields.path) {
            VideoSettings(
                requestedQuality = fields.enum("requestedQuality", VideoQuality.entries),
                resolution = Resolution(width = fields.int("width"), height = fields.int("height")),
            )
        }.also { fields.finish() }

    private fun photoSettings(fields: JsonFields): PhotoSettings =
        build(fields.path) {
            PhotoSettings(
                resolution = Resolution(width = fields.int("width"), height = fields.int("height")),
                timestampSource = fields.enum("timestampSource", TimestampSource.entries),
            )
        }.also { fields.finish() }

    private fun volley(fields: JsonFields): TruthVolley {
        val volley = build(fields.path) {
            TruthVolley(
                index = fields.int("index"),
                video = fields.string("video"),
                start = stamp(fields.obj("start"), withPosition = false),
                end = fields.objOrNull("end")?.let { stamp(it, withPosition = true) },
                darts = fields.objects("darts").map(::dart),
            )
        }
        if (fields.boolean("empty") != volley.empty) JsonFields.fail("${fields.path}.empty", "does not match darts")
        fields.finish()
        return volley
    }

    private fun dart(fields: JsonFields): TruthDart {
        val dart = build(fields.path) {
            TruthDart(
                number = fields.int("number"),
                dart = Dart(fields.int("segment"), fields.enum("multiplier", Multiplier.entries)),
                entry = stamp(fields.obj("entry"), withPosition = true),
                voidedAt = fields.objOrNull("voidedAt")?.let { stamp(it, withPosition = true) },
                photo = fields.objOrNull("photo")?.let(::photo),
                gravity = fields.objOrNull("gravity")?.let(::gravity),
            )
        }
        if (fields.boolean("voided") != dart.voided) JsonFields.fail("${fields.path}.voided", "does not match voidedAt")
        fields.finish()
        return dart
    }

    private fun photo(fields: JsonFields): TruthPhoto =
        build(fields.path) { TruthPhoto(file = fields.string("file"), taken = stampValue(fields, true)) }
            .also { fields.finish() }

    private fun gravity(fields: JsonFields): Gravity =
        Gravity(x = fields.float("x"), y = fields.float("y"), z = fields.float("z")).also { fields.finish() }

    private fun stamp(fields: JsonFields, withPosition: Boolean): Stamp =
        build(fields.path) { stampValue(fields, withPosition) }.also { fields.finish() }

    /** Reads the stamp members of [fields]; the caller calls [JsonFields.finish] once it read its own members. */
    private fun stampValue(fields: JsonFields, withPosition: Boolean): Stamp {
        val utcText = fields.string("utc")
        val utc = UtcFormat.parse(utcText) ?: JsonFields.fail("${fields.path}.utc", "not a UTC time \"$utcText\"")
        return Stamp(
            utc = utc,
            monotonicNanos = fields.long("monotonicNanos"),
            videoPositionMs = if (withPosition) fields.longOrNull("videoPositionMs") else null,
        )
    }

    /** Runs [block], turning a model constructor's rejection into a [TruthFormatException] at [path]. */
    private fun <T> build(path: String, block: () -> T): T =
        try {
            block()
        } catch (error: IllegalArgumentException) {
            JsonFields.fail(path, error.message ?: "invalid value")
        }
}
