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
while the input is enabled and only if the dart is offered by the current configuration. It refuses (ignores, no
state change, no sound) a dart while a turn waits for "Next player" (CR7), a dart while a win is pending (CR25, the
input is locked until "Confirm" or "Undo"), and a 25 or Bull with 25-Bull off (CR1). The X01 keypad and its events
are untouched.

Button model (review A2, A4, T2): `CricketTargetButton(dart: Dart, dotCount: Int, isEnabled: Boolean)`, with
`dotCount` 0, 2 or 3 (Bull row: 0 and 2), in a new file `ui/cricket/CricketTargetButtonModel.kt` with the
`buttonRows` builder, so that `CricketGameScreenState.kt` (218 lines) does not grow towards a detekt size limit. It
holds no text: the composable resolves the label (the segment number, or `BULL`) and the TalkBack text (« Double
%1$s », « Triple %1$s », « 25 », « Bull ») from resources, and the JVM tests assert the data. The drawn dots set a
custom semantics property `CricketSemantics.DotCount` with the number of dots actually drawn, the seam for the
Robolectric "0, 2, 3 dots" check.

Spacing between buttons (review U4 a): each button keeps a touch area of the full cell (at least 48 x 48 dp in
portrait and in landscape, the Pixel 7 baseline), and its drawn key is inset by 2 dp on each
side inside that area, so adjacent keys show a 4 dp gap, horizontally and between rows, without shrinking the touch
areas. The captures show it.

Buttons of a dead row (CR15) stay enabled: a dart in a dead sector is a real dart and plays the closed-sector sound
(AN30). They take the muted style of the row, and the strike-through line crosses only the player cells, not the
buttons, so that they do not look disabled. Every button is locked while a turn waits or a win is pending.
Dead and locked must not look alike (review U4 b), using only existing design-system tokens (no new local color):
- dead: key container and label at full opacity, label in `onBackgroundMuted` on the key's `surfaceHigh`, with a
  text contrast of at least 4.5:1 in the dark theme (asserted by a JVM test from the token values);
- locked: the existing 35 % opacity of the whole key (`States.DISABLED_ALPHA`), container and label.

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
- picks its size from the space: `CricketBoardLayoutPolicy.totalTextSize(digits, columnWidthDp, candidates)` returns
  the largest of 24, 20, 16, 14 sp in portrait (20, 16, 14 sp fixed in landscape) whose line fits the column (digits
  x 0.6 em + 4 dp, the em measured in dp at the current font scale), and the fixed 14 sp when none fits (section 4);
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

### 4. Layout contract (new CR22), pure `CricketBoardLayoutPolicy`

Reference device (Kosmo, instruction of the R2 stop): small phones are not a concern; the smallest phone is the
Pixel 7, 1080 x 2400 px at 2.625 px per dp, so 411 x 914 dp (Robolectric's `RobolectricDeviceQualifiers.Pixel7`,
`w411dp-h914dp`, 420 dpi). The 360 x 640, 360 x 568 and 640 x 336 budgets of the earlier versions are no longer a
design constraint and no test uses them. System bars on the Pixel 7, taken as upper bounds (the phone check confirms
them): portrait, status bar 52 dp and navigation bar 48 dp (three-button navigation; 24 dp with gestures), so at
least 914 - 100 = 814 dp of usable height; landscape, status bar 32 dp at the top, navigation bar 24 dp at the bottom
(gestures) or 48 dp on a side (three buttons), and the camera hole as a side cutout inset of at most 48 dp.

Line heights used below are the ones developer-build-R2 measured in Robolectric (Android's font scaling is not
linear, so each text size has its own effective scale at a given system font scale):

| System font scale | 1.0 | 1.3 | 1.5 | 2.0 |
|---|---|---|---|---|
| 14 sp body line (names, title), dp | 22.5 | 30.2 | 35.3 | 41.8 |
| 20 sp effective scale | 1.0 | 1.18 | 1.3 | 1.7 |
| 24 sp display line (totals), dp | 37.5 | 41.3 | 43.8 | 56.3 |
| `ActionBar`, one-line labels, dp | 48 | 82 | 88.5 | 110.5 |
| `ActionBar` with "VALIDER LA PARTIE" (at 360 dp wide; taken as the worst case at 411), dp | 71.5 | about 115 | about 125 | 151.5 |

The 1.3 and 1.5 values of the last line are estimates (two lines); R3's Robolectric fit tests measure them at 411 dp.

Board columns, from start (left) to end (right): the player columns, which share the width left equally, then the
button column (Kosmo, Q7 B).
- Button column: 3 x 48 = 144 dp at least (48 x 48 dp per button, the touch-target floor), up to 3 x 56 = 168 dp when
  the player columns keep at least 76 dp each.
- Rows: 48 dp at least (button floor) in both orientations, up to 56 dp; the 14 sp row text (41.8 dp at scale 2.0)
  always fits a 48 dp row. There is no landscape exception any more (the 32 dp floor is removed, see below).
- Column header: 3 dp strip + total line + name line, about 63 dp at scale 1.0 and 101 dp at scale 2.0 in portrait;
  pinned. The button column's top cell ("RATÉ" alone, Kosmo, Q9 B) has the same height, at least 48 dp, and the
  whole width of the button column.
- Fallback only: when the rows do not fit at 48 dp (a screen smaller than the Pixel 7, or a larger "display size"
  setting, both unsupported), the target rows scroll under the pinned header, in both orientations; rows never go
  under 48 dp and nothing is cut. On the Pixel 7 and larger, at every font scale from 1.0 to 2.0, nothing scrolls
  (budgets below). This replaces review U2's "4 visible rows at 360 x 640" (all 7 rows are visible now), and the
  14 sp cap of the totals from scale 1.5 is dropped.
- Action bar: the existing `ActionBar`, unchanged, "ANNULER" then "JOUEUR SUIVANT" (or the CR25 confirm label), labels
  in the `button` style (20 sp) with the word-safe auto-size (U6, U11: a label wraps only between words and shrinks
  rather than cut a word; both buttons take the taller height); side by side, equal width, 12 dp gap, in both
  orientations (the landscape stacking of review U3 is no longer needed, see below). Effective label size at least
  12 sp at scale 1.0 to 2.0 (asserted).

Policy inputs (R2's finding): the new pure `CricketBoardLayoutPolicy` (its own file and test; the old
`CricketLayoutPolicy` object already has 10 functions and stays, unchanged, until R4 removes its last user and
deletes it with its test) does no font-scale arithmetic of its own. The screen measures the pinned parts with
Compose (the board takes the height left with `weight(1f)` under a `BoxWithConstraints`) and passes dp values: the
board's width and height, the column header's line heights read from the text styles with `Density` (which applies
Android's non-linear font scaling), the player and row counts and the orientation. The policy returns the row height
(48 to 56 dp), whether the rows scroll (fallback only), the column widths, the button width, the landscape band height
and the total's size. `totalTextSize(digits, columnWidthDp, candidates)` receives the candidate sizes with their
measured em in dp and returns the largest that fits (`digits x 0.6 em + 4 dp`); when none fits (4 digits at scale
2.0 in a 61 dp column), it returns the fixed 14 sp size (`density.fixedSp`, 37.6 dp for 4 digits), so a total is
never cut. The JVM tests use the measured values of the table above as fixtures; the whole-screen fit on the Pixel 7
is proven by the Robolectric tests of R3 and R4, which measure the real texts.

Portrait budget at 411 x 814 (worst bars), 4 players, 7 rows (Bull on), "VALIDER LA PARTIE" pending (the tallest
bar): top margin 8 + `MatchHeader` (one 14 sp line) + gap 6 + zone + gap 6 + column header + 7 x 48 + gap 6 + bar +
bottom margin 8.

| Scale | `MatchHeader` | Zone | Header | Rows | Bar | Total | Spare at 814 (at 843, gestures) |
|---|---|---|---|---|---|---|---|
| 1.0 | 24 | 84 | 63 | 336 | 71.5 | 612.5 | 201.5 (230.5) |
| 1.3 | 32.2 | 112.8 | 74.5 | 336 | 115 | 704.5 | 109.5 (138.5) |
| 1.5 | 37.7 | 126 | 82.1 | 336 | 125 | 740.8 | 73.2 (102.2) |
| 2.0 | 44.6 | 126 | 101 | 336 | 151.5 | 793.1 | 20.9 (49.9) |

(The zone is 84 dp x the 14 sp effective scale, capped at x 1.5; margins 8 and gaps 6 are the normal values, 18 dp
more could be taken back with the minimum ones.) So at the Pixel 7 in portrait nothing scrolls at any font scale,
with 1 to 4 players (1 to 3 players have the same heights; the solo header has no total line, so it is shorter), with
or without the Bull (without it, 48 dp more spare), during a pending win. The tightest case, scale 2.0, 4 players,
pending win, has about 21 dp spare: R3's Robolectric fit test at 411 x 814 is the proof; if it scrolls, R3 stops and
reports to main (a design change, not a local fix).

Portrait width: 411 - 2 x 12 = 387 dp. 4 players: 144 dp of buttons, 61 dp per player column; 3 players: the button
column grows to 159 dp (53 dp buttons) with 76 dp player columns; 2 players and solo: 168 dp of buttons, 110 and
219 dp player columns. Totals at 4 players (61 dp): 3 digits at 24 sp at scale 1.0 and at 16 sp at scale 2.0, 4
digits at 20 sp at scale 1.0 and the fixed 14 sp at scale 2.0.

Portrait action bar width: (387 - 12) / 2 = 187 dp per button, 163 dp for the label after the 12 dp padding on each
side ("VALIDER LA PARTIE" may still wrap on two lines between words, as today; the budget above takes the measured
worst case).

Landscape (Kosmo, Q1): the same board as in portrait (targets in rows, the buttons at the end of each row, on the
right of the player cells, not turned), everything visible at once with no scrolling, the fonts may be slightly
reduced. Layout (Kosmo, Q10 A: the board on the left, the zone and the action bar on the right):
- top band, one height for both columns: on the left the board's pinned column header (color strip, the total in
  large type, the name under it) and "RATÉ"; on the right the `MatchHeader` ("CRICKET · STANDARD", M14 position, the
  S/M chips at its end) over the current player's zone, the two together limited to the height of the band (Kosmo's
  Q1 suggestion: a zone limited to the height of the board header plus the title). The title moves from above the
  board (earlier versions) to above the zone: with the title over the board the rows get (411 - 8 - 57 - 24) / 7 =
  46.1 dp, under 48 dp; with the title in the right column they get 49.5 dp (developer proposal, accepted by
  Kosmo in Q11 A; the UX review judges its details);
