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

## Reference: the Dartsmind screen given by Kosmo

Kosmo gave a Dartsmind Cricket screenshot (portrait, 3 players) as inspiration; it was added to the branch as a
temporary file and is deleted by task R7, so no file of the repository points to it. What it shows:
- the player columns on the left, the buttons on the right, about 40 % of the width;
- each column header: the total, very large (the biggest text of the screen), the name under it, then "MPR: 0.0" on a
  dark band; the current player's header has a red background and a small pointer; the others are grey;
- a top strip with the three darts of the visit (dart icons), and no other player zone;
- at the top of the button column, at the height of the headers: a wide "MISS" button and an undo button (arrow
  icon);
- one row per target, 20 to 15: three buttons all labeled with the number; the double has two dots under the number,
  the triple three dots; the background goes from light (single) to darker gold (triple). The Bull row has two
  buttons, "BULL" and "BULL" with two dots;
- the button rows are the board rows: same top and bottom as the player cells, which are empty at the start;
- at the bottom, "NEXT PLAYER" in a large gold button under the button column only; under the player columns, an
  empty hatched area;
- sizes, estimated from the image (1080 x 2134 px, about 411 x 813 dp if 2.625 px per dp): rows and buttons about
  79 dp tall, buttons about 55 dp wide, the header block about 105 dp tall. With 3 players and no player zone, the rows
  are much taller than our 48 dp floor.

Kosmo's instruction: take from the reference only the organization of the buttons (aligned with the score rows) and
its representation of the double and the triple. Everything else follows the answers Q1 to Q6 and the current design
system (`docs/design/design-system.md`); no other visual choice is copied (colors, red header, gold gradient, hatched
area, dart icons, fonts).

Taken:
- the button organization: one row of buttons per target, sharing the board row cell for cell (same top and bottom
  as the player cells); three buttons for 20 to 15, two for the Bull; the button column on the right of the player
  columns (Kosmo, Q7 B); "RATÉ" in the top cell of the button column, at the height of the column headers (Kosmo, Q9
  B: "RATÉ" alone there, the undo button is not taken from the reference, it stays in the bottom bar);
- the double/triple representation (Kosmo's clarification, replacing the "D20"/"T20" labels of the first version):
  the three buttons all show the number; the double has two dots under it, the triple three dots; the single has
  none. The Bull row follows the same representation: "BULL" (the 25, 1 mark) and "BULL" with two dots (the bull,
  2 marks). Colors, fonts and key style are those of the design system's keys (the X01 keypad), not Dartsmind's
  gradient. The dots carry the information, never a color alone; TalkBack reads « 20 », « Double 20 », « Triple 20 »,
  « 25 », « Bull ».

Difference with the first version of this plan: the labels were "20", "D20", "T20" and "25", "BULL"; they become
the number with 0, 2 or 3 dots, and "BULL" with 0 or 2 dots. The dots are drawn (not characters), at a fixed size
under the number, so they are not cut at large font scales.

Not taken (Q1 to Q6 and the design system apply): no removal of our player zone (Q4 A); MPR only in the zone; the
total in a red or grey header block; the undo button next to "MISS" (Kosmo, Q9 B: it stays in the bottom bar);
"NEXT PLAYER" under the button column only (ours stays the full-width `ActionBar`, "ANNULER" then "JOUEUR SUIVANT",
as today). The place of the totals (section 2) is not taken from the reference: it is a UX proposal of this plan.

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

The label column is removed; each target row ends with its own buttons, in a button column on the right of the
player columns (Kosmo, Q7 B, as in the reference):
- rows 20 to 15: three buttons, single, double, triple, each showing the number, with two drawn dots under it on the
  double and three on the triple (Kosmo, from the reference); the single button also reads as the row label. Order
  from left to right: single, double, triple (in portrait the triple is at the right edge of the screen). TalkBack:
  « 20 », « Double 20 », « Triple 20 » / "20", "Double 20", "Triple 20";
- Bull row (only with 25-Bull on, CR1): two buttons sharing the width of the three: "BULL" (the 25, 1 mark) and
  "BULL" with two dots (the bull, 2 marks); TalkBack « 25 » and « Bull ». There is no triple bull on a dartboard, so
  no third button;
- the top cell of the button column, at the height of the column headers (as in Dartsmind), holds only "RATÉ" /
  "MISS", one key over the whole width of the column (144 to 168 dp) and the height of the headers (Kosmo, Q9 B);
- the bottom action bar is the existing `ActionBar`, unchanged: "ANNULER" (secondary, at the start) and "JOUEUR
  SUIVANT" or the CR25 confirm label (primary, at the end), full width, as today (Kosmo, Q9 B). No new undo icon.

Layout direction: the board is built with start and end (Compose `Row`), so the button column is at the end of the
row. The app ships only English and French (both left-to-right), so on Kosmo's phones the buttons are always on the
right. `android:supportsRtl` is already `true`: in a right-to-left system locale, Compose would mirror the board
(buttons on the left, player columns from the right) and keep TalkBack in reading order; the plan does not force
left-to-right, since no right-to-left translation exists and none is in scope.

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
  *first* column, and with 3 or 4 players the columns would not be split evenly;
- the button column on the left, in the place of the removed label column (the first proposal of this plan).
  Rejected by Kosmo (Q7 B): on the right, as in the reference;
- the undo button next to "RATÉ" in the top cell, as in the reference, with only "JOUEUR SUIVANT" in the bottom bar
  (the first proposal of this plan). Rejected by Kosmo (Q9 B): the bottom bar keeps "ANNULER", as today.

### 2. Totals: at the top of each column, the largest text of the board

The total is removed from the zone (Kosmo comment 1). In the board, each pinned column header shows, from top to bottom: the player's color strip (3 dp), the total, then the name (1 line,
ellipsis). The total:
- is the largest text of the board, in the display font, `onBackground`, centered, inside the current player's
  `accent` border (with the "▶" kept before the current name);
