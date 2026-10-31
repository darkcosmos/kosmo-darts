# T4: Storage: MediaStore, settings, delete all

## Goal
Write session files to `Download/KosmoCapture/<session>/` through MediaStore without storage permission, keep the
quality setting and total counter privately, and delete every session of this installation.

## Files
- Added in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/storage/`: `CaptureStorage` (interface:
  create truth file entry, write truth text (overwrite `"wt"`), photo output target, video output target, delete all
  own sessions, list own sessions), `MediaStoreCaptureStorage` (`MediaStore.Downloads`, `RELATIVE_PATH`
  `Download/KosmoCapture/<session>/`; list and delete query only own entries whose `RELATIVE_PATH` starts with
  `Download/KosmoCapture/`, with `QUERY_ARG_MATCH_PENDING` = `MATCH_INCLUDE`, and delete by the returned URIs only,
  never by a path built from input), `CaptureSettings` (quality UHD/FHD default UHD, total count; small text file
  under `filesDir`, written atomically; missing or corrupt file reads as defaults and total 0), `TruthWriter`
  (writes the truth text and the total after every event, reports failures).
- Test fake: `FakeCaptureStorage` in the test source set, modelling owner and pending flag per entry, so foreign
  entries can exist in the same folder.

## Depends on
T3.

## Rules
CP1 (collision check uses the own folder list), CP7, CP8, CP9, CP11. Root plan section "Data read, stored or
exposed". Test names or comments cite the CP id.

## Tests
- `TruthWriter` with the fake: one write per event, the last write decodes to the final session; for a session
  killed mid-volley the last write decodes with `endedAt`, `endReason` and the last volley `end` null; a failing
  write is reported, not swallowed.
- CP7: persisted total right after Undo, after Stop and after Interrupt.
- CP8: Delete all removes every own entry, pending ones included; a foreign file with a similar path in the same
  folder survives; total reset to 0, quality kept.
- `CaptureSettings` round trip, default values on first launch, corrupt file falls back to defaults.
- Real MediaStore ownership and pending behavior are proven only by Kosmo's manual check (root plan step 7).
- `MediaStoreCaptureStorage` under Robolectric: entry created with the right display name and relative path, truth
  text overwritten, delete removes entries (skip only what Robolectric cannot emulate, and say so in notes).

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: platform code behind an interface, tests listed.

## Implementation notes
- Package `capture.storage`. `CaptureStorage` is Android-free: `createTruthFile(folder): CaptureEntry(id, folder, name)`,
  `writeTruth(entry, text)`, `photoTarget/videoTarget(folder, file): CaptureOutputTarget(folder, displayName,
  relativePath, mimeType)`, `deleteAllOwnSessions(): Int`, `listOwnSessions(): Set<String>`. Failures are
  `CaptureStorageException` (an `IOException`). `CAPTURE_ROOT = "Download/KosmoCapture/"`.
- The camera targets are descriptions only: T8 creates the platform entries for CameraX (pending flag) from
  `MediaStoreCaptureStorage.contentValues(target)` plus `MediaStore.Downloads.EXTERNAL_CONTENT_URI`, and must put the
  video and photo in the folder returned by `CaptureNaming.sessionFolder`. Folder and file names are validated by regex
  (session folder `YYYYMMDD-HHMMSS[-n]`, lowercase file names), so no path can be injected.
- `CaptureSettings(file)` keeps quality and total in one `key=value` file (atomic move); `read()` gives `StoredSettings`
  (defaults on a missing or corrupt file), `saveQuality`, `saveTotal`. T8 passes `File(filesDir, ...)`.
- `TruthWriter(storage, settings).write(entry, truth, counters)` returns `TruthWriteResult(truthFailure, totalFailure)`;
  both writes are attempted. T5/T8 show a message when `succeeded` is false. Delete all = `deleteAllOwnSessions()` then
  `settings.saveTotal(counters.cleared().total)`; the quality is untouched.
- Tests: `TruthWriterTest`, `CaptureSettingsTest`, `DeleteAllTest` (with `FakeCaptureStorage`: owner and pending per
  entry), `MediaStoreCaptureStorageTest` under Robolectric with `FakeMediaProvider` registered as authority `media`.
  Robolectric has no MediaStore provider, so ownership and real pending behavior are only simulated by that fake; the
  real behavior stays with Kosmo's manual check.
- `kosmo-detekt` does not analyse `:capture` (see T2 notes).
