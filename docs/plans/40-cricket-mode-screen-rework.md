# Plan addendum: Cricket game screen rework (issue #40, PR #60)

Addendum to `docs/plans/40-cricket-mode.md`, written after Kosmo's review of PR #60 on head `2f1fc6a`. It only
changes the Cricket game screen (CR22 to CR24, the zone of CR25 stays). Everything else in the main plan (engine, save
format, between-legs and end screens, setup, home) is unchanged and stays in force.

## Goal

Answer Kosmo's three comments on PR #60 (issue #40):
1. the player zone is decluttered: the player's total (in white) is removed from it;
2. the total in the Cricket board becomes much more visible;
3. the separate keypad, with its Double and Triple modifiers, is replaced by three buttons per target (single,
   double, triple) aligned with the target's row of the board, in portrait and in landscape. The board's first column
   (the target labels) is removed, since the buttons carry the target.

The dartsmind screenshot given by Kosmo could not be viewed: its download is redirected to
`github-production-user-asset-*.s3.amazonaws.com`, which is outside the sandbox allowlist (connection refused). Nothing
in this plan is taken from it; the layout below is built only from Kosmo's words and answers.

## Scope and out of scope

In scope:
- the Cricket game screen in portrait and landscape: zone, board, target buttons, Miss, action bar;
- the layout policy and its JVM tests, the Robolectric fit and TalkBack tests, the game captures;
- the rules text CR22, CR23, CR24 in `docs/rules/cricket.md`, `docs/design/design-system.md` (components table, 6.5),
  the strings in `values/` and `values-fr/`, the phone checks.

Out of scope:
- any game rule, the engine, the save format: a dart is still a `Dart(segment, multiplier)`, and the multiplier was
  never saved (it is UI state only);
- the X01 screens and keypad: the X01 captures must stay byte-identical to the `e7372f8` baseline as amended by T10
  (the 15 `home_*` and `match_home_summary_portrait_en` captures);
- the Cricket between-legs, end, setup and home screens and their captures;
- the pending minors of earlier missions.

## Approach

### 1. Input: three buttons per target, inside the board

Each target row starts with its own buttons, in the place of the removed label column:
- rows 20 to 15: three buttons, single, double, triple. Labels: "20", "D20", "T20" (the single button also reads as
  the row label). TalkBack: « 20 », « Double 20 », « Triple 20 » / "20", "Double 20", "Triple 20";
- Bull row (only with 25-Bull on, CR1): two buttons sharing the width of the three: "25" (1 mark) and "BULL"
  (2 marks). There is no triple bull on a dartboard, so no third button;
- the footer row of the board starts with the "RATÉ" / "MISS" button, over the same width;
- "ANNULER" (undo the last dart) stays in the action bar with "JOUEUR SUIVANT" (or the CR25 confirm label).

One tap records one dart, there is no modifier state any more: `CricketGameScreenState.multiplier`,
`toggleMultiplier`, the Cricket branch of `AppEvent.MultiplierTapped` and `CricketKeypad` are removed. A new
`AppEvent.CricketDartTapped(dart: Dart)` goes to `CricketGameScreenState.throwDart(dart)`, which records it only
while the input is enabled (not while a turn waits) and only if the dart is offered by the current configuration
(no 25 or Bull with 25-Bull off). The X01 keypad and its events are untouched.

Buttons of a dead row (CR15) stay enabled: a dart in a dead sector is a real dart and plays the closed-sector sound
(AN30). They take the muted style of the row, and the strike-through line crosses only the player cells, not the
buttons, so that they do not look disabled. Every button is locked (35 %, as today) while a turn waits.

Alternatives considered:
- keep a separate keypad below the board (rejected by Kosmo);
- one button per target and a long press for double or triple (hidden gesture, not discoverable, rejected);
- the target buttons in a central column between players (classic chalkboard). Rejected: Kosmo asked to remove the
  *first* column, and with 3 or 4 players the columns would not be split evenly.

### 2. Totals: a pinned footer row, large

The total is removed from the zone (Kosmo comment 1). In the board, the points footer row:
- is pinned at the bottom of the board (like the header is pinned at the top), so it never scrolls away;
- is 48 dp tall (the height of the button rows, since it holds "RATÉ");
- shows each total in `tileScore` (the large display style the zone used), in `onBackground`, centered in the
  player's column, inside the current player's `accent` border;