- picks its size from the space: `CricketLayoutPolicy.totalTextSize(digits, columnWidth, scale)` returns the largest
  of 24, 20, 16, 14 sp in portrait (20, 16, 14 sp in landscape) whose line fits the column (digits x 0.6 em + 4 dp);
  a total is never cut (CR22 rule kept);
- is absent in a solo game: the "4/7" (closed targets) display is removed entirely (Kosmo, Q2); the solo header
  shows only the strip and the name.

There is no footer row any more. Same place in portrait and landscape.

Alternatives for the UX review: (b) a pinned footer row with the totals (the first version of this plan); (c) the
total under the name. (a) is proposed: landscape has no room for a points row, so it gives one place in both
orientations, it stays visible when the rows
scroll at large font scales, and it is the same in both orientations.

### 3. Zone

`CricketPlayerZone` keeps: line 1, name (1 line, ellipsis), "MPR 2,4", S/M chips; line 2, the `DartSlots` (scoring
darts, or "RATÉ"), and the "VICTOIRE" banner of CR25 (Kosmo, Q4 A). The `ScoreLine` loses the points and the "n/7".
In portrait its height goes from 96 (minimum 76) to 84 (minimum 68), see the layout contract. In landscape it is a
single line (section 4).

### 4. Layout contract (new CR22), pure `CricketLayoutPolicy`

Board columns, from start (left) to end (right): the player columns, which share the width left equally, then the
button column (Kosmo, Q7 B).
- Button column: 3 x 48 = 144 dp at least (48 x 48 dp per button, the touch-target floor), up to 3 x 56 = 168 dp when
  the player columns keep at least 76 dp each.
- Rows: 48 dp at least (button floor), up to 56 dp; the text line of the 14 sp texts is still 22.5 dp x label scale,
  so a row is `max(48, 22.5 x scale + 2)`.
- Column header: 3 dp strip + total line (24 sp display font, about 38.5 dp) + name line (22.5 dp x label scale),
  about 64 dp at scale 1.0; pinned. The button column's top cell ("RATÉ" alone, Kosmo, Q9 B) has the same height, at
  least 48 dp, and the whole width of the button column.
- Only the target rows scroll, under the pinned header, when they do not fit at 48 dp (portrait at large font scales).
- Action bar: the existing `ActionBar`, "ANNULER" then "JOUEUR SUIVANT" (or the CR25 confirm label), two buttons of
  equal width with a 12 dp gap, labels in the `button` style (20 sp) with the word-safe auto-size (U6, U11: a label
  wraps only between words and shrinks down to 8 sp rather than cut a word; both buttons take the taller height).
  48 dp minimum in the budgets below.

Portrait at 360 x 640 (about 568 dp usable), 4 players, 7 rows, scale 1.0, all at their minimums:
top margin 4 + `MatchHeader` 24 + gap 4 + zone 68 + gap 4 + column header 64 + 7 rows x 48 (336) + gap 4 + action
bar 48 + bottom margin 0 = 556 dp, which fits in 568 without scrolling (12 dp spare). Width: 360 - 2 x 12 = 336 dp,
so 336 - 144 = 192 dp, 48 dp per player column. With 2 players: 96 dp each; 3 players: 64 dp. Without the Bull row,
48 dp more for the rows. At scale 2.0 the rows region scrolls; the header with the totals, "RATÉ" and the action bar
(with "ANNULER") stay visible.

Portrait action bar width: 336 dp, so (336 - 12) / 2 = 162 dp per button, 138 dp for the label after the 12 dp
padding on each side: the same widths as the current screen, so the portrait bar is unchanged ("JOUEUR SUIVANT" and
"VALIDER LA PARTIE" wrap on two lines or shrink exactly as they do today). At scale 1.0 its height must stay within
the 48 dp of the budget plus the 12 dp spare; a policy test and a Robolectric fit test check the 360 x 640 case with
the longest label of each language ("VALIDER LA PARTIE", "CONFIRM MATCH") pending.

Landscape (Kosmo, Q1): the same board as in portrait (targets in rows, the buttons at the end of each row, on the
right of the player cells, not turned), everything visible at once with no scrolling, the fonts may be slightly
reduced. Layout (Kosmo, Q10 A: the board on the left, the zone and the action bar on the right):
- top band, one height for both columns: on the left the `MatchHeader` ("CRICKET · STANDARD", M14 position) over the
  board's pinned column header (color strip, name, and the total in large type under the name); on the right the
  current player's zone, limited to the height of that band (Kosmo's suggestion);
