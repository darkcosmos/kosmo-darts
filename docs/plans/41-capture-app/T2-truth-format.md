# T2: Truth file model and JSON codec, format doc

## Goal
Immutable truth model and a hand-written strict JSON codec, pure Kotlin, plus the documented format for phase 0b.

## Files
- Added in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/truth/`: `TruthSession`, `TruthVolley`,
  `TruthDart`, `Stamp` (utc `Instant`, monotonic nanos, optional video position ms), `Gravity`, `Resolution`,
  `TruthJson` (encode to `String`, decode to a result type with an error message), `UtcFormat` (ISO 8601, millis, `Z`).
- Added `docs/camera/capture-format.md`: field-by-field schema, units, clocks and how to align photos with videos,
  file naming, version policy, one full example.
- Tests in `capture/src/test/kotlin/.../capture/truth/`.

## Depends on
T1.

## Rules
CP3, CP4, CP5, CP6, CP10 (format parts). Schema: root plan, section "Truth file outline". Engine `Dart` for values
(segment 0 miss, 25, 50; multiplier `SINGLE` only for those, rule D2 of `docs/rules/x01.md`).

## Tests
- Round trip of a full session (3 volleys, voided dart with photo, empty volley, miss, 25, bull, null gravity).
- Golden text for a small session (stable key order, pretty printed, UTF-8, escaped strings).
- UTC format: `.000Z` kept, millis truncated not rounded, parse back equal.
- Decode rejects: unknown `format` or `version`, missing required field, wrong type, bad multiplier, triple bull,
  segment 21, trailing garbage, duplicate key.
- Large session (1000 darts) round trip.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
opus. Why: case "new stored format" (schema, strict decoder, version policy that phase 0b depends on).

## Implementation notes
