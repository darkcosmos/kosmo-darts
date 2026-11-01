# T2: Truth file model and JSON codec, format doc

## Goal
Immutable truth model and a hand-written strict JSON codec, pure Kotlin, plus the documented format for phase 0b.

## Files
- Added in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/truth/`: `TruthSession`, `TruthVolley`,
  `TruthDart`, `Stamp` (utc `Instant`, monotonic nanos, optional video position ms), `Gravity`, `Resolution`,
  `TruthJson` (encode to `String`, decode to a result type with an error message), `UtcFormat` (ISO 8601, millis, `Z`).
- Added `docs/camera/capture-format.md`: field-by-field schema, units, clocks and how to align photos with videos,
  file naming, version policy, null photo (CP13), killed session, one full example.
- Added `docs/adr/0009-capture-app.md`: content listed in the root plan "Files and types added or changed".
- Tests in `capture/src/test/kotlin/.../capture/truth/`.

## Depends on
T1.

## Rules
CP3, CP4, CP5, CP6, CP9, CP10, CP13 (format parts). Schema: root plan, section "Truth file outline". The decoder
builds values through the engine `Dart` constructor (segment 0 miss, 1 to 20, 25, 50; `SINGLE` only for 0, 25 and
50, rule D2 of `docs/rules/x01.md`). Test names or comments cite the CP id.

## Tests
- Round trip of a full session (3 volleys, voided dart with photo, dart with null photo, empty volley, miss, 25,
  bull, null gravity, null video positions).
- Round trip of a killed session: `endedAt` and `endReason` null, last volley `end` null.
- Golden text for a small session (stable key order, pretty printed, UTF-8, escaped strings).
- UTC format: `.000Z` kept, millis truncated not rounded, parse back equal.
- Decode rejects: unknown `format` or `version`, missing required field, wrong type, bad multiplier, triple bull,
  segment 21, trailing garbage, duplicate key, truncated text (every prefix of the golden text), text over 10 MB
  (rejected before parsing).
- Large session (1000 darts) round trip.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
opus. Why: case "new stored format" (schema, strict decoder, version policy that phase 0b depends on).

## Implementation notes
- Public API (`capture.truth`): `TruthJson.encode(session): String`, `TruthJson.decode(text): TruthDecodeResult`
  (`Success(session)` or `Failure(message)` with a JSON path such as `$.volleys[0].darts[1].voided`), constants
  `FORMAT`, `VERSION`, `CLOCK`, `MAX_TEXT_BYTES` (10 MiB of UTF-8, checked before parsing). `UtcFormat.format/parse`.
- Model: `TruthSession` (in `TruthSession.kt` with the small types `AppInfo`, `DeviceInfo`, `VideoSettings`,
  `PhotoSettings` and enums `EndReason`, `VideoQuality` (UHD/FHD), `TimestampSource` (UNKNOWN/REALTIME)),
  `TruthVolley`, `TruthDart` + `TruthPhoto` (added type: photo file plus its stamp), `Stamp`, `Gravity` (Floats),
  `Resolution`. `TruthDart.voided` and `TruthVolley.empty` are derived properties (from `voidedAt` and the darts);
  the decoder rejects a file whose flags contradict the data.
- Invariants enforced by `require` in the constructors (T3 must respect them): `Stamp.utc` whole milliseconds
  (use `Instant.truncatedTo(ChronoUnit.MILLIS)`) within years 0000 to 9999, `monotonicNanos >= 0`, position null or
  `>= 0`; `startedAt`, `endedAt` and volley `start` have a null position; `endedAt` and `endReason` both null or
  both set; at least one volley; volley indexes 1..n; dart numbers 1..N across volleys; every volley but the last
  has an `end`, and the last too once `endedAt` is set; dart number >= 1. The CP3 limit of 3 darts is not checked
  here (T3).
- Deviation from the outline: start stamps (`startedAt`, `endedAt`, volley `start`) have no `videoPositionMs` key;
  event stamps (`end`, `entry`, `voidedAt`, `photo`) always have it (null allowed). Unknown keys are rejected.
- Internal pieces: `JsonParser` (strict RFC 8259, depth limit 32), `JsonWriter` (pretty, no trailing newline),
  `JsonFields` (typed access + unknown-field check), `TruthEncoder`, `TruthDecoder`, `TruthFormatException`.
- Tests: `UtcFormatTest`, `TruthJsonRoundTripTest`, `TruthJsonGoldenTest` (its `GOLDEN` text is the example in
  `docs/camera/capture-format.md`; update both together), `TruthJsonRejectTest`, fixtures in `TruthFixtures`.
- `kosmo-detekt` does not analyse `:capture` (input `app/src:game-engine/src`, Q13); lines kept within 120 columns.
- Open for T8: the volley video start monotonic time is not stored, so events with a null position cannot be placed
  in the video; the format doc says so.