- the zone is one line: name (ellipsis), "MPR 2,4", then the visit's dart cells at their minimum size (about 28 dp
  tall, text 12 sp). The S/M chips (kept, Q4 A) move to the end of the `MatchHeader` line in landscape, since they do
  not fit on the zone's single line at 640 dp wide (for the UX review);
- "RATÉ" alone in the top cell of the button column, as in portrait, at the height of the column headers (part of the
  band's height). The button column is at the right edge of the board, so in landscape it sits in the middle of the
  screen, between the player columns and the zone column (Q10 A);
- below the band: on the left the 6 or 7 target rows (player cells, then their buttons); on the right the action bar,
  "ANNULER" then "JOUEUR SUIVANT", pinned to the bottom of the zone column, as today;
- the landscape text sizes: board names, button labels and the `MatchHeader` at 12 sp instead of 14 sp, totals at
  20 sp at most (the "slightly reduced" font). To stay in one block at every system font size, the landscape board,
  zone and title use the existing "fixed" styles (`density.fixedSp`, as `scoreHuge` and `tileScore` already do), so
  their size does not follow the font scale (developer proposal, for the UX review); portrait keeps scaled texts.

Height budget at 640 x 360 (about 336 dp usable), 7 rows, scale 1.0: top margin 4 + band (title 20 + strip 3 + name
line 19.5 + total line 30 = 72.5) + 7 rows = 336. The rows get (336 - 4 - 72.5) / 7 = 37 dp, under the 48 dp
touch-target floor. On Kosmo's Pixel 7 (412 dp tall, about 370 dp usable with the system bars) the rows get about
42 dp; with the system bars hidden in landscape, about 48 dp. No layout that shows the 7 rows at once keeps 48 dp
buttons on a 360 dp tall screen. Kosmo, Q5 B: the Cricket game screen hides the system bars in landscape (a swipe
from the edge brings them back for a moment), and the rows take the whole height left, even under 48 dp on small
phones; the policy never scrolls in landscape. Floor kept: 32 dp (rows and buttons never below, and the row text
fits it at 12 sp fixed). The 48 dp exception is proposed to Kosmo for CLAUDE.md in the PR's "Proposed owner changes";
no squad member edits CLAUDE.md.

Width at 640 dp, 4 players: 616 dp after margins; buttons 3 x 48 = 144 dp + 4 x 64 dp = 400 dp for the board, a
12 dp gap, 204 dp for the right column (zone line: name about 60 + MPR about 50 + 3 dart cells of 30 = 200 dp). With
2 or 3 players the player columns stop at 76 dp (`PLAYER_COLUMN_WIDTH`) and the right column is wider. The right
column is never narrower than 204 dp at 640 dp wide (policy test).

Landscape action bar: at 4 players it has 204 dp, so (204 - 12) / 2 = 96 dp per button and 72 dp for the label
(today about 126 dp per button, since the board took at most 55 % of the width). "JOUEUR SUIVANT" and "VALIDER LA
PARTIE" then wrap on two lines ("JOUEUR" / "SUIVANT", "VALIDER LA" / "PARTIE") and the word-safe auto-size shrinks
them to about 16 to 18 sp so that no word is cut; "ANNULER" stays on one line. The bar's height is not taken from
the rows: below the band, the right column has about 336 - 4 - 72.5 - 4 = 255 dp for the bar alone, so a two-line
bar (about 68 dp at scale 1.0, more at scale 2.0, since the bar keeps scaled texts) always fits. The bar keeps the
scaled `button` style in landscape (it is not part of the one-block board).

### 5. TalkBack order (CR23, replaces review U7's order)

Same order in portrait and landscape, following the reading order of the screen (left to right, then top to bottom)
except for the current player's header, which comes first (U7):
1. the column headers, the current player's first, each with its total (« Anna, au lancer, 60 points, dernière
   volée : 3 marques »; solo: no points), then the others in configuration order;
2. « Raté » (the top cell of the button column, on the right of the headers);
3. row by row from 20 to Bull: first the row's cells in column order (each cell already names its target, « 20,
   Anna : 2 marques »), then its buttons, single, double, triple (« 20 », « Double 20 », « Triple 20 »), as they are
   read from left to right; a dead row: its single merged node, then its buttons;
4. the action bar: « Annuler », then « Joueur suivant » (or the CR25 confirm label), as today.

There is no points row any more. Points 1 to 3 are the board's own order, set with the existing traversal groups and
indexes of `CricketBoard` (`isTraversalGroup`, `traversalIndex`), so they do not depend on the side of the button
column. The place of the zone and of the action bar around the board is not changed by this addendum (portrait:
title, zone, board, action bar; landscape: title and board, then zone and action bar). In a right-to-left locale the
reading order would mirror with the layout (no such locale is shipped).

## Files and types added or changed

`app/src/main/kotlin/io/github/darkcosmos/kosmodarts/` (below, `ui/...`):
- `ui/cricket/CricketGameScreenState.kt`: `throwDart`, `CricketTargetButton` (dart, label, description, enabled),
  `buttonRows`, `headerTotals` (none in solo); `multiplier`, `toggleMultiplier`, `throwNumber`, `throwOuterBull`,
  `throwBull`, `areBullKeysEnabled` removed (R5); `zoneScoreOf` removed (R5, R6);