- picks its text size from the space: `CricketLayoutPolicy.totalTextSize(digits, columnWidth, labelScale)` returns
  the largest of the sizes 28, 24, 20, 16, 14 sp whose line fits the column (digits x 0.6 em + 4 dp); a total is never
  cut (CR22 rule kept);
- in a solo game, shows no total: the "4/7" (closed targets) display is removed entirely (Kosmo, Q2); the footer
  then holds only "RATÉ".

In landscape the totals sit in the pinned column header instead, under each name (section 4), since a footer row does
not fit there.

Alternatives for the UX review: (b) the total under the name in the pinned column header; (c) both. (a), the
footer, is proposed because it keeps the column header small and puts the total next to "RATÉ", where the eye
already is.

### 3. Zone

`CricketPlayerZone` keeps: line 1, name (1 line, ellipsis), "MPR 2,4", S/M chips; line 2, the `DartSlots` (scoring
darts, or "RATÉ"), and the "VICTOIRE" banner of CR25 (Kosmo, Q4 A). The `ScoreLine` loses the points and the "n/7".
In portrait its height goes from 96 (minimum 76) to 84 (minimum 68), see the layout contract. In landscape it is a
single line (section 4).

### 4. Layout contract (new CR22), pure `CricketLayoutPolicy`

Board columns: a button column then the player columns, which share the rest equally.
- Button column: 3 x 48 = 144 dp at least (48 x 48 dp per button, the touch-target floor), up to 3 x 56 = 168 dp when
  the player columns keep at least 76 dp each.
- Rows: 48 dp at least (button floor), up to 56 dp; the text line of the 14 sp texts is still 22.5 dp x label scale,
  so a row is `max(48, 22.5 x scale + 2)`.
- Column header: 3 dp strip + name line, as today; pinned. Footer: pinned, 48 dp at least.
- Only the target rows scroll, between the pinned header and footer, when they do not fit at 48 dp.

Portrait at 360 x 640 (about 568 dp usable), 4 players, 7 rows, scale 1.0, all at their minimums:
top margin 4 + `MatchHeader` 24 + gap 4 + zone 68 + gap 4 + column header 25.5 + 7 rows x 48 (336) + footer 48 +
gap 4 + action bar 48 + bottom margin 0 = 565.5 dp, which fits in 568 without scrolling (2.5 dp spare). Width: 360 -
2 x 12 = 336 dp, so 336 - 144 = 192 dp, 48 dp per player column. With 2 players: 96 dp each; 3 players: 64 dp.
Without the Bull row, 48 dp more for the rows. At scale 2.0 the rows region scrolls; header, footer, "RATÉ" and the
action bar stay visible.

Landscape (Kosmo, Q1): the same board as in portrait (targets in rows, buttons at the start of each row, not
turned), everything visible at once with no scrolling, the fonts may be slightly reduced. Layout:
- top band, one height for both columns: on the left the `MatchHeader` ("CRICKET · STANDARD", M14 position) over the
  board's pinned column header (color strip, name, and the total in large type under the name); on the right the
  current player's zone, limited to the height of that band (Kosmo's suggestion);
- the zone is one line: name (ellipsis), "MPR 2,4", then the visit's dart cells at their minimum size (about 28 dp
  tall, text 12 sp). The S/M chips (kept, Q4 A) move to the end of the `MatchHeader` line in landscape, since they do
  not fit on the zone's single line at 640 dp wide (for the UX review);
- below the band: on the left the 6 or 7 target rows; on the right "RATÉ", "ANNULER" and "JOUEUR SUIVANT", pinned to
  the bottom;
- the landscape text sizes: board names, button labels and the `MatchHeader` at 12 sp instead of 14 sp, totals at
  20 sp at most (the "slightly reduced" font). To stay in one block at every system font size, the landscape board,
  zone and title use the existing "fixed" styles (`density.fixedSp`, as `scoreHuge` and `tileScore` already do), so
  their size does not follow the font scale (developer proposal, for the UX review); portrait keeps scaled texts.

Height budget at 640 x 360 (about 336 dp usable), 7 rows, scale 1.0: top margin 4 + band (title 20 + strip 3 + name
line 19.5 + total line 30 = 72.5) + 7 rows = 336. The rows get (336 - 4 - 72.5) / 7 = 37 dp, under the 48 dp
touch-target floor. On Kosmo's Pixel 7 (412 dp tall, about 370 dp usable with the system bars) the rows get about
42 dp; with the system bars hidden in landscape, about 48 dp. No layout that shows the 7 rows at once keeps 48 dp
buttons on a 360 dp tall screen: this is question Q5 for Kosmo, which R2 and R4 wait for.

