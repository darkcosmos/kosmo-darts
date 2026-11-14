# T10: Back stack for history screens

## Goal
A real back stack for Players, History, Profile, Head-to-head and Match detail, without copies, as a parent chain
(decision D5). Root "Approach" 4.

## Files
- `app/.../ui/app/AppState.kt`: `Screen.MatchDetail(parent: Screen, ...)` instead of `list: History`; new
  `Screen.Profile(parent: Screen, profileId, profile: ProfileState placeholder)` and
  `Screen.HeadToHead(parent: Screen, a, b, state placeholder)`; a helper `Screen.open(target)` that returns to an equal
  screen already in the chain (same profile, same ordered pair, same session) or pushes it.
- `app/.../ui/app/AppReducer.kt`, `HistoryReducer.kt`: Back pops one level; History filters and scroll kept.
- `app/src/test/.../ui/app/NavigationStackTest.kt` (new); update the History/MatchDetail reducer tests.
- `KosmoDartsApp.kt`: placeholders `Unit` for the new screens (screens come in T13 and T25).

## Depends on
T1.

## Rules
HS6, HS9.

## Tests
- History > detail > Back: History with filters and scroll.
- Players > Profile A > ... > detail > Profile A: back to the first Profile A, screens above dropped.
- Profile A > H2H (A, B) > detail > Profile B > H2H (B, A): pushed (different ordered pair); Back returns one level
  each time.
- Back from Players or History returns home as today.
- Rotation (a new composition over the same ViewModel state): the stack Players > Profile > detail is unchanged.
- Process death (a new ViewModel without state): the app starts at home (or the resume offer), never on a history
  screen; tested at the ViewModel level.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: reducer change specified by HS9, JVM tests.

## Implementation notes