- `ui/cricket/CricketLayoutPolicy.kt`: new contract (button column at the end, 48 dp rows in portrait, header with
  totals and the "RATÉ" cell, zone 84/68, `totalTextSize`, landscape top band, single-line zone, rows filling the
  height with a 32 dp floor, landscape right column of at least 204 dp for the zone and the action bar);
- `ui/cricket/CricketBoard.kt`: no label column, no points row; header with the totals; the "RATÉ" cell at the end
  of the header row; `CricketTargetButtons` at the end of each row; dead-row strike over the cells only;
- `ui/cricket/CricketTargetButtons.kt` (new): the buttons of one row;
- `ui/cricket/CricketGameScreen.kt` (the `Actions` bar, "ANNULER" + "JOUEUR SUIVANT", is kept as is),
  `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt`; `ui/components/ActionBar.kt` is not changed;
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
- turn in progress, 2 and 4 players (portrait and landscape): player columns on the left, button column on the
  right, "RATÉ" alone at the top of the button column, "ANNULER" and "JOUEUR SUIVANT" in the bottom bar;
- turn waiting for "Next player": all buttons locked;
- a dead row: muted, struck cells, buttons enabled;
- 25-Bull off: no Bull row, so no 25/BULL buttons;
- solo: no total anywhere on the game screen, nor on the home card;
- landscape: single-line zone in the top band, totals in the column headers, no scrolling;
- pending win (CR25), also by a non-thrower: "ANNULER" and the confirm label ("VALIDER LA PARTIE", the longest) in
  the bar, in portrait (162 dp per button) and in landscape (96 dp per button at 4 players);
- totals with 1, 3 and 4 digits in a 48 dp column;
- font scale 2.0 with 4 players (portrait: rows scroll, header with totals, "RATÉ" and the action bar pinned;
  landscape: no scroll, fixed sizes);
- landscape with the system bars hidden, and after a swipe that brings them back.

### Strings (new, `values/` and `values-fr/`)

| Strings | English | French |
|---|---|---|
| Bull buttons label (both, the dots are drawn) | "BULL" | identical |
| TalkBack, double | "Double %1$s" | « Double %1$s » |
| TalkBack, triple | "Triple %1$s" | « Triple %1$s » |
| TalkBack, header total (plurals `cricket_header_points`) | one: "%1$d point"; other: "%1$d points" | one: « %1$d point »; other: « %1$d points » |

"25", "BULL", "RATÉ" / "MISS" and "ANNULER" / "UNDO" (`key_undo`, in the bar as today) reuse the existing strings.
The X01 Double/Triple strings stay (X01 keypad).
`cricket_closed_targets` ("%1$d/%2$d") is removed in both languages (Q2, Q6 A, task R6). The header total plurals
are used in both orientations.

## Test plan

Rules cited: CR1, CR7, CR15, CR22, CR23, CR24, CR25 (new text).

`:app` pure JVM:
- `CricketGameScreenStateTest`: CR24 `throwDart` records D20 and T20 as one dart each; refuses 25 and Bull with
  25-Bull off; refuses any dart while a turn waits (CR7); a dart on a dead row is recorded; `buttonRows` gives 3
  buttons for 20 to 15 and 2 for Bull, none with 25-Bull off; buttons all disabled while a turn waits and enabled on a
  dead row; `headerTotals` gives the points, and nothing in a solo game.
- `CricketReducerTest`: `CricketDartTapped` reaches `throwDart`; undo after a triple removes the triple.
- `CricketLayoutPolicyTest` (CR22): portrait 360 x 568, 4 players, 7 rows, scale 1.0 does not scroll and sums to at
  most 568; every button is at least 48 x 48 at every player count and scale 1.0 to 2.0; player columns 48/64/96 dp
  at 4/3/2 players; the button column is the last column (its start equals the sum of the player columns) and the
  "RATÉ" cell has the button column's width and the header's height; scale 2.0 scrolls the rows only; the portrait
  action bar keeps 162 dp per button at 360 dp wide; `totalTextSize` for 1 to 5 digits at 48 and 96 dp, never above
  the column; landscape: at 640 x 336 and at about 412 x 370 (Pixel 7), 4 players, 7 rows, scale 1.0 and 2.0,
  nothing scrolls, the top band has one height for the zone and for the title plus column header, the rows fill the
  height left (about 37 dp at 640 x 336, about 48 dp at 412 x 412 with the bars hidden) and never go under the 32 dp
  floor; the right column is at least 204 dp wide at 640 dp and 1 to 4 players, separated from the board by the
  12 dp gap, and the height left under its zone is at least the bar's two-line height at scale 2.0.

Robolectric (`CricketScreenTest`):
- CR24: each button sends its dart (20, D20, T20, 25, Bull, RATÉ); the single, double and triple show the same
  number with 0, 2 and 3 dots, and their TalkBack texts differ; no Bull node with 25-Bull off; locked while a
  turn waits;
