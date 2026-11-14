# T24: Head-to-head data and state

## Goal
Load what the head-to-head needs, and its state holder: picker, filters, figures, list of common matches,
navigation to the detail. Root "Screens and states", Head-to-head.

## Files
- `app/.../history/ProfileStatsRepository.kt`, `StatsDao.kt`: `loadPicker(profileId)` (other profiles except the
  anonymous one, with common counts) and `loadPair(a, b)` (common matches with darts, both profiles' dates).
- `app/.../ui/headtohead/HeadToHeadState.kt` (new): picker state, loaded state, filters (mode All / X01 / Cricket,
  ST3, period), date line data (HH8), empty, loading, failure.
- `app/.../ui/app/HeadToHeadReducer.kt`, `HeadToHeadEvent.kt` (new), `AppReducer.kt`, `PersistenceEffect.kt`:
  open from the Profile, pick, filters, match tapped (push the detail, HS9), retry.
- Tests: `HeadToHeadStateTest.kt`, `HeadToHeadReducerTest.kt`; repository cases in `ProfileStatsRepositoryTest.kt`.

## Depends on
T9, T10, T23.

## Rules
HH1 to HH9, HS9, AR9.

## Tests
- Picker excludes self and anonymous, sorted by common count then name, zero listed.
- Picking a zero profile gives the empty state.
- Filters and period recompute; Back from a detail keeps them.
- Date line: neither reset, one really reset, both (latest).
- Failure and retry.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: loader and state holder with JVM tests, behavior specified.

## Implementation notes
