# T2: Home version footer, strings, tests and captures

## Goal
A fixed footer at the bottom of the home screen shows "Version <versionName> (build <buildNumber>)" without hiding
any home control (root plan, Approach steps 2 to 4; Decisions D2, D3, D6; Open question O1 answered by main).

## Files
- `ui/home/HomeScreen.kt`: new parameters `versionName: String`, `buildNumber: String` (before `modifier`); root
  `Column` in the safe drawing area: the existing scroll column with `weight(1f)`, then the footer `Text`
  (`stringResource(R.string.home_version, versionName, buildNumber)`, `onBackgroundMuted`, centered, one line,
  tag `HomeTags.VERSION`). Update both `@Preview`s.
- `ui/home/HomeTags.kt`: `VERSION`.
- `ui/KosmoDartsApp.kt`: pass `BuildConfig.VERSION_NAME` and `BuildConfig.BUILD_NUMBER`.
- `res/values/strings.xml` and `res/values-fr/strings.xml`: `home_version` (wording from O1).
- Tests: `ui/home/HomeScreenTest.kt`, new `ui/home/HomeVersionFooterTest.kt`, `ui/home/HomeFitTest.kt`,
  `ui/home/FrenchStringsTest.kt`, `screenshots/HomeScreenScreenshotTest.kt`, `screenshots/HistoryScreenshotTest.kt`,
  `screenshots/CricketSetupScreenshotTest.kt` (all under `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/`).

## Depends on
T1 (`BuildConfig.BUILD_NUMBER`, rules AV1 to AV4).

## Rules
AV1, AV2, AV3; HS1; CLAUDE.md touch targets (48dp, 42dp in landscape).

## Tests
Root plan, "Test plan", every T2 case: `HomeScreenTest` footer text from `BuildConfig`; the
`HomeVersionFooterTest` matrix (en/fr, port/land, font 1 and 2, with and without a saved 4-player match): footer
displayed on one line above the bottom safe area, not inside the scroll container, and each control
(settings, resume card, X01, Cricket, History) after `performScrollTo()` fully above the footer top, clickable and
tall enough; `FrenchStringsTest` for `home_version`. Tests and captures pass `"0.1.0"` and `"123"`.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0;
committed. Look at the five `home_*` captures and note in the notes that the footer is visible and covers nothing.

## Model
sonnet. Why: specified UI change from the plan with its Robolectric fit tests listed; no stored format.

## Implementation notes