- CR22: fit at 360 x 640 scale 1.0 with 4 players (no scroll), also with the "VALIDER LA PARTIE" bar pending, at
  scale 2.0 (rows scroll; header with totals, "RATÉ" and the action bar with "ANNULER" fully visible), landscape
  640 x 360 at 1.0 and 2.0 with no scroll container; each button's bounds at least 48 x 48 dp in portrait, at least
  48 dp wide and 32 dp tall in landscape; buttons vertically aligned with their row's cells (same top and bottom);
  Q7 B: every target button's left edge is at or right of the right edge of the last player cell of its row, and in
  portrait the triple's right edge is the board's right edge; the "RATÉ" cell aligned with the headers and as wide as
  the button column; Q9 B: no undo node in the board, the undo node (`KeypadTags.UNDO`) is in the action bar, on the
  left of the primary button, same top and bottom; totals and button labels not clipped;
- CR22 action bar: in portrait and in landscape (640 x 360, 4 players), at scale 1.0 and 2.0, "ANNULER" and "JOUEUR
  SUIVANT", then "ANNULER" and "VALIDER LA PARTIE" (French) and "CONFIRM MATCH" (English): no label clipped, every
  line break between words (the existing U11 check, `breaksOnlyBetweenWords`), both buttons at least 48 dp tall and
  of the same height;
- CR22: the zone has no total and no "n/7", in a solo game too; nothing on the game screen shows "n/7";
- CR22 landscape: zone on one line (name, MPR, darts in the same row of bounds), its height equal to the
  `MatchHeader` plus the column header; no scroll container on the screen;
- CR22 landscape (Q5 B): the system bars are hidden while the landscape game screen is shown and restored when it
  leaves (portrait, between legs, home), tested through a fake of the insets controller seam (the decision to hide
  is a pure function of orientation and screen, tested on the JVM);
- CR23: TalkBack order of section 5 (headers with the current player first, « Raté », then for each row its cells
  before its buttons, then « Annuler » and the primary button), asserted on the traversal order of the semantics tree
  in portrait and in landscape; dead-row merged node and strike node kept.

Roborazzi captures (record only, Pixel 7, French unless stated), re-recorded: `board_2p_portrait_fr`,
`zone_miss_2p_portrait_fr`, `board_2p_landscape_fr`, `board_4p_portrait_fr`, `board_4p_landscape_fr`,
`board_4p_portrait_en`, `board_4p_portrait_fr_font200`, `board_4p_360x640_fr`, `board_4p_640x360_fr`, the dead rows
(2 and 4 players, with the grayscale copy), solo, 25-Bull off, waiting turn, the three win captures. Removed:
`modifier_triple_portrait_fr`, `keypad_with_bull_fr`, `keypad_without_bull_fr`. Added: `buttons_with_bull_fr`,
`buttons_without_bull_fr` (board crops), `totals_4digits_4p_portrait_fr`, `board_4p_412x412_landscape_fr` (Pixel 7
with the bars hidden), `win_pending_4p_landscape_fr` (the two-line "VALIDER LA PARTIE" in the 96 dp landscape bar).
The solo home capture of `CricketSetupScreenshotTest` is re-recorded without "4/7" (R6). X01
captures checked with `cmp` against the baseline (see Out of scope).

