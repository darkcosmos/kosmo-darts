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
- Placement (VT9): in `CurrentPlayerCard`, in the remaining score row, after the remaining (and after the BUST badge
  when shown); in `PlayerRow`, after the remaining score. The remaining keeps its place; the name keeps `weight(1f)`
  and its ellipsis, so it is the only element that shrinks.
- Fixed size (VT11): the box width is the widest of `BUST` and `180` measured with `rememberTextMeasurer` in the
  box's text style, plus padding. It is computed once per style and font scale, so it never changes with the value.
  The text is centered, one line, no ellipsis.
- Colors (VT10): in the card, the card's `onPrimaryContainer` for text and border (like `DartSlots`); in a row, the
  row's existing `onBackground` text and `onSurfaceVariant` border. No new color.

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
- **Rebase `LastTurnScore` on `VisitTotals.lastCompleted`**: it would remove a near-duplicate, but it changes
  validated code outside the issue. Left for a later cleanup; the reviewers may ask for it.
- **Fixed box width in dp**: rejected. It breaks at large font sizes; measuring the reference texts follows the font
  scale.

## Files and types added or changed

Added:
- `docs/rules/visit-total.md` (VT1 to VT12), `docs/plans/36-visit-total.md`.
- `game-engine/src/main/kotlin/io/github/darkcosmos/kosmodarts/engine/x01/VisitTotal.kt`: `VisitTotal`, `VisitTotals`.
- `game-engine/src/test/kotlin/io/github/darkcosmos/kosmodarts/engine/x01/VisitTotalsTest.kt`.
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/VisitBox.kt`: `VisitBoxText`, `visitBoxTexts`.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/game/VisitBoxTextTest.kt`.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/VisitTotalScreensTest.kt` (English) and
  `VisitTotalFrenchScreensTest.kt` (French).
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/resume/VisitTotalResumeTest.kt`.
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/VisitTotalScreenshotTest.kt`.

Changed:
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/GameScreen.kt`: the box, placement, sizing.
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/game/GameTags.kt`: `visitTotal(playerIndex)`.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`: four descriptions, one value format.

Unchanged: the save format and codec, `GameScreenState`, `LastTurnScore`, the theme, the build files. No dependency
is added: Robolectric 4.17 and Roborazzi 1.76.0 are already in the catalog.

Housekeeping (not a commit): the issue #30 leftover file under `app/src/main/kotlin/.../ui/game/.git/squad/` is not
tracked by git. I leave it in place: Kosmo's comment on the issue does not mention it, and I need his explicit
agreement before deleting it.

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
| Game start | `0`, "Current turn: 0" | `0`, "Not played yet" |
| After 20, 15, 15 | 20, 35, 50, "Current turn: N" | unchanged |
| Normal turn waiting for Next player | its total, "Last turn: N" | next player keeps his last visit or "Not played yet" (Q3) |
| Bust waiting | `BUST`, "Last turn: bust", next to the existing BUST badge | as above |
| After "Next player" | new thrower: `0`, "Current turn: 0" | previous thrower keeps his total or `BUST` |
| Checkout dialog | winner: total of the winning turn | others: last visit, bust or not played |
| Undo that reopens a turn | the reopened turn's running total | the next player is back to his previous state |

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
- VT3: next round: player 1 keeps `LastVisit(50)` while players 2 and 3 throw, then `InProgress(0)` on his turn.
- VT6: `lastCompleted` for each case: no turn, normal, bust, checkout, and it ignores a turn in progress.
- VT7: undo in the turn in progress: `InProgress` drops by the undone dart.
- VT7: undo that reopens the previous player's turn (removes his third dart): he is back to `InProgress` of his
  first two darts, the next player is back to `NotPlayed` (or to his earlier `LastVisit` in a later round).
- VT7: undo of a bust dart: the thrower is back to `InProgress` of the darts before it.
- VT7: restored game: `X01Game(config, darts)` built from the same darts gives the same list (replay, X01 rule P1).
- VT1: 1-player and 4-player games give one entry per player.

### `:app` pure JVM (JUnit 4), `VisitBoxTextTest`

- VT8: each of the four states maps to its value resource, description resource and arguments.
- VT6: waiting normal turn and waiting bust: the next player (engine current) shows `lastCompleted`, not
  `InProgress(0)`; after `nextPlayer()`, `InProgress(0)`.
