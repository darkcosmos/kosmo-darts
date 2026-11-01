# T1: Room and KSP setup spike, Room3 fallback

## Goal
Prove that Room compiles and runs in `:app` with KSP, AGP 9 built-in Kotlin and Kotlin 2.4.21, with the schema
exported, before any schema work. Root plan "Approach" 1, decision D3, ADR 0012 "Decision".

## Files
- `gradle/libs.versions.toml`: `room`, `ksp` versions; `androidx-room-runtime`, `androidx-room-compiler` libraries;
  `ksp` and `androidx-room` plugins. No `room-ktx`, no `sqlite-bundled`.
- `build.gradle.kts` (plugins `apply false`), `app/build.gradle.kts` (KSP, Room plugin, `schemaDirectory`).
- `app/src/main/kotlin/.../history/HistoryDatabase.kt` with one placeholder entity `ProfileEntity(id, name)` and its
  DAO; `app/schemas/.../1.json` committed. T7 replaces the placeholder (version stays 1: nothing is released yet).
- Test `app/src/test/kotlin/.../history/HistoryDatabaseSpikeTest.kt`.

## Depends on
None.

## Rules
ADR 0012. D3.

## Steps
1. Re-check the latest stable Room, Room3 and KSP on Google Maven and Maven Central; note them with the source.
2. Wire Room 2.8.x. If the build or KSP fails, switch to Room3 (latest stable) at once: no workaround, no diagnosis
   beyond reading the first error. Write the error and the switch in the notes and in ADR 0012 "Status".
3. Check the merged manifest (`app/build/intermediates/merged_manifest*/`): no `INTERNET` and no other new
   permission, no new exported component; Room's `MultiInstanceInvalidationService` is `exported="false"` (ADR 0012,
   review S3). Write the list of added components and permissions in the notes.
4. Corrupt file (review A8): with the Room version kept, find the supported way to stop the framework from deleting
   a corrupt database file (custom `openHelperFactory` or callback whose `onCorruption` does nothing), and prove it
   with a Robolectric test that writes garbage bytes into an on-disk database file, opens it, gets an exception, and
   finds the file still there with the same bytes. Record the Room version, the API used and the verified behavior in
   the notes and complete ADR 0012 "Failure contract" with them, so T7 does not choose. If no supported way exists,
   stop and report to main (root plan, open question 1); do not work around it.

## Tests
- Robolectric, in-memory database: insert then read one placeholder row; no main-thread query (use
  `allowMainThreadQueries()` only in the test).
- Existing tests unchanged and green.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `git status` clean in
`app/schemas/` after a second build; merged manifest without `INTERNET`; `kosmo-detekt` exit 0.

## Model
sonnet. Why: build wiring with a written fallback rule; no design choice left.

## Implementation notes
- Versions re-checked 2026-11-01: Room 2.8.5 (Google Maven, latest 2.8.x; Room3 3.0.3 exists), KSP 2.3.13 (Maven
  Central). Room 2.8.5 + KSP 2.3.13 compile with AGP 9.4.1 built-in Kotlin and Kotlin 2.4.21 first try: no fallback to
  Room3 was needed.
- Wiring: catalog entries `room`, `ksp`, `androidx-room-runtime`, `androidx-room-compiler`, plugins `ksp` and
  `androidx-room` (declared `apply false` in the root build, applied in `app`); `room { schemaDirectory }` in
  `app/build.gradle.kts`; `ksp(libs.androidx.room.compiler)`. Schema `app/schemas/.../HistoryDatabase/1.json`
  committed; unchanged after a second build.
- Code: `history/HistoryDatabase.kt` (placeholder `ProfileEntity`, `ProfileDao`, `HistoryDatabase`,
  `KeepCorruptFileOpenHelperFactory`). T7 replaces the placeholder and keeps `KeepCorruptFileOpenHelperFactory`.
- Merged manifest (debug and release): no `INTERNET`, no new permission, no new exported component. The only addition
  is `androidx.room.MultiInstanceInvalidationService` with `exported="false"` (`directBootAware="true"`).
- Corrupt file (open question 1, answered): a supported way exists. `RoomDatabase.Builder.openHelperFactory` with a
  `SupportSQLiteOpenHelper.Factory` that wraps the configuration callback in a `SupportSQLiteOpenHelper.Callback`
  whose `onCorruption` is empty (the default implementation deletes the file). Verified by Robolectric tests: garbage
  bytes in `kosmo-history.db`, first and second access throw a `RuntimeException`, the file still exists with the same
  bytes. Control test: with Room's default factory the original bytes are gone (file replaced). Use
  `allowMainThreadQueries()` only in tests.