Local checks, every task: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`,
`kosmo-detekt` (exit 0).

## How Kosmo verifies on the phone

Replaces steps 3, 4, 7, 13, 14, 16 and 17 of the main plan for the game screen; the other steps stay.
1. Start a Cricket game with 2 players: there is no keypad and no Double/Triple key. The players' columns are on the
   left, the buttons on the right: each row of the board ends with its three buttons, from left to right the number
   alone, the number with two dots (double), with three dots (triple); the Bull row has "BULL" and "BULL" with two
   dots; the target column is gone; "RATÉ" alone sits at the top of the buttons, next to the names; "ANNULER" and
   "JOUEUR SUIVANT" are in the bottom bar, as before.
2. Tap the triple 20: one dart, three marks for the player. Tap the double 19, then the single 18: three darts in the
   zone.
3. The zone shows the name, the MPR and the darts, but no total. Each total is in large type at the top of its
   player's column, above the name, and always visible.
4. "RATÉ" counts a miss; "ANNULER" in the bottom bar removes the last dart, including a triple.
5. While "JOUEUR SUIVANT" waits, no target button reacts.
6. Close the 20 for everyone: the 20 row is greyed and struck across the players' cells; its buttons still work and
   play the closed-sector sound.
7. 25-Bull off: no Bull row and no 25/BULL buttons.
8. Solo: no total and no "4/7" on the game screen; leave the game, the home "resume" card shows no "4/7" either.
9. 4 players on a small phone, portrait: every row and its buttons visible without scrolling; totals readable.
10. Landscape with 4 players: the status and navigation bars disappear; the same board as in portrait, all 7
    targets visible without scrolling; the player's zone is one line (name, MPR, darts) at the top right, as tall as
    the title plus the names row; totals above the names; the buttons on the right of the players' cells, lined up
    with their target; "ANNULER" and "JOUEUR SUIVANT" at the bottom right, on two lines if needed, no word cut;
    nothing cut. Win a leg: "ANNULER" and "VALIDER LA MANCHE" (or "LA PARTIE") are readable there too. A swipe from
    the edge brings the bars back for a moment; back to portrait, the bars are back for good.
11. Largest system font, 4 players, portrait: the rows scroll, but the names with their totals, "RATÉ", "ANNULER"
    and "JOUEUR SUIVANT" stay visible. In landscape, everything stays in one block.
12. TalkBack: the headers with their totals, then « Raté », then for each row its cells (« 20, Anna : 2 marques »)
    and then its buttons (« 20 », « Double 20 », « Triple 20 »), then « Annuler » and « Joueur suivant ».

## Tasks

### R1 - Rules text, dart buttons model and event

- Goal: rewrite CR22 (zone without total and without "n/7", layout contract of section 4, portrait and landscape,
  system bars hidden in landscape and rows down to 32 dp there, Q5 B), CR23 (no label column, no points row, totals
  at the top of each column header, the "RATÉ" cell alone at the top of the button column, buttons, TalkBack order of
  section 5 with each row's cells before its buttons, undo kept in the action bar, Q9 B), CR24 (target buttons with
  the dot representation, in a button column at the end of the board, on the right of the player columns, Q7 B) and
  CR20 (no "4/7" on the solo home card, Q6 A) in
  `docs/rules/cricket.md`; add `throwDart`, `CricketTargetButton`, `buttonRows`, `headerTotals` (none in solo) to
  `CricketGameScreenState`; `AppEvent.CricketDartTapped` and its `CricketReducer` branch; the new strings. The old
  keypad and modifier stay working until R5, so the build stays green.
- Files: `docs/rules/cricket.md`, `ui/cricket/CricketGameScreenState.kt`, `ui/app/AppState.kt`,
  `ui/app/CricketReducer.kt`, `res/values/strings.xml`, `res/values-fr/strings.xml`, `CricketGameScreenStateTest`,
  `CricketReducerTest`.
- Depends on: approval of this addendum (Q1 to Q7, Q9 and Q10 answered). CR22 is written in full here, landscape
  included (Q10 A).
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a state holder extension and its JVM tests following the existing reducer pattern.

### R2 - Layout policy for the button board

- Goal: rewrite `CricketLayoutPolicy` to the contract of section 4 (player columns then the button column at the
  end, Q7 B; 48 dp rows; pinned header with the totals and the "RATÉ" cell alone of the same height and the button
  column's width, Q9 B; zone 84/68; `totalTextSize`; player column widths; the action bar budget with "ANNULER" and
  "JOUEUR SUIVANT", 162 dp per button in portrait at 360 dp) in portrait, and in landscape (system bars hidden, Q5 B)
  the top band (one height for the single-line zone and for the title plus the column header with totals), the
  fixed landscape text sizes, the dart cells at their minimum, the row height that shows every row without scrolling
  (32 dp floor) and the right column (at least 204 dp, with room under the zone for a two-line action bar at scale
  2.0) on the right of the board (Q10 A). With
  `CricketLayoutPolicyTest` rewritten (old cases about the label column and keypad rows removed only where the
  contract they prove is removed; each new case cites CR22).
- Files: `ui/cricket/CricketLayoutPolicy.kt`, `CricketLayoutPolicyTest`.
- Depends on: R1.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: opus
- Why this model: the dp budget is tight in both orientations (12 dp spare in portrait at 360 x 640, no scrolling
  allowed in landscape) and this contract needed two review rounds before (U1); the arithmetic and font-scale cases
  are subtle.

### R3 - Portrait screen: buttons in the board, totals on top, zone without total

- Goal: `CricketBoard` without label column and without points row: the player columns first, then the button
  column at the end (Q7 B); pinned header with the large totals and, at its end, the "RATÉ" cell alone (Q9 B);
  `CricketTargetButtons` at the end of each row (single, double, triple from left to right, sending
  `CricketDartTapped`, the number with 0, 2 or 3 drawn dots, in the design system's key style); dead-row strike over
  the cells only; `CricketPlayerZone` without total; portrait `CricketGameScreen` without `CricketKeypad`, the
  existing `Actions` bar kept unchanged ("ANNULER" + "JOUEUR SUIVANT" or the confirm label, Q9 B); tags and the
  TalkBack order of section 5 (cells before buttons in each row); the Robolectric portrait cases (including the Q7 B
  side, the Q9 B undo place and the action bar labels) and the portrait captures; `docs/design/design-system.md`
  (components table, 6.5 portrait).
- Files: `ui/cricket/CricketBoard.kt`, `CricketTargetButtons.kt` (new), `CricketGameScreen.kt`,
  `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt`, `docs/design/design-system.md`,
  `CricketScreenTest`, `CricketScreenshotTest`.
- Depends on: R2.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; X01 captures `cmp`-identical to the
  baseline; `kosmo-detekt` exits 0. If beyond about 60 tool calls, stop green and report what is left.
- Model: sonnet
- Why this model: composables built from the existing board and button components against a fixed policy.

### R4 - Landscape screen

- Goal: the landscape layout of section 4 (Kosmo, Q1), with the board on the left and the zone and action bar on
  the right (Kosmo, Q10 A): the same board as portrait, not turned (player columns
  then the button column, "RATÉ" alone at the top of the buttons), with no scrolling; the top band with the
  `MatchHeader` (and the S/M chips) over the column header with the totals, and beside it the single-line zone (name,
  MPR, minimal dart cells) of the same height; the existing action bar ("ANNULER" + "JOUEUR SUIVANT", scaled labels
  on up to two lines) pinned at the bottom of the zone column; the fixed landscape text sizes; the system bars
  hidden while the Cricket game screen is shown in landscape and restored when it leaves or turns to portrait (Q5 B,
  through the platform `Window.insetsController`, available from API 30 = `minSdk`, no new dependency; the hide-or-show decision is a pure `SystemBarsPolicy`,
  tested on the JVM); its Robolectric cases at 640 x 360 and 412 x 412, scale 1.0 and 2.0 (no scroll container,
  one-line zone, band heights equal, nothing clipped, buttons at least 48 dp wide and 32 dp tall, buttons on the right
  of the player cells, the action bar labels with "JOUEUR SUIVANT" and "VALIDER LA PARTIE" / "CONFIRM MATCH" not
  clipped and broken only between words, the TalkBack order); its captures; the 6.5 landscape paragraph of the
  design system.
- Files: `ui/cricket/CricketGameScreen.kt`, `CricketBoard.kt`, `CricketPlayerZone.kt` (single-line variant, the
  shared `DartSlots` unchanged so X01 is untouched), `ui/cricket/SystemBarsPolicy.kt` (new), `docs/rules/cricket.md`,
  `docs/design/design-system.md`, `SystemBarsPolicyTest` (new), `CricketScreenTest`, `CricketScreenshotTest`.
- Depends on: R3.
- Done when: same checks as R3.
- Model: sonnet
- Why this model: it reuses the portrait board and the policy of R2, where the hard arithmetic is; the system bars
  are one platform call behind a tested pure decision.

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

### R6 - Remove the solo "n/7" from the home card

- Goal: the home resume card of a solo Cricket game no longer shows "4/7" (CR20 amended in R1): remove
  `ResumeSummary.PlayerScore.closedTargets`, its use in `ResumeCard.tileValue`, `CricketZoneScore.ClosedTargets` if
  nothing uses it any more, and `cricket_closed_targets` in both languages; adapt the tests and re-record the solo
  home capture. `CricketPlayerState.closedTargetCount` in `:game-engine` stays (engine API, tested in
  `CricketStatisticsTest`).
- Files: `ui/home/ResumeSummary.kt`, `ui/components/ResumeCard.kt`, `ui/cricket/CricketGameScreenState.kt`,
  `res/values/strings.xml`, `res/values-fr/strings.xml`, `ResumeSummaryCricketTest`, `CricketSetupScreenshotTest`.
- Depends on: R5 (Kosmo, Q6 A).
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; X01 captures `cmp`-identical; the
  `home_*` X01 captures unchanged; `kosmo-detekt` exits 0.
- Model: haiku
- Why this model: a fully specified removal of one display, with no design choice.

### R7 - Delete Kosmo's reference screenshot

- Goal: delete the Dartsmind PNG that Kosmo uploaded at the repository root in commit `aff3b27` ("Add files via
  upload"), as Kosmo asked once the work is done. Check with `git grep` that no file of the repository names it.
  Last commit of the rework, before the code review of the final head.
- Files: the PNG at the repository root (deleted), this plan's Implementation notes.
- Depends on: R6.
- Done when: `git ls-files ':(glob)*.png'` (root only) lists nothing; `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: haiku
- Why this model: one file deletion, fully specified.

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
  Applied in section 4 and tasks R2 and R4.
