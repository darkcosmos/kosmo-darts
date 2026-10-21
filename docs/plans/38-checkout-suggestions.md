# Plan: checkout suggestion on the current player's card (issue #38)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/38

During an X01 game, the highlighted (current player) card shows one checkout route: the darts that finish the leg
with the darts left in the turn, under the game's checkout rule. The route is computed by a deterministic algorithm in
`:game-engine` (no fixed table), recomputed after every dart and every undo.

Owner answers (issue comment, 2026-10-21), which complete or replace the issue body:

| Id | Decision |
|---|---|
| Q1 | At equal value, prefer the easiest dart: single, then double, then triple. |
| Q2 | A translated label, "Checkout: " / « Sortie : », placed under the remaining score. |
| Q3 | The suggestion line is always reserved, empty when there is no suggestion: nothing moves. |
| Q4 | TalkBack reads e.g. « Sortie : triple 20, bull », with the localized words of the voice announcements. |
| Q5 | A single is written without "S", like the dart slots (`20 · D20`, `1 · D1`). Otherwise `T20`, `D16`, `25`, `Bull`, separator ` · `. |

## Scope and out of scope

In scope:
- Engine: route search for a remaining score, a number of darts left (1 to 3) and a checkout rule; the suggestion of
  a game for its engine current player; AN3 (`Checkouts.isPossible`) backed by the same search.
- Rules document `docs/rules/checkout-suggestions.md` (rule ids CS1 to CS9 below).
- App: show/hide decision in `GameScreenState`, a reserved suggestion line in the highlighted card, strings in
  `values/` and `values-fr/`, TalkBack description.
- Tests: engine JVM tests (reference table, rule cases, exhaustive brute force), app JVM and Robolectric tests,
  Roborazzi captures.

Out of scope (issue): placement advice when no checkout exists, voice announcement of the route, per-player routes,
any setting, any persistence change. Also out of scope: suggestions in non-highlighted player rows.

## Rules (to be written in `docs/rules/checkout-suggestions.md`)

Vocabulary: *scoring dart* = any dart of rule D1 except a miss (singles, doubles, triples of 1 to 20, 25, Bull).
*Finishing dart* = in double out a double or the Bull (D3); in single out any scoring dart.

- CS1. A *route* for a remaining score R and k darts left (1 <= k <= 3) is a sequence of 1 to k scoring darts whose
  values total exactly R and whose last dart is a finishing dart. Misses are never part of a route.
- CS2. A suggestion exists if and only if at least one route exists. No suggestion for R < 1, for k = 0, or when no
  route exists (e.g. 101 with 1 dart, 159 with 3 darts in double out, 1 in double out).
- CS3. Among routes, the suggestion is the first one in this order:
  1. fewest darts;
  2. in double out only, last dart by preference: D20, D16, D8, D18, D12, D10, then the other doubles from highest to
     lowest (D19, D17, D15, D14, D13, D11, D9, D7, D6, D5, D4, D3, D2, D1), the Bull last;
  3. the preceding darts (in single out: all darts, since there is no last-dart preference) compared position by
     position, the highest value first;
  4. at equal value at a position, the easiest dart first: single, then double, then triple (Q1).
  This order is total: two different routes are never equal.
- CS4. The suggestion of a running leg is computed for the engine current player: their remaining score, 3 minus the
  darts already thrown in their turn, and the game's checkout rule. A finished leg has no suggestion.
- CS5. The suggestion is shown only on the highlighted card, and only when no turn waits: it is hidden while a turn
  waits for "Next player" (after the 3rd dart or a bust) and while the checkout dialog is open. The end screen has no
  suggestion.
- CS6. It is recomputed after every dart and every undo (it is derived from the game state, never stored).
- CS7. Display: label "Checkout: " / « Sortie : » then the darts separated by ` · `, written like the dart slots:
  `T20`, `D16`, `20` (single), `25`, `Bull` (Q2, Q5).
- CS8. The suggestion line is always present in the highlighted card, under the remaining score; it is empty and
  silent for TalkBack when there is no suggestion, so the card never changes height because of it (Q3).
- CS9. TalkBack reads the label then the darts with the words of the voice announcements, separated by commas:
  « Sortie : Triple 20, Triple 20, Bull » / "Checkout: Triple 20, Triple 20, Bull" (Q4).