Width at 640 dp, 4 players: 616 dp after margins; buttons 3 x 48 = 144 dp + 4 x 64 dp = 400 dp for the board, a
12 dp gap, 204 dp for the right column (zone line: name about 60 + MPR about 50 + 3 dart cells of 30 = 200 dp).

### 5. TalkBack order (CR23, replaces review U7's order)

1. the column headers, the current player's first (« Anna, au lancer, dernière volée : 3 marques »), then the others;
2. row by row from 20 to Bull: the row's buttons, then its cells in column order (a dead row: its buttons, then its
   single merged node);
3. the footer: « Raté », then « Points : Anna 60, Bob 40 » (solo: only « Raté »).

In landscape, the totals are read with each column header (« Anna, au lancer, 60 points ») and « Raté » comes after
the rows, before the action bar.

## Files and types added or changed

`app/src/main/kotlin/io/github/darkcosmos/kosmodarts/` (below, `ui/...`):
- `ui/cricket/CricketGameScreenState.kt`: `throwDart`, `CricketTargetButton` (dart, label, description, enabled),
  `buttonRows`, `footerTotals`; `multiplier`, `toggleMultiplier`, `throwNumber`, `throwOuterBull`, `throwBull`,
  `areBullKeysEnabled` removed (R5); `zoneScoreOf` moves to the footer;
- `ui/cricket/CricketLayoutPolicy.kt`: new contract (button column, 48 dp rows, pinned footer, zone 84/68,
  `totalTextSize`, landscape top band and single-line zone, landscape row height per Q5);
- `ui/cricket/CricketBoard.kt`: no label column; `CricketTargetButtons` per row; pinned footer with "RATÉ" and the
  totals; dead-row strike over the cells only;
- `ui/cricket/CricketTargetButtons.kt` (new): the buttons of one row;
- `ui/cricket/CricketGameScreen.kt`, `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt`;
- `ui/cricket/CricketKeypad.kt`: deleted (R5);
- `ui/app/AppState.kt` (`AppEvent.CricketDartTapped`), `ui/app/CricketReducer.kt`;
- `res/values/strings.xml`, `res/values-fr/strings.xml`;
- `docs/rules/cricket.md` (CR22, CR23, CR24), `docs/design/design-system.md` (`CricketBoard`, `CricketTargetButtons`
  instead of `CricketKeypad`, section 6.5);
- tests: `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketLayoutPolicyTest`, `CricketScreenTest`,
  `CricketScreenshotTest`, `CricketGameCuesTest`, `CricketPersistenceEffectsTest`, `CricketResumeScreensTest`
  (the last three only where they use the modifier or keypad tags).

## Data read, stored or exposed

No change. The removed multiplier was UI state, never saved (`persistence/` does not read it). No new permission, no
new dependency.

## Screens and states

Cricket game, each in portrait and landscape where marked:
- turn in progress, 2 and 4 players (portrait and landscape);
- turn waiting for "Next player": all buttons locked;
- a dead row: muted, struck cells, buttons enabled;
- 25-Bull off: no Bull row, so no 25/BULL buttons;
- solo: no total anywhere on the game screen, footer with "RATÉ" only;
- landscape: single-line zone in the top band, totals in the column headers, no scrolling;
- pending win (CR25), also by a non-thrower;
- totals with 1, 3 and 4 digits in a 48 dp column;
- font scale 2.0 with 4 players (rows scroll, footer and action bar pinned).

### Strings (new, `values/` and `values-fr/`)

| Strings | English | French |
|---|---|---|
| Double button label | "D%1$s" | identical |
| Triple button label | "T%1$s" | identical |
| TalkBack, double | "Double %1$s" | « Double %1$s » |
| TalkBack, triple | "Triple %1$s" | « Triple %1$s » |
| TalkBack, landscape header total (plurals `cricket_header_points`) | one: "%1$d point"; other: "%1$d points" | one: « %1$d point »; other: « %1$d points » |

"25", "BULL" and "RATÉ" / "MISS" reuse the existing strings. The X01 Double/Triple strings stay (X01 keypad).
`cricket_closed_targets` ("%1$d/%2$d") leaves the game screen; it is removed in both languages only if Q6 removes it
from the home card too.

