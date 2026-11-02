# T1 - Pixel 7 as the default test device

## Goal

Every Robolectric test of `:app` runs on the Pixel 7 unless it names another supported size: add the named test
devices and make the Pixel 7 the default device, so no UI test runs on Robolectric's default 320 x 470 mdpi any more.
See root plan, "Approach" (A1, A2) and "Inventory" (group S6).

## Files

- Added: `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/TestDevices.kt` (`const val` qualifier strings, see
  root plan "Files and types added or changed").
- Changed: `app/src/test/resources/robolectric.properties` (`qualifiers=` the value of
  `RobolectricDeviceQualifiers.Pixel7`, with a comment saying why).
- Changed: class or method `@Config` whose qualifiers have no leading `+` and no size (for example `"fr"` in
  `FrenchStringsTest`, `VisitTotalFrenchScreensTest`, method configs of `MatchTextTest`, `DartWordsTest`,
  `ResourcePhrasesTest`, `HistoryRowsTest`, `MatchDetailTextTest`, `PersistenceScreensTest`, `SettingsScreenTest`,
  `AnnouncementsScreenTest`): turn them into `"+fr"` / `"+en"` so they keep the default device. Find them with
  `grep -rnE 'qualifiers = "[a-z]{2}(-(port|land))?"' app/src/test`.

## Depends on

None.

## Rules

None changed. The tests moved to the Pixel 7 keep their rule identifiers.

## Tests

- No test added or removed. Record the test count of `:app:testDebugUnitTest` before and after (same number).
- `TestDevices`: literals as in root plan "Approach" A1 (KDoc naming Roborazzi 1.76.0 `Pixel7` as the source).
- Prove the default applies (D12): in one class without a size (for example `HomeScreenTest`), add a temporary
  assertion that the screen is 411 x 914 dp; run it red with a wrong expected value, then green; commit the real work
  first and revert the canary file by file. Name the check and its result in the Implementation notes.
- A test that turns red on the Pixel 7 is a finding: stop, keep it red locally, and report to main with the failure
  (never weaken, skip or retarget it to a smaller size).

## Done when

`./gradlew :app:testDebugUnitTest` and `./gradlew build` green, `kosmo-detekt` exit 0.

## Model

`sonnet`. Why: configuration and wiring with a test-by-test check of red results, no design choice.

## Implementation notes