- VT6: one-player game waiting: the box shows the waiting turn's total.
- VT6: undo of a waiting turn: the thrower shows `InProgress` of his remaining darts.
- VT6: checkout dialog: the winner shows `LastVisit`.

### Robolectric (`MainActivity`, Compose UI test, as `X01ScreensTest`)

`VisitTotalScreensTest` (English) follows the issue's phone scenario with three players:
- VT2, VT5, VT8: start: texts `0`, `0`, `0`; descriptions "Current turn: 0", "Not played yet", "Not played yet".
- VT2: 20, 15, 15: box 1 shows 20, 35, 50.
- VT6: waiting turn: box 2 still "Not played yet"; after Next player, box 1 "Last turn: 50", box 2 "Current turn: 0".
- VT4: player 2 busts: `BUST`, "Last turn: bust", remaining unchanged.
- VT1: player 3 throws a miss: box 3 stays `0`, "Current turn: 0".
- VT7: undo until player 2's turn reopens: box 2 shows his running total again.
- VT3: checkout: the winner's box shows the winning total behind the dialog.

`VisitTotalFrenchScreensTest` (`@Config(qualifiers = "fr")`): the same states give `BUST` and the four French
descriptions, with the non-breaking space before the colon.

`VisitTotalResumeTest` (`AppProcess`, as `PersistenceScreensTest`), VT7. Each test reads all boxes (text and
description) before and after, and compares the lists:
- rotation in the middle of a turn, and during a waiting bust;
- process death and return (R1) during a waiting turn (Q3 case);
- fresh launch and "Resume" from home (R2).

Layout tests (VT11), English, 4 players with 20-character names, portrait and landscape, at font scale 1.0 and 2.0:
- every box and every remaining score is fully inside the root bounds;
- no box text has a visual overflow (`SemanticsActions.GetTextLayoutResult`);
- a box has the same width and height when it shows `BUST` and when it shows `180`.

### Roborazzi captures (record only, as existing captures)

`VisitTotalScreenshotTest`, English portrait and landscape for each, French portrait for the first two:
- two players mid-turn; four players mid-turn (maximal number); a `BUST` box; a `180` box; game start (all `0`);
- four players in portrait at font scale 2.0.
Existing game screen captures change too, since every row gains a box.

### Canary (not committed; shown in the PR)

After committing, I break `SavedGame.toGameScreenState()` so that a resumed game loses its waiting flag, run
`VisitTotalResumeTest`, and show the failure (box 2 reads "Current turn: 0" instead of "Not played yet"). Then I
revert that file alone (`git checkout -- <file>`) and show the test passing again.

### Local checks

`./gradlew build :app:assembleDebug` (as CI): compile, engine and app unit tests (Robolectric and Roborazzi
included), lint.

## How Kosmo verifies on the phone (Pixel 7)

The issue's ten steps, with the owner answers:
1. Start a three-player game: every box shows 0.
2. Player 1: 20, 15, 15: his box shows 20, then 35, then 50. Player 2's box still shows 0.
3. "Joueur suivant": player 1 keeps 50, player 2 shows 0.
4. Player 2 busts: his box shows `BUST`, his remaining does not change, the big BUST badge is still there.
5. "Joueur suivant", player 3 throws a miss: his box stays 0.
6. Undo until player 2's turn reopens: his box shows his running total again.
7. Rotate, then leave and resume the game: the same values.
8. Score 180: the box shows 180 without overflowing, in portrait and landscape.
9. Four players: everything is readable in portrait and landscape.
10. TalkBack: each box reads "Volée en cours : …", "Dernière volée : …", "Dernière volée : bust" or
    "Pas encore joué".
11. (Q3) With a player who already has a last visit (for example 45): after the thrower's third dart, before tapping
    "Joueur suivant", that player's box still shows 45; it shows 0 once "Joueur suivant" is tapped.
12. (Q2) The "Préc." label is still under the average for every player.

## Open questions

- None for the product. Kosmo's issue comment does not mention the deletion of the untracked leftover file; I will
  delete it once main confirms that Kosmo agreed.
- For the reviewers: whether `LastTurnScore` should be rebased on `VisitTotals.lastCompleted` in this PR (see
  Alternatives). My default is no.
