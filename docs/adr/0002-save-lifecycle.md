# ADR 0002: When the app loads, saves and deletes the game in progress

- Status: Proposed (design review of issue #30; becomes Accepted when the persistence PR is merged)
- Date: 2026-10-18
- Rules: `docs/rules/persistence.md` (R1, R2, R3, R6, R9, R10, R12). Format: ADR 0001.

## Context

ADR 0001 fixes what is written. This ADR fixes when and by whom:
- which component owns the files and the write thread;
- how the app decides to write or delete;
- when the files are read, and how a return after process death (R1) is told apart from a fresh launch (R2).

These choices are cross-cutting. Every future screen and every future game mode inherits them.

## Decision 1: one `Persistence` per process, held by the `Application`, with one save thread

`KosmoDartsApplication` creates `Persistence` lazily. `Persistence` owns:
- the two `AtomicFileStore`s (`filesDir/saved_game`, `filesDir/last_setup`);
- the codecs;
- a `SaveWriter` running on one daemon thread (`kosmodarts-save`).

The ViewModel gets `Persistence` through its factory, and tests can inject a fake.

Why: the thread must outlive the ViewModel. When the player leaves with back on the home screen, the activity
finishes and `viewModelScope` is cancelled, but a pending write must still complete. One thread for both files also
gives a single order for all reads and writes (R12).

Alternatives:
- *`viewModelScope` with coroutines*: pending writes are cancelled when the activity finishes. It would also mean
  relying on `kotlinx.coroutines`, which only reaches the app as a transitive dependency.
- *A static `object` executor*: hidden global state that tests cannot replace.
- *WorkManager*: a new dependency, and deferred scheduling that R12 does not want.

## Decision 2: storage effects are derived from state changes

After every event, the ViewModel calls the pure `PersistenceEffects.between(before, after)`. It compares
`savedGameOf(before)` with `savedGameOf(after)`, and `lastSetup` with `lastSetup`. The result is `SaveGame`,
`DeleteGame` and/or `SaveSetup`.

Deletion therefore follows from the state: when the new state no longer carries a saved game, the file is deleted.
The reducer stays pure and never calls storage.

`savedGameOf` is an exhaustive `when` over `Screen` with no `else` branch. Every new `Screen` therefore fails to
compile until its author states whether it carries the saved game. Consequence for every future screen: a screen
that carries the game but returns `null` would delete the save. The `PersistenceEffectsTest` matrix has one case per
screen and per transition, so such a mistake fails a test.

Alternative: explicit `save()` / `delete()` calls at each reducer branch or event. The decision would be spread over
many call sites, and a missed call silently leaves a stale file (R6). The reducer would also stop being pure.
Rejected.

## Decision 3: synchronous load at ViewModel creation, on the main thread

The ViewModel factory calls `Persistence.load()` once per ViewModel. That happens on every new process, and after
the activity was finished. It does not happen on a configuration change: the ViewModel survives, and the files are
not read again.

`load()` runs the read on the save thread and waits for it. The read is therefore ordered after any pending write
from a previous activity in the same process. The cost is one bounded read (at most 64 KiB, under 41 KB in practice)
plus one linear replay through the engine. `X01Game(config, darts)` replays each dart once. The decoder builds the
game with this single call, never dart by dart with `throwDart`, which would cost time proportional to n². A test
counts the calls.

This is main-thread disk I/O. It is visible to StrictMode, which is not enabled in this app, and is accepted for a
read of this size. The only wait is behind a write already in progress, which takes milliseconds.

Alternative: an asynchronous load with a blank `Loading` screen. Rejected:
- it adds a screen state, and a frame in which R1 shows something other than the game screen;
- it needs idling support in every UI test;
- it saves a few milliseconds of main thread time.

## Decision 4: R1 is detected from the saved instance state

`MainActivity.onSaveInstanceState` stores one boolean, `wasOnGameScreen`. Android hands the bundle back only when it
recreates the activity in an existing task, after process death or after the system destroyed the activity. A force
stop, a reboot, or removal from the recents gives no bundle. The bundle is read only when a new ViewModel is created,
so a rotation never uses it.

Alternative: storing the current screen in the save file. That would need one more stored UI field (issue #30 T4),
and the app could not tell a background kill (R1) from a force stop (R2): both leave the same file. Rejected.

## Decision 5: every rejected file is deleted

Owner decision Q9 (issue #30). It replaces the first version of this decision, which kept the file on a read error.

A file that cannot be loaded is deleted inside `load()`, on the save thread, before `load()` returns. That covers
`READ_FAILED` (an `IOException` while opening or reading), `TOO_LARGE`, and every parse, range, engine or flag
reason of ADR 0001. For the game file, the R9 message is then shown exactly once. For the last setup, the defaults
are used silently (R10).

Why: errors when reading internal storage are rare and often permanent. Keeping the file would show the message at
each launch, and a promise to offer the game again would become false as soon as a new game starts.

Alternative: keep the file on `READ_FAILED` and retry at the next launch. Rejected by the owner (Q9).

Every deletion (R3, R6, R9) goes through the same save thread as the writes. A write queued before a deletion
therefore runs first, or is replaced by it ("latest wins"), and never recreates the file afterwards. One test per
deletion case covers this (`DeletionOrderTest`).

Residual risks, accepted after the code review (security-reviewer S4, S5):
- An exception that the loader does not foresee (a bug, not a known invalid content) reaches `Persistence.load` as
  an `ExecutionException`. Both files are then deleted through the queue and the R9 message is shown
  (`UNEXPECTED_ERROR`), so that the same file cannot crash every launch (R11). The decoder itself still has no
  catch-all.
- On the save thread, `SaveWriter` only catches `IOException`, which is how `AtomicFile` reports storage failures. A
  `RuntimeException` there would be a bug and is not hidden.

## Consequences

- Every future `Screen` must state whether it carries the saved game (compile-time, Decision 2).
- Every future storage effect goes through `persistenceEffects` (`ui/app/PersistenceEffect.kt`), never through a
  direct call from a screen.
- Process death restores only the game screen. Other screens restart on home (R1).
- Dependencies go one way only: `ui` uses `persistence`, and `persistence` uses the engine only, tests included. The
  conversions between the game screen and the saved game (`GameScreenState.toSavedGame`,
  `SavedGame.toGameScreenState`) live in `ui/game/SavedGameScreen.kt`. Resolved (architecture-reviewer A7): at
  first, `persistence.SavedGame` held these two conversions and depended on `ui.game.GameScreenState`. The test
  fixtures of `persistence` build saved games from the engine alone.
- The `Application` subclass is the only place that knows `filesDir`. Plain JVM tests use in-memory stores and a
  manual executor; Robolectric tests use the real files.
