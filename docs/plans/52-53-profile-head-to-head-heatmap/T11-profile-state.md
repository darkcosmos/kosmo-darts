# T11: Profile state holder

## Goal
State, events and effects of the Profile screen (no composable yet): loading, sections, filters, periods, figures.
Root "Screens and states", Profile, decision D3.

## Files
- `app/.../ui/profile/ProfileState.kt` (new): load state (loading, failed, loaded), section (X01, CRICKET, HEATMAP),
  X01 filter, Cricket filter, period per section, derived figures (`X01Figures`, `CricketFigures`, trend), since label
  data (ST9 date or "since always"), trend summary (`TrendSummary`, T8), number of ignored matches (ST10, from
  `ProfileStatsData.skippedMatches`; notice shown when above 0).
- `app/.../ui/app/ProfileEvent.kt`, `ProfileReducer.kt` (new): open, loaded, retry, section, filter and period
  selected; figures computed on the read executor (`Persistence` effect like Players, root T9 of #51 notes).
- `app/.../ui/app/AppReducer.kt`, `PersistenceEffect.kt`: route events, run the load effect with
  `ProfileStatsRepository`.
- Tests: `ProfileStateTest.kt`, `ProfileReducerTest.kt` with a fake repository.

## Depends on
T2, T8, T9, T10.

## Rules
PS2 (label only), PS3, PS4, PS5 (summary), PS6, ST2 to ST10.

## Tests
- Load success, empty (no match at all), empty for filters, failure and retry; HS7 loading delay reused.
- Filter and period changes recompute without reloading; the selections survive a push and Back (HS9).
- Since label: never reset, really reset, merged without real reset.
- Ignored matches: 0 gives no notice; 2 gives the notice with count 2; figures exclude them.
- Trend summary in state matches the engine summary for the selection.
- Figures are computed off the main thread (effect on the executor, checked through the fake).

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder with JVM tests, behavior specified.

## Implementation notes
