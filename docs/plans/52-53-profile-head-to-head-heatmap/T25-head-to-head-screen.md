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

- Screen in `ui/headtohead/`: `HeadToHeadScreen` (dispatch on `state.view(partner)`), `HeadToHeadPicker`, `HeadToHeadFilters`, `HeadToHeadFigures` (side-by-side blocks), `CommonMatchText.kt` (match card), `HeadToHeadTags`. Wired in `KosmoDartsApp` in place of the placeholder.
- Opening: new `ProfileEvent.CompareTapped`, handled in `reduceProfileEvent` with `Screen.open` (no copy, HS9); the button (`ProfileTags.COMPARE`) shows in the Profile header once the name is known. The picker load comes from `headToHeadEffects`.
- Deviations: the two extra files (filters, match card); every target uses the 48 dp minimum, including landscape; wins and legs are plain numbers side by side; single rows (common, draws) reuse `FigureRowView` with an `h2h_` key prefix.
- Gotcha: a `testTag` placed after `clearAndSetSemantics` is cleared; the tag is set inside the block.
- Tests: `HeadToHeadScreenTest` (HH1 to HH9, HS7, PS8, ST6, ST10), reducer test for `CompareTapped`, `HeadToHeadScreenshotTest` (12 captures, `h2h_*.png`, Pixel 7, portrait EN, landscape FR, 200% font).