- the zone is one line: name (ellipsis), "MPR 2,4", then the visit's dart cells at their minimum size (about 28 dp
  tall). The S/M chips (kept, Q4 A) stay at the end of the `MatchHeader` line in landscape;
- "RATÉ" alone in the top cell of the button column, as in portrait, at the height of the column headers (part of the
  band's height). The button column is at the right edge of the board, so in landscape it sits in the middle of the
  screen, between the player columns and the zone column (Q10 A);
- below the band: on the left the 6 or 7 target rows (player cells, then their buttons); on the right the action bar
  pinned to the bottom of the zone column, as today, its two buttons side by side as in portrait ("ANNULER" then
  "JOUEUR SUIVANT" or the confirm label), about 173 dp each at 4 players;
- the landscape text sizes: board names and the `MatchHeader` at 14 sp (as portrait at scale 1.0), totals at 20 sp
  at most (the "slightly reduced" font, Q1); button labels as in portrait. To stay in one block with 48 dp rows at
  every system font size, the landscape board, zone and title use the existing "fixed" styles (`density.fixedSp`, as
  `scoreHuge` and `tileScore` already do), so their size does not follow the font scale (developer proposal, accepted
  by the UX review with U5: Kosmo is told in the PR text that in landscape the board, zone and title do not follow
  the system font size; portrait keeps scaled texts). Still needed on the Pixel 7: with scaled texts at scale 2.0 the
  band is 3 + 53.1 (20 sp line x 1.7) + 41.8 = 98 dp and the rows get (411 - 8 - 98) / 7 = 43.6 dp; at scale 1.3
  already 3 + 36.9 + 30.2 = 70 dp and (411 - 8 - 70) / 7 = 47.6 dp, both under 48 dp. Alternative rejected: texts
  scaled up to a cap of about 1.2, one more rule for no visible gain. The current player stays identifiable in landscape by the "▶" before the name and the `accent`
  border of the column, which do not depend on the text size.

System bars and display cutout in landscape (Q5 B, review U1):
- the screen keeps the cutout mode set by `enableEdgeToEdge` in `MainActivity` (unchanged) and pads its content
  with insets, never with fixed margins;
- portrait: `safeDrawing` padding, as today (bars, cutout, keyboard);
- landscape with the bars hidden: the padding is the `displayCutout` insets only (on the Pixel 7 the punch hole is on
  one side in landscape; its inset is taken as at most 48 dp), so no content goes under the cutout; the hidden bars'
  insets are 0;
- the hide call uses `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`: after a swipe the bars appear over the content for a
  moment and the layout does not move, because the landscape padding does not include the bars' insets;
- the choice of padding is part of `SystemBarsPolicy`: `decide(isLandscape, isCricketGame)` returns whether to hide
  the bars and which insets to pad (`SAFE_DRAWING` or `CUTOUT_ONLY`), tested on the JVM; "hide" always comes with
  `CUTOUT_ONLY`, "show" always with `SAFE_DRAWING`.

Where the side effect lives (review A3): one `DisposableEffect` in `CricketGameScreen`, keyed on the policy decision,
calls a small `SystemBarsController` interface (production: the activity window's platform `insetsController`, API
30 = `minSdk`, no new dependency; tests: a fake). Its `onDispose` always shows the bars again, so every exit path
(turn to portrait, between legs, end of match, abandon, home, the activity being destroyed) restores them, since each
removes the effect from the composition. This is the only place of the app that hides the bars; it is described in
CR22 and in section 6.5 of the design system (no ADR: it is local to one screen and stores nothing).

Landscape height budget on the Pixel 7, 914 x 411 dp: top margin 4 + band + rows + bottom margin 4, the band being
the column header with fixed texts, 3 dp strip + 20 sp total line 31.3 + 14 sp name line 22.5 = 57 dp (the right
column's title 24 + zone line 28 + gap 4 = 56 dp fits in it). The texts are fixed, so the rows do not depend on the
font scale (1.0 to 2.0):

| Landscape case | Height for the screen | Rows, 7 (Bull on) | Rows, 6 (Bull off) |
|---|---|---|---|
| Bars hidden (Q5 B), cutout on a side (no height lost) | 411 | (411 - 8 - 57) / 7 = 49.5 dp | 56 dp (capped) |
| Bars shown, gesture navigation (32 + 24 dp) | 355 | 41.4 dp | 48.3 dp |
| Bars shown, three-button navigation on a side (32 dp) | 379 | 44.9 dp | 52.3 dp |
| Bars hidden, title over the board (earlier layout) | 411 | 46.1 dp | 53.8 dp |
| Bars hidden, scaled texts at scale 2.0 (band 98 dp) | 411 | 43.6 dp | 50.9 dp |

Conclusion: on the Pixel 7, 48 dp buttons with the 7 rows in one block (Q1) need both the hidden bars (Q5 B) and the
fixed landscape texts, and the title in the right column. With the bars shown, the 7 rows either go under 48 dp (41
to 45 dp, which would need the CLAUDE.md exception) or scroll (against Q1). So Q5 B is still needed, task R4b stays,
and with this layout the 32 dp floor and the CLAUDE.md 48 dp exception are no longer needed: rows are 48 dp at least
in both orientations. Kosmo chose this layout (Q11 A, see Decisions). Spare at 7 rows with the bars hidden:
7 x 1.5 = 10.5 dp; R2's policy test and R4's Robolectric fit at 914 x 411 prove it. Fallback (below the Pixel 7 only): the rows keep 48 dp and scroll under the pinned band, as in
portrait.

Landscape width: 914 - 2 x 12 = 890 dp, minus a cutout of at most 48 dp on one side = 842 dp. Board at 4 players:
4 x 76 dp (`PLAYER_COLUMN_WIDTH`) + 168 dp of buttons (56 dp each) = 472 dp, a 12 dp gap, so 358 dp for the right
column (title line: "CRICKET · STANDARD", the M14 position and the S/M chips; zone line: name, MPR and three dart
cells, about 200 dp). With 1 to 3 players the right column is wider. The right column is at least 340 dp wide at
914 dp with a 48 dp cutout and 1 to 4 players (policy test).

Landscape action bar: side by side as in portrait (review U3's stacking is dropped, it answered the 204 dp right
column of a 640 dp wide screen): (358 - 12) / 2 = 173 dp per button at 4 players, 149 dp for the label, close to the
portrait 163 dp. Under the band the right column has 411 - 4 - 57 - 6 - 4 = 340 dp for the bar, which holds its
151.5 dp at scale 2.0 with "VALIDER LA PARTIE". The bar keeps the scaled `button` style (it is not part of the
one-block board). `ActionBar` is not changed (no `isStacked` parameter), so X01 is untouched.

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
title, zone, board, action bar; landscape: title and board, then zone and action bar; the landscape title, now
above the zone in the right column, keeps its first place through a traversal index). In a right-to-left locale the
reading order would mirror with the layout (no such locale is shipped).

## Files and types added or changed

`app/src/main/kotlin/io/github/darkcosmos/kosmodarts/` (below, `ui/...`):
- `ui/cricket/CricketGameScreenState.kt`: `throwDart` (refuses while a turn waits, while a win is pending, and 25 or
  Bull with 25-Bull off); `buttonRows` (delegating to the model file); `headerTotals: List<Int>?` (plain points, `null`
  in solo, formatted by the composable); `boardPoints` (the points row feed) removed and replaced by `headerTotals` in
  R3, its test cases moved to `headerTotals`, so there is one source for the total (review A1); `multiplier`,
  `toggleMultiplier`, `throwNumber`, `throwOuterBull`, `throwBull`, `areBullKeysEnabled` removed (R5); `zoneScoreOf`
  removed (R5, R6). `CricketBoardRow(target, marks, isDead)` keeps its three fields: `target` still feeds the cells'
  TalkBack text and the buttons, and it had no field tied to the label column or the points row;
- `ui/cricket/CricketTargetButtonModel.kt` (new, R1): `CricketTargetButton(dart, dotCount, isEnabled)` and the
  `buttonRows` builder, no text (review A2, A4, T2);
- `ui/cricket/SystemBarsPolicy.kt` (new, R4): the pure decision (hide or show, `SAFE_DRAWING` or `CUTOUT_ONLY`
  padding) and the `SystemBarsController` interface with its window implementation (review U1, A3);
- `ui/cricket/CricketBoardLayoutPolicy.kt` (new, R2): the new contract from measured dp inputs (button column at the
  end, rows 48 to 56 dp in both orientations with the scroll fallback, header with totals and the "RATÉ" cell,
  `totalTextSize` with the fixed 14 sp fallback, landscape band height, landscape right column of at least 340 dp);
- `ui/cricket/CricketLayoutPolicy.kt` and `CricketLayoutPolicyTest`: unchanged until R4 removes their last user, then
  deleted in R4 (their cases prove the old layout only);
- `ui/cricket/CricketBoard.kt`: no label column, no points row; header with the totals; the "RATÉ" cell at the end
  of the header row; `CricketTargetButtons` at the end of each row; dead-row strike over the cells only;
- `ui/cricket/CricketTargetButtons.kt` (new): the buttons of one row;
- `ui/cricket/CricketGameScreen.kt` (the `Actions` bar, "ANNULER" + "JOUEUR SUIVANT", kept, side by side in both
  orientations; the system bars effect), `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt` (`DotCount`
  property);
- `ui/components/ActionBar.kt`: not changed (the stacked variant is dropped);
- `ui/cricket/CricketKeypad.kt`: deleted (R5);
- `ui/app/AppState.kt` (`AppEvent.CricketDartTapped`), `ui/app/CricketReducer.kt`;
- `res/values/strings.xml`, `res/values-fr/strings.xml`;
- `docs/rules/cricket.md` (CR22, CR23, CR24), `docs/design/design-system.md` (`CricketBoard`, `CricketTargetButtons`
  instead of `CricketKeypad`, section 6.5);
- tests: `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketBoardLayoutPolicyTest` (new),
  `CricketLayoutPolicyTest` (deleted in R4), `CricketScreenTest`,
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
- landscape: title and single-line zone in the right part of the top band, totals in the column headers, no
  scrolling, rows of at least 48 dp;
- pending win (CR25), also by a non-thrower: "ANNULER" and the confirm label ("VALIDER LA PARTIE", the longest) in
  the bar, side by side, in portrait (187 dp per button) and in landscape (173 dp per button at 4 players);
- totals with 1, 3 and 4 digits in the 61 dp column of 4 players;
- font scale 2.0 with 4 players and a pending win (portrait: nothing scrolls on the Pixel 7, header with totals,
  "RATÉ" and the action bar visible; landscape: no scroll, fixed sizes);
- landscape with the system bars hidden, and after a swipe that brings them back (over the content, no layout move);
- landscape on a phone with a display cutout, the cutout on either side: no content under it;
- a dead row next to open rows, while the turn is open and while it waits (dead and locked side by side).

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
  25-Bull off; refuses any dart while a turn waits (CR7); refuses any dart while a win is pending (CR25, review T1),
  the state being unchanged; a dart on a dead row is recorded; `buttonRows` gives 3 buttons for 20 to 15 with
  `dotCount` 0, 2, 3 and 2 for Bull with 0, 2 (review T2), none with 25-Bull off; buttons all disabled while a turn
  waits or a win is pending, and enabled on a dead row; `headerTotals` gives the points as ints (the cases of the
  removed `boardPoints` move here), and `null` in a solo game.
- `CricketReducerTest`: `CricketDartTapped` reaches `throwDart`; it is ignored while a win is pending and for a 25 or
  Bull with 25-Bull off (review T1, no state change and no cue effect); undo after a triple removes the triple.
- `SystemBarsPolicyTest` (CR22, Q5 B, review U1, A3): landscape Cricket game hides the bars with `CUTOUT_ONLY`
  padding; portrait, and every other screen in either orientation, shows them with `SAFE_DRAWING`; "hide" never comes
  without `CUTOUT_ONLY`.
- Dead-style contrast (CR15, review U4 b), in `CricketBoardStyleTest` (new, JVM): the WCAG contrast ratio of
  `onBackgroundMuted` on `surfaceHigh` and on the board background, computed from the dark theme's token values, is
  at least 4.5:1.
- `CricketBoardLayoutPolicyTest` (CR22, new; reference sizes are the Pixel 7, the measured line heights of the
  section 4 table are the fixtures for scales 1.0, 1.3, 1.5 and 2.0): portrait board space left at 411 x 814 by the
  pinned parts of the table (4 players, 7 rows, the "VALIDER LA PARTIE" bar), at each of the four scales: rows of
  48 dp, no scroll, and the sum of the pinned parts and the rows at most 814; the same with 1, 2 and 3 players (review
  T4; the solo header has no total line) and with 6 rows (Bull off); every button at least 48 x 48 dp at every player
  count and scale, in both orientations; player columns 61/76/110/219 dp and button column 144/159/168/168 dp at 4/3/
  2/1 players at 411 dp wide; the button column is the last column (its start equals the sum of the player columns)
  and the "RATÉ" cell has the button column's width and the header's height; the portrait bar has 187 dp per button
  at 411 dp; `totalTextSize` for 1 to 5 digits at 61 and 110 dp and each scale, never wider than the column, the fixed
  14 sp fallback for 4 digits at scale 2.0 in 61 dp; fallback: a board space smaller than 48 dp x the rows plus the
  header scrolls the rows only and keeps 48 dp rows (both orientations); landscape (review T3, now the Pixel 7 only):
  914 x 411 with the bars hidden, with no cutout and with a 48 dp cutout on one side, 1 to 4 players, 6 and 7 rows,
  scales 1.0 and 2.0 (fixed texts, so the same result): nothing scrolls, rows of 48 to 56 dp (49.5 dp at 7 rows), the
  band has one height for the right column (title plus zone line) and for the column header, the right column is at
  least 340 dp wide and separated from the board by the 12 dp gap, its bar has at least 173 dp per button at 4
  players, and the height under the band holds the 151.5 dp bar of scale 2.0.

Robolectric (`CricketScreenTest`):
- CR24: each button sends its dart (20, D20, T20, 25, Bull, RATÉ); the single, double and triple show the same
  number and their `CricketSemantics.DotCount` is 0, 2 and 3 (Bull: 0 and 2), and their TalkBack texts differ; no
  Bull node with 25-Bull off; locked while a turn waits and while a win is pending;
- CR22 (Pixel 7 sizes only): portrait fit at `w411dp-h814dp` (the Pixel 7 minus the worst bars; Robolectric shows
  no bars) with 1, 2 and 4 players, Bull on, at scales 1.0, 1.3, 1.5 and 2.0, during a turn and with the "VALIDER LA
  PARTIE" bar pending: no scroll, all 7 target rows, the header with totals, "RATÉ" and the action bar with "ANNULER"
  fully inside the root bounds (replaces review U2's 4 rows; if scale 2.0 scrolls, R3 stops and reports to main);
  landscape `w914dp-h411dp` (bars hidden) at 1.0 and 2.0 with no scroll container; each button's touch bounds at
  least 48 x 48 dp in both orientations; the drawn keys of adjacent buttons (tag on the key surface) at least 2 dp apart,
  horizontally and between rows, in both orientations (review U4 a); buttons vertically aligned with their row's
  cells (same top and bottom);
  Q7 B: every target button's left edge is at or right of the right edge of the last player cell of its row, and in
  portrait the triple's right edge is the board's right edge; the "RATÉ" cell aligned with the headers and as wide as
  the button column; Q9 B: no undo node in the board, the undo node (`KeypadTags.UNDO`) is in the action bar, in
  both orientations on the left of the primary button with the same top and bottom; totals and button labels not
  clipped;
- CR22 action bar: in portrait (411 x 814) and in landscape (914 x 411, 4 players), at scale 1.0 and 2.0, "ANNULER" and "JOUEUR
  SUIVANT", then "ANNULER" and "VALIDER LA PARTIE" (French) and "CONFIRM MATCH" (English): no label clipped, every
  line break between words (the existing U11 check, `breaksOnlyBetweenWords`), both buttons at least 48 dp tall
  (same height), and the effective font size of every label, read from its `TextLayoutResult`
  (`SemanticsActions.GetTextLayoutResult`), at least 12 sp (review U3);
- CR22: the zone has no total and no "n/7", in a solo game too; nothing on the game screen shows "n/7";
- CR22 landscape: zone on one line (name, MPR, darts in the same row of bounds), the `MatchHeader` above it in the
  right column, the two together as tall as the column header (one band height); no scroll container on the screen;
- CR22 landscape (Q5 B): the system bars are hidden while the landscape game screen is shown and restored when it
  leaves (portrait, between legs, end of match, abandon, home), tested through a fake `SystemBarsController` (the
  decision is the pure `SystemBarsPolicy`, tested on the JVM);
- CR22 landscape cutout and transient bars (review U1): with simulated insets dispatched to the root view
  (`WindowInsetsCompat` with a 48 dp `displayCutout` on the left side, then on the right side), every node of the
  screen stays out of the cutout (its left bound at least 48 dp, or its right bound at most 914 - 48 dp); then the same screen receives non-zero `systemBars` insets (the bars shown
  for a moment after a swipe) and the bounds of the board, the zone and the action bar do not move;
- CR23: TalkBack order of section 5 (headers with the current player first, « Raté », then for each row its cells
  before its buttons, then « Annuler » and the primary button), asserted on the traversal order of the semantics tree
  in portrait and in landscape; dead-row merged node and strike node kept.

Roborazzi captures (record only, Pixel 7, French unless stated), re-recorded: `board_2p_portrait_fr`,
`zone_miss_2p_portrait_fr`, `board_2p_landscape_fr`, `board_4p_portrait_fr`, `board_4p_landscape_fr`,
`board_4p_portrait_en`, `board_4p_portrait_fr_font200`, the dead rows (2 and 4 players, with the grayscale copy),
solo, 25-Bull off, waiting turn, the three win captures. Removed: `modifier_triple_portrait_fr`,
`keypad_with_bull_fr`, `keypad_without_bull_fr`, and the small-phone captures `board_4p_360x640_fr` and
`board_4p_640x360_fr` (Kosmo: the smallest phone is the Pixel 7). Added: `buttons_with_bull_fr`,
`buttons_without_bull_fr` (board crops, showing the 4 dp gaps between keys), `totals_4digits_4p_portrait_fr`,
`buttons_landscape_fr` (board crop at the 49.5 dp landscape rows, showing the gaps, review U4 a),
`dead_row_locked_2p_portrait_fr` (a dead row while the turn waits, next to an open row, so dead and locked can be
compared; with the existing dead-row capture of an open turn, review U4 b), `win_pending_4p_portrait_fr_font200`
(the tightest portrait case, 411 x 814 at scale 2.0), `win_pending_4p_landscape_fr` (the "ANNULER" / "VALIDER LA
PARTIE" bar side by side in the right column). Portrait captures use the Pixel 7 qualifier (411 x 914 dp) unless the
name says otherwise; the landscape captures use the Pixel 7 at 914 x 411 dp (bars hidden, review T3).
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
   play the closed-sector sound; their labels stay readable, and they look different from the buttons locked while
   "JOUEUR SUIVANT" waits (those are all faded).
7. 25-Bull off: no Bull row and no 25/BULL buttons.
8. Solo: no total and no "4/7" on the game screen; leave the game, the home "resume" card shows no "4/7" either.
9. 4 players on the Pixel 7, portrait: every row and its buttons visible without scrolling; totals readable.
10. Landscape with 4 players: the status and navigation bars disappear; the same board as in portrait, all 7
    targets visible without scrolling, the buttons as big as in portrait; at the top right, the title "CRICKET ·
    STANDARD" then the player's zone on one line (name, MPR, darts), the two together as tall as the names row of the
    board; totals above the names; the buttons on the right of the players' cells, lined up with their target, with a
    small visible gap between the keys; "ANNULER" and "JOUEUR SUIVANT" side by side at the bottom right, readable, no
    word cut; nothing cut. Win a leg: "ANNULER" and "VALIDER LA MANCHE" (or "LA PARTIE") are
    readable there too. Turn the phone both ways in landscape (camera hole on the left, then on the right): nothing
    of the board, the zone or the buttons is hidden by the camera hole. A swipe from the edge brings the bars back for
    a moment, over the screen: the board does not jump or move; back to portrait, the bars are back for good. Leave
    the game from landscape (abandon, or the end of the match): the bars are back on the next screen.
11. Largest system font, 4 players, portrait, also when a win waits for "VALIDER LA PARTIE": nothing scrolls, the 7
    targets, the names with their totals, "RATÉ", "ANNULER" and the primary button are all visible. In landscape, everything stays in one
    block, and the board's texts keep their landscape size (they do not grow with the system font, a consequence of
    the landscape choice Q5 B).
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
  `docs/rules/cricket.md` (CR22 also states the cutout padding and the transient bars of section 4; CR24 states the
  refused darts: turn waiting, win pending, 25 or Bull with 25-Bull off); add `throwDart` (with those refusals),
  `buttonRows` and `headerTotals: List<Int>?` (`null` in solo) to `CricketGameScreenState`, and the new
  `CricketTargetButtonModel.kt` (`CricketTargetButton(dart, dotCount, isEnabled)`, no text, review A2, A4);
  `AppEvent.CricketDartTapped` and its `CricketReducer` branch; the new strings; the JVM cases of the test plan for
  these (including review T1 and T2). The old keypad, the modifier and `boardPoints` stay working until R3 and R5,
  so the build stays green.
- Files: `docs/rules/cricket.md`, `ui/cricket/CricketGameScreenState.kt`, `ui/cricket/CricketTargetButtonModel.kt`
  (new), `ui/app/AppState.kt`, `ui/app/CricketReducer.kt`, `res/values/strings.xml`, `res/values-fr/strings.xml`,
  `CricketGameScreenStateTest`, `CricketReducerTest`.
- Depends on: approval of this addendum (Q1 to Q7, Q9 and Q10 answered). CR22 is written in full here, landscape
  included (Q10 A).
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a state holder extension and its JVM tests following the existing reducer pattern.

### R2 - Board layout policy for the Pixel 7 (new `CricketBoardLayoutPolicy`)

- Goal: add the new pure `CricketBoardLayoutPolicy` (new file, its own `CricketBoardLayoutPolicyTest`) to the contract
  of section 4, from measured dp inputs (board width and height left by the pinned parts, column header line heights
  read from the styles with `Density`, player and row counts, orientation, the landscape cutout inset): player
  columns then the button column at the end (Q7 B), 144 to 168 dp, the player column widths; rows 48 to 56 dp in both
  orientations, with the scroll fallback below 48 dp (rows only, the header pinned); the column header with the totals
  (no total line in solo, review T4) and the "RATÉ" cell of the same height and the button column's width (Q9 B);
  `totalTextSize(digits, columnWidthDp, candidates)` with the fixed 14 sp fallback; in landscape (Q5 B, Q10 A) the
  band height, max of the column header and of the right column's title plus single-line zone, with the fixed texts,
  and the right column width (at least 340 dp at 914 dp with a 48 dp cutout, 12 dp gap, room under the band for the
  side-by-side bar). The cases of the `CricketBoardLayoutPolicyTest` item of the test plan, with the measured line
  heights of the section 4 table as fixtures (scales 1.0, 1.3, 1.5, 2.0) and the Pixel 7 sizes only (411 x 814
  portrait, 914 x 411 landscape). The old `CricketLayoutPolicy` and its test are not touched (R4 deletes them once
  nothing uses them). Rewrite the layout contract paragraph of CR22 in `docs/rules/cricket.md` (written in R1 for the
  360 dp budgets) to section 4: Pixel 7 reference, 48 dp rows in both orientations, no 32 dp floor, scroll fallback,
  the landscape title above the zone, the side-by-side bar, the 340 dp right column. If a budget of section 4 does not
  hold with the fixtures (a portrait case scrolls, or landscape rows under 48 dp with the bars hidden), stop and
  report to main.
- Files: `ui/cricket/CricketBoardLayoutPolicy.kt` (new), `CricketBoardLayoutPolicyTest` (new),
  `docs/rules/cricket.md`.
- Depends on: R1 (the landscape part follows Kosmo's Q11 A).
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: on the Pixel 7 the budgets have real spare (21 dp in the worst portrait case, 10.5 dp in
  landscape) and the policy only distributes measured dp values, a pure object with JVM tests like the existing
  policy; the real font-scale fit is proven by R3 and R4's Robolectric tests, not by this arithmetic.

### R3 - Portrait screen: buttons in the board, totals on top, zone without total

- Goal: `CricketBoard` without label column and without points row: the player columns first, then the button
  column at the end (Q7 B); pinned header with the large totals and, at its end, the "RATÉ" cell alone (Q9 B);
  `CricketTargetButtons` at the end of each row (single, double, triple from left to right, sending
  `CricketDartTapped`, the number with `dotCount` drawn dots exposed as `CricketSemantics.DotCount`, in the design
  system's key style, each key inset 2 dp in its touch area, review U4 a); dead-row strike over the cells only; the
  dead and locked styles of section 1 with existing tokens only (review U4 b); the header totals from `headerTotals`,
  and `boardPoints` removed with its cases moved to `headerTotals` (review A1); `CricketPlayerZone` without total;
  portrait `CricketGameScreen` without `CricketKeypad`, the existing `Actions` bar kept unchanged ("ANNULER" +
  "JOUEUR SUIVANT" or the confirm label, Q9 B); tags and the TalkBack order of section 5 (cells before buttons in each
  row); the portrait screen uses the new `CricketBoardLayoutPolicy` (the pinned parts measured by Compose, the board
  in the height left); the Robolectric portrait cases at 411 x 814 (including the Q7 B side, the Q9 B undo place, the
  action bar labels at 12 sp at least, the no-scroll fits with 1, 2 and 4 players at scales 1.0, 1.3, 1.5 and 2.0
  with and without the "VALIDER LA PARTIE" bar, the key gaps and the dots) and the portrait captures (with
  `dead_row_locked_2p_portrait_fr` and `win_pending_4p_portrait_fr_font200`; `board_4p_360x640_fr` removed);
  `CricketBoardStyleTest` (dead-style contrast); `docs/design/design-system.md` (components table, 6.5 portrait). If
  the 411 x 814 fit scrolls at scale 2.0, stop green and report to main (design change).
- Files: `ui/cricket/CricketBoard.kt`, `CricketTargetButtons.kt` (new), `CricketGameScreen.kt`,
  `CricketGameScreenState.kt` (`boardPoints` removed), `CricketPlayerZone.kt`, `CricketTags.kt`,
  `CricketSemantics.kt`, `docs/design/design-system.md`, `CricketScreenTest`, `CricketScreenshotTest`,
  `CricketBoardStyleTest` (new), `CricketGameScreenStateTest`.
- Depends on: R2.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; X01 captures `cmp`-identical to the
  baseline; `kosmo-detekt` exits 0. If beyond about 60 tool calls, stop green and report what is left.
- Model: sonnet
- Why this model: composables built from the existing board and button components against a fixed policy.

### R4 - Landscape screen

- Goal: the landscape layout of section 4 (Kosmo, Q1), with the board on the left and the zone and action bar on
  the right (Kosmo, Q10 A): the same board as portrait, not turned (player columns
  then the button column, "RATÉ" alone at the top of the buttons), with no scrolling and rows of at least 48 dp; the
  top band with the column header and the totals on the left, and on the right the `MatchHeader` (and the S/M chips)
  over the single-line zone (name, MPR, minimal dart cells), the two sides of the same height (band height from
  `CricketBoardLayoutPolicy`); the existing action bar, unchanged and side by side, pinned at the bottom of the zone
  column; the fixed landscape text sizes (14 sp names and title, totals 20 sp at most); the TalkBack order with the
  title first (section 5); its Robolectric cases at 914 x 411, scale 1.0 and 2.0 (no scroll container, one-line zone,
  band heights equal, nothing clipped, buttons at least 48 x 48 dp, key gaps of at least 2 dp, buttons on the right of
  the player cells, the bar with "JOUEUR SUIVANT" and "VALIDER LA PARTIE" / "CONFIRM MATCH" not clipped, broken only
  between words and at 12 sp at least, the TalkBack order); its captures (with `buttons_landscape_fr` and
  `win_pending_4p_landscape_fr`; `board_4p_640x360_fr` removed); the 6.5 landscape paragraph of the design system;
  then delete the old `CricketLayoutPolicy.kt` and `CricketLayoutPolicyTest`, which have no user left (their cases
  prove only the old layout). The system bars and the cutout are task R4b; until then the landscape screen keeps
  today's `safeDrawing` padding, so its rows may be under 48 dp on a real phone in between (Robolectric shows no
  bars, so the tests stay green).
- Files: `ui/cricket/CricketGameScreen.kt`, `CricketBoard.kt`, `CricketPlayerZone.kt` (single-line variant, the
  shared `DartSlots` unchanged so X01 is untouched), `ui/cricket/CricketLayoutPolicy.kt` (deleted),
  `docs/design/design-system.md`, `CricketScreenTest`, `CricketScreenshotTest`, `CricketLayoutPolicyTest` (deleted).
- Depends on: R3.
- Done when: same checks as R3.
- Model: sonnet
- Why this model: it reuses the portrait board and the policy of R2; the layout follows section 4 without a new
  design choice.

### R4b - System bars and display cutout in landscape

- Goal: section 4 "System bars and display cutout" and review U1, A3: the pure `SystemBarsPolicy.decide(isLandscape,
  isCricketGame)` (hide or show, `CUTOUT_ONLY` or `SAFE_DRAWING` padding), the `SystemBarsController` interface and
  its window implementation (platform `insetsController`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, API 30 =
  `minSdk`, no new dependency), the single `DisposableEffect` in `CricketGameScreen` that applies the decision and
  shows the bars again in `onDispose`; the landscape padding from the `displayCutout` insets only; the cutout mode
  left as set by `enableEdgeToEdge`. Tests: `SystemBarsPolicyTest` (JVM); Robolectric with a fake controller (hidden
  in landscape, shown again on every exit path: portrait, between legs, end, abandon, home), with a simulated 48 dp
  side cutout on the left then on the right (nothing under it, rows still at least 48 dp, right column at least
  340 dp) and with transient `systemBars` insets (no bounds move). The CR22 text on this point was written in R1; the
  6.5 landscape paragraph gets one sentence on the hidden bars and the cutout.
- Files: `ui/cricket/SystemBarsPolicy.kt` (new), `ui/cricket/CricketGameScreen.kt`, `docs/design/design-system.md`,
  `SystemBarsPolicyTest` (new), `CricketScreenTest` (or a new `CricketSystemBarsTest`).
- Depends on: R4.
- Done when: same checks as R3.
- Model: opus
- Why this model: platform insets and a side effect that must be undone on every exit path are subtle and hard to
  test in Robolectric; a mistake leaves the whole app without system bars or puts content under the camera hole.

### R5 - Remove the modifier keypad, finish tests and captures

- Goal: delete `CricketKeypad.kt`, the Cricket `multiplier` state, `toggleMultiplier`, `throwNumber`,
  `throwOuterBull`, `throwBull`, `areBullKeysEnabled` and the Cricket `MultiplierTapped`/`NumberTapped`/
  `OuterBullTapped`/`BullTapped` branches; move every test that used them to `CricketDartTapped` (same cases, same
  assertions, no case dropped: `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketGameCuesTest`,
  `CricketPersistenceEffectsTest`, `CricketResumeScreensTest`); remove the three keypad/modifier captures; check the
  unused strings (Cricket-only ones removed in both languages); update the main plan's phone steps reference and the
  Implementation notes. The only cases removed rather than moved are those of the solo zone score: the
  `zoneScoreOf` cases, including `CR22 owner D6 the zone shows points, or the closed targets in a solo game`; the
  Implementation notes line of R5 says that this removal follows from Kosmo's Q2 (no total and no "n/7" in the zone)
  and names the cases removed (review T5).
- Files: `ui/cricket/CricketKeypad.kt` (deleted), `CricketGameScreenState.kt`, `ui/app/CricketReducer.kt`, the five
  test files above, `CricketScreenshotTest`, `res/values*/strings.xml`.
- Depends on: R4b.
- Done when: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; X01 captures
  `cmp`-identical; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: mechanical removal, but across several test files where each case must be kept, not dropped.

### R6 - Remove the solo "n/7" from the home card

- Goal: the home resume card of a solo Cricket game no longer shows "4/7" (CR20 amended in R1): remove
  `ResumeSummary.PlayerScore.closedTargets`, its use in `ResumeCard.tileValue`, `CricketZoneScore.ClosedTargets` if
  nothing uses it any more, and `cricket_closed_targets` in both languages; adapt the tests and re-record the solo
  home capture. `CricketPlayerState.closedTargetCount` in `:game-engine` stays (engine API, tested in
  `CricketStatisticsTest`). The Implementation notes line of R6 says that the removed "n/7" cases follow from Kosmo's
  Q6 A (review T5).
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
  Status after Kosmo's Pixel 7 instruction (below): the hidden bars are kept (they are still needed for 48 dp rows on
  the Pixel 7); the "under 48 dp" part is no longer used, so the 32 dp floor and the proposed CLAUDE.md exception are
  dropped, as Kosmo confirmed in Q11 A.
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
  label, the existing `ActionBar`; in landscape its two buttons were stacked in the same place by the design review
  decision U3 below, side by side again since the Pixel 7 revision); "RATÉ" stays alone at the top of the button column, over its whole
  width. The first proposal (undo next to "RATÉ", bar with "JOUEUR SUIVANT" only) is dropped; no undo icon is added.
- Kosmo, Q10 A: in landscape the zone column stays on the right of the screen, as planned: the zone at the top
  right, "ANNULER" / "JOUEUR SUIVANT" at the bottom right; the board (player columns, then the buttons) on the left,
  so the target buttons sit in the middle of the screen. Applied in section 4 and tasks R1, R2 and R4; the mirrored
  layout (zone column on the left) is dropped.
- Developer, accepted in design review (plan approved at `39e9807`): the totals at the top of each column header, as
  the largest text of the board, in both orientations (no points row), because landscape has no room for a points
  row (not copied from the reference).
- Developer, accepted in design review (consequence of Q7 B): TalkBack reads each row in reading order, its cells
  first (each already names the target) and then its buttons, single, double, triple; the current player's header
  still comes first (U7). The board uses start and end, so it would mirror in a right-to-left locale; none is
  shipped, so left-to-right is not forced and no right-to-left test is added.
- Developer, accepted in design review: in landscape the S/M chips move to the end of the `MatchHeader` line (which
  is above the zone since Q11 A), and the landscape texts use the fixed styles so the one-block layout holds at every
  font scale.
- Developer, accepted in design review: buttons of a dead row stay enabled (closed-sector sound, AN30); the strike
  crosses only the cells.

Design review of `0c2b012` (security, architecture and tests approved; UX one blocking finding; confrontation
additions from the test, architecture and security reviewers), applied in this version:
- U1 (blocking): in landscape with the bars hidden, the content is padded with the `displayCutout` insets only, the
  cutout mode stays the one set by `enableEdgeToEdge`, and transient bars after a swipe appear over the content
  without moving it; one tested place, `SystemBarsPolicy` plus the `SystemBarsController` effect (architecture
  addition). Tests: `SystemBarsPolicyTest`, Robolectric with a simulated cutout and with transient bar insets; phone
  step 10 with the camera hole on each side. New task R4b.
- U2: at least 4 full rows visible in portrait at scale 2.0 (totals capped at 14 sp from scale 1.5, zone at its
  minimum); policy test and Robolectric assertion; R2 stops and reports if the arithmetic does not reach it.
  Superseded by the Pixel 7 revision below (all 7 rows visible at every scale, no 14 sp cap).
- U3: the landscape action bar is stacked ("ANNULER" above the primary button, 204 dp wide each) through
  `ActionBar(isStacked = true)`; every bar label is at least 12 sp (asserted). A layout detail inside Kosmo's Q9 B,
  not a product change: "ANNULER" stays in the bottom bar. Superseded by the Pixel 7 revision below (side by side,
  173 dp per button); the 12 sp assertion stays.
- U4: (a) each key is inset 2 dp in its touch area, so adjacent keys show a 4 dp gap (Robolectric bounds assertion of
  at least 2 dp, captures in both orientations); (b) dead keys at full opacity in `onBackgroundMuted` on
  `surfaceHigh` with a contrast of at least 4.5:1 (JVM test from the tokens), locked keys at 35 % opacity, existing
  tokens only (architecture addition), compared in `dead_row_locked_2p_portrait_fr`; (c) the 12 dp gap between the
  board and the right column is kept (policy test).
- U5: the PR description tells Kosmo, under "Known limitations", that in landscape the board, the zone and the title
  do not follow the system font size (consequence of Q5 B); portrait does. The current player stays identifiable in
  landscape by the "▶" and the `accent` border.
- A1: `boardPoints` is removed in R3 and replaced by `headerTotals`, its cases moved; `CricketBoardRow` keeps its
  fields.
- A2: `CricketTargetButton(dart, dotCount, isEnabled)` and `headerTotals: List<Int>?` carry data only; the composable
  resolves labels and TalkBack texts from resources.
- A3: the hide side effect lives in one `DisposableEffect` of `CricketGameScreen` behind `SystemBarsController`; its
  `onDispose` restores the bars on every exit path; the decision is in `SystemBarsPolicy`. No ADR (local to one
  screen, nothing stored).
- A4: the button model goes in its own file, `CricketTargetButtonModel.kt`, from R1, rather than growing
  `CricketGameScreenState.kt`; no suppression.
- T1 and security addition: a dart is refused while a win is pending (CR25) and a 25 or Bull with 25-Bull off, stated
  in section 1 and CR24 and tested in `CricketGameScreenStateTest` and `CricketReducerTest`.
- T2: `dotCount` on the model (JVM) and the `CricketSemantics.DotCount` property on the drawn dots (Robolectric).
- T3: landscape sizes are 640 x 336/360 and the Pixel 7 at 914 x 411 dp, both with the bars hidden; the 412 x 370
  and 412 x 412 sizes are dropped. Superseded by the Pixel 7 revision below (914 x 411 only).
- T4: the portrait fit at 360 x 640 is checked with 1, 2 and 4 players. Superseded by the Pixel 7 revision below
  (411 x 814, same player counts).
- T5: the Implementation notes of R5 and R6 say that the removed zone-score and "n/7" cases (including D6) follow
  from Q2 and Q6 A.

Revision for the Pixel 7 baseline (after developer-build-R2 stopped: at 360 x 640 the "VALIDER LA PARTIE" bar wraps
on 2 to 3 lines and the 4-player grid scrolled by 8 to 12 dp, see the measurements of section 4):
- Kosmo, instruction (verbatim): « Nouvelle instruction : on ne se préoccupe nullement des petits téléphones. Le plus
  petit téléphone est le Pixel 7. » Applied: the reference and smallest device is the Pixel 7, 411 x 914 dp (portrait
  budgets at 411 x 814 with the worst bars, landscape at 914 x 411); the 360 x 640, 360 x 568 and 640 x 336/360 sizes
  are dropped from the budgets, the tests and the captures.
- Developer, from the budgets of section 4: portrait fits without scrolling at every font scale from 1.0 to 2.0,
  1 to 4 players, Bull on or off, during a pending win (worst spare about 21 dp). Review U2's "4 visible rows at
  360 x 640" and the 14 sp cap of the totals from scale 1.5 are replaced by "nothing scrolls on the Pixel 7"; the
  scroll of the rows stays only as a fallback for unsupported smaller screens, and rows never go under 48 dp.
- Developer: landscape keeps 48 dp rows (49.5 dp at 7 rows) with the bars hidden (Q5 B kept), the fixed landscape
  texts (kept, U5 kept) and the `MatchHeader` moved above the zone in the right column (accepted by Kosmo, Q11 A: with
  the title over the board the rows would get 46.1 dp). The 32 dp floor and the CLAUDE.md 48 dp exception are
  dropped (Kosmo, Q11 A); landscape names and title at 14 sp fixed instead of 12 sp.
- Developer: the landscape action bar is side by side as in portrait (173 dp per button at 4 players); review U3's
  stacking and the `ActionBar(isStacked)` parameter are dropped (U3's cause, a 204 dp right column at 640 dp wide,
  is gone); the 12 sp minimum label assertion of U3 stays.
- Developer, from R2's finding: the new contract lives in a new `CricketBoardLayoutPolicy` (own file and test, the old
  `CricketLayoutPolicy` already has 10 functions); it takes measured dp values (Compose measures the pinned parts,
  `Density` gives the non-linear font scaling) instead of a single font scale, the measured line heights are the JVM
  fixtures; the old object and its test are deleted in R4 once unused. `totalTextSize` falls back to a fixed 14 sp
  when no scaled size fits (4 digits at scale 2.0 in 61 dp), so a total is never cut.
