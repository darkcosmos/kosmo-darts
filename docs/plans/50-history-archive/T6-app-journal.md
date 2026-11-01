# T6: Journal, match id and clock in the reducers

## Goal
Every game in progress keeps its `MatchJournal` in the app state, in step with the match, and saves it (DJ1, DJ2,
AR4). Root plan "Approach" 4.

## Files
- Changed in `app/src/main/kotlin/.../ui/app/`: `AppState.kt` (game screens carry the journal),
  `AppReducer.kt`, `CricketReducer.kt`, `SetupTransitions.kt` (Start and Rematch: new match id and start time),
  `KosmoDartsViewModel.kt` (injects `Clock` and an id source; production: `System.currentTimeMillis()` and
  `UUID.randomUUID()`), `InitialState.kt` (resume keeps the loaded journal).
- Tests: `app/src/test/kotlin/.../ui/app/AppReducerJournalTest.kt` with a fake clock and fixed ids.

## Depends on
T5.

## Rules
DJ1, DJ2, DJ4, AR4, AR5 (start time), R4, R5, R12, R14, R15.

## Approach
- A dart event appends `DartEntered(dart, clock.nowMillis(), MANUAL)`; Undo appends `Undone` only when the match
  changed (DJ2); confirming a leg ("Finish leg", "Finish set") starts the next leg journal; "Rematch" starts a new
  journal with a new id.
- The game save effect writes the journal with the match (T5 types).
- Visible behavior unchanged: existing reducer tests stay green without edits to their expectations.

## Tests
- Start: id from the source, start time from the clock; darts timed in order; undo journaled; no-op undo not journaled.
- Legs: the journal gains a leg per confirmed leg, X01 and Cricket; Rematch: new id, new start, empty journal.
- Resume keeps id, start and journal; DJ4 holds after each event of a scripted match.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder wiring with JVM tests.

## Implementation notes
