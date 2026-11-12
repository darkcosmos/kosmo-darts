# T1: Build number in BuildConfig, rules AV1 to AV4

## Goal
The APK carries the CI build number as `BuildConfig.BUILD_NUMBER` (root plan, Approach step 1; Decisions D1, D5),
and the rules AV1 to AV4 are written down.

## Files
- `app/build.gradle.kts`: in `buildFeatures`, `buildConfig = true`; in `defaultConfig`,
  `buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")` where `buildNumber` comes from
  `providers.environmentVariable("GITHUB_RUN_NUMBER")`, kept only if it matches `^[0-9]+$`, else `dev`.
  Do not touch `versionName` or `versionCode`.
- New `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/BuildNumberTest.kt` (plain JUnit, no Robolectric needed).
- `docs/rules/history.md`: new section `## App version (issue #78)` before `## Related rules`, with AV1 to AV4 as
  summarised in the root plan, "Test plan" (AV4 refers to AR5 and D31).

## Depends on
Nothing.

## Rules
AV2 (written here), AV4; Decisions D1, D4, D5.

## Tests
- `BuildConfig.BUILD_NUMBER` matches `^[0-9]+$` or equals `dev` (AV2).
- If `System.getenv("GITHUB_RUN_NUMBER")` is all digits, `BUILD_NUMBER` equals it; otherwise it equals `dev` (AV2).
- Run once locally with `GITHUB_RUN_NUMBER=42 ./gradlew :app:testDebugUnitTest --tests '*BuildNumberTest'` and once
  without it; note both results below.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0; committed.

## Model
haiku. Why: mechanical, fully specified build configuration and rule text; no design choice.

## Implementation notes