## Test plan

Rules cited: CR1, CR7, CR15, CR22, CR23, CR24, CR25 (new text).

`:app` pure JVM:
- `CricketGameScreenStateTest`: CR24 `throwDart` records D20 and T20 as one dart each; refuses 25 and Bull with
  25-Bull off; refuses any dart while a turn waits (CR7); a dart on a dead row is recorded; `buttonRows` gives 3
  buttons for 20 to 15 and 2 for Bull, none with 25-Bull off; buttons all disabled while a turn waits and enabled on a
  dead row; `footerTotals` gives the points, and nothing in a solo game.
- `CricketReducerTest`: `CricketDartTapped` reaches `throwDart`; undo after a triple removes the triple.
- `CricketLayoutPolicyTest` (CR22): portrait 360 x 568, 4 players, 7 rows, scale 1.0 does not scroll and sums to at
  most 568; every button is at least 48 x 48 at every player count and scale 1.0 to 2.0; player columns 48/64/96 dp
  at 4/3/2 players; scale 2.0 scrolls the rows only; `totalTextSize` for 1 to 5 digits at 48 and 96 dp, never above
  the column; landscape: at 640 x 336 and at about 412 x 370 (Pixel 7), 4 players, 7 rows, scale 1.0 and 2.0,
  nothing scrolls, the top band has one height for the zone and for the title plus column header, and the row height
  follows Q5 (its floor asserted).

Robolectric (`CricketScreenTest`):
- CR24: each button sends its dart (20, D20, T20, 25, BULL, RATÉ); no 25/BULL node with 25-Bull off; locked while a
  turn waits;
- CR22: fit at 360 x 640 scale 1.0 with 4 players (no scroll), at scale 2.0 (rows scroll, footer, "RATÉ" and action bar
  fully visible), landscape 640 x 360 at 1.0 and 2.0 with no scroll container; each button's bounds at least
  48 x 48 dp (in landscape: the floor chosen in Q5); buttons vertically
  aligned with their row's cells (same top and bottom); totals and button labels not clipped;
- CR22: the zone has no total and no "n/7", in a solo game too; nothing on the game screen shows "n/7";
- CR22 landscape: zone on one line (name, MPR, darts in the same row of bounds), its height equal to the
  `MatchHeader` plus the column header; no scroll container on the screen;
- CR23: TalkBack order of section 5; dead-row merged node and strike node kept.

Roborazzi captures (record only, Pixel 7, French unless stated), re-recorded: `board_2p_portrait_fr`,
`zone_miss_2p_portrait_fr`, `board_2p_landscape_fr`, `board_4p_portrait_fr`, `board_4p_landscape_fr`,
`board_4p_portrait_en`, `board_4p_portrait_fr_font200`, `board_4p_360x640_fr`, `board_4p_640x360_fr`, the dead rows
(2 and 4 players, with the grayscale copy), solo, 25-Bull off, waiting turn, the three win captures. Removed:
`modifier_triple_portrait_fr`, `keypad_with_bull_fr`, `keypad_without_bull_fr`. Added: `buttons_with_bull_fr`,
`buttons_without_bull_fr` (board crops), `totals_4digits_4p_portrait_fr`. X01 captures checked with `cmp` against the
baseline (see Out of scope).

