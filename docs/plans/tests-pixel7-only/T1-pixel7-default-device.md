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

- `TestDevices` (app test source set) holds the 4 sizes and the `FR_` / `EN_` variants. `robolectric.properties` sets
  `qualifiers=` the exact `RobolectricDeviceQualifiers.Pixel7` value (checked with javap on Roborazzi 1.76.0).
- Turned into `"+xx"` (no size, keep the default device): `en`, `fr`, `de`, `en-rUS`, `fr-rFR`, `land` (10 files, sed
  on exactly those literals). No test added or removed: 141 classes, 1595 tests before and after.
- D12 proof: a temporary `HomeScreenTest` test read the display metrics (rounded dp): `411x914` green, expected
  `411x913` red (`but was 411x914`). Reverted file by file; not in the commits.
- D6 folded into T1 (the build must be green): `CheckoutBannerFitTest` `M15 fr-land at font scale 1_0` lost its
  `PRE_FIX["frland"]` entry and check (36 dp was measured on the old 470 x 320 default device; the Pixel 7 landscape
  gives 94.857 dp). Its other assertions are unchanged. T2 must not repeat it.
