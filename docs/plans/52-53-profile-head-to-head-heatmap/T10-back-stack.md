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
- `Screen.Stacked` (sealed, in `AppState.kt`) is the parent chain: `parent`, `reparented(parent)`, `isSameAs(other)`.
  Implemented by `MatchDetail(parent, detail)`, `Profile(parent, profileId, profile: Unit)` and
  `HeadToHead(parent, a, b, state: Unit)`. `Screen.open(target)` returns the equal screen already in the chain (HS9) or
  stacks `target` on the current screen. Callers (T13, T25) call `screen.open(Screen.Profile(screen, id))`.
- Back for Profile and HeadToHead is `reduceStacked` (HistoryReducer.kt): pops to `parent`. T13/T25 should replace the
  `Unit` placeholders and add their own events in a dedicated reducer, keeping Back = parent.
- Real-code divergence: History and Players keep `home` as their parent (they are chain roots), so `Players >
  Profile` works and Back from both returns home as before. `homeOfStack()` and `historyListOfStack()` give the saved
  game (HS1) and the History read (HS7) from any depth; a detail opened from a Profile triggers no History read.
- Process death: as History does today (HS9), History and its detail (opened straight from History) still come back
  through `historySnapshot`; Profile, Head-to-head and a detail above them store nothing, so the app restarts at home.
- KosmoDartsApp renders nothing for Profile/HeadToHead yet (T13, T25).
- Tests: `NavigationStackTest` (rotation at ViewModel level, process death with a new ViewModel); one assertion in
  `HistoryNavigationTest` follows the `parent` rename.
