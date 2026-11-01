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