- Review T3 and T4 updated: landscape sizes are the Pixel 7 only (914 x 411, no cutout and a 48 dp cutout on either
  side, instead of 30 dp); the portrait fit is checked with 1, 2 and 4 players at 411 x 814.
- R2 changes model (opus to sonnet): with real spare the policy is a plain distribution of measured values.
- The CR22 contract text written in R1 (360 dp budgets, 32 dp floor, stacked bar, 204 dp column) is rewritten in R2.
- Kosmo, Q11 A (asked after the Pixel 7 instruction; options were A: landscape full screen, B: bars shown with
  buttons of about 41 to 45 dp and a CLAUDE.md exception, C: bars shown with 48 dp buttons and scrolling rows): the
  Cricket game stays full screen in landscape (bars hidden, Q5 B kept, task R4b kept), every button is at least 48 dp
  tall in both orientations, the title "CRICKET · STANDARD" moves to the top right above the player's zone, and no
  CLAUDE.md exception is proposed (no "Proposed owner changes" entry for the 48 dp rule). On a screen smaller than the
  Pixel 7 (unsupported) the rows scroll at 48 dp rather than shrink.

## Open questions

None. Q1 to Q7, Q9, Q10 and Q11 are answered by Kosmo (see Decisions); Q8 was withdrawn.