- Kosmo, Q2: the solo "4/7" display is useless and is removed: no total and no "n/7" on the solo game screen.
- Kosmo, Q3 A: rule unchanged (CR1). With 25-Bull on, a Bull row with "25" and "BULL"; with it off, no Bull row and
  no Bull button. No engine or save change.
- Kosmo, Q4 A: the zone keeps the name, the MPR, the set/leg chips and the visit's darts.
- Kosmo, Q5 B: in landscape the Cricket game screen hides the system bars (a swipe from the edge brings them back),
  and the buttons may go under 48 dp tall on small phones (32 dp floor in the policy). The 48 dp exception for this
  screen in landscape is proposed in the PR's "Proposed owner changes"; CLAUDE.md is never edited by the squad.
- Kosmo, Q6 A: the "4/7" is also removed from the solo home resume card (task R6, CR20 amended).
- Kosmo, Dartsmind reference: added to the branch as a temporary file, to be deleted once the work is done (task R7);
  no file that stays in the repository names it.
- Kosmo, clarification on the reference: take only the organization of the buttons (aligned with the score rows) and
  its representation of the double and the triple (the number with two or three dots); everything else follows
  Q1 to Q6 and the design system. Applied: the "D20"/"T20" labels of the first version are replaced by the dots, and
  the question about the labels (Q8 in an intermediate version) is withdrawn.
- Kosmo, Q7 B: the button column goes on the right: the player columns on the left, the buttons on the right, as in
  the reference. Applied in sections 1 and 4 and tasks R1 to R4: the board is built start to end (player columns,
  then buttons), single, double, triple from left to right; the first proposal (buttons on the left) is dropped.
- Kosmo, Q9 B: the undo button stays in the bottom bar, as today ("ANNULER" then "JOUEUR SUIVANT" or the confirm
  label, the existing `ActionBar` unchanged); "RATÉ" stays alone at the top of the button column, over its whole
  width. The first proposal (undo next to "RATÉ", bar with "JOUEUR SUIVANT" only) is dropped; no undo icon is added.
