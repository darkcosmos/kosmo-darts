# T25: Head-to-head screen

## Goal
The Head-to-head screen and the "Compare with…" button of the Profile. Root "Screens and states", Head-to-head.

## Files
- `app/.../ui/headtohead/HeadToHeadScreen.kt`, `HeadToHeadPicker.kt`, `HeadToHeadFigures.kt`, `HeadToHeadTags.kt`
  (new): picker list (name, common count), header (two names, date line), counters (common, wins, draws, legs),
  side-by-side figures ("–" when null), list of common matches (reuse the History row card).
- `app/.../ui/profile/ProfileHeader.kt`: enable "Compare with…".
- `app/.../ui/app/KosmoDartsApp.kt`: show the screen.
- Strings en and fr.
- Tests: `HeadToHeadScreenTest.kt`; captures `HeadToHeadScreenshotTest.kt`.

## Depends on
T13, T24.

## Rules
HH1 to HH9, PS8.

## Tests
- Picker tap opens the comparison; match row opens the detail; long names (20 code points) fit in two columns.
- Captures (Pixel 7, portrait, landscape, 200 % font): picker, filled X01, filled "All" with a draw, empty.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan with Compose tests and captures.

## Implementation notes
