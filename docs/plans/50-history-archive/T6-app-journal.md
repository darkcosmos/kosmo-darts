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
- Added `game-engine/.../engine/journal/GameProgress.kt` (with its JVM test): the one place that applies a dart or an
  undo to the match and to the journal together (ADR 0011, review A1); reducers never change one without the other.
- Clock and ids (review A4): the ViewModel reads `clock.nowMillis()` and the id source and puts the values in the
  events (`StartRequested`, `Rematch` carry `matchId` and `startedAt`; dart events carry `enteredAt`). Reducers stay
  pure: no `UUID.randomUUID()` and no system time inside them.
- A dart event appends `DartEntered(dart, clock.nowMillis(), MANUAL)`; Undo appends `Undone` only when the match
  changed (DJ2); confirming a leg ("Finish leg", "Finish set") starts the next leg journal; "Rematch" starts a new
  journal with a new id.
- The game save effect writes the journal with the match (T5 types).
- Visible behavior unchanged: existing reducer tests stay green without edits to their expectations.

## Tests
- Start: id from the source, start time from the clock; darts timed in order; undo journaled; no-op undo not journaled.
- Legs: the journal gains a leg per confirmed leg, X01 and Cricket; Rematch: new id, new start, empty journal.
- Resume keeps id, start and journal; DJ4 holds after each event of a scripted match.
- Undo cases, each followed by a DJ4 check: undo of the winning dart while the X01 CHECKOUT zone or the Cricket win
  zone is pending (journal gets `Undone`, the zone disappears, confirm is no longer offered); undo after a bust; undo
  on the first dart of a leg right after "Finish leg" (the previous leg's journal is untouched; with nothing to undo,
  nothing is journaled); Rematch after an undo (new journal, no event carried over).

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder wiring with JVM tests.

## Implementation notes

- Design: `GameProgress.follow(journal, before, after, at)` (game-engine, `engine/journal/GameProgress.kt`) derives the
  journal from the transition of the match (one dart added, one removed, no change, or a confirmed leg that opens the
  next journal leg); any other change is an error. No per-screen Progress classes.
- The journal lives on `Screen.Game`, `Screen.BetweenLegs`, `Screen.CricketGame` and `Screen.CricketBetweenLegs`
  (not on the End screens). "Next leg" and Resume keep it unchanged.
- Purity: `AppReducer.reduce(state, event, stamp: EventStamp(at, matchId))`; events are untouched. The ViewModel
  builds the stamp (`clock`, `newMatchId` constructor parameters); the id is requested only for Start and Rematch,
  otherwise `NO_MATCH_ID` ("") that the reducer never reads.
- Bridge removed: `TransitionalJournal.kt` and the `journal` defaults of `SavedGame`/`SavedCricketGame` are gone.
  Tests use `testing/MatchJournals` (journal derived from a match) and `testing/ScreenFixtures` (`savedGame`,
  `gameScreen`, `TEST_STAMP`, `startedJournal()`, and `AppReducer.reduce(state, event)` with `TEST_STAMP`).
- New tests: `GameProgressTest` (engine) and `AppReducerJournalTest` (app, DJ4 after every event).
- For T9 (archive flow): the End screens do not carry the journal; the archive must be built from the last
  `Game`/`BetweenLegs` screen journal at the moment the match ends (`finish`/`finishCricket` in the reducers).
