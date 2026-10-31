# T4: Storage: MediaStore, settings, delete all

## Goal
Write session files to `Download/KosmoCapture/<session>/` through MediaStore without storage permission, keep the
quality setting and total counter privately, and delete every session of this installation.

## Files
- Added in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/storage/`: `CaptureStorage` (interface:
  create truth file entry, write truth text (overwrite `"wt"`), photo output target, video output target, delete all
  own sessions, list own sessions), `MediaStoreCaptureStorage` (`MediaStore.Downloads`, `RELATIVE_PATH`
  `Download/KosmoCapture/<session>/`, own entries only), `CaptureSettings` (quality UHD/FHD default UHD, total count;
  small text file under `filesDir`, written atomically), `TruthWriter` (writes after every event, reports failures).
- Test fake: `FakeCaptureStorage` in the test source set.

## Depends on
T3.

## Rules
CP7, CP8, CP9, CP11. Root plan section "Data read, stored or exposed".

## Tests
- `TruthWriter` with the fake: one write per event, the last write decodes to the final session; a failing write is
  reported, not swallowed.
- Delete all: every own session removed, total reset to 0, settings quality kept.
- `CaptureSettings` round trip, default values on first launch, corrupt file falls back to defaults.
- `MediaStoreCaptureStorage` under Robolectric: entry created with the right display name and relative path, truth
  text overwritten, delete removes entries (skip only what Robolectric cannot emulate, and say so in notes).

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: platform code behind an interface, tests listed.

## Implementation notes