- Kosmo, Q10 A: in landscape the zone column stays on the right of the screen, as planned: the zone at the top
  right, "ANNULER" / "JOUEUR SUIVANT" at the bottom right; the board (player columns, then the buttons) on the left,
  so the target buttons sit in the middle of the screen. Applied in section 4 and tasks R1, R2 and R4; the mirrored
  layout (zone column on the left) is dropped.
- Developer, pending design review: the totals at the top of each column header, as the largest text of the board,
  in both orientations (no points row), because landscape has no room for a points row (not copied from the
  reference).
- Developer, pending design review (consequence of Q7 B): TalkBack reads each row in reading order, its cells first
  (each already names the target) and then its buttons, single, double, triple; the current player's header still
  comes first (U7). The board uses start and end, so it would mirror in a right-to-left locale; none is shipped, so
  left-to-right is not forced and no right-to-left test is added.
- Developer, pending design review (consequence of Q9 B): the landscape action bar keeps the existing side-by-side
  `ActionBar` with scaled labels (96 dp per button at 4 players, labels on up to two lines with the word-safe
  auto-size); its height comes from the free space under the zone, never from the rows.
- Developer, pending design review: in landscape the S/M chips move to the end of the `MatchHeader` line, and the
  landscape texts use the fixed styles so the one-block layout holds at every font scale.
- Developer, pending design review: buttons of a dead row stay enabled (closed-sector sound, AN30); the strike crosses
  only the cells.

## Open questions

Product questions for Kosmo (in French, for main to relay):

None. Q1 to Q7, Q9 and Q10 are answered by Kosmo (see Decisions); Q8 was withdrawn.

## Risks

- Portrait 360 x 640 with 4 players and the Bull: 12 dp spare at all minimums; the zone minimum (68 dp) and the
  header (about 64 dp with the total) must be measured against the real text lines, or the rows scroll on small
  phones.
- Player columns of 48 dp at 4 players: names get a strong ellipsis, totals of 4 digits fall to 16 to 20 sp, and at
  font scale 2.0 even 3 digits may need 14 sp; to be asserted by the clipping tests.
- Button labels: two digits plus drawn dots in a 48 x 48 dp button at font scale 2.0 (portrait): the number and the
  dots must both fit the height; the clipping test covers it. In landscape (rows down to 32 dp), the dots are the
  first thing to lose room: they keep a fixed size and the number shrinks first.
- The dots are a new convention for the players: the phone check and the TalkBack texts confirm that the double and
  the triple are told apart.
- Landscape touch targets: about 37 dp tall at 640 x 360 and about 48 dp on a Pixel 7 with the bars hidden, accepted
  by Kosmo (Q5 B) but against the CLAUDE.md 48 dp rule until he adds the exception; reviewers will see the conflict.
- Hidden system bars: they must come back when the screen leaves landscape or the game (between legs, end, home,
  abandon), or the rest of the app stays without bars; tested through `SystemBarsPolicy` and checked on the phone.
- Landscape at font scale 2.0: with scaled texts the 14 sp line (45 dp) does not fit a 37 to 42 dp row; the plan uses
  fixed text sizes in landscape (no growth with the system font), an accessibility trade-off for the UX review.
- Landscape single-line zone: in 204 dp, name (strong ellipsis), MPR and three minimal dart cells (about 30 dp each)
  just fit; "RATÉ" in a 30 dp cell at 12 sp and the "VICTOIRE" banner (CR25) in a band of about 72 dp must be checked
  by the clipping tests and captures.
- Landscape totals above the names in 64 dp columns: 4-digit totals need 16 to 20 sp.
- About 25 existing Robolectric cases and 20 game captures use the keypad or the modifier and are rewritten; the
  X01 captures must stay byte-identical.
- Landscape action bar (Q9 B): at 4 players each button has 96 dp instead of about 126 dp today; "JOUEUR SUIVANT" and
  "VALIDER LA PARTIE" go to two lines and shrink to about 16 to 18 sp, and at font scale 2.0 the word-safe auto-size
  may shrink them further. The bar takes its height from the free space under the zone (about 255 dp), so the rows
  are not affected; the label tests at scale 1.0 and 2.0 and the `win_pending_4p_landscape_fr` capture check it.
- Portrait action bar (Q9 B): unchanged from today (162 dp per button); its height must stay within the 48 dp of the
  budget plus the 12 dp spare at 360 x 640 scale 1.0 with "VALIDER LA PARTIE", or the rows scroll on small phones in
  a pending win; covered by the policy and fit tests.
- Right-hand button column (Q7 B): the reading order puts each row's cells before its buttons, so a TalkBack user
  hears a row's state before its buttons; to be confirmed by the UX review. Under Q10 A the target buttons are in
  the middle of the landscape screen, beside the action bar (the Bull and 15 rows are at the height of "ANNULER"):
  the 12 dp gap between the board and the right column must be kept so that a tap aimed at a triple does not land
  on "ANNULER" (policy test).
- Risk removed by Q9 B: no undo icon and no 48 dp undo key to fit next to "RATÉ" in a 144 dp cell.

## Implementation notes

(One line per task: task id, deviations, where the main pieces live.)
