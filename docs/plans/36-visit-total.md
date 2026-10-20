# Plan: visit total for each player on the game screen (issue #36)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/36

On the game screen, every player gets a framed box next to his remaining score with the total of his visit: the
running total while he throws (20, then 35, then 50), then the total of his last visit, or `BUST`. Display only:
nothing new is stored, the save file does not change.

Rules: `docs/rules/visit-total.md` (VT1 to VT12, new in this mission). The owner's answers of 2026-10-20 (Q1 to Q4)
are consolidated there and listed as deviations from the issue body in the PR.

## Scope and out of scope

In scope:
- Engine computation of the four states (VT1 to VT6) in `:game-engine`, with JVM tests.
- UI mapping from states to texts and descriptions (VT6, VT8, VT12), with the Q3 choice during a waiting turn.
- The box on the game screen, highlighted card and player rows (VT9 to VT11), English and French strings.
- Robolectric tests (texts, descriptions, rotation, resume), Roborazzi captures, the canary on the resume test.
- `docs/rules/visit-total.md`, this plan.

Out of scope (issue): voice announcement of the total, averages or statistics, history of earlier visits, general
visual redesign. Out of scope (owner answers): light theme (Q1); removing or changing "Prev." / « Préc. » (Q2).

Not done here, on purpose: `ui/game/LastTurnScore.kt` (the "Prev." label, PR #28) also reads the last completed
turn. It stays unchanged to keep this PR minimal (see Alternatives).

## Approach

### 1. Engine (`:game-engine`, pure Kotlin)

New file `game-engine/src/main/kotlin/io/github/darkcosmos/kosmodarts/engine/x01/VisitTotal.kt`:

```kotlin
/** What a player's visit total box shows (docs/rules/visit-total.md, VT1). */
sealed interface VisitTotal {
    data object NotPlayed : VisitTotal                       // VT5
    data class InProgress(val points: Int) : VisitTotal      // VT2
    data class LastVisit(val points: Int) : VisitTotal       // VT3
    data object Bust : VisitTotal                            // VT4
}

object VisitTotals {
    /** VT1 to VT5: one state per player, in configuration order. */
    fun of(state: X01State): List<VisitTotal>

    /** VT6: the player's last completed visit (NotPlayed, LastVisit or Bust), ignoring the turn in progress. */
    fun lastCompleted(state: X01State, playerIndex: Int): VisitTotal
}
```

- `of`: for `currentPlayerIndex`, `InProgress(currentTurnDarts.sumOf { it.value })`; for every other player,
  `lastCompleted`.
- `lastCompleted`: the last element of `completedTurns` with that `playerIndex`. None: `NotPlayed`. `BUST`: `Bust`.
  `NORMAL` or `CHECKOUT`: `LastVisit(darts.sumOf { it.value })`.
- It only reads `X01State` (X01 rules A2, A3): no new field, no change to the replay, the save or undo. A finished
  leg has no current player, so the winner gets `LastVisit` of his winning turn (VT3).

### 2. UI mapping (`:app`, no composable)

New file `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/VisitBox.kt`:

```kotlin
/** Text of one visit total box (VT8). Composables resolve it with stringResource only (VT12). */
data class VisitBoxText(
    @StringRes val valueRes: Int,          // R.string.game_visit_value ("%1$d") or R.string.game_bust
    @StringRes val descriptionRes: Int,    // one of the four game_visit_*_description strings
    val formatArgs: List<Int>,             // [points], or empty for not played and bust
)

/** One per player, in configuration order (VT6, VT8). */
fun visitBoxTexts(screen: GameScreenState): List<VisitBoxText>
```

- The state shown: `VisitTotals.of(state)[i]`, except while a turn waits (`isAwaitingConfirmation`) for the engine
  current player, who gets `VisitTotals.lastCompleted(state, i)` (VT6, Q3). With one player, the engine current
  player is the thrower himself: `lastCompleted` returns his waiting turn, which is the expected value.
- `NotPlayed` maps to the value `0` with the "Not played yet" description (VT8).
- Same pattern as `ResumeSummary` and `GameScreenState`: pure, tested on the JVM without Robolectric.

### 3. Game screen (`GameScreen.kt`)

- `Scoreboard` computes `visitBoxTexts(game)` once and passes each player's entry down.
- New private composable `VisitTotalBox(text, index, style, borderColor)`: a `Surface` framed like the existing
  `DartSlots` (border 1 dp, `shapes.small`), with `testTag(GameTags.visitTotal(index))` and
  `clearAndSetSemantics { contentDescription = ... }`. Not clickable, so no 48 dp touch target is needed.
- Box text style (U3): `titleLarge` in the highlighted card (the style of the dart slots); `titleMedium` in a player
  row (the style of the row's name). Each box measures `BUST` and `180` in its own style.
- Fixed size (VT11): the box width is the widest of `BUST` and `180` measured with `rememberTextMeasurer` in the
  box's style, plus padding. It depends only on the style and the font scale, never on the value shown. The text
  is centered, on one line, with no ellipsis.
- Colors (VT10): in the card, the card's `onPrimaryContainer` for text and border (like `DartSlots`); in a row, the
  row's existing `onBackground` text and `onSurfaceVariant` border. No new color.

#### Placement and narrow widths (VT9, VT11; findings U1, U2)

The box is always just after the remaining score, on its right or, when the width runs out, directly below it.
Whether a line is "too narrow" is decided at layout time only, from measured widths. The thresholds are named
constants of `GameScreen.kt`, never game rules, and nothing is stored (architecture review).

Named constants (UI layer):
- `NAME_MIN_REFERENCE = "Mmmmmm"`: a player name always gets at least the width of these 6 characters, measured in
  the name's style. With the ellipsis, at least the first 5 letters of a long name stay visible.
- `REMAINING_REFERENCE = "888"`: the remaining score is laid out for the widest 3-digit score, measured in its
  style. The layout therefore does not switch while the score goes down. The remaining text has `maxLines = 1` and
  `softWrap = false`.

**Player row** (`PlayerRow`):
- One line when the measured widths fit: `name (weight, min NAME_MIN_REFERENCE) | stats (average and "Prev.") |
  remaining | box`.
- Two lines otherwise, which happens at large font sizes and in landscape with half the screen. Line 1:
  `name (weight, min NAME_MIN_REFERENCE) | remaining | box`. Line 2: the stats column, under the name and aligned to
  the start. "Prev." stays visible (Q2); only its position changes.

**Highlighted card score line** (`CurrentPlayerCard`):
- One line when it fits: `remaining (REMAINING_REFERENCE) | BUST badge (only while a bust waits) | box`.
- Otherwise the remaining keeps the whole first line, and the BUST badge and the box move to a second line, aligned
  to the end, just below it. The remaining is never squeezed, wrapped or clipped.
- The name row of the card (name and stats) is unchanged. It is not part of the issue, and its name is the only
  name on its line.

Both use one private layout helper, `OneOrTwoLines(first, trailing, minFirstWidth)`. It is a `Layout` that places
`trailing` after `first` when `minFirstWidth + trailing width + spacing <= maxWidth`, and under it otherwise. It
contains no game logic.

At the worst case (4 players, 20-character names, font scale 2.0, Pixel 7 portrait 412 dp and landscape scoreboard of
about 430 dp), my estimates in the row two-line layout are: name ≥ about 100 dp, remaining about 85 dp, box about 100
dp. They fit in both orientations; the layout tests below check it.

TalkBack order (U5), which follows the screen from top to bottom and then from start to end, checked by a
semantics test:
- Row on one line: name, average, "Prev.", remaining, box. Row on two lines: name, remaining, box, average, "Prev.".
- Card: "<name> to throw" heading, average, "Prev.", remaining, BUST badge (while shown), box, dart slots.
The box description does not repeat the player name: the name is read just before, on the same row or card.

### 4. Strings (`values/` and `values-fr/`)

| Name | English | French |
|---|---|---|
| `game_visit_value` (`translatable="false"`) | `%1$d` | (default only) |
| `game_visit_in_progress_description` | Current turn: %1$d | Volée en cours : %1$d |
| `game_visit_last_description` | Last turn: %1$d | Dernière volée : %1$d |
| `game_visit_last_bust_description` | Last turn: bust | Dernière volée : bust |
| `game_visit_not_played_description` | Not played yet | Pas encore joué |

`BUST` reuses the existing `game_bust`. English says "turn", like the existing English texts ("Prev.", "your turn").

### Alternatives considered

- **Engine takes the waiting flag** (`VisitTotals.of(state, isWaiting)`): rejected. "Waiting for Next player" is a
  screen concept (PR #28); the engine knows nothing about it. The engine exposes both states and the app picks (Q3).
- **Store the visit total in `X01PlayerState`**: rejected. It duplicates what `completedTurns` and
  `currentTurnDarts` already give, and the issue forbids stored duplicates.
- **Rebase `LastTurnScore` on `VisitTotals.lastCompleted`** (A1): it would remove a near-duplicate, but it changes
  validated code outside the issue. It is left for a follow-up. In this PR, one test proves that both give the same
  points for normal and checkout turns (Test plan), so they cannot diverge unnoticed.
- **Fixed box width in dp**: rejected. It breaks at large font sizes; measuring the reference texts follows the font
  scale.
- **Shrinking text or hiding "Prev." when the row is narrow** (U1): rejected. Shrinking hurts readability, and Q2
  keeps "Prev.". The stats move to a second line instead.

## Files and types added or changed

Added:
- `docs/rules/visit-total.md` (VT1 to VT12), `docs/plans/36-visit-total.md`.
- `game-engine/src/main/kotlin/io/github/darkcosmos/kosmodarts/engine/x01/VisitTotal.kt`: `VisitTotal`, `VisitTotals`.
- `game-engine/src/test/kotlin/io/github/darkcosmos/kosmodarts/engine/x01/VisitTotalsTest.kt`.
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/VisitBox.kt`: `VisitBoxText`, `visitBoxTexts`.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/game/VisitBoxTextTest.kt`.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/VisitTotalScreensTest.kt` (English),
  `VisitTotalFrenchScreensTest.kt` (French), `VisitTotalLayoutTest.kt` (layout and TalkBack order).
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/resume/VisitTotalResumeTest.kt`.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/VisitTotalScreenshotTest.kt`.

Changed:
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/GameScreen.kt`: the box, `OneOrTwoLines`, placement,
  sizing, named constants.
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/GameTags.kt`: `visitTotal(playerIndex)`,
  `playerName(playerIndex)`, `remaining(playerIndex)` (layout tests).
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`: four descriptions, one value format.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/game/LastTurnScoreTest.kt`: the A1 consistency test.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/X01ScreensScreenshotTest.kt`: one new capture
  (waiting bust, 4 players, font scale 2.0).

Unchanged: the save format and codec, `GameScreenState`, `LastTurnScore.kt`, the theme, the build files. No
dependency is added: Robolectric 4.17 and Roborazzi 1.76.0 are already in the catalog.

Housekeeping: the untracked issue #30 leftover under `app/src/main/kotlin/.../ui/game/.git/` has been deleted, with
Kosmo's agreement (2026-10-20). It was never tracked, so no commit changes. Before each review and before the PR, I
check that the diff has nothing under `ui/game/.git` (S1): `git diff --name-only origin/main...HEAD | grep '/\.git/'`
must print nothing.

## Data read, stored or exposed

- Read: the existing `X01State` (completed turns, darts of the turn in progress, current player) and the existing
  `isAwaitingConfirmation` flag.
- Stored: nothing new. The save file (`SavedGame`, ADR 0001) is unchanged.
- Exposed: nothing outside the screen. TalkBack reads the descriptions; they contain only scores.
- Permissions, exported components, untrusted input: unchanged.

## Screens and states

Game screen only, portrait (scores above the keypad) and landscape (keypad beside the scores).

| Situation | Highlighted card box | Other players' boxes |
|---|---|---|
| Game start, new game or rematch | `0`, "Current turn: 0" | `0`, "Not played yet" |
| After 20, 15, 15 | 20, 35, 50, "Current turn: N" | unchanged |
| Normal turn waiting for Next player | its total, "Last turn: N" | next player keeps his last visit or "Not played yet" (Q3) |
| Bust waiting | `BUST`, "Last turn: bust", next to the existing BUST badge | as above |
| After "Next player" | new thrower: `0`, "Current turn: 0" | previous thrower keeps his total or `BUST` |
| Checkout dialog | winner: total of the winning turn | others: last visit, bust or not played |
| Undo that reopens a turn | the reopened turn's running total | the next player is back to his previous state |

Narrow widths (large font, landscape): see section 3, "Placement and narrow widths".

## Test plan

Every test name or KDoc cites its VT rule.

### `:game-engine` (JUnit 6), `VisitTotalsTest`

Three players, 501, double out, unless stated otherwise.
- VT2, VT5: game start: player 1 `InProgress(0)`, players 2 and 3 `NotPlayed`.
- VT2: after 20, then 15, then 15: `InProgress(20)`, `(35)`; after the third dart player 1 is `LastVisit(50)` and
  player 2 `InProgress(0)`.
- VT1: a miss counts 0: 20, MISS gives `InProgress(20)`; a whole turn of misses gives `LastVisit(0)`.
- VT4: bust after T20, T20 on 101 left gives `Bust` for the thrower (with points scored before the bust dart).
- VT3: winning turn: winner `LastVisit(points)`, no current player, others keep their states.
- VT3 (T1 b): 301 single out, a checkout on the first dart of a turn and another on the second dart: the winner is
  `LastVisit` of the sum of those 1 or 2 darts.
- VT3: next round: player 1 keeps `LastVisit(50)` while players 2 and 3 throw, then `InProgress(0)` on his turn.
- VT6: `lastCompleted` for each case: no turn, normal, bust, checkout, and it ignores a turn in progress.
- VT7: undo in the turn in progress: `InProgress` drops by the undone dart.
- VT7: undo that reopens the previous player's turn (removes his third dart): he is back to `InProgress` of his
  first two darts, the next player is back to `NotPlayed` (or to his earlier `LastVisit` in a later round).
- VT7: undo of a bust dart: the thrower is back to `InProgress` of the darts before it.
- VT7: restored game: `X01Game(config, darts)` built from the same darts gives the same list (replay, X01 rule P1).
- VT1, VT2, VT4 (T1 c): one-player game with a bust. The only player is `InProgress` again right after his bust
  (he is the next thrower), and `lastCompleted` gives `Bust`.
- VT1, VT3, VT4, VT5 (T1 c): four-player game. After player 1's turn: `LastVisit`, `InProgress(0)`, `NotPlayed`,
  `NotPlayed`. After player 2's turn and a bust by player 3: `LastVisit`, `LastVisit`, `Bust`, `InProgress(0)`.

### `:app` pure JVM (JUnit 4)

`VisitBoxTextTest`:
- VT8: each of the four states maps to its value resource, description resource and arguments.
- VT6: waiting normal turn and waiting bust: the next player (engine current) shows `lastCompleted`, not
  `InProgress(0)`; after `nextPlayer()`, `InProgress(0)`.
- VT6: one-player game waiting: the box shows the waiting turn's total.
- VT6: undo of a waiting turn: the thrower shows `InProgress` of his remaining darts.
- VT6: checkout dialog: the winner shows `LastVisit`.

Restart (T1 a), in `AppReducerTest`, through the existing reducer path (no new reset code): from a game in its
second round, `AppEvent.Rematch` from the end screen, and `NewGame` then Start from setup. In both cases
`visitBoxTexts` on the resulting game screen gives "Current turn: 0" for the new first player and "Not played yet"
for the others (VT2, VT5).

`LastTurnScoreTest` (A1): for normal turns and a checkout turn, `lastTurnScores` and `VisitTotals.lastCompleted`
give the same points, and both give bust and none for the same players.

### Robolectric (`MainActivity`, Compose UI test, as `X01ScreensTest`)

`VisitTotalScreensTest` (English) follows the issue's phone scenario with three players:
- VT2, VT5, VT8: start: texts `0`, `0`, `0`; descriptions "Current turn: 0", "Not played yet", "Not played yet".
- VT2: 20, 15, 15: box 1 shows 20, 35, 50.
- VT6: waiting turn: box 2 still "Not played yet"; after Next player, box 1 "Last turn: 50", box 2 "Current turn: 0".
- VT6, VT7 (T3): after the third dart (waiting), undo once: box 1 is back to "Current turn: 35", box 2 back to
  "Not played yet".
- VT4: player 2 busts: `BUST`, "Last turn: bust", remaining unchanged.
- VT1: player 3 throws a miss: box 3 stays `0`, "Current turn: 0".
- VT7: undo until player 2's turn reopens: box 2 shows his running total again.
- VT3: checkout: the winner's box shows the winning total behind the dialog.
- VT2, VT5 (T1 a): "Finish game", then "Rematch" on the end screen: all boxes are back to `0`, "Current turn: 0"
  for the new first player and "Not played yet" for the others.

`VisitTotalFrenchScreensTest` (`@Config(qualifiers = "fr")`): the same states give `BUST` and the four French
descriptions, with the non-breaking space before the colon.

`VisitTotalResumeTest` (`AppProcess`, as `PersistenceScreensTest`), VT7. Each test checks that there is one box per
player, asserts the explicit expected text and description of every box before the event, and asserts the same
explicit values after it (T2):
- rotation in the middle of a turn (box 1 "Current turn: 35"), and during a waiting bust (`BUST`, "Last turn: bust");
- process death and return (R1) during a waiting turn, Q3 case: box 1 "Last turn: 50", box 2 "Not played yet";
- in a second round, process death during a waiting turn: the next player keeps "Last turn: 45" (Q3);
- fresh launch and "Resume" from home (R2).

`VisitTotalLayoutTest` (VT11, U1, U2, U5). The game is reached by ViewModel events, as in the screenshot tests. It
uses 4 players with 20-character names, in portrait and landscape (Pixel 7 qualifiers), at font scale 1.0 and 2.0:
- every box and every remaining score is fully inside the root bounds;
- no box text and no remaining text has a visual overflow, and each remaining text is on one line
  (`SemanticsActions.GetTextLayoutResult`: `lineCount == 1`, `!hasVisualOverflow`);
- every player name node is at least as wide as `NAME_MIN_REFERENCE` measured in its style (U1);
- a box has the same width and height when it shows `BUST` and when it shows `180`;
- waiting bust in the highlighted card, 4 players, font scale 2.0, portrait and landscape: the remaining is complete
  ("501"-like three digits on one line, inside the card) and the BUST badge and the box are inside the card (U2);
- TalkBack order (U5): the traversal order of the row (one-line and two-line cases) and of the card matches
  section 3.

### Roborazzi captures (record only, as existing captures)

`VisitTotalScreenshotTest`, English portrait and landscape for each, French portrait for the first two:
- two players mid-turn; four players mid-turn (maximal number); a `BUST` box; a `180` box; game start (all `0`);
- four players at font scale 2.0, in portrait and in landscape (row two-line layout);
- waiting bust with four players at font scale 2.0, in portrait and in landscape (card two-line layout, U2).

The existing game screen captures (`X01ScreensScreenshotTest`, `PersistenceScreenshotTest`,
`AnnouncementsScreenshotTest`) are re-recorded by the same build, in the same PR, since every row gains a box (T4).
The repository commits no capture (none is tracked today): captures are build outputs (`app/build/outputs/roborazzi`),
so "re-recorded" means regenerated by the build of the reviewed head. The code-phase review material
gives the list of capture files and their path, and says which ones are new and which existing ones change, so that
the reviewers check each changed image.

### Canary (not committed; shown in the PR)

After committing, I break `SavedGame.toGameScreenState()` so that a resumed game loses its waiting flag, run
`VisitTotalResumeTest`, and show the failure (box 2 reads "Current turn: 0" instead of "Not played yet"). Then I
revert that file alone (`git checkout -- <file>`) and show the test passing again.

### Local checks

`./gradlew build :app:assembleDebug` (as CI): compile, engine and app unit tests (Robolectric and Roborazzi
included), lint.

## How Kosmo verifies on the phone (Pixel 7)

The issue's ten steps (1 to 10), then four steps for the owner answers and the review findings:
1. Start a three-player game: every box shows 0.
2. Player 1: 20, 15, 15: his box shows 20, then 35, then 50. Player 2's box still shows 0. At arm's length, can you
   tell the box from the remaining score at a glance? (U4: the box has no visible label; tell us if this is a
   problem.)
3. "Joueur suivant": player 1 keeps 50, player 2 shows 0.
4. Player 2 busts: his box shows `BUST`, his remaining does not change, the big BUST badge is still there.
5. "Joueur suivant", player 3 throws a miss: his box stays 0.
6. Undo until player 2's turn reopens: his box shows his running total again.
7. Rotate, then leave and resume the game: the same values.
8. Score 180: the box shows 180 without overflowing, in portrait and landscape.
9. Four players: everything is readable in portrait and landscape.
10. TalkBack: each box reads "Volée en cours : …", "Dernière volée : …", "Dernière volée : bust" or
    "Pas encore joué", right after the player's name and scores.
11. (Q3) With a player who already has a last visit (for example 45): after the thrower's third dart, before tapping
    "Joueur suivant", that player's box still shows 45; it shows 0 once "Joueur suivant" is tapped.
12. (Q2) The "Préc." label is still there for every player.
13. (U6) Largest font size (Settings, Display, Font size at maximum), four players with long names, portrait and
    landscape: names stay readable (at least their first letters), no box and no remaining score is cut. When space
    is short, the average and "Préc." go on a second line under the name.
14. Finish a game, then "Revanche": every box shows 0 again.

## Known limitations (for the PR)

- (U4) The box shows a bare number with no visible label: "0" looks the same for "turn in progress, no dart yet" and
  "not played yet", and "50" next to "451" could be read as a second score. Only TalkBack says which is which. A
  visible label would change the design chosen in the issue (T1, T2) and is a product decision, not taken here.
- Player names longer than the space available are still cut with an ellipsis, as today; at least their first
  letters stay visible (VT11).
- No light theme: contrast is checked in the dark theme only (Q1).

## Open questions

- None for the product. U4 (visible label) is listed in Known limitations and asked in step 2 of the phone
  verification; the decision stays with Kosmo.
- `LastTurnScore` stays unchanged in this PR; a follow-up issue can rebase it on `VisitTotals.lastCompleted` (A1).

## Owner review of PR #37 (PR37-R1, PR37-R2, 2026-10-20)

Kosmo's review of PR #37 (CHANGES_REQUESTED), recorded in `docs/rules/visit-total.md` as PR37-R1 and PR37-R2 (his
R1 and R2, renamed so that they never mix with the persistence rules R1 to R12):

- "No readability or visibility problem": U4 is settled. The box keeps no visible label.
- PR37-R1: remove "Prev." / « Préc. »: it is redundant with the box. This replaces Q2.
- PR37-R2: while a bust waits for "Next player", only the big BUST badge is shown in the thrower's card: no box. After
  "Next player", the current behaviour stays: his row box shows `BUST`.

This section prevails over the sections above where they differ.

### PR37-R1: removing "Prev."

- `GameScreen.kt`:
  - `PlayerStats` shows only the average (one line). It loses its `lastTurn` parameter and becomes `PlayerAverage`.
  - `Scoreboard` no longer computes `lastTurnScores`. `lastTurnText` is deleted.
- Deleted:
  - `ui/game/LastTurnScore.kt` and its test `LastTurnScoreTest.kt`. The A1 consistency test goes with it: the
    duplicate it guarded no longer exists, which also closes the A1 follow-up.
  - `GameTags.lastTurnScore`.
  - The strings `game_last_turn_score` (`values/` and `values-fr/`) and `game_last_turn_none` (`values/`, not
    translatable). `game_bust` stays: the badge and the box use it.
- The `OneOrTwoLines` fallback of a row stays, with only the average as its trailing part. At the worst case (4 long
  names, scale 2.0, landscape), the average goes under the name; the name keeps `NAME_MIN_REFERENCE`.
- TalkBack order of a row: name, average, remaining, box (`ROW_ORDER_STATS` is renamed `ROW_ORDER_AVERAGE`).
- `X01ScreensTest`, existing tests that check "Prev.":
  - `eachPlayerRowShowsTheirPreviousTurnScoreBustOrADash` becomes
    `eachPlayerRowShowsTheTotalOfTheirLastTurnBustOrZero`. Same game; it now checks the boxes:
    - before any turn, `0` and "Not played yet" (player 1: "Current turn: 0");
    - at the end, `BUST`, `6` and `180` with their "Last turn" descriptions;
    - and that no "Prev." text exists (the `game_last_turn_score` string is deleted, so the check is that no node
      tagged with the old tag exists).
  - Its other "Prev." assertions (lines 209, 236 and 425 today) are replaced by the same check on the player's box.
    The value checked stays the same (45, BUST and 60). Where the BUST is still waiting there (PR37-R2), the test
    checks the badge and that the box is absent, then the box after "Next player". Nothing is removed without an
    equivalent check.
- `VisitTotalLayoutTest`:
  - the two-line check reads the average node (new tag `GameTags.average(index)`) instead of "Prev.";
  - the TalkBack order test checks name < average < remaining < box.

### PR37-R2: no box while a bust waits

- Engine: unchanged. The thrower is still in *bust* (VT4); hiding the box is a display rule (VT13).
- Mapping (`VisitBoxText.kt`, VT12, VT13): `visitBoxTexts(screen)` returns `List<VisitBoxText?>`. The entry is `null`
  for the thrower of a waiting bust (`screen.isBustShown` and `player == screen.pendingTurn.playerIndex`), otherwise
  as today. With one player, the same rule applies: no box until "Next player".
- `CardScoreLine`: the box is drawn only when its text is not `null`. The badge and the box are never shown together
  any more. The `OneOrTwoLines` fallback of the card stays: at scale 2.0 in landscape, the BUST badge alone may still
  go under the remaining score. The remaining score keeps the whole first line (U2 unchanged).
- TalkBack: during a waiting bust, the card reads name, average, remaining, BUST badge (already announced by its
  live region), dart slots. There is no box description; the badge says BUST.
- Tests:
  - `VisitBoxTextTest`:
    - waiting bust: `[null, lastVisit(3)]`;
    - after `nextPlayer()`: `[bust, inProgress(0)]`;
    - undo of the bust dart: `inProgress` of the darts before it;
    - one player waiting a bust: `[null]`, then `[inProgress(0)]`.
  - `VisitTotalScreensTest` (issue scenario):
    - after Bob's bust, his box does not exist and the badge is shown;
    - after "Next player", his box is `BUST`, "Last turn: bust";
    - the undo steps are unchanged.
  - `VisitTotalFrenchScreensTest`: the bust is checked after "Next player" (`BUST`, « Dernière volée : bust »). While
    it waits, no box.
  - `VisitTotalResumeTest`, rotation during a waiting bust: box 0 does not exist, the badge is shown, and box 1 is
    "Last turn: 3", before and after. The VT7 waiting-turn tests and the canary are unchanged.
  - `VisitTotalLayoutTest`, waiting bust:
    - remaining complete on one line, badge inside the card, no box in the card;
    - box size: the row boxes compare `BUST` (after "Next player"), `180`, `3` and `0`;
    - the card box compares a waiting `180` with an in-progress value;
    - TalkBack order of the card: name < remaining < badge < dart slots during a waiting bust, and name < remaining <
      box < dart slots otherwise.
- Captures (`VisitTotalScreenshotTest`, record only):
  - `visit_total_bust_*` now shows the card with the badge only;
  - new `visit_total_bust_after_next_portrait_en` and `_landscape_en`: the busted player's row shows `BUST`;
  - the `_font200` waiting-bust captures show the badge alone.
  - Existing captures that show "Prev." all change: every game screen capture of `X01ScreensScreenshotTest`,
    `PersistenceScreenshotTest` and `AnnouncementsScreenshotTest`.

### Phone verification (replaces steps 4, 12 and 13 above)

4. Second player: bust. While "Joueur suivant" waits, his card shows the big BUST badge and **no** box; his remaining
   does not change. After "Joueur suivant", his row box shows `BUST`.
12. « Préc. » is no longer shown anywhere: neither in the rows nor in the card.
13. Largest font, 4 long names, portrait and landscape:
    - names readable;
    - no box or remaining score cut;
    - when space is short, the average goes under the name;
    - during a waiting bust, the BUST badge may go under the remaining score.

### Known limitations update

U4 (label) is settled by Kosmo, so it leaves the known limitations. The PR text also drops "Préc." from steps 2, 10
and 13 and from the U8 question, which no longer applies.

### Open questions for Kosmo

- None blocking. One reading to confirm: "supprimer Préc." is applied everywhere, in the player rows **and** in the
  card of the player who throws. Kosmo's text does not say "rows only", so the plan removes both.
