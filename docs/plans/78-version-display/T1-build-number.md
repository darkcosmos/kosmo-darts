# T1: Build number in BuildConfig, rules AV1 to AV3

## Goal
The APK carries the CI build number as `BuildConfig.BUILD_NUMBER` (root plan, Approach step 1; Decisions D1, D5,
D7), and the rules AV1 to AV3 are written down.

## Files
- `app/build.gradle.kts`: in `buildFeatures`, `buildConfig = true`; in `defaultConfig`,
  `buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")`, where `buildNumber` is
  `providers.environmentVariable("GITHUB_RUN_NUMBER")` trimmed, kept only if `Regex("[0-9]{1,10}").matches(it)`
  (full match), else `dev`. Inline, no separate function (D7). Do not touch `versionName` or `versionCode`.
- New `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/BuildNumberTest.kt` (plain JUnit).
- `docs/rules/history.md`: new section `## App version (issue #78)` before `## Related rules`, with AV1 to AV3 as
  summarised in the root plan, "Test plan", and one sentence saying the archived version (AR5) is unchanged.
  Why this file: root plan, "Files and types added or changed".

## Depends on
Nothing.

## Rules
AV2 (written here); Decisions D1, D4, D5, D7.

## Tests
- `BuildNumberTest`: `BuildConfig.BUILD_NUMBER` fully matches `[0-9]{1,10}|dev` (AV2). No assertion against the
  environment.
- Scripted loop: root plan, "Test plan", the T1 build-script filter cases (11 inputs, unset to 11 digits). Run each
  with `./gradlew :app:generateDebugBuildConfig`, read `BUILD_NUMBER` from the generated `BuildConfig.java` under
  `app/build/generated/`, paste the input -> output table below. Every row must match the expected value.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; the loop table matches; `kosmo-detekt` exit 0;
committed.

## Model
haiku. Why: mechanical, fully specified build configuration, rule text and check loop; no design choice.

## Implementation notes

- Done as specified: `app/build.gradle.kts` (`buildConfig = true`, inline filter in `defaultConfig`, no
  `versionName`/`versionCode` change), `BuildNumberTest.kt`, and AV1 to AV3 in `docs/rules/history.md`
  ("App version (issue #78)", before "Related rules", with the AR5 sentence).
- Scripted loop (`generateDebugBuildConfig --rerun` per input, `BUILD_NUMBER` read from the generated `BuildConfig.java`);
  every row matches the expected value:

| `GITHUB_RUN_NUMBER` | `BUILD_NUMBER` | Expected |
|---|---|---|
| unset | `dev` | `dev` |
| `""` | `dev` | `dev` |
| `123` | `123` | `123` |
| `0123` | `0123` | `0123` |
| `12a` | `dev` | `dev` |
| `1 2` | `dev` | `dev` |
| `-5` | `dev` | `dev` |
| `12` + LF | `12` | `12` |
| `12` + space | `12` | `12` |
| `1234567890` | `1234567890` | `1234567890` |
| `12345678901` | `dev` | `dev` |

- Blocked check: `./gradlew build` and `./gradlew :app:testDebugUnitTest` are red in this sandbox, not because of this
  change. 79 Robolectric class setups in `:app` (and 7 in `:capture` for `build`) fail with
  `MavenArtifactFetcher` / `NoRouteToHostException`: Robolectric cannot download its Android runtime jars (outbound
  network is allowlisted). `./gradlew :app:testDebugUnitTest --tests '*HistorySchemaTest*'` fails the same way on the
  unchanged base (stash), so the failure predates this task. The failures are all class-level setup
  failures (no test case fails); `BuildNumberTest` passes. `kosmo-detekt` exit 0.