## Risks

- Portrait on the Pixel 7 at scale 2.0, 4 players, pending win: about 21 dp spare at 411 x 814 (worst bars), resting
  on line heights measured at 360 dp wide and on two estimated bar heights (scales 1.3 and 1.5); R3's Robolectric fit
  at 411 x 814 is the proof and R3 stops if it scrolls.
- Real bar and cutout sizes on Kosmo's Pixel 7 (status 52, navigation 48, cutout 48 dp are assumed upper bounds):
  the phone check confirms them; the scroll fallback keeps everything reachable if the real height is smaller.
- Display size: a "larger display size" setting shrinks the dp of the Pixel 7 below the reference; unsupported
  (Kosmo's instruction), covered only by the scroll fallback (48 dp rows kept, nothing cut).
- Player columns of 61 dp at 4 players in portrait: names get an ellipsis, 4-digit totals fall to 20 sp at scale 1.0
  and to the fixed 14 sp at scale 2.0; asserted by the clipping tests.
- Button labels: two digits plus drawn dots in a 48 x 48 dp button at font scale 2.0 (portrait): the number and the
  dots must both fit the height; the clipping test covers it. The dots keep a fixed size and the number shrinks
  first.
- The dots are a new convention for the players: the phone check and the TalkBack texts confirm that the double and
  the triple are told apart.
- Landscape touch targets: 49.5 dp at 7 rows with the bars hidden, only 1.5 dp of spare per row; a taller band (for
  example the right column's title plus zone line growing past 57 dp) takes it; the policy test and R4's fit test at
  914 x 411 check the 48 dp floor. Until R4b lands, a real phone shows the bars in landscape and the rows are under
  48 dp (intermediate commits only, never in the PR head).
- Hidden system bars: they must come back when the screen leaves landscape or the game (between legs, end, home,
  abandon), or the rest of the app stays without bars; tested through `SystemBarsPolicy` and the fake controller,
  restored by the effect's `onDispose`, and checked on the phone.
- Display cutout: with the bars hidden, only the `displayCutout` padding keeps content out of the camera hole; the
  Robolectric case simulates the insets, which may differ from a real device, so the phone check (camera hole on
  each side) is required. The cutout (at most 48 dp assumed) is taken from the width: 890 dp becomes 842 dp, and the
  right column keeps at least 340 dp at 4 players.
- Landscape at font scale 2.0: with scaled texts the band grows to 98 dp and the rows fall to 43.6 dp; the plan uses
  fixed text sizes in landscape (no growth with the system font), an accessibility trade-off accepted by the UX
  review with U5 and stated to Kosmo in the PR.
- Landscape right column top: title line (24 dp) plus single-line zone (28 dp dart cells) in the 57 dp band, 5 dp
  of spare; the "VICTOIRE" banner (CR25) must fit the zone line; checked by the clipping tests and captures.
- Landscape title moved above the zone (Kosmo, Q11 A): its details (alignment, chips) are for the UX review;
  TalkBack keeps it first.
- About 25 existing Robolectric cases and 20 game captures use the keypad or the modifier and are rewritten; the
  X01 captures must stay byte-identical.
- Landscape action bar (Q9 B): side by side, 173 dp per button at 4 players; at scale 2.0 it takes about 151.5 of
  the 340 dp under the band; the 12 sp minimum label test at scale 1.0 and 2.0 and the `win_pending_4p_landscape_fr`
  capture check it. `ActionBar` is not changed.
- Portrait action bar (Q9 B): unchanged from today, 187 dp per button at 411 dp; "VALIDER LA PARTIE" may take two or
  three lines at large scales, which the portrait budget already counts (151.5 dp at scale 2.0).
- Right-hand button column (Q7 B): the reading order puts each row's cells before its buttons, so a TalkBack user
  hears a row's state before its buttons. Under Q10 A the target buttons are in
  the middle of the landscape screen, beside the action bar (the Bull and 15 rows are at the height of "ANNULER"):
  the 12 dp gap between the board and the right column must be kept so that a tap aimed at a triple does not land
  on "ANNULER" (policy test).
- Risk removed by Q9 B: no undo icon and no 48 dp undo key to fit next to "RATÉ" in a 144 dp cell.

## Implementation notes

(One line per task: task id, deviations, where the main pieces live.)

- R1: no deviation. Rules CR20, CR22 to CR24 rewritten in `docs/rules/cricket.md`; `throwDart`, `buttonRows`,
  `headerTotals` in `CricketGameScreenState`, `CricketTargetButton` and `buttonRow` in `CricketTargetButtonModel.kt`
  (`buttonRows` maps `boardRows`' targets to `buttonRow`); `AppEvent.CricketDartTapped` goes through
  `CricketReducer.playCricket`; strings `cricket_button_double`, `cricket_button_triple`, plural `cricket_header_points`
  added (the "BULL" label reuses `dart_bull`, TalkBack « 25 » / « Bull » reuse `dart_outer_bull` / `announce_dart_bull`);
  `FrenchStringsTest` plural list now includes `cricket_header_points`. `cricket_closed_targets` is still there (R6).
- R2: `CricketBoardLayoutPolicy.kt` (policy, `CricketBoardLayout`, `CricketLandscapeLayout`, `CricketLandscapeSpace`,
  `TotalSize`) and its test. API: `portraitBoardHeightDp`, `portraitZoneHeightDp`, `columnHeaderHeightDp(totalLine?,
  nameLine)`, `portrait(...)`, `landscape(CricketLandscapeSpace)`, `totalTextSize(digits, columnWidth, candidates)` (the
  fallback `TOTAL_FALLBACK_SP` is `isFixed`: draw it with `density.fixedSp`); the 0.6 em, 4 dp, margins and gaps are
  constants of the policy only (R3/R4 must use them, not repeat them). Landscape uses 76 dp player columns and 56 dp
  buttons at every player count. The landscape `barRoomHeightDp` is 340 dp. Fixtures are the 360 dp measurements,
  reused at 411 dp (single-line heights do not depend on width; the 1.3 and 1.5 values and the total em are
  estimates, stated in the test). Rule text CR22/CR24 in `docs/rules/cricket.md` rewritten for the Pixel 7. No budget
  of section 4 failed. Line 819 of Decisions (915 x 412) corrected to 914 x 411.
- R3: no budget of section 4 failed: at 411 x 814 dp, 1/2/4 players, scales 1.0/1.3/1.5/2.0, with and without the
  pending-win bar, nothing scrolls (`CricketScreenTest`, Robolectric). Deviations: the landscape screen is interim
  (scaled texts, `portraitZoneHeightDp`, `LANDSCAPE_ZONE_LINE_DP` 28; R4 replaces it, only one 914 x 411 sanity test);
  the 25 button and the Bull button both read "BULL" (the 25 has 0 dots, the Bull 2), as `dart_bull` is the only label
  (TalkBack says « 25 » and « Bull »). Pieces: `CricketTargetButtons.kt` (`CricketTargetButtons`, `BoardKey`,
  `KeyTags`, `Dots`), `CricketHeaderMetrics.kt` (`rememberHeaderMetrics`, `rememberLineHeightDp`, `CricketHeaderMetrics`),
  `CricketBoard(content, layout, header, onDart, onMiss)` with `HeaderRow`/`MissCell`/`BoardRow` (header row height is
  `layout.missCellHeightDp`), `PortraitGame` in `CricketGameScreen.kt`; tags `dartButton`/`dartKey`/`dartDots`/
  `columnTotal`; `CricketSemantics.DotCount`; `boardPoints`, `POINTS_ROW`, `ZONE_SCORE`, `rowLabel` removed. Tests:
  `CricketScreenTest` (new, 411 x 814), `CricketBoardStyleTest`, fixture `annScoring`. Still to do by R4:
  `CricketLayoutPolicy` and its test; by R5: `CricketKeypad.kt` and `CricketTags.KEYPAD`. Captures: removed
  `modifier_triple`, `keypad_*`, `board_4p_360x640`/`640x360`; added `buttons_with_bull`/`buttons_without_bull`,
  `totals_4digits_4p_portrait_fr`, `dead_row_locked_2p_portrait_fr`, `win_pending_4p_portrait_fr_font200`. The X01
  captures are `cmp`-identical to the ones recorded at e5ba601.
- R4: landscape at 914 x 411 dp, 1 to 4 players, 6 and 7 rows, with and without the pending-win bar, font scales 1.0
  and 2.0, full width and 866 dp wide (48 dp cutout simulated): no scroll, rows at 49.2 dp, band about 58.5 dp
  (`CricketLandscapeScreenTest`, Robolectric). Deviations: the S/M chips in the title use the 14 sp `label`
  (`labelLarge` made the band 64.8 dp and the BULL row 47.2 dp); the landscape dart text is `LANDSCAPE_DART_SP` 14
  (18 did not fit a 24 dp slot); the win banner has no vertical padding on the single line; `cutoutInsetDp` is 0 in
  `LandscapeGame` (R4b passes the real one). Kosmo chose to shorten the landscape position ("S2 · M3", strings `match_position_*_short`, `Header(isShortPosition)`); before that, with 4 players, Bull off, legs and sets, in French,
  the title wraps (mode 225.5 dp, position 108.6 dp, chips about 55 dp, right column 358 dp) and the position falls 9
  dp into the zone. The fixture now uses legs and sets for every case and asserts the title is clear of the zone. Pieces:
  `CricketLandscapeScreen.kt` (`LandscapeGame`, `TitleRow`, `FixedTextSizes`: board, title and zone use
  `Density(fontScale = 1f)`, the action bar is put back in the outer density), `CricketPlayerZone(isSingleLine)`,
  `CricketTags.TITLE`; `Header`, `Zone`, `Board`, `Actions` are now `internal`. `CricketLayoutPolicy` and its test
  deleted. Captures: `board_*_landscape_fr` at 914 x 411, new `buttons_landscape_fr`, `win_pending_4p_landscape_fr`,
  `title_chips_4p_landscape_fr`. X01 captures `cmp`-identical.
- R4b: no budget failed with a real 48 dp cutout inset (left or right, scales 1.0 and 2.0, 1 to 4 players, 6 and 7
  rows, with and without the pending win: rows at least 48 dp, right column at least 340 dp, no node under the
  cutout). Pieces: `ui/cricket/SystemBarsPolicy.kt` (`SystemBarsPolicy.decide`, `SystemBarsDecision` whose `padding`
  is derived from `isHidden` so "hide" cannot come without `CUTOUT_ONLY`, `SystemBarsController`,
  `WindowSystemBarsController` on `View.windowInsetsController`, `LocalSystemBarsController` (null in production,
  the fake in tests) and the `SystemBarsEffect` `DisposableEffect`). `CricketGameScreen` decides from the unpadded
  constraints (landscape when wider than tall), then pads `WindowInsets.displayCutout` (landscape) or `safeDrawing`
  (portrait); `LandscapeGame` gets the space inside the padding plus `cutoutInsetDp` (left plus right) and gives the
  policy the whole width, so the cutout is not counted twice. Deviation: the 866 dp qualifier cases are replaced by
  914 dp cases with a 48 dp `displayCutout` dispatched to the view (`CricketLandscapeScreenTest`, plus the transient
  bars case: status 24 dp and navigation 48 dp shown, no bound moves). Exit paths through `KosmoDartsApp` with a fake
  controller in the new `CricketSystemBarsTest` (portrait, between legs, end, abandon then home, view removed); the
  window controller's swipe behavior is checked there under Robolectric (the actual hiding only on the phone, step 10).
  CR22 already had the text (R1); design system 6.5 got the sentence. No capture changed; X01 captures
  `cmp`-identical.
- R5: removed `CricketKeypad.kt`, `CricketTags.KEYPAD`, `CricketGameScreenState.multiplier` (the field, so the saved game
  and the undo no longer touch a modifier), `toggleMultiplier`, `throwNumber`, `throwOuterBull`, `throwBull`,
  `areBullKeysEnabled`, the Cricket branches of `NumberTapped`/`MultiplierTapped`/`OuterBullTapped`/`BullTapped` in
  `CricketReducer` (the X01 events stay) and the unused strings `cricket_board_points*` (with their `FrenchStringsTest`
  line; the points row went in R3). Deviation: `zoneScoreOf` and `CricketZoneScore` stay, because the home card
  (`ResumeSummary`) still calls them; R6 removes them with the `CR22 owner D6` case of `CricketGameScreenStateTest`
  (Kosmo's Q2 and Q6 A: no total and no "n/7"), so no `zoneScoreOf` case is dropped in R5. Removed cases that follow
  from the modifier no longer existing (not from Q2/Q6 A): `CR24 modifiers are one-shot and toggle` (replaced by `CR24
  a triple is one dart and a miss is always a single miss`), `CR24 owner O2 A 25 and Bull are disabled while a modifier
  is active` (replaced by `CR24 the 25 and the Bull are recorded in order`; the lock while a turn waits is
  `buttons are locked`), the `multiplier` assertions of `CR24 one tap records one dart` and of `R21 the saved game`;
  the `toggleMultiplier(DOUBLE)` and `areBullKeysEnabled` lines of the waiting and 25-Bull-off cases. Moved to
  `CricketDartTapped` / `throwDart`: every other case of `CricketGameScreenStateTest`, `CricketReducerTest` (`the dart
  events reach the game`), `CricketGameCuesTest` and `CricketPersistenceEffectsTest`; `MultiplierTapped` had no
  Cricket meaning left, so its "no cue" and "writes nothing" cases became "a refused dart (25 with 25-Bull off) gives
  no cue / writes nothing". `CricketResumeScreensTest` and `CricketScreenshotTest` used neither the keypad nor the
  modifier any more. The captures `modifier_triple`, `keypad_*` and the 360 x 640 board captures went in R3; all the
  planned captures exist; the 209 captures recorded before R5 are byte-identical after it (X01 included).
- R6: removed `CricketZoneScore` and `zoneScoreOf` (nothing else used them), `ResumeSummary.PlayerScore.closedTargets`,
  the `closedTargets` branch of `ResumeCard.tileValue` and `cricket_closed_targets` in both languages. Deviation:
  `PlayerScore.remaining` is now `Int?`, `null` in a solo game, so the solo tile shows no score at all (CR20, Q6 A)
  instead of "0". The removed cases (`CR22 owner D6` of `CricketGameScreenStateTest`, the two solo closed-targets
  cases of `ResumeSummaryCricketTest`) follow from Kosmo's Q2 and Q6 A (review T5); the replacement case is `CR20 Q6 A
  a solo game shows no score on its tile`. `CricketPlayerState.closedTargetCount` stays (engine). Captures: only
  `cricket_home_solo_fr` changed (the "2/7" is gone); the other 208 are byte-identical to the baseline recorded before
  R6.
- R7: deleted the root PNG `Screenshot_20261031-112252.png` (Kosmo's Dartsmind reference, `git rm`). Before the
  deletion, `git ls-files ':(glob)*.png'` listed only that file and `git grep Screenshot_20261031` found no reference
  in the tree; no code, test or plan file changed. The Decisions section still describes the reference as deleted by R7.
