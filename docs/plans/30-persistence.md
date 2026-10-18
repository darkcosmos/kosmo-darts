# Plan: persist the game in progress and the last setup (issue #30)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/30

A game must never be lost. If Android kills the app, if Kosmo force-stops it, or if the phone restarts, the game
in progress comes back exactly where it was, and the last setup prefills the next game.

Rules: `docs/rules/persistence.md` (R1 to R12, new). Decisions (new, Proposed): `docs/adr/0001-save-format.md`
(save format) and `docs/adr/0002-save-lifecycle.md` (who loads, saves and deletes, and when).

Owner decisions taken on the issue (2026-10-18, comments on #30):

| Question | Decision |
|---|---|
| Q1, Q7 | With a saved game, the home screen shows "New game", then "Resume game" right under it. "New game" asks for confirmation; "Delete" deletes the saved game at once and opens the setup screen (going back then leaves nothing to resume). |
| Q2 | "Abandon" deletes the saved game. |
| Q3 | A rematch updates the last setup, in the rematch order. |
| Q4 | The summary shows the player the game screen highlights; for an open checkout dialog, the winner with "Checkout to confirm". |
| Q5 | An active Double/Triple modifier and an open "Abandon the game?" dialog are not restored. |
| Q6 | The persistence rules use the prefix R, never P. |
| Q8 | The button label is "Resume game" / « Reprendre la partie ». |

## Scope and out of scope

In scope:
- Saving and restoring the game in progress, including a turn waiting for "Next player" and the checkout dialog
  (R1, R2, R4, R5).
- New-game confirmation (R3); deleting the saved game on a confirmed win and on abandon (R6).
- Retaining the last setup on Start and on Rematch, and prefilling it on the setup screen (R7, R8).
- Rejecting unreadable files: a message dialog once for the game, silent defaults for the setup (R9 to R11).
- Serialized, atomic, off-main-thread writes in `filesDir` (R12).
- New docs: `docs/rules/persistence.md`, `docs/adr/0001-save-format.md`, and this plan.

Out of scope, from the issue:
- History and statistics.
- Several saved games.
- Export or sharing.
- Encryption of the save.
- The player-list overflow at 3 or 4 players in portrait.

Out of scope, from this plan:
- Restoring a setup screen being edited (after process death, the app comes back on the home screen; R1).
- An active Double/Triple modifier and the abandon dialog (owner decision Q5).
- Telling the user when a write fails (see Known limitations below).

## Approach

### Where the code lives

All new code goes in `:app`, in a new package `io.github.darkcosmos.kosmodarts.persistence`. `:game-engine` is not
changed: the engine already offers what persistence needs (`X01Config`, `X01Game(config, darts)` replay,
`LegFinishedException`, `InvalidX01ConfigException`; X01 rule P1).

The package is split into two parts:
- **Pure Kotlin** (no `android.*` import; tested with plain JUnit 4 in `app/src/test`):
  - `SavedGame`;
  - `GameSaveCodec` and `LastSetupCodec`;
  - `SaveFormat` (shared line, Base64 and CRC helpers);
  - `ByteStore` (interface);
  - `SaveWriter`;
  - `PersistenceEffects`.
- **Android**:
  - `AtomicFileStore` (implements `ByteStore` with `android.util.AtomicFile`);
  - `Persistence` (wires the stores, the codecs and the writer), held by a new `KosmoDartsApplication`.

Alternatives considered:
- *A new `:persistence` JVM module*: a cleaner boundary, but one more module for six small classes. The pure part
  stays free of Android imports, so it can move to a module later without changes.
- *Codec in `:game-engine`*: rejected. The engine holds game rules only, and the save format is a storage concern
  of the app. It also has to know a UI field, the waiting flag.

### Save format

Hand-written, versioned, line-based UTF-8 text with a CRC-32. Names are in Base64. The full comparison with
kotlinx.serialization and the format specification are in `docs/adr/0001-save-format.md`. No new dependency.

### Stored model: config + darts + one flag (T4)

`SavedGame(game: X01Game, isAwaitingNextPlayer: Boolean)` is built from `GameScreenState`:
- `SavedGame.of(screen)` uses `screen.game` and `screen.isAwaitingConfirmation`.
- `toGameScreenState()` uses `GameScreenState(game, isAwaitingConfirmation = isAwaitingNextPlayer)`, with the
  default multiplier and no abandon dialog (Q5).

The checkout dialog is derived (finished game ⇒ dialog). The flag is the only stored UI field, and is justified in
the ADR: "Next player" changes no dart. Two consistency rules are checked on load:
- finished ⇒ flag `true`;
- flag `true` ⇒ the last dart completed a turn.

Alternative: storing "Next player" as a token in the dart list. Same information, more to validate (ADR).

### App state changes

`Screen` (in `AppState.kt`):
- `Home` becomes a data class, `Home(resumableGame: SavedGame? = null, isNewGameDialogShown: Boolean = false)`.
  `resumableGame` is the saved game shown in the summary (R2). Only the home screen ever carries a resumable game.
  Every other way to leave a game removes it (R3, R6).
- `Setup` gains `isRestoreFailedShown: Boolean = false` (R9).
- `AppState.lastNames: List<String>` is replaced by `lastSetup: X01Config?` (names, start score and checkout rule;
  R7). A last setup is a valid configuration of a started game, so it reuses `X01Config` instead of a new type.
- `SetupState.prefilled(names)` becomes `SetupState.prefilled(lastSetup: X01Config?)`, which sets names, start
  score and checkout rule.

New `AppEvent`s:
- `ResumeTapped`;
- `NewGameConfirmed`, `NewGameDismissed`;
- `RestoreFailedDismissed`.

Reducer changes (`AppReducer`):
- Home + `NewGame` ⇒ the setup screen when there is no resumable game, else `isNewGameDialogShown = true`.
- Home + `NewGameDismissed` or `Back` while the dialog is shown ⇒ the dialog closes. While the dialog is closed,
  `Back` is not handled on home: the app closes, as today.
- Home + `NewGameConfirmed` ⇒ `Setup(prefilled(lastSetup))` with no resumable game, which deletes the save.
- Home + `ResumeTapped` ⇒ `Game(resumableGame.toGameScreenState())`.
- Setup + `StartRequested` (valid) ⇒ as today, with `lastSetup = config`.
- End + `Rematch` ⇒ as today, with `lastSetup = rematch config` (Q3).
- Setup + `RestoreFailedDismissed` (or `Back` while the message is shown) ⇒ the message closes and the setup screen
  stays.
- Abandon and Finish are unchanged in the reducer. The save is deleted because the new state has no saved game.

### Deciding what to write: `PersistenceEffects` (pure)

`savedGameOf(state)` is the game that must be on disk. It is an exhaustive `when` over `Screen`, with no `else`
branch, so every new screen must state whether it carries the saved game (ADR 0002, decision 2):
- `Game`: `SavedGame.of(game)`;
- `Home`: `resumableGame`;
- `Setup` and `End`: `null`.

`PersistenceEffects.between(before, after): List<PersistenceEffect>` compares `savedGameOf(before)` and
`savedGameOf(after)`, and returns:
- `SaveGame(saved)` when the saved game changed to non-null;
- `DeleteGame` when it changed to null;
- `SaveSetup(config)` when `lastSetup` changed.

Consequences, all covered by tests:
- Every dart, undo and "Next player" writes. "Finish game", "Abandon" and "Delete" delete.
- Toggling Double/Triple, opening dialogs and Resume write nothing, since the saved game is unchanged.
- T1's "checkout pop-up shown, confirmed or dismissed" maps to the winning dart (write), "Finish game" (delete) and
  Undo (write).

`KosmoDartsViewModel.onEvent` computes the effects after `reduce` and hands them to `Persistence`. The equality
check is cheap: `X01Game.equals` compares the config and the darts list.

### Writing: `SaveWriter` + `AtomicFileStore` (R12)

- `SaveWriter(executor: Executor)` keeps, for each store, the latest pending content in an `AtomicReference` (bytes,
  or a delete marker) and runs one drain task at a time on a single-thread executor. A new request replaces the
  pending one ("latest wins"), so the file always ends at the latest action. A queue of N writes is never needed.
- The game file and the setup file share the same executor, so all writes are serialized.
- `AtomicFileStore(file: File)`:
  - `write(bytes)`: `startWrite`, write, `finishWrite`. On `IOException`, it calls `failWrite`, keeps the previous
    file, and the failure is swallowed after the attempt. There is no log of the content; the content holds names.
  - `delete()`: `AtomicFile.delete()`, which also removes the `.new` and legacy `.bak` files.
  - `read(maxBytes)`: returns `Absent` if no file exists. Returns `TooLarge` if `baseFile.length() > maxBytes`;
    otherwise it reads `openRead()` with a loop that stops after `maxBytes + 1` bytes, so it is still bounded if the
    file grew meanwhile. Returns `Bytes(...)` or `Failed` (`IOException`).
- The production executor is one daemon thread named `kosmodarts-save`, created by `KosmoDartsApplication`. It
  outlives the ViewModel, so a pending write is not cancelled when the activity finishes (back on home).
- Files: `filesDir/saved_game` and `filesDir/last_setup` (private app storage, mode private by default). There is no
  cache dir and no external storage.
- The encoder returns `null` for a game over 10,000 darts. The writer then keeps the previous file, and a later
  `DeleteGame` (Finish, Abandon) still deletes it (R12).

### Loading and the first screen (R1, R2, R9, R10)

- The `KosmoDartsViewModel` factory, in `MainActivity` via `viewModels { factory }`, calls `Persistence.load()` once
  per ViewModel creation:
  - not on a configuration change, since the ViewModel survives;
  - but on every new process, and after the activity was finished.
- `load()` submits the read to the save executor and waits for it. The read is therefore ordered after any write
  still pending from a previous activity in the same process. The cost is one bounded read plus one linear replay.
  The decoder builds the game with a single `X01Game(config, darts)` call, never dart by dart. This main-thread
  read, and the rejected asynchronous alternative, are recorded in ADR 0002, decision 3.
- `load()` is a thin wrapper over a pure `SaveLoader(gameStore, setupStore, gameCodec, setupCodec)`, tested with
  in-memory `ByteStore`s and the manual executor.
- The result is `LoadResult(game: GameLoad, lastSetup: X01Config?)`, where `GameLoad` is `Absent`, `Valid(SavedGame)`
  or `Invalid(reason)`. A read result of `TooLarge` maps to `Invalid(TOO_LARGE)`, and `Failed` to
  `Invalid(READ_FAILED)`. A game file judged invalid is deleted in `load()` before it returns (R9, "once"). On
  `READ_FAILED` the file is kept (ADR 0002, decision 5). An invalid setup file is deleted and yields `null`; on a
  setup read failure the file is kept and also yields `null` (R10).
- R1 detection (ADR 0002, decision 4): `MainActivity.onSaveInstanceState` stores one boolean, `wasOnGameScreen`,
  from the ViewModel. On `onCreate`, `savedInstanceState?.getBoolean(...) == true` is passed to the factory. It only
  has an effect when a new ViewModel is created, which means process death or an activity destroyed by the system.
  Stored there because this is exactly the "was the user on the game screen" question, and Android hands the bundle
  back only when it restores the task. Force stop, reboot or removal from the recents give no bundle, hence R2.
- `initialState(load: LoadResult, wasOnGameScreen: Boolean): AppState` is pure:
  - `Valid` + `wasOnGameScreen` ⇒ `Game(saved.toGameScreenState())` (R1);
  - `Valid` otherwise ⇒ `Home(resumableGame = saved)` (R2);
  - `Absent` ⇒ `Home()`;
  - `Invalid` ⇒ `Setup(prefilled(lastSetup), isRestoreFailedShown = true)` (R9).
  - `lastSetup` is always carried (R8).
- Restoring sets the initial state directly. It does not go through `onEvent`, so it fires no hook and writes
  nothing.

### Turn-completed signal (R4)

`completedTurn(before, after)` returns a turn today whenever `after` waits and `before` did not. That includes
Home → Game on "Resume game" with a waiting turn, which would announce that turn a second time. Change: it returns
`null` unless `before.screen` is also `Game` and `after` has exactly one more dart than `before`. The existing tests
stay green, and new ones cover Resume and the initial restore.

### Home screen with a saved game (R2, R3)

The column layout stays: title, welcome text. When `resumableGame` is set, a summary card follows:
- the title "Game in progress";
- a line "501 · Double out" (reusing the existing setup strings);
- one row per player, with the name and the remaining score, in throwing order;
- a line "Alice to throw" (reusing `game_to_throw`), or "Checkout to confirm: Alice" (Q4).

Then the "New game" button, then "Resume game" under it (Q1, Q8). While a game is saved, "Resume game" is the main
action: it is a filled `Button`, and "New game" becomes an `OutlinedButton`. Both are full width, at least 48 dp
high, with the same order in portrait and landscape.

Layout rules for long names, 4 players, large fonts and landscape:
- A player row is a `Row`: the name takes the remaining width (`weight(1f)`) and wraps onto as many lines as it
  needs. The score is a fixed, non-wrapping `Text` at the end of the row, so a 20-character name never pushes it out
  or clips it.
- The mode line, the "to throw" line and the checkout line wrap. No text in the card is truncated with an ellipsis,
  and nothing has a fixed height.
- The whole home column scrolls vertically, as it does today. In landscape, or at 200 % font scale, "Resume game" may
  be below the fold. It is always reachable by scrolling, and a test checks that.
- The order is identical in both orientations; there is no separate landscape layout.

TalkBack:
- "Game in progress" is a heading.
- Each player row is one merged node, read as "Alice, 341".
- The mode line and the "to throw" or checkout line are plain text. Their meaning never depends on color or bold
  alone.
- Focus order follows the visual order: summary, "New game", "Resume game".

The summary model is a pure function, `ResumeSummary.of(saved)`, so that it can be tested without Compose. It holds
no layout decision. It builds `saved.toGameScreenState()` and reads the player to throw from that state's
`highlightedPlayerIndex` and `isCheckoutDialogShown`. The summary and the resumed game screen therefore use the same
logic and cannot disagree.

New-game confirmation: an `AlertDialog`, titled "A game is in progress", with the text "Delete it and start a new
one?". The buttons are "Delete" (confirm) and "Cancel" (dismiss). Back or a tap outside cancels.

Unreadable save: an `AlertDialog` over the setup screen, with the text "The saved game could not be resumed" and an
"OK" button. Back or a tap outside also closes it. A dialog is used rather than a snackbar: the message is shown
only once, so it must not vanish on a timer before it is read. TalkBack also announces it.

Both dialogs are part of the ViewModel state (`Home.isNewGameDialogShown`, `Setup.isRestoreFailedShown`), so they
survive a rotation.

The message text is the one Kosmo wrote in the issue (P9), unchanged. ux-reviewer's suggestion U5 to add "and was
deleted" is not applied, because the wording is the owner's. It is listed in the PR as a possible later change.

### Manifest

- `android:name=".KosmoDartsApplication"` is added on `<application>`.
- No permission is added, and the backup settings are unchanged. `allowBackup="false"`, `data_extraction_rules` and
  `full_backup_content` already exclude the `file` domain, so both files stay out of cloud backup and
  device-to-device transfer.
- No component is exported apart from the existing launcher activity.

## Files and types added or changed

Added:
- `docs/rules/persistence.md`, `docs/adr/0001-save-format.md`, `docs/adr/0002-save-lifecycle.md`,
  `docs/plans/30-persistence.md`.
- In `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/`:
  - `KosmoDartsApplication.kt`: holds `persistence: Persistence`, created lazily from `filesDir` with the
    single-thread executor. Robolectric tests can replace it before the activity starts, through a
    `@VisibleForTesting` setter, with a `Persistence` over the same files that counts loads and writes.
  - `persistence/SavedGame.kt`: `SavedGame`, plus `of` and `toGameScreenState`.
  - `persistence/SaveFormat.kt`: line reader and writer, canonical integers, Base64 names, CRC-32, size constants.
  - `persistence/GameSaveCodec.kt`: `encode(SavedGame): ByteArray?` and `decode(ByteArray): GameDecodeResult`, with
    `InvalidSaveReason`.
  - `persistence/LastSetupCodec.kt`: `encode(X01Config)` and `decode(ByteArray): X01Config?`.
  - `persistence/ByteStore.kt`: the `ByteStore` interface and `ReadResult`.
  - `persistence/AtomicFileStore.kt`.
  - `persistence/SaveWriter.kt`.
  - `persistence/SaveLoader.kt`: reads both stores, decodes them, deletes content judged invalid, and returns
    `LoadResult` (pure; tested with in-memory stores).
  - `persistence/PersistenceEffects.kt`: `PersistenceEffect`, `savedGameOf`, `between`.
  - `persistence/Persistence.kt`: `load(): LoadResult` and `apply(effects)`, plus a `@VisibleForTesting`
    `awaitIdle()` that waits for pending writes, for Robolectric tests.
  - `ui/app/InitialState.kt`: `initialState(load, wasOnGameScreen)`.
  - `ui/home/ResumeSummary.kt`.
- Tests and screenshots, listed in the test plan below.

Changed:
- `AndroidManifest.xml`: the application name.
- `MainActivity.kt`: the ViewModel factory, and `onSaveInstanceState`.
- `KosmoDartsViewModel.kt`:
  - the constructor becomes `(random, persistence, initial)`;
  - it applies effects;
  - it gains `wasOnGameScreen`;
  - its KDoc is updated (process death is handled now).
- `AppState.kt`: `Screen.Home`, `Screen.Setup`, `lastSetup`, and the new events.
- `AppReducer.kt`: the transitions above.
- `TurnCompletion.kt`: the change described above.
- `SetupState.kt`: `prefilled(lastSetup)`.
- `KosmoDartsApp.kt`:
  - `BackHandler` stays disabled on home, except while the new-game dialog is open.
  - The home screen receives its state.
- `HomeScreen.kt`: the summary card, the buttons and the dialog.
- `SetupScreen.kt`: the restore-failed dialog.
- `values/strings.xml` and `values-fr/strings.xml`: new strings, below.

New strings (English / French):

| Key | English | French |
|---|---|---|
| `home_resume_game` | Resume game | Reprendre la partie |
| `home_saved_game_title` | Game in progress | Partie en cours |
| `home_saved_game_mode` | %1$d · %2$s | %1$d · %2$s |
| `home_saved_game_score` | %1$s: %2$d | %1$s\u00A0: %2$d |
| `home_saved_game_checkout` | Checkout to confirm: %1$s | Checkout à confirmer\u00A0: %1$s |
| `new_game_confirm_title` | A game is in progress | Une partie est en cours |
| `new_game_confirm_text` | Delete it and start a new one? | La supprimer et en commencer une nouvelle ? |
| `new_game_confirm_delete` | Delete | Supprimer |
| `new_game_confirm_cancel` | Cancel | Annuler |
| `restore_failed_message` | The saved game could not be resumed | La partie enregistrée n'a pas pu être reprise |
| `restore_failed_ok` | OK | OK |

In French, `\u00A0` is a non-breaking space before the colon, written as an escape in `values-fr/strings.xml`. The
line then cannot wrap before the colon and leave a lone ":". The row score is not rendered from
`home_saved_game_score`: it is a separate `Text`, and the string is the content description of the merged row in
both languages.

`home_saved_game_mode` takes the start score and the existing `setup_double_out` / `setup_single_out` string. The
player to throw reuses `game_to_throw`.

No dependency, plugin or tool is added or updated.

## Data read, stored or exposed

- Stored, in `filesDir` (app-private, not readable by other apps, removed on uninstall):
  - `saved_game`: player names, start score, checkout rule, darts, the waiting flag. At most 64 KiB.
  - `last_setup`: player names, start score, checkout rule. At most 4 KiB.
  - Transient `saved_game.new` and `last_setup.new` (AtomicFile), present only during a write or after an
    interrupted one.
- Read: only these two files, at ViewModel creation, with a size limit before parsing. All their content is treated
  as untrusted (R9 to R11).
- Exposed: nothing.
  - No permission, no exported component besides the launcher activity, no content provider or file sharing.
  - Backup and device transfer stay excluded (existing XML rules cover the `file` domain).
  - Names are never logged; nothing is logged about the content.
  - The saved instance state holds only one boolean (`wasOnGameScreen`), never names or darts.
- Not encrypted (out of scope in the issue). The CRC detects accidental corruption only.

## Screens and states

| Screen | State | What the player sees |
|---|---|---|
| Home | no saved game | as today: title, welcome text, "New game" |
| Home | saved game | title, welcome text, summary card (mode, rule, players and remaining scores, player to throw or "Checkout to confirm: X"), outlined "New game", filled "Resume game" under it |
| Home | new-game dialog | "A game is in progress" / "Delete it and start a new one?" — "Cancel", "Delete" |
| Setup | restore failed | the setup screen prefilled with the last setup (or the defaults), and the dialog "The saved game could not be resumed" with "OK" |
| Setup | prefilled | names in the last order, last 501/301, last checkout rule |
| Game | resumed | identical to before the kill: the turn in progress, a waiting turn (BUST, keys disabled, "Next player" on), or the checkout dialog |

Orientation: all states work in portrait and in landscape; home and setup scroll. Dialogs are standard Material 3
`AlertDialog`s with text buttons (48 dp minimum touch target).

## Test plan

Rule ids are cited in each test name and KDoc, as in `:game-engine` (for example `` `R9 truncated file is
rejected` ``).

### Pure JVM tests (`app/src/test/.../persistence`, JUnit 4, no Robolectric)

`GameSaveCodecTest`:
- Round trip (encode, then decode, gives an equal `SavedGame`), for:
  - 1, 2, 3 and 4 players;
  - 501 and 301;
  - double-out and single-out;
  - an empty game;
  - a game in the middle of a turn;
  - a waiting normal turn;
  - a waiting bust;
  - a waiting checkout;
  - a game after "Next player";
  - names with accents, an emoji (outside the BMP), exactly 20 code points, and a name containing `=`, a space and
    a non-NFC input normalized by the config;
  - exactly 10,000 darts.

  (R4, R5, R12)
- Golden test: one fixed game encodes to the exact bytes in the ADR format, with the real CRC. This freezes v1.
- R12: 10,001 darts encode to `null`.
- R12, ADR 0002 decision 3: decoding a 10,000-dart save builds the game exactly once. The codec takes its game factory
  as a constructor parameter (default `::X01Game`), and the test counts the calls. There is no wall-clock
  assertion.

`GameSaveDecodeInvalidTest` (R9). Each case asserts `Invalid` with its reason:
- Empty file.
- Every truncation of a valid file: a loop over all prefix lengths.
- Size limit + 1 (`TOO_LARGE`).
- Wrong kind line.
- Version `0`, `2`, `99`, `01` and `x` (`UNSUPPORTED_VERSION`). The test for a newer version uses a file whose
  remainder is otherwise valid.
- BOM; CRLF; a CR inside a value; missing final LF; empty line; duplicate key; keys out of order; unknown key;
  missing `end`; data after `end`.
- CRC mismatch after a one-character change; malformed CRC (uppercase, 7 digits).
- Start score `401`, `0501`, `+501`; checkout `double_out`.
- Players `0`, `5`; a `players` count that does not match the number of `player` lines.
- Name:
  - invalid Base64;
  - non-canonical Base64 (missing padding, extra bits);
  - invalid UTF-8;
  - not trimmed;
  - not NFC;
  - blank;
  - 21 code points;
  - duplicate names ignoring case (`INVALID_CONFIG`).
- Dart tokens `S5`, `21`, `D25`, `T50`, `D0`, `05`, an empty token (double space), and a trailing space.
- 10,001 darts.
- A dart after the win (`REJECTED_BY_ENGINE`).
- Flag `true` in the middle of a turn; flag `true` with no dart; flag `false` on a finished game
  (`INCONSISTENT_WAITING_FLAG`). The valid counterparts are covered by the round trips: flag `true` after a
  normal third dart, after a bust on the first or second dart, and after a checkout. The check reads the replayed
  engine state (ADR 0001).

`GameSaveFuzzTest` (R11): `Random(FUZZ_SEED)`, a fixed constant seed. 5,000 mutations of valid saves (flip, insert,
delete or duplicate bytes, truncate, splice two saves), plus 1,000 random byte arrays from 0 to 70,000 bytes. `decode` never throws, and
returns `Valid` only when the result re-encodes to the same bytes. On failure, the assertion message gives the
seed, the iteration and the input as hex. The fixtures use made-up names only, and nothing is logged.

`LastSetupCodecTest`:
- Round trip for 1 to 4 players, both modes and both rules (R7).
- Invalid cases decode to `null`: the same structural cases as above, plus an invalid config (R10).
- Fuzz: never throws (R11).

`SaveWriterTest` (R12), with a manual executor and an in-memory `ByteStore`:
- Writes run only on the executor, never on the caller.
- Three saves before the drain give one write with the latest content.
- Save then delete gives a deleted file; delete then save gives the latest content.
- The game and setup stores do not overwrite each other.
- A store that throws on write keeps its previous content; the next request is written; nothing propagates.
- An encoder result of `null` (over the limit) keeps the previous content. A later `DeleteGame` still deletes it.
- Recovery from the limit: a game at 10,001 darts keeps the 10,000-dart file. An undo back to 10,000 darts writes
  again (R12).

`SaveLoaderTest` (R8, R9, R10, R12), with in-memory stores and the manual executor:
- A load submitted after a pending write runs after it and sees the new content.
- `TooLarge` gives `Invalid(TOO_LARGE)`, and the game file is deleted.
- `Failed` gives `Invalid(READ_FAILED)`, and the game file is kept (ADR 0002, decision 5).
- Two loads in a row over a store that fails on read both give `Invalid(READ_FAILED)`, and the file is intact after
  the second one. A later `SaveGame` (a new game started) overwrites the kept file (test-reviewer T8).
- Each invalid content deletes the game file and leaves the setup file untouched (R8).
- An invalid setup deletes the setup file, gives `null`, and leaves the game file untouched (R8, R10). A setup read
  failure gives `null` and keeps the file.
- Absent files give `Absent` and `null`, and nothing is written or deleted.

`PersistenceEffectsTest` (R3, R6, R7, R12). One test per transition:
- A dart, Undo, "Next player", the winning dart, and Undo in the checkout dialog each give `SaveGame`.
- Double/Triple toggle, opening or closing the abandon dialog, `ResumeTapped`, and opening the new-game dialog give
  nothing.
- "Finish game", "Abandon" and "Delete" in the new-game dialog give `DeleteGame`.
- Start gives `SaveSetup` + `SaveGame`.
- Rematch gives `SaveSetup` (rotated) + `SaveGame`.
- Cancel in the new-game dialog gives nothing.
- One case per `Screen` for `savedGameOf`, so a future screen cannot silently delete the save (ADR 0002, decision 2).

`AppReducerTest` additions:
- R2: Home with a resumable game, then `ResumeTapped`, gives the game screen with the same game and flag.
- R3:
  - `NewGame` with a resumable game opens the dialog;
  - Cancel and Back keep the game;
  - Confirm opens a setup prefilled from `lastSetup` with no resumable game;
  - `NewGame` without a saved game opens the setup directly;
  - Back from that setup gives a home with nothing to resume.
- R6: Abandon gives a home with no resumable game; Finish gives the end screen.
- R7: Start sets `lastSetup`; Rematch sets the rotated config; `NewGame` from the end screen prefills names, score
  and rule.
- R9: `RestoreFailedDismissed` and Back close the message and stay on setup.

`InitialStateTest` (R1, R2, R8, R9, R10). The full matrix of `GameLoad` (`Absent`, `Valid` with each waiting kind,
`Invalid`) × `wasOnGameScreen` × last setup (present or absent).

`TurnCompletionTest` additions (R4):
- `ResumeTapped` on a waiting turn returns `null`.
- The initial restore never calls the hook (ViewModel test with a recording hook).
- The existing once-per-turn tests stay unchanged.

`SetupStateTest` addition (R7): `prefilled(config)` sets names, start score and rule; `prefilled(null)` gives the
defaults.

`ResumeSummaryTest` (R2): mode and rule; players with their remaining scores in order. For each case below, the player
to throw is asserted equal to `toGameScreenState().highlightedPlayerIndex`, and the checkout mention to
`isCheckoutDialogShown`:
- a turn in progress;
- a waiting normal turn and a waiting bust (the thrower, as highlighted);
- an empty game (the first player);
- a waiting checkout (the winner, with the checkout mention).

### Robolectric tests

`AtomicFileStoreTest` (R9, R12), on real files in the Robolectric `filesDir`:
- Write then read.
- Overwrite.
- Delete removes the base and `.new` files.
- A missing file gives `Absent`.
- A file of the limit + 1 bytes gives `TooLarge`, and the read stops at the limit + 1, checked with a counting
  stream wrapper on the read path.
- A leftover `.new` from an interrupted write does not replace the last complete content.
- The path is under `context.filesDir`.
- Write failure (R12): a directory is created where `AtomicFile` puts its `.new` file, so `startWrite` throws a
  real `IOException`. The previous content stays readable, nothing propagates, and once the directory is removed
  the next write succeeds.

`PersistenceScreensTest` (Compose UI through `MainActivity`). Process death is simulated with a Robolectric
`ActivityController`: run the first activity, take `saveInstanceState`, destroy it, then
`buildActivity(...).setup(bundle)`. That gives a new ViewModel, which reads the files again. Writes are awaited with
`persistence.awaitIdle()` before each relaunch.
- R1: process recreation during a turn in progress shows the game screen directly: same player, darts and scores.
- R4, R1: recreation with a waiting turn: BUST, dart keys disabled, "Next player" enabled. Tapping it moves on.
- R4: recreation with the checkout dialog open. "Undo" reopens the turn; then winning again and tapping "Finish
  game" goes to the end screen.
- R2: a fresh launch (no bundle) shows the summary (mode, rule, names, scores, player to throw), then "New game",
  then "Resume game"; "Resume game" opens the same game.
- R5: after resume, Undo back to the first dart; Undo is then disabled.
- R3: "New game" with a saved game; Cancel keeps the summary; "Delete" opens setup; Back shows home without a
  summary; a relaunch also shows none.
- R6: "Finish game", then relaunch on the end screen, gives home without "Resume game". Abandon, then relaunch,
  gives the same.
- R7: after the game starts, a relaunch, then "New game" shows the names in order, the mode and the rule. After a
  rematch, a relaunch shows the rotated order.
- R9: a corrupted `saved_game` written before launch shows the message over a setup prefilled with a valid
  `last_setup` (R8). "OK" closes it. A relaunch shows no message and no summary, and the file is gone.
- R10, R8: a corrupted `last_setup` with a valid `saved_game` shows the summary on home, and the setup defaults
  after "New game" and "Delete".
- R4: resuming fires the turn-completed hook zero times.
- R4, Q5: an active Double modifier before recreation is inactive after it.
- R1, second sentence: with a saved game on disk, recreation with `wasOnGameScreen = false` from the home screen with
  the summary gives the home screen with the summary, not the game. Recreation from the setup screen (reached through
  "Delete") gives the home screen with no summary. Recreation from the end screen (after "Finish game") gives the
  home screen with no summary.
- R12, rotation (`ActivityScenario.recreate()`, which keeps the ViewModel): on a game in progress, on the home screen
  with the new-game dialog open, and on the setup screen with the restore-failed message. The screen and the dialog
  are unchanged. `load()` is not called again (the test `Persistence` counts loads), and no write happens (it counts
  writes).
- R2, layout: 4 players with 20-code-point names, in landscape. "Resume game" is reached with `performScrollTo` and
  is displayed. Each row's score node is fully displayed (its bounds are inside the card and not clipped).

`FrenchStringsTest` (Robolectric, `fr` locale; ux-reviewer U4): `home_saved_game_checkout` and `home_saved_game_score`
contain U+00A0 before the colon, and no plain space.

### Roborazzi captures (record only; portrait English, landscape English, portrait French, as the existing tests do)

- Home with "Resume game", 3 players mid-game.
- Home with "Resume game", 4 players with 20-code-point names (landscape English, portrait French).
- Home with "Resume game" at 200 % font scale (`@Config(fontScale = 2.0f)`, portrait English): the top of the
  screen, plus a capture after scrolling to "Resume game".
- Home with "Resume game", checkout to confirm.
- New-game confirmation dialog.
- Unreadable-save message over the setup screen.
- All existing captures (home, setup, game, end) are recorded again by the same build, since the build records every
  capture each time. The PR lists which ones changed: home and setup only, through the prefill.

### Local checks before review of code

- `./gradlew build :app:assembleDebug`: compile, every unit test (engine and app, Robolectric included), Android
  Lint, screenshots, debug APK.
- `aapt2 dump permissions`: no `INTERNET`.

## How Kosmo verifies on the phone (Pixel 7)

1. Uninstall the previous APK, then install the APK from the PR's latest commit.
2. Start a 501 game with 3 players. After 2 darts, force-stop the app (Settings > Apps > Kosmo Darts > Force
   stop). Reopen the app.
   - The home screen shows "Partie en cours" with 501 and the checkout rule, the 3 names and their scores, and
     "À <name> de lancer". Below are "Nouvelle partie", then « Reprendre la partie ».
   - Tap « Reprendre la partie ». Check the same player, the 2 darts and the scores, then use Undo back to the first
     dart (Undo then becomes grey).
3. Finish a turn so that "Joueur suivant" is waiting, force-stop, reopen, and resume. The turn is still waiting:
   the number keys are grey, and "Joueur suivant" works.
4. Reach a checkout so that the "Checkout !" pop-up is open, force-stop, and reopen.
   - The summary says "Checkout à confirmer : <name>". Resume: the pop-up is open.
   - Tap « Terminer la partie », then force-stop on the end screen, and reopen.
   - There is no « Reprendre la partie ». Tap "Nouvelle partie": the names (in the same order), 501 and the checkout
     rule are prefilled.
5. With a saved game, tap "Nouvelle partie" on the home screen. The question "Une partie est en cours" appears.
   - "Annuler" keeps the game: the summary is still there.
   - Tap "Nouvelle partie" again, then "Supprimer": the setup screen opens. Go back: nothing is left to resume.
6. Developer options > Background process limit > "No background processes". During a game, go to the home screen
   of the phone, open another app, then come back: the game is shown directly, without the summary. Reset the
   option afterwards.
7. Repeat step 2 in landscape. On the home screen in landscape, check that the summary is readable and that
   « Reprendre la partie » can be reached by scrolling.
8. During a game, press back and choose "Abandonner". The home screen no longer offers « Reprendre la partie »,
   even after a force stop.
9. Long names and large text: start a game with 4 players, one of them named with 20 letters (for example
   « Maximilien-Alexandre »), throw a few darts, then force-stop and reopen.
   - In portrait, then in landscape: every name is complete (it may wrap onto two lines), and every score is fully
     visible next to its name.
   - Settings > Display > Font size at the maximum: the same checks. « Reprendre la partie » is still reachable by
     scrolling. Reset the font size afterwards.
10. Rotation with a window open: on the home screen with a saved game, tap "Nouvelle partie" so that the question
    appears, then turn the phone. The question is still shown, and "Annuler" still keeps the game.
11. The phone is in French for all the steps above: check that every new text on the home screen and in the
    windows is in French, and that "Checkout à confirmer : <name>" never puts the ":" alone at the start of a line.

The unreadable-save cases cannot be triggered on the phone (no adb); the automated tests cover them, and their
screenshot is attached to the PR.

## Known limitations

- A write that fails (disk full) is not reported to the player. The previous save stays on disk, and the next action
  tries again.
- An action less than a few milliseconds before the process dies may not be on disk yet, since writes run in the
  background. The file then holds the previous action, never a broken mix.
- An older build that finds a save from a newer build shows the R9 message, and that game is lost.
- After process death on the setup screen, the setup being edited is not restored; the app comes back on the home
  screen.
- Beyond 10,000 darts, saving pauses (R12).
- If storage fails while reading the saved game (a hardware or system error, not a damaged file), the message of R9
  appears, but the game is kept and comes back at the next launch once the error is gone. If the error persists, the
  message appears at each launch (ADR 0002, decision 5).
- The unreadable-save message keeps the wording from the issue ("could not be resumed"). It does not say that the
  game was deleted (ux-reviewer U5, left to Kosmo).

## Design review, round 1: findings and where they are addressed

| Finding | Resolution in this plan or the ADRs |
|---|---|
| A1 | New ADR 0002 (Application-held `Persistence` and save thread; effects derived from state, with an exhaustive `savedGameOf`; synchronous load; R1 detection), with alternatives and consequences. |
| A2, S2 | ADR 0002 decision 3: main-thread read accepted, linear replay; test counts the game builds (no timing). |
| A3 | `ResumeSummary` reads `toGameScreenState()` highlight; test asserts equality. |
| A4 | Flag check reads the replayed engine state (ADR 0001). |
| S1 | ADR 0002 decision 5: `READ_FAILED` keeps the file; R9, R10 refined; tested in `SaveLoaderTest`. |
| S3 | No log of names or content (plan, Data section); checked at code review. |
| U1 | Layout rules and TalkBack for the summary card; 4-player, long-name, landscape and 200 % captures; scroll and clipping test. |
| U2 | Phone steps 7, 9, 10, 11. |
| U3 | TalkBack rules in "Home screen with a saved game". |
| U4 | Non-breaking spaces in French strings; `FrenchStringsTest`. |
| U5 | Not applied: owner wording from the issue; listed in Known limitations for Kosmo. |
| T1 | Recreation tests from home, setup and end screens. |
| T2 | `SaveLoader` is pure; `SaveLoaderTest`. |
| T3 | Real write failure in `AtomicFileStoreTest`. |
| T4 | Limit recovery and delete-over-limit in `SaveWriterTest`. |
| T5 | 4-player captures; existing captures re-recorded and listed. |
| T6 | Rotation tests: no reload, no write, dialogs kept. |
| T7 | Fixed seed; hex input in the failure message; made-up names only. |
| T8 (round 2, minor) | Repeated `READ_FAILED` loads and the later overwrite, in `SaveLoaderTest`. |

## Open questions

None. All product questions were answered by Kosmo on issue #30 (Q1 to Q8).