Local checks, every task: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`,
`kosmo-detekt` (exit 0).

## How Kosmo verifies on the phone

Replaces steps 3, 4, 7, 13, 14, 16 and 17 of the main plan for the game screen; the other steps stay.
1. Start a Cricket game with 2 players: there is no keypad and no Double/Triple key. Each row of the board starts with
   "20", "D20", "T20" (Bull row: "25", "BULL"); the target column on the left is gone.
2. Tap "T20": one dart, three marks for the player. Tap "D19", then "18": three darts in the zone.
3. The zone shows the name, the MPR and the darts, but no total. The totals are in large type on the bottom row of
   the board, next to "RATÉ", and always visible.
4. "RATÉ" counts a miss; "ANNULER" removes the last dart, including a triple.
5. While "JOUEUR SUIVANT" waits, no target button reacts.
6. Close the 20 for everyone: the 20 row is greyed and struck across the players' cells; its buttons still work and
   play the closed-sector sound.
7. 25-Bull off: no Bull row and no 25/BULL buttons.
8. Solo: no total and no "4/7" anywhere on the game screen; the bottom row only has "RATÉ".
9. 4 players on a small phone, portrait: every row and its buttons visible without scrolling; totals readable.
10. Landscape with 4 players: the same board as in portrait, all 7 targets visible without scrolling; the player's
    zone is one line (name, MPR, darts) at the top right, as tall as the title plus the names row; the totals are
    under the names; buttons lined up with their target; nothing cut.
11. Largest system font, 4 players, portrait: the rows scroll, but the names row, the totals row, "RATÉ" and "ANNULER" stay
    visible.
12. TalkBack: the headers, then each row's buttons (« Double 20 ») and cells, then « Raté » and the points.

## Tasks

### R1 - Rules text, dart buttons model and event

- Goal: rewrite CR22 (zone without total and without "n/7", layout contract of section 4, portrait and landscape,
  the landscape row floor per Q5), CR23 (no label column, buttons, totals in the pinned footer in portrait and in the
  column header in landscape, TalkBack order of section 5) and CR24 (target buttons) in `docs/rules/cricket.md`, and
  CR20 per Q6; add `throwDart`, `CricketTargetButton`, `buttonRows`, `footerTotals` (no total in solo) to
  `CricketGameScreenState`; `AppEvent.CricketDartTapped` and its `CricketReducer` branch; the new strings. The old
  keypad and modifier stay working until R5, so the build stays green.
- Files: `docs/rules/cricket.md`, `ui/cricket/CricketGameScreenState.kt`, `ui/app/AppState.kt`,
  `ui/app/CricketReducer.kt`, `res/values/strings.xml`, `res/values-fr/strings.xml`, `CricketGameScreenStateTest`,
  `CricketReducerTest`.
- Depends on: approval of this addendum, answers to Q5 and Q6.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a state holder extension and its JVM tests following the existing reducer pattern.

### R2 - Layout policy for the button board

- Goal: rewrite `CricketLayoutPolicy` to the contract of section 4 (button column, 48 dp rows, pinned header and
  footer, zone 84/68, `totalTextSize`, player column widths) in portrait, and in landscape the top band (one height
  for the single-line zone and for the title plus the column header with totals), the fixed landscape text sizes, the
  dart cells at their minimum and the row height that shows every row without scrolling (floor per Q5), with
  `CricketLayoutPolicyTest` rewritten (old cases about the label column and keypad rows removed only where the
  contract they prove is removed; each new case cites CR22).
- Files: `ui/cricket/CricketLayoutPolicy.kt`, `CricketLayoutPolicyTest`.
- Depends on: R1, answer to Q5.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: opus
- Why this model: the dp budget is tight in both orientations (2.5 dp spare in portrait at 360 x 640, no scrolling
  allowed in landscape) and this contract needed two review rounds before (U1); the arithmetic and font-scale cases
  are subtle.

### R3 - Portrait screen: buttons in the board, pinned totals, zone without total

- Goal: `CricketBoard` without label column, `CricketTargetButtons` per row (sending `CricketDartTapped`), pinned
  footer with "RATÉ" and the totals, dead-row strike over the cells only; `CricketPlayerZone` without total; portrait
  `CricketGameScreen` without `CricketKeypad`; tags and TalkBack order; the Robolectric portrait cases and the
  portrait captures; `docs/design/design-system.md` (components table, 6.5 portrait).
- Files: `ui/cricket/CricketBoard.kt`, `CricketTargetButtons.kt` (new), `CricketGameScreen.kt`,
  `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt`, `docs/design/design-system.md`,
  `CricketScreenTest`, `CricketScreenshotTest`.
- Depends on: R2.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; X01 captures `cmp`-identical to the
  baseline; `kosmo-detekt` exits 0. If beyond about 60 tool calls, stop green and report what is left.
- Model: sonnet
- Why this model: composables built from the existing board and button components against a fixed policy.

### R4 - Landscape screen

- Goal: the landscape layout of section 4 (Kosmo, Q1): the same board as portrait, not turned, in the left column
  with no scrolling; the top band with the `MatchHeader` (and the S/M chips) over the column header with the totals,
  and on its right the single-line zone (name, MPR, minimal dart cells) of the same height; "RATÉ", "ANNULER" and
  "JOUEUR SUIVANT" pinned at the bottom right; the fixed landscape text sizes; the landscape TalkBack order; its
  Robolectric cases at 640 x 360 and about 412 x 370, scale 1.0 and 2.0 (no scroll container, one-line zone, band
  heights equal, nothing clipped, button floor per Q5); its captures; the 6.5 landscape paragraph of the design system.
- Files: `ui/cricket/CricketGameScreen.kt`, `CricketBoard.kt` (header with totals), `CricketPlayerZone.kt`
  (single-line variant), `ui/components/DartSlots` call site only (no change to the shared component, X01 untouched),
  `docs/design/design-system.md`, `CricketScreenTest`, `CricketScreenshotTest`. If Q5 B: the system bars hidden
  while the Cricket game screen is shown in landscape and restored when it leaves (through the window insets
  controller of the platform, `Window.insetsController`, available from API 30 = `minSdk`; no new dependency), in
  `CricketGameScreen.kt`.
- Depends on: R3, answer to Q5.
- Done when: same checks as R3.
- Model: sonnet
- Why this model: it reuses the portrait board and a fixed policy from R2; the hard arithmetic is already in R2.

### R5 - Remove the modifier keypad, finish tests and captures

- Goal: delete `CricketKeypad.kt`, the Cricket `multiplier` state, `toggleMultiplier`, `throwNumber`,
  `throwOuterBull`, `throwBull`, `areBullKeysEnabled` and the Cricket `MultiplierTapped`/`NumberTapped`/
  `OuterBullTapped`/`BullTapped` branches; move every test that used them to `CricketDartTapped` (same cases, same
  assertions, no case dropped: `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketGameCuesTest`,
  `CricketPersistenceEffectsTest`, `CricketResumeScreensTest`); remove the three keypad/modifier captures; check the
  unused strings (Cricket-only ones removed in both languages); update the main plan's phone steps reference and the
  Implementation notes.
- Files: `ui/cricket/CricketKeypad.kt` (deleted), `CricketGameScreenState.kt`, `ui/app/CricketReducer.kt`, the five
  test files above, `CricketScreenshotTest`, `res/values*/strings.xml`.
- Depends on: R4.
- Done when: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; X01 captures
  `cmp`-identical; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: mechanical removal, but across several test files where each case must be kept, not dropped.

### R6 - Remove the solo "n/7" from the home card (only if Kosmo answers Q6 A)

- Goal: the home resume card of a solo Cricket game no longer shows "4/7" (CR20 amended in R1): remove
  `ResumeSummary.PlayerScore.closedTargets`, its use in `ResumeCard.tileValue`, `CricketZoneScore.ClosedTargets` if
  nothing uses it any more, and `cricket_closed_targets` in both languages; adapt the tests and re-record the solo
  home capture. `CricketPlayerState.closedTargetCount` in `:game-engine` stays (engine API, tested in
  `CricketStatisticsTest`).
- Files: `ui/home/ResumeSummary.kt`, `ui/components/ResumeCard.kt`, `ui/cricket/CricketGameScreenState.kt`,
  `res/values/strings.xml`, `res/values-fr/strings.xml`, `ResumeSummaryCricketTest`, `CricketSetupScreenshotTest`.
- Depends on: R5, answer to Q6.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; X01 captures `cmp`-identical; the
  `home_*` X01 captures unchanged; `kosmo-detekt` exits 0.
- Model: haiku
- Why this model: a fully specified removal of one display, with no design choice.

## Decisions

- Kosmo, PR #60 review: the zone loses the player's total; the total stays only in the board and becomes much more
  visible; no Double/Triple modifier keys; every dart in direct access, aligned with the board, portrait and
  landscape; the board's label column is removed.
- Kosmo, answer 1: three buttons per target (single, double, triple) on the target's row.
- Kosmo, answer 2: the total is removed from the zone and kept only in the board, made much more visible (design
  proposed in section 2, for the UX review).
- Kosmo, answer 3: "RATÉ" and "ANNULER" stay; the Bull follows the 25-Bull option.
- Kosmo, Q1 (landscape): the same board as portrait, not turned, everything visible in one block without scrolling,
  the font may be slightly reduced; to investigate: a zone limited to the height of the board header plus the
  "CRICKET · MODE" title, with the visit's dart cells at their minimum and name, MPR and darts on a single line.
  Applied in section 4 and tasks R2 and R4; the touch-target conflict it creates is Q5.
- Kosmo, Q2: the solo "4/7" display is useless and is removed: no total and no "n/7" on the solo game screen. Whether
  the home card's "4/7" goes too is Q6.
- Kosmo, Q3 A: rule unchanged (CR1). With 25-Bull on, a Bull row with "25" and "BULL"; with it off, no Bull row and
  no Bull button. No engine or save change.
- Kosmo, Q4 A: the zone keeps the name, the MPR, the set/leg chips and the visit's darts.
- Developer, pending design review: in landscape the totals sit in the column header under each name (no room for a
  footer row), the S/M chips move to the end of the `MatchHeader` line, and the landscape texts use the fixed styles
  so the one-block layout holds at every font scale.
- Developer, pending design review: buttons of a dead row stay enabled (closed-sector sound, AN30); the strike crosses
  only the cells.
- Developer, pending design review: "RATÉ" sits in the pinned footer, left of the totals, rather than in the action
  bar, to keep the action bar's two buttons at their size.

## Open questions

Product questions for Kosmo (in French, for main to relay):

Q1 to Q4: answered by Kosmo (see Decisions).

- Q5. En paysage, pour voir les 7 cibles d'un bloc sans défilement, chaque ligne (et donc chaque bouton) ne peut
  mesurer qu'environ 37 dp de haut sur un petit téléphone (360 dp de haut) et environ 42 dp sur le Pixel 7. La règle
  du projet demande des zones tactiles d'au moins 48 dp (CLAUDE.md, accessibilité).
  - A : en paysage seulement, on accepte des boutons plus petits que 48 dp (37 à 42 dp de haut, 48 dp de large). Tout
    tient d'un bloc sur tous les téléphones. Il faut que vous ajoutiez cette exception dans CLAUDE.md (proposé dans
    la PR, section « Proposed owner changes »).
  - B (recommandée) : en paysage, la partie masque les barres du système (plein écran, on les fait revenir d'un
    glissement depuis le bord). Sur le Pixel 7, les boutons gardent environ 48 dp et tout tient d'un bloc ; sur les
    téléphones plus petits, les boutons descendent sous 48 dp, avec la même exception à écrire dans CLAUDE.md.
  - C : on garde 48 dp partout ; en paysage, les lignes du bas (15, Bull) demandent alors un petit défilement.
- Q6. Le « 4/7 » du solo apparaît aussi sur la carte « Reprendre la partie » de l'accueil (à la place des points du
  joueur).
  - A (recommandée) : on le supprime aussi de l'accueil ; la carte n'affiche plus de score en solo ;
  - B : on le garde sur l'accueil, on le supprime seulement de l'écran de jeu.

## Risks

- Portrait 360 x 640 with 4 players and the Bull: 2.5 dp spare at all minimums; the zone minimum (68 dp) must be
  measured against the real name line and `DartSlots` (36 dp) height, or the rows scroll on small phones.
- Player columns of 48 dp at 4 players: names get a strong ellipsis, totals of 4 digits fall to 20 sp, and at font
  scale 2.0 even 3 digits may need 14 sp; to be asserted by the clipping tests.
- Button labels "T20" in 48 dp at font scale 2.0 (about 46 dp of text): at the limit; the clipping test covers it.
- Landscape touch targets: showing 7 rows in one block gives about 37 dp rows at 640 x 360 and about 42 dp on a
  Pixel 7 with its system bars, under the 48 dp rule; Q5 decides. Hiding the system bars (Q5 B) also changes how the
  player leaves the game screen (a swipe brings the bars back), to be checked on the phone.
- Landscape at font scale 2.0: with scaled texts the 14 sp line (45 dp) does not fit a 37 to 42 dp row; the plan uses
  fixed text sizes in landscape (no growth with the system font), an accessibility trade-off for the UX review.
- Landscape single-line zone: in 204 dp, name (strong ellipsis), MPR and three minimal dart cells (about 30 dp each)
  just fit; "RATÉ" in a 30 dp cell at 12 sp and the "VICTOIRE" banner (CR25) in a band of about 72 dp must be checked
  by the clipping tests and captures.
- Landscape totals under the names in 64 dp columns: 4-digit totals need 16 to 20 sp.
- About 25 existing Robolectric cases and 20 game captures use the keypad or the modifier and are rewritten; the
  X01 captures must stay byte-identical.

## Implementation notes

(One line per task: task id, deviations, where the main pieces live.)