Reference table (issue, adapted to Q5; mandatory tests):

| Remaining | Darts left | Rule | Suggestion |
|---|---|---|---|
| 170 | 3 | double | T20 · T20 · Bull |
| 167 | 3 | double | T20 · T19 · Bull |
| 160 | 3 | double | T20 · T20 · D20 |
| 110 | 2 | double | T20 · Bull |
| 100 | 3 | double | T20 · D20 |
| 60 | 3 | double | 20 · D20 |
| 50 | 3 | double | Bull |
| 40 | 1 | double | D20 |
| 3 | 3 | double | 1 · D1 |
| 101 | 1 | double | none |
| 159 | 3 | double | none |
| 180 | 3 | single | T20 · T20 · T20 |

I checked each row by hand against CS3: 60 relies on CS3.4 (single 20 before D10); 167 on CS3.3 (T20 before T19).

## Approach

### 1. Engine (`:game-engine`, pure Kotlin)

- New `CheckoutRoute.kt` (package `engine.x01`): `object CheckoutRoutes` with
  `fun suggest(remaining: Int, dartsLeft: Int, rule: CheckoutRule): List<Dart>?`
  (`null` = no route; `dartsLeft` outside 0..3 throws `IllegalArgumentException`; `dartsLeft == 0` or
  `remaining < 1` returns `null`).
- Search: for length L = 1..dartsLeft, enumerate the L-1 setup darts over the 62 scoring darts (at most 62² = 3,844
  pairs), and for each, the finishing darts whose value equals the rest. Collect the routes of the first length that
  has any (CS3.1) and take the minimum with an explicit `Comparator<List<Dart>>` built from CS3.2 to CS3.4. Cost per
  call: a few thousand iterations, no cache, no table.
- `Checkouts.isPossible(remaining, rule)` becomes `CheckoutRoutes.suggest(remaining, DARTS_PER_TURN, rule) != null`
  (one implementation, AN3 doc unchanged in meaning). Its precomputed sets are removed. Existing `CheckoutsTest`
  stays unchanged and must stay green.
- `X01Game.checkoutSuggestion: List<Dart>?` (CS4): `null` if the leg is finished; else for the engine current player
  `suggest(remaining, DARTS_PER_TURN - currentTurnDarts.size, config.checkoutRule)`.

### 2. App state (`GameScreenState`, pure JVM)

- `val checkoutSuggestion: List<Dart>?` = `null` if `isAwaitingConfirmation`, else `game.checkoutSuggestion` (CS5).
  When no turn waits the highlighted player is the engine current player, so the engine value is the right one.
  While a turn waits the engine has already moved to the next player: that is why the app must hide it.

### 3. Game screen

- `CurrentPlayerCard` (`GameScreen.kt`) gets a `checkoutSuggestion: List<Dart>?` parameter and a new
  `CheckoutSuggestionLine` between `CardScoreLine` and `DartSlots` (Q2).
- The line is a single `Text` (`titleMedium`, `maxLines = 1`) whose text is the label + route or an empty string; an
  empty `Text` with `minLines = 1` keeps the same height (CS8). Test tag `GameTags.CHECKOUT_SUGGESTION`.
- Semantics: with a suggestion, `clearAndSetSemantics { contentDescription = <CS9 text> }`; without, empty
  `clearAndSetSemantics {}` so TalkBack skips it. Traversal: name 0, remaining 1, bust 2, box 3, suggestion 4, dart
  slots 5 (`CARD_ORDER_DARTS` moves from 4 to 5). No live region: the voice announcements already speak, and a
  live region on every dart would be noisy for TalkBack users.
- Formatting in `ui/format/DartText.kt`: `checkoutRouteText(darts)` reuses `dartText` (singles already written
  without "S", Q5) joined by the untranslatable separator; `dartSpokenText(dart)` uses the existing
  `announce_dart_*` strings (no miss in a route).

### 4. Strings (`values/` and `values-fr/`)

| Name | English | French |
|---|---|---|
| `game_checkout_suggestion` | `Checkout: %1$s` | `Sortie : %1$s` |
| `game_checkout_suggestion_description` | `Checkout: %1$s` | `Sortie : %1$s` |
| `checkout_route_separator` (`translatable="false"`, `values/` only) | ` · ` | |
| `checkout_spoken_separator` | `, ` | `, ` |

A non-breaking space before the French colon keeps "Sortie :" on one line.

### Alternatives considered

- Precomputed table (remaining x darts left x rule) at first use: faster lookups but a startup cost and more state;
  the per-call search is cheap enough (under 4k iterations per dart) and simpler. Rejected.
- Keeping `Checkouts.isPossible` on its own enumeration plus an equivalence test: two implementations of the same
  rule. Rejected in favor of one implementation; an equivalence test is still added as a guard (see tests).
- Hide logic in the engine: the "waiting turn" concept lives in `GameScreenState` (PR #28), not in the engine, so
  the engine gives the suggestion for its current player and the app applies CS5.
- Hiding the line (collapsing) when empty: rejected by Q3.

## Files and types added or changed

- `game-engine/src/main/kotlin/io/github/darkcosmos/kosmodarts/engine/x01/CheckoutRoute.kt` (new): `CheckoutRoutes`.
- `game-engine/.../x01/Checkouts.kt`: `isPossible` delegates to `CheckoutRoutes`.
- `game-engine/.../x01/X01Game.kt`: `checkoutSuggestion` property.
- `game-engine/src/test/.../x01/CheckoutRoutesTest.kt` (new), `X01CheckoutSuggestionTest.kt` (new),
  `CheckoutsTest.kt` (one equivalence test added, nothing removed).
- `app/src/main/kotlin/.../ui/game/GameScreenState.kt`: `checkoutSuggestion`.
- `app/src/main/kotlin/.../ui/game/GameScreen.kt`: card parameter, `CheckoutSuggestionLine`, traversal constants.
- `app/src/main/kotlin/.../ui/game/GameTags.kt`: `CHECKOUT_SUGGESTION`.
- `app/src/main/kotlin/.../ui/format/DartText.kt`: `checkoutRouteText`, `dartSpokenText`.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`.
- `app/src/test/.../ui/game/GameScreenStateTest.kt`, `app/src/test/.../ui/x01/X01ScreensTest.kt` (new cases),
  `app/src/test/.../screenshots/CheckoutSuggestionScreenshotTest.kt` (new).
- `docs/rules/checkout-suggestions.md` (new), `docs/rules/announcements.md` (AN3 points to the shared search).

No new dependency (Roborazzi 1.76.0 is already in the catalog).

## Data read, stored or exposed

Read: the in-memory game (remaining score, darts of the current turn, checkout rule). Nothing stored, nothing new in
the save format or settings, no new permission. The route is derived on every recomposition from the state.

## Screens and states

Highlighted card, both orientations:
- Turn in progress with a route: "Checkout: T20 · T20 · Bull" under the remaining score.
- Turn in progress without a route (e.g. 301 at start, 90 with 1 dart): empty line of the same height.
- Turn waiting for "Next player" (3rd dart or bust): empty line (BUST badge unchanged).
- Checkout dialog open: empty line. End screen: unchanged.
- Longest texts: "Checkout: T20 · T20 · Bull" (26 characters) and « Sortie : T20 · T20 · Bull » (25).

## Test plan

### `:game-engine` (JUnit 6)

`CheckoutRoutesTest`, each test citing its CS id:
- CS3 reference table: the 12 rows above (as `List<Dart>`, not text).
- CS3.1: 100 with 3 darts gives 2 darts; 50 with 3 darts gives 1 dart.
- CS3.2: preferred double order: 32 with 1 dart = D16; 56 with 2 darts = `16 · D20` (D20 before D16); a case where
  only a non-listed double works (38 with 1 dart = D19); Bull only when no double fits (110 with 2 darts).
- CS3.3: 167 with 3 darts = `T20 · T19 · Bull` (not `T19 · T20 · Bull`).
- CS3.4: 60 with 3 darts double out = `20 · D20`; single out 18 with 1 dart = `18`; 30 with 1 dart = `D15`
  (not T10); 36 with 1 dart = `D18` (not T12).
- CS2: `null` for R in {0, -1, 1 (double out), 101/1, 159/3, 181/3}; `dartsLeft = 0` gives `null`;
  `dartsLeft` of -1 and 4 throw.
- Exhaustive (issue): for each R in 1..180, k in 1..3 and each rule, an independent brute force in the test
  enumerates every sequence of 1..k darts of the full board (misses included), keeps the valid routes, and checks:
  suggestion exists iff a valid route exists; the suggestion totals R, ends on a finishing dart, has at most k darts
  and no miss; its length is the minimum; and it equals the minimum of the valid routes sorted by a sort key written
  in the test from the CS3 text (independent of the production comparator).

`CheckoutsTest`: existing AN3 tests unchanged; new equivalence test: for R in -5..600 and each rule,
`isPossible(R, rule) == (CheckoutRoutes.suggest(R, 3, rule) != null)`.

`X01CheckoutSuggestionTest` (CS4, CS6): 301 double out, 1 player: start has none; after T20, T20 none (181);
after a 3rd dart and new turn at 170: `T20 · T20 · Bull`; after T20: `T20 · Bull`; after S20 (90, 1 dart): none;
undo twice restores each step; finished leg: `null`; single out game: 180 at turn start gives `T20 · T20 · T20`;
two players: the suggestion follows the engine current player.

### `:app` pure JVM (`GameScreenStateTest`)

CS5: suggestion present mid-turn; `null` while a normal turn waits, while a bust waits, while the checkout dialog
is open; back after `nextPlayer()`; back after `undo()` on a waiting turn.

### Robolectric (`X01ScreensTest`, Compose UI)

- Text of `CHECKOUT_SUGGESTION` is "Checkout: T20 · T20 · Bull" in English and « Sortie : T20 · T20 · Bull » in
  French (`+fr` qualifier), and `20 · D20` shows a single without "S".
- Content description "Checkout: Triple 20, Triple 20, Bull" / French equivalent; empty line has no content
  description and no text for TalkBack.
- CS8: the top of the dart slots (`dartSlot(0)` bounds) and the card height are the same with and without a
  suggestion.
- Hidden after a bust and after a 3rd dart until "Next player"; the PO scenario steps 1 to 5 end to end.

### Roborazzi captures (record only, as existing captures)

`CheckoutSuggestionScreenshotTest`, Pixel 7: with suggestion (1 player, 301, 170 left) and without (game start),
portrait and landscape, English and French; plus 4 players in landscape with the longest route, to check that the
line does not wrap or clip in the narrow scoreboard column. Existing captures will change (taller card): expected.

### Local checks

`./gradlew :game-engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (or the full `build`), and
`kosmo-detekt` (exit 0).

## How Kosmo verifies on the phone (Pixel)

The issue's steps, with Q5 notation: 1 player, 301, double out.
1. T20, T20, 11: no suggestion during the turn (the line stays empty, nothing moves); 170 left.
2. Next player: « Sortie : T20 · T20 · Bull ». T20: « Sortie : T20 · Bull ». 20 (90 left, 1 dart): empty line.
3. Undo: « T20 · Bull » comes back. Undo again: « T20 · T20 · Bull ».
4. Cause a bust: the line empties.
5. New game, single out: T20, T20, 1, Next player: « Sortie : T20 · T20 · T20 ».
6. Same checks in landscape. With TalkBack: the card reads « Sortie : Triple 20, Triple 20, Bull ».

## Trade-offs and risks

- The highlighted card grows by one line in every state (Q3). In landscape with 4 players, the scoreboard scrolls
  sooner; the 4-player landscape capture checks this.
- With `maxLines = 1`, a very large font scale could clip the end of the route; the text is at most 26 characters
  in `titleMedium`, which fits on a Pixel 7 at default scale in both orientations (checked by the captures). Allowing
  a second line would break Q3 at large font scales; I keep one line and list it as a known limitation.
- CS3.3 ("strongest first") is the owner's deterministic rule, not always what a professional would throw (e.g.
  setup darts that leave a preferred double are not favored beyond CS3.2). This follows the issue on purpose.
- No live region: TalkBack users hear the route when they move to the card, not after each dart.

## Open questions

None for Kosmo. Reviewers may challenge the "no live region" choice (UX) and the one-line limit (UX).
