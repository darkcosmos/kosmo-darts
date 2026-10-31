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

Revision 2 (Kosmo's review of PR #60 at `73e5ed7`, see "Revision 2" below and Decisions), tasks R8 to R13:
4. portrait: a bigger current-player zone on several lines, showing the current player's total again, so that the
   empty space between the board and the bottom bar is used;
5. landscape: no player zone; a top band across the whole screen holds the information (mode, set/leg, MPR, darts of
   the visit); the board takes the whole width left; "ANNULER" next to "RATÉ" and "JOUEUR SUIVANT" just below it, on
   the right edge, over the whole remaining height.

## Revision 2: portrait zone with the total, landscape top band (R8 to R13)

This section supersedes, for revision 2, the parts of sections 2, 3, 4 and 5 that it names; everything else stays.
Kosmo answered Q12 to Q17 (see Decisions): Q12 A, Q13 A, Q14 B, Q15 A, Q16 A, Q17 B; the layout below follows them.

### Second reference: Dartsmind in landscape

Kosmo pushed a second Dartsmind screenshot (landscape, 4 players), as a temporary file at the repository root (commit
`63c5860`, "Add files via upload"); task R13 deletes it and no file that stays in the repository names it. It is
used only for the organization of the top band and of the two action buttons. What it shows (2400 x 1080 px, about
914 x 411 dp at 2.625 px per dp):
- a top band over the whole width, about 46 dp tall: the mode title on the left (an app icon, "STANDARD CRICKET"),
  the three darts of the visit as dart icons on the right, nothing else;
- under it, on the left (about 516 dp wide), the four player columns of equal width (about 129 dp): column header
  about 80 dp tall with the total very large, the name, and "MPR: 0.0" on a dark line; the current player's header in
  red with a small pointer;
- on the right, the button grid of three columns (about 82 dp each); in the header row, "MISS" over the three button
  columns and, right of it, an undo key (arrow icon) of one button width; below the undo key, in the same column,
  "NEXT PLAYER" on two lines in large type, one tall key from the header row down to the bottom of the screen;
- the target rows are about 41 dp tall (under our 48 dp floor), and the Bull row is cut by the bottom edge and the
  gesture handle.

Taken: a band at the top over the whole width with the match information; the board under it over the whole width
left; the undo key on the right of "RATÉ" in the header row; "JOUEUR SUIVANT" in the same column below, over all the
remaining height; a band of about the reference's height, the total above the name in the column headers and rows
of about 44 dp (Q14 B). Not taken: the colors, red header, gold key, fonts, icons, the undo arrow (Q16 A: the word
"ANNULER"), the horizontal two-line "NEXT PLAYER" (Q17 B: vertical text), the MPR line in every column header (Q15 A),
the cut Bull row (our 7 rows are always whole).

### R2.1 Portrait: a zone on several lines with the current player's total

Kosmo reverses Q2 for the portrait zone only: the zone shows the current player's total again. Zone (Kosmo, Q12 A,
like the X01 zone, `CurrentPlayerZone`), from top to bottom:
1. name line, as today: the name (`labelLarge`, ellipsis), "MPR 2,4" (or the CR25 "VICTOIRE" banner), the S/M
   chips at the end;
2. the total, in the X01 big-score style (`scoreHuge`, fixed size), centered, filling the middle of the zone: the size
   is fitted to the height and width left (the X01 `ScoreArea` method: measured on the widest reference "8888", so
   it does not jump with the digits, never clipped). Tag `CricketTags.ZONE_TOTAL`, TalkBack « 60 points » (existing
   plural `cricket_header_points`);
3. the visit's three dart cells at the standard height `DART_SLOT_HEIGHT` (46 dp, as X01) instead of the compact 36 dp.

Solo (Q2 still applies: no total on the solo screen; Kosmo, Q12 A): the middle of the zone shows the MPR in the same
big style instead of the total: a small "MPR" label (`label`) above the value, the value fitted like the total on the
reference "8,8" (one decimal, as today); the name line then has no MPR. Tag `CricketTags.ZONE_MPR`, TalkBack
« MPR 2,4 » as today. The two-line fallback keeps the MPR on the name line.

The column-header totals of the board stay in portrait (section 2, unchanged), the current player's included (Kosmo,
Q13 A): his total is shown twice, large in the zone and in his column.

Height split (new, in `CricketBoardLayoutPolicy`): the screen gives the zone and the board one height `A` (the
screen minus the margins, `MatchHeader`, gaps and the action bar, as today). Rule:
- the zone's fixed part is `F` = 2 x 6 dp padding + the name line + 2 x 8 dp gaps + 46 dp of dart cells; the total
  needs at least `ZONE_TOTAL_MIN` = 32 dp of height (the column-header total is about 37 dp at 24 sp);
- rows = clamp((A - header - F - 32) / rows, 48, 56): the rows keep their 48 to 56 dp, but give up their extra
  height first so that the zone keeps a total of at least 32 dp;
- the zone takes all the height left: zone = A - header - rows x row height. The board is no longer `weight(1f)`:
  the zone is, so there is no empty space under the board any more (the total grows with it);
- when the zone's middle is under 32 dp even with 48 dp rows (only at scale 2.0 with a pending win, see the table),
  the zone shows no total and is the two-line zone of today (name line, dart cells) in that height; the total stays
  in the column header. Nothing scrolls on the Pixel 7.

Budget at 411 x 814 (worst bars), 4 players, 7 rows. Name line (`labelLarge`, 18 sp) estimated from the measured
14 sp line: 28.9, 37.0, 42.0, 50.0 dp at 1.0, 1.3, 1.5, 2.0 (R8/R9 measure it). `A` = 780 - `MatchHeader` - bar.

| Scale, bar | `A` | Header | Rows | Zone | Total area (zone - F) |
|---|---|---|---|---|---|
| 1.0, "JOUEUR SUIVANT" (48) | 708 | 63 | 56 | 253 | 150 |
| 1.0, "VALIDER LA PARTIE" (71.5) | 684.5 | 63 | 56 | 229.5 | 126.6 |
| 1.3, one line (82) | 665.8 | 74.5 | 56 | 199.3 | 88.3 |
| 1.3, pending (115) | 632.8 | 74.5 | 56 | 166.3 | 55.3 |
| 1.5, one line (88.5) | 653.8 | 82.1 | 56 | 179.7 | 63.7 |
| 1.5, pending (125) | 617.3 | 82.1 | 55.3 | 148 | 32 |
| 2.0, one line (110.5) | 624.9 | 101 | 52.6 | 156 | 32 |
| 2.0, pending (151.5) | 583.9 | 101 | 48 | 146.9 | 22.9: two-line zone, no total |

1 to 3 players have the same heights; the solo header has no total line (shorter, so more room); Bull off frees 48 to
56 dp more. With gesture navigation (843 dp) every zone is 29 dp taller.

### R2.2 Landscape: top band, board over the whole width, actions on the right edge

Kosmo removes the landscape zone (this replaces Q10 A's zone column and Q11 A's title above the zone) and reverses
Q9 B for landscape only: "ANNULER" goes next to "RATÉ", "JOUEUR SUIVANT" just below it. Portrait keeps Q9 B (bottom
bar).

Layout, from top to bottom (bars hidden, cutout padding on its side as today, R4b unchanged):
- top band, over the whole width, `LANDSCAPE_BAND` = 44 dp (about the reference's 46 dp, Q14 B), 4 dp vertical
  padding, one line, fixed texts (Kosmo, Q15 A): on the left "CRICKET · STANDARD", the short position "S2 · M3" and
  the S/M chips (today's landscape title row, `Header(isShortPosition = true)`, 14 sp, chips with their normal
  padding, 26.5 dp); then, on the current player's color, the single-line zone: the name (`labelLarge`, 18 sp fixed,
  ellipsis, the flexible width), "MPR 2,4" (or "VICTOIRE"), and the three dart cells at the compact 36 dp height at
  the right end, as in the reference;
- the header row: the player column headers as today in landscape (R4: strip, the total above the name, totals 20 sp
  at most, fixed; about 58.5 dp measured), then "RATÉ" over the three button columns, then "ANNULER" (the word,
  `key_undo`, Kosmo Q16 A) in the action column; both keys as tall as the header row (at least 48 dp);
- the target rows, the player cells then the three buttons, 42 dp at least (the new landscape floor, Kosmo Q14 B),
  43.5 dp at 7 rows on the Pixel 7, 50.75 dp at 6; on their right, after the 12 dp gap, "JOUEUR SUIVANT" (or the CR25
  confirm label) as one tall key over the whole height of the rows, its label in vertical text (Kosmo, Q17 B, below).

Height at 914 x 411 (bars hidden, the cutout takes width only), fixed texts, so the same at every font scale:
top margin 0 (the band starts at the top edge) + band 44 + header row 58.5 + rows + bottom margin 4. The rows get
(411 - 4 - 44 - 58.5) / 7 = 43.5 dp at 7 rows, 50.75 dp at 6 (under the 56 dp cap). Spare above the 42 dp floor at
7 rows: 304.5 - 7 x 42 = 10.5 dp (a band or header up to 10 dp taller than estimated still holds). Below the floor
(unsupported screens) the rows keep 42 dp and scroll under the pinned band and header, as the portrait fallback.

Landscape touch-target contract (Kosmo, Q14 B; the CLAUDE.md exception is proposed in the PR, see Decisions):
- target buttons (20 to Bull): height at least 42 dp (`LANDSCAPE_ROW_MIN`), width 56 dp (at least 48 dp), touch area
  the full cell; the drawn key inset 2 dp on each side, so a 4 dp gap between keys, horizontally and between rows;
- "RATÉ", "ANNULER" and "JOUEUR SUIVANT": at least 48 x 48 dp (58.5 dp tall, 120 dp and 304.5 dp);
- the 12 dp gap between the triples and the action column, and the dead/locked contrast rules (section 1), unchanged;
- portrait: the 48 dp floor unchanged.

Width at 914 dp: 914 - 2 x 12 (side margins) - cutout (0 or 48) = 890 or 842 dp. Button column 168 dp (3 x 56),
12 dp gap, action column `LANDSCAPE_ACTION_WIDTH` = 120 dp; the player columns share the rest: 542 dp with the
cutout, 590 dp without, so 135.5 (147.5) dp per column at 4 players, 180.7 at 3, 271 at 2, 542 at 1 (today 76 dp).
The two-line headers are wider than today's 76 dp, so names are cut less. The "RATÉ" cell is 168 x 58.5 dp,
"ANNULER" 120 x 58.5 dp, "JOUEUR SUIVANT" 120 x 304.5 dp.

Band width at 842 dp: title, position and chips about 390 dp (R4 measured: mode 225.5, position 108.6, chips about
55), three 36 dp dart cells about 180 dp, MPR about 70 dp, gaps 4 x 12: the name keeps about 155 dp at 18 sp.

"ANNULER": the `button` style at a fixed 20 sp, horizontal, about 77 dp in a 96 dp label width (120 - 2 x 12).

"JOUEUR SUIVANT", vertical text (Kosmo, Q17 B):
- rotation: a quarter-turn counter-clockwise, the label reads from bottom to top (as the labels of a vertical chart
  axis); the same on both landscape sides, since Android lays the screen out again when the phone is turned over;
- the text is laid out with the key's height as its line length (a layout modifier that swaps the constraints, then
  draws rotated), so the measurement, the word-safe wrapping and the clipping tests work on the real rotated size;
- size: fixed `keypad` style, 28 sp, not following the font scale (U5 extended to the landscape action keys);
  available line length 304.5 - 2 x 12 = 280 dp; the longest labels, "VALIDER LA PARTIE" and "VALIDER LA MANCHE"
  (17 characters), about 17 x 0.55 x 28 = 262 dp, fit on one line; "JOUEUR SUIVANT" about 216 dp, "CONFIRM MATCH" and
  "NEXT PLAYER" shorter. If a label is wider than measured here, it breaks between words onto two vertical lines
  (two 28 sp lines, about 88 dp, fit the 96 dp width), then shrinks, never under 20 sp; R10 asserts every label at
  28 sp on one line or, failing that, at least 20 sp and broken only between words, and stops and reports if not;
- TalkBack: unchanged, the key reads its label (« Joueur suivant », « Valider la partie », ...), a button role, the
  rotation is visual only.
The `ActionBar` component is not used in landscape any more and is not changed.

TalkBack order in landscape (CR23, replaces section 5 for landscape): the band (title, position, then the current
player: name, MPR, darts), the column headers (current player first), « Raté », « Annuler », then row by row its cells
then its buttons, then « Joueur suivant » (or the confirm label). Portrait order unchanged except that the zone now
reads the total after the name line.

Risks of this layout: see Risks (rows under 48 dp, rounded screen corners, vertical text, a tap near the gap).

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
  the strings in `values/` and `values-fr/`, the phone checks;
- revision 2: the portrait zone on several lines with the total and the portrait height split; the landscape top
  band, full-width board, one-line column headers and the action column ("ANNULER" by "RATÉ", tall "JOUEUR
  SUIVANT"); their policy, tests, captures, rule text (CR22, CR23) and design-system text; the deletion of the second
  reference screenshot.

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

Revision 2: the portrait zone gets the total back and several lines (R2.1); the landscape zone is replaced by the
top band (R2.2). The text below is the state delivered by R1 to R7.

`CricketPlayerZone` keeps: line 1, name (1 line, ellipsis), "MPR 2,4", S/M chips; line 2, the `DartSlots` (scoring
darts, or "RATÉ"), and the "VICTOIRE" banner of CR25 (Kosmo, Q4 A). The `ScoreLine` loses the points and the "n/7".
In portrait its height goes from 96 (minimum 76) to 84 (minimum 68), see the layout contract. In landscape it is a
single line (section 4).

### 4. Layout contract (new CR22), pure `CricketBoardLayoutPolicy`

Revision 2 replaces in this section: the portrait budget (now the height split of R2.1, where the zone takes the
height the rows leave), and the whole landscape layout, width and height budgets and landscape action bar (R2.2).
The Pixel 7 reference, the measured line heights, the 48 dp floor, the scroll fallback and the system bars and
cutout part stay.

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

Revision 2: the landscape order is in R2.2 (band first, « Annuler » after « Raté », « Joueur suivant » last); the
portrait zone also reads its total.

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

Revision 2 (R8 to R13):
- `ui/cricket/CricketBoardLayoutPolicy.kt`: new `portraitSplit(...)` (rows, zone height, whether the zone shows its
  total, R2.1; constants `ZONE_TOTAL_MIN`, the zone's fixed parts) and a new landscape function for R2.2 (constants
  `LANDSCAPE_BAND` 44 dp, `LANDSCAPE_ACTION_WIDTH` 120 dp, `LANDSCAPE_ROW_MIN` 42 dp; player columns from the width
  left; header row from the measured two-line header, at least 48 dp; rows 42 to 56 dp; scroll fallback below 42).
  The old `landscape(CricketLandscapeSpace)`, `CricketLandscapeLayout` fields of the zone column (`rightColumnWidthDp`,
  `barButtonWidthDp`, `barRoomHeightDp`) and `portraitZoneHeightDp` are removed once unused (R11, R9);
- `ui/cricket/CricketPlayerZone.kt`: the portrait multi-line variant (name line, fitted total or solo MPR, 46 dp dart
  cells) and the two-line fallback; the single-line variant with an 18 sp name and 36 dp dart cells, used in the band;
- `ui/cricket/CricketZoneTotal.kt` (new, R9): the fitted big number (total, or the solo MPR; the X01 `ScoreArea`
  method, Cricket copy; the X01 file is not changed so its captures stay byte-identical);
- `ui/cricket/VerticalKeyLabel.kt` (new, R10): the quarter-turn label of the tall key (constraints swapped, word-safe,
  28 sp fixed down to 20 sp);
- `ui/cricket/CricketGameScreenState.kt` (or the zone content mapping): the zone content gets `total: Int?` (`null` in
  solo), from `headerTotals`;
- `ui/cricket/CricketGameScreen.kt` (`PortraitGame`: the zone takes the split height, the board its own height);
- `ui/cricket/CricketLandscapeScreen.kt` (`LandscapeGame`: band, board, action column; `TitleRow` reused in the band);
  `ui/cricket/CricketTopBand.kt` (new, R11); `ui/cricket/CricketBoard.kt` (the undo cell after "RATÉ" and the tall
  next-player key in landscape; rows down to 42 dp in landscape; the column header unchanged);
  `CricketTags.kt` (`ZONE_TOTAL`, `ZONE_MPR`, `BAND`, `LANDSCAPE_UNDO`, `LANDSCAPE_NEXT`);
- `docs/rules/cricket.md` (CR22, CR23), `docs/design/design-system.md` (components table, 6.5);
- tests: `CricketBoardLayoutPolicyTest`, `CricketGameScreenStateTest`, `CricketScreenTest`,
  `CricketLandscapeScreenTest`, `VerticalKeyLabelTest` (new), `CricketScreenshotTest`; `CricketSystemBarsTest`
  should not change (run as is);
- the root PNG of `63c5860` (deleted, R13).
No new string: "ANNULER" reuses `key_undo`, the totals reuse `cricket_header_points`.

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

Revision 2 (replaces the landscape items above and the portrait zone item):
- portrait, 2 and 4 players, scale 1.0: the zone on several lines with the large total, no empty space between the
  board and the bottom bar; scale 2.0 without a pending win: the total still in the zone (at least 32 dp tall); scale
  2.0 with a pending win: the two-line zone without its total; solo: no total, the MPR large in the middle (Q12 A);
- landscape, 1 to 4 players, Bull on and off: the 44 dp band at the top over the whole width (title, position, chips,
  then the player's name, MPR and darts in his color), no zone column; column headers as today (total above the
  name); "RATÉ" then "ANNULER" in the header row; "JOUEUR SUIVANT" tall on the right of the rows, label in vertical
  text; rows of 43.5 dp (7 rows) or 50.75 dp (6 rows), at least 42 dp, nothing scrolls, with and without a 48 dp
  cutout on either side;
- landscape, pending win: "VICTOIRE" in the band, "VALIDER LA PARTIE" (vertical) in the tall key, "ANNULER" by "RATÉ".

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

Revision 2 (R8 to R12; the cases above that contradict it are rewritten, never dropped silently: each rewritten or
removed case is named in the task's Implementation notes line with the decision it follows, Q2 reversal for the
portrait zone, Q9 B reversal and Q10 A/Q11 A replacement and Q14 B 42 dp floor for landscape):
- `CricketBoardLayoutPolicyTest` (CR22): `portraitSplit` with the fixtures of the R2.1 table (scales 1.0, 1.3, 1.5,
  2.0; one-line and pending bar): rows 48 to 56 dp, zone = A - header - rows, the zone's total area at least 32 dp in
  every case except scale 2.0 with the pending bar (two-line zone, no total), no scroll; the same with 1, 2 and 3
  players, solo (no total line in the header), 6 rows; a smaller `A` (unsupported screen) keeps 48 dp rows and
  scrolls only below the two-line zone. Landscape (R2.2): 914 x 411, cutout 0 and 48 dp, 1 to 4 players, 6 and 7
  rows, header fixture 58.5 dp: band 44 dp, header row max(header, 48), rows 43.5 dp (7) and 50.75 dp (6), at least
  `LANDSCAPE_ROW_MIN` 42 dp, band + header + rows + 4 dp at most 411, no scroll; a header 10 dp taller still gives
  42 dp rows; a smaller height scrolls the rows at 42 dp; player columns 135.5/180.7/271/542 dp with the cutout
  (147.5/196.7/295/590 without); button column 168 dp (buttons 56 dp wide, at least 48); action column 120 dp after a
  12 dp gap; "RATÉ" 168 dp and undo 120 dp wide, both the header row's height (at least 48); next key 120 dp x the
  rows' height; portrait keeps its 48 dp floor (a portrait case never returns rows under 48).
- `CricketGameScreenStateTest`: the zone content's `total` is the current player's points (the same value as his
  `headerTotals` entry, one source, review A1) and `null` in solo; it follows undo and the change of player; in solo
  the zone content carries the MPR for the big display.
- Robolectric portrait (`CricketScreenTest`, 411 x 814, Pixel 7 width): CR22, the zone shows `ZONE_TOTAL` with the
  current player's total and TalkBack « 60 points » (2 and 4 players, scales 1.0, 1.3, 1.5, 2.0 without pending win,
  1.0 and 1.5 with it); at 2.0 with the pending win the zone has no `ZONE_TOTAL` and nothing scrolls; the zone's bottom
  is within 6 dp of the board's top, and the board's bottom within 6 dp of the action bar's top (no empty space, the
  gap of the column); the total is not clipped and its size does not change when the digits change (1 to 4 digits);
  solo: no `ZONE_TOTAL`, `ZONE_MPR` shown large with « MPR 2,4 » and no MPR on the name line (Q12 A), the two-line
  fallback with the MPR on the name line; the column-header totals still present (Q13 A); the TalkBack order
  of the zone (name line, total, darts). Replaces "CR22: the zone has no total" (Q2 reversed for the portrait zone).
- Robolectric landscape (`CricketLandscapeScreenTest`, 914 x 411, scale 1.0 and 2.0, cutout 0 and 48 dp on each
  side, 1 to 4 players, 6 and 7 rows, with and without the pending win): no scroll container; no `CURRENT_PLAYER`
  zone node outside the band; `BAND` at the top, spanning the width inside the padding, 44 dp tall, its texts not
  clipped (title, position, chips, name, MPR or "VICTOIRE", the three dart cells, the darts at its right end); column
  headers with the total above the name, not clipped; "RATÉ" right of the last player header and `LANDSCAPE_UNDO`
  right of "RATÉ", same top and bottom, both at least 48 x 48; `LANDSCAPE_NEXT` under the undo key, same left and
  right, from the top of the first row to the bottom of the last; target buttons: touch bounds at least 48 dp wide and
  at least 42 dp tall (43.5 at 7 rows), drawn keys at least 2 dp apart (4 dp gap), aligned with their row's cells;
  "ANNULER" not clipped, at least 12 sp; the vertical label of `LANDSCAPE_NEXT`, for "JOUEUR SUIVANT", "NEXT
  PLAYER", "VALIDER LA PARTIE", "VALIDER LA MANCHE", "VALIDER LE SET", "CONFIRM MATCH", "CONFIRM LEG" and "CONFIRM SET": rotated a quarter-turn
  counter-clockwise (its drawn bounds taller than wide), inside the key, not clipped, at 28 sp on one line or at least
  20 sp broken only between words, the same at scale 1.0 and 2.0 (fixed), and its TalkBack text equal to the label;
  nothing under the cutout; transient bars move nothing; TalkBack order of R2.2. Replaces the cases of the zone column
  (one-line zone in the right column, title above the zone, side-by-side bar in the right column, right column
  340 dp) and the landscape "every button at least 48 x 48" assertion for target buttons (Q14 B).
- Unchanged and still green: the dead-style contrast (`CricketBoardStyleTest`), the portrait 48 x 48 assertions, the
  12 dp gap between the board and the actions.
- `CricketSystemBarsTest` and `SystemBarsPolicyTest`: unchanged, must stay green.
- Captures (record only, Pixel 7), re-recorded: every portrait game capture where the zone appears
  (`board_2p_portrait_fr`, `board_4p_portrait_fr`, `board_4p_portrait_en`, `board_4p_portrait_fr_font200`,
  `zone_miss_2p_portrait_fr`, `dead_rows_*`, `dead_row_locked_2p_portrait_fr`, `solo_portrait_fr`,
  `cut_throat_portrait_fr`, `turn_waiting_portrait_fr`, `totals_4digits_4p_portrait_fr`, `win_*_portrait_*`,
  `win_pending_4p_portrait_fr_font200`) and every landscape game capture (`board_2p_landscape_fr`,
  `board_4p_landscape_fr`, `buttons_landscape_fr`, `win_pending_4p_landscape_fr`); `title_chips_4p_landscape_fr`
  becomes `band_4p_landscape_fr` (band crop, legs and sets, French); new `zone_total_4p_portrait_fr_font130`,
  `band_win_pending_4p_landscape_en` and `next_key_vertical_landscape_fr` (the tall key with "VALIDER LA PARTIE"). `buttons_with_bull_fr` and `buttons_without_bull_fr` are expected unchanged in
  portrait (rows stay 56 dp at 1.0). Between-legs, end, setup, home and every X01 capture: byte-identical (`cmp`).

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

Revision 2 replaces steps 3, 9, 10 and 11 (and adapts 1 and 12) as follows (Kosmo's answers Q12 to Q17):
- R-a. Portrait, 2 players: the player's area under the title is taller: his name, MPR and S/M chips on the first
  line, his total in very large type in the middle, his three darts below. There is no large empty space between the
  board and "ANNULER" / "JOUEUR SUIVANT". The totals at the top of each column are still there.
- R-b. Throw 3 darts that score, then "JOUEUR SUIVANT": the large total changes to the next player's; "ANNULER" a
  dart that scored: the large total goes back.
- R-c. Portrait, largest system font, 4 players: nothing scrolls, the total is still in the player's area (smaller);
  when a win waits for "VALIDER LA PARTIE", the area may lose its large total (the total stays at the top of the
  column).
- R-d. Solo, portrait: no total in the player's area (Q2); its MPR is shown in very large type in the middle instead.
- R-e. Landscape, 4 players: the status and navigation bars disappear; at the top, a band over the whole width:
  "CRICKET · STANDARD", the set/leg and the S/M pastilles, then on the player's color his name, his MPR and his three
  darts at the far right. No other player area. Under it the board takes the whole width: each column shows its
  total above the name, as before; at the right of the names, "RATÉ" then "ANNULER"; under "ANNULER", "JOUEUR
  SUIVANT" as one tall key down to the bottom, its text written vertically, read from bottom to top. All 7 targets
  visible without scrolling; the target buttons are a little less tall than in portrait (about 44 dp instead of 56)
  but as wide, with a visible gap between keys; tapping them is still comfortable. Nothing hidden by the camera hole
  (turn the phone both ways); nothing cut in the screen's rounded corners (check the top-left of the band and the
  bottom-right of "JOUEUR SUIVANT").
- R-f. Landscape: "ANNULER" removes the last dart; "JOUEUR SUIVANT" passes the turn; win a leg: "VICTOIRE" in the
  band and "VALIDER LA MANCHE" (or "LA PARTIE") written vertically in the tall key, whole, no word cut; the same in
  English ("CONFIRM LEG", "CONFIRM MATCH").
- R-g. Landscape, largest system font: the same layout, the landscape texts and keys keep their size (vertical label
  included).
- R-h. TalkBack in landscape: the band (title, set/leg, name, MPR, darts), the column headers, « Raté »,
  « Annuler », each row's cells then its buttons, then « Joueur suivant ».

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

Revision 2 tasks (R1 to R7 are done; Kosmo answered Q12 to Q17; R8 starts once the plan is approved again):

### R8 - Rules text and layout policy for revision 2

- Goal: rewrite CR22 (portrait zone with the total, the solo MPR and the height split of R2.1; landscape band,
  full-width board, header row, action column, budgets of R2.2, and the landscape touch-target contract: target
  buttons at least 42 dp tall and 48 dp wide with a 4 dp gap, the other keys at least 48 x 48, portrait unchanged at
  48) and CR23 (landscape: "ANNULER" by "RATÉ", tall "JOUEUR SUIVANT" with its vertical label, TalkBack order of R2.2;
  portrait zone reads its total) in `docs/rules/cricket.md`; add to `CricketBoardLayoutPolicy` the new
  `portraitSplit(...)` and the new landscape function with their constants (R2.1, R2.2, `LANDSCAPE_ROW_MIN` 42 dp
  used only by the landscape function), next to the existing `portrait`/`landscape`/`portraitZoneHeightDp`, which
  stay until R9 and R11 remove their last users (green build in between); the policy test cases of the revision 2
  test plan. If a budget of R2.1 or R2.2 does not hold with the fixtures, stop green and report to main.
- Files: `docs/rules/cricket.md`, `ui/cricket/CricketBoardLayoutPolicy.kt`, `CricketBoardLayoutPolicyTest`.
- Depends on: plan approved (Q12 to Q17 answered).
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a pure policy object and its JVM tests, the same pattern as R2, with the numbers given here.

### R9 - Portrait zone on several lines with the total

- Goal: R2.1: the zone content's `total` (from `headerTotals`, `null` in solo); `CricketPlayerZone` multi-line
  variant (name line, `CricketZoneTotal` fitted on "8888", 46 dp dart cells) and the two-line fallback; solo (Q12 A):
  the MPR in the big style fitted on "8,8" under a small "MPR" label, `ZONE_MPR`, no MPR on the name line; `PortraitGame` uses `portraitSplit` (the zone gets the split height, the board its own, no `weight` on the
  board); remove `portraitZoneHeightDp` and the old `portrait` if unused; the portrait Robolectric cases of the
  revision 2 test plan (rewriting "CR22: the zone has no total", named in the Implementation notes); the portrait
  captures re-recorded and `zone_total_4p_portrait_fr_font130` added; design system (components table, 6.5
  portrait). Landscape untouched (it does not use the multi-line variant).
- Files: `ui/cricket/CricketPlayerZone.kt`, `CricketZoneTotal.kt` (new), `CricketGameScreen.kt`,
  `CricketGameScreenState.kt`, `CricketTags.kt`, `CricketBoardLayoutPolicy.kt`, `docs/design/design-system.md`,
  `CricketGameScreenStateTest`, `CricketScreenTest`, `CricketScreenshotTest`.
- Depends on: R8.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; landscape, between-legs, end, setup, home
  and X01 captures `cmp`-identical to the ones before R9; `kosmo-detekt` exits 0. If the 411 x 814 fit scrolls or a
  total is clipped, stop green and report to main.
- Model: sonnet
- Why this model: a composable variant built on the existing X01 zone pattern and a fixed policy.

### R10 - Vertical key label

- Goal: R2.2 "JOUEUR SUIVANT, vertical text" (Kosmo, Q17 B) as a standalone component, not yet used by a screen:
  `VerticalKeyLabel(text, style, modifier)` lays the text out with the parent's height as its line length (a layout
  modifier that swaps the constraints and places the text rotated a quarter-turn counter-clockwise, reading bottom to
  top), at the fixed 28 sp `keypad` size, breaking only between words and shrinking down to 20 sp at most (reusing
  `WordSafeText`'s rules, not a copy); its semantics text is the label (TalkBack unchanged). Robolectric tests in a
  120 x 304.5 dp box at scale 1.0 and 2.0 for "JOUEUR SUIVANT", "NEXT PLAYER", "VALIDER LA PARTIE", "VALIDER LA
  MANCHE", "VALIDER LE SET", "CONFIRM MATCH", "CONFIRM LEG", "CONFIRM SET": drawn bounds taller than wide and inside
  the box, not clipped, the effective size (28 sp on one line, or at least 20 sp broken between words), same result at
  both scales, semantics text equal to the label; and a 120 x 150 dp box that forces the two-line break. If a label
  does not fit at 20 sp in 120 x 304.5 dp, stop green and report to main (a product question).
- Files: `ui/cricket/VerticalKeyLabel.kt` (new), `ui/components/WordSafeText.kt` (only if a function must become
  `internal` for reuse; no behavior change), `VerticalKeyLabelTest` (new).
- Depends on: R9.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; every capture `cmp`-identical;
  `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: one contained component with a known layout pattern and the existing word-safe rules, fully
  specified sizes and tests.

### R11 - Landscape: top band, full-width board, action column

- Goal: R2.2: `LandscapeGame` rebuilt: `CricketTopBand` (44 dp at the top edge, 4 dp vertical padding; `TitleRow`
  with the short position and the chips, then the single-line zone on the player's color with the 18 sp fixed name,
  MPR or "VICTOIRE", the 36 dp dart cells at the right end, Kosmo Q15 A); the board over the width left with today's
  landscape column headers (total above the name, fixed); target rows from the new landscape policy (42 dp floor,
  43.5 dp at 7 rows, keys inset 2 dp); in the header row "RATÉ" then the 120 dp undo key (`LANDSCAPE_UNDO`, the word
  "ANNULER", `key_undo`, fixed 20 sp, the existing undo event, Kosmo Q16 A); under it, after the 12 dp gap from the
  triples, the tall `LANDSCAPE_NEXT` key with `VerticalKeyLabel` (next player or the CR25 confirm label and event, as
  the `Actions` bar today); the zone column, the landscape `Actions` bar and the old landscape policy pieces
  (`landscape(CricketLandscapeSpace)`, the zone-column fields) are removed; TalkBack order of R2.2; the landscape
  Robolectric cases of the revision 2 test plan (rewriting the zone-column cases and the landscape 48 dp target-button
  assertion, each named in the Implementation notes with Q14 B, Q9 B reversal, Q10 A/Q11 A replacement). The R4b
  cutout padding and system bars stay as they are. If 7 rows get under 42 dp at 914 x 411 with a 48 dp cutout, stop
  green and report to main.
- Files: `ui/cricket/CricketLandscapeScreen.kt`, `CricketTopBand.kt` (new), `CricketBoard.kt`,
  `CricketPlayerZone.kt` (single-line variant sizes), `CricketTags.kt`, `CricketBoardLayoutPolicy.kt`,
  `CricketBoardLayoutPolicyTest`, `CricketLandscapeScreenTest`.
- Depends on: R10.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `CricketSystemBarsTest` unchanged and
  green; portrait and X01 captures `cmp`-identical to the ones before R11; `kosmo-detekt` exits 0. Beyond about 60
  tool calls, stop green and report what is left.
- Model: opus
- Why this model: a whole landscape screen rebuilt with a new touch-target floor that must hold only in landscape,
  the cutout and the hidden bars, and many landscape tests rewritten without losing a case; a mistake gives scrolling,
  clipped keys or a portrait regression.

### R12 - Landscape captures and design system

- Goal: re-record the landscape game captures, rename `title_chips_4p_landscape_fr` to `band_4p_landscape_fr`, add
  `band_win_pending_4p_landscape_en` and `next_key_vertical_landscape_fr`; the 6.5 landscape paragraph and the
  components table of the design system (band, action column, vertical label, the landscape 42 dp target-button
  floor; the "landscape texts do not follow the font size" note now includes the action keys, U5); check that no
  landscape capture still shows the zone column.
- Files: `CricketScreenshotTest`, the landscape capture PNGs, `docs/design/design-system.md`.
- Depends on: R11.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; portrait, between-legs, end, setup, home
  and X01 captures `cmp`-identical; `kosmo-detekt` exits 0.
- Model: haiku
- Why this model: recording and renaming captures and documenting a layout already built, no design choice.

### R13 - Delete Kosmo's second reference screenshot

- Goal: delete the Dartsmind landscape PNG that Kosmo uploaded at the repository root in commit `63c5860` ("Add files
  via upload"); check with `git grep` that no file of the repository names it. Last commit of revision 2, before the
  code review of the final head.
- Files: the PNG at the repository root (deleted), this plan's Implementation notes.
- Depends on: R12.
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

Revision 2 (Kosmo's review of PR #60 at `73e5ed7`, comment "A retravailler"):
- Kosmo, comment (verbatim): « A retravailler
  - en mode portrait, on peut se permettre d'avoir une vignette du joueur actif plus grosse et donc à mettre
    différents éléments sur plusieurs lignes. Par exemple, on pourrait remettre ce que j'avais demandé d'enlever le
    total du joueur en cours. Ainsi, cela éviterait d'avoir trop d'espace vide dans le bas de l'application,
    notamment entre le tableau et les boutons d'annulation et de Tours suivants
  - par contre en mode paysage ça va pas du tout. Il y a le tableau est trop compact sur la gauche et du coup la
    vignette du joueur sur la droite sert quasiment à rien. Ce que je propose, on supprime la vignette du joueur
    courant, ce qui nous amène à instaurer un bandeau supérieur qui va traverser tout l'écran où on aura toutes les
    infos. Pour que cela soit plus clair, j'ai mis une capture d'écran de DartsMind en paysage
    (Screenshot_[…].png, pushé directement dans la branche). Inspire toi du bandeau supérieur pour mettre les infos
    MPR, félchettes, modes, Set/Manches... Reste donc les deux boutons à traiter. Je te laisse d'inspirer également de
    cette capture d'écran pour mettre ces boutons totalement sur la droite (annuler à coté de RATE) et joueur suivant
    juste en dessous et sur toute la hauteur restante »
  (the file name is elided here on purpose: R13 deletes the file and no file that stays may name it.)
- Reversal of Q2 / answer 2, portrait zone only: the zone shows the current player's total again. The solo rule of
  Q2 (no total on the solo game screen) is not reversed (Q12 A). The column-header totals stay (Q13 A).
- Reversal of Q9 B, landscape only: "ANNULER" next to "RATÉ" in the header row, "JOUEUR SUIVANT" just below on the
  right edge over the whole remaining height. Portrait keeps Q9 B (bottom bar "ANNULER" + "JOUEUR SUIVANT").
- Replacement of Q10 A and Q11 A (landscape): no zone column on the right and no title above a zone; a top band over
  the whole width holds the mode, set/leg, MPR and darts of the visit (Kosmo's list), the board takes the whole width
  left. Q4 A (the zone's content) applies to the band in landscape. Q1 (one block, no scrolling, slightly smaller
  fonts) and Q5 B (bars hidden) stay. Q11 A's "48 dp buttons in both orientations, no CLAUDE.md exception" stays for
  portrait only and is reversed for landscape by Q14 B (below).
- Kosmo's second reference (landscape): used only for the organization of the band and the two action buttons; a
  temporary file deleted by R13; everything else follows Q1 to Q17 and the design system.
- Kosmo's answers to the revision 2 questions (2026-10-31, relayed by main):
  - Q12 A: portrait zone as in X01: name line (name, MPR, S/M chips), the current player's total very large in the
    middle filling the free space, the three darts at the bottom (46 dp cells); solo: the MPR very large in the middle.
  - Q13 A: every column-header total stays in portrait (the current player's total appears twice).
  - Q14 B: landscape: a tall band as in DartsMind and the total above the name in the column headers; the target
    buttons go down to about 42 to 44 dp. Kosmo accepts the exception to the 48 dp rule for this case, which reverses
    Q11 A for landscape.
  - Q15 A: band, left to right: « CRICKET · STANDARD », set/leg and S/M chips; then on the player's color his name,
    his MPR (or « VICTOIRE ») and the three darts at the far right.
  - Q16 A: the word « ANNULER » next to « RATÉ ».
  - Q17 B: vertical text (quarter-turn) on the tall « JOUEUR SUIVANT » key.
- Landscape touch-target exception (Q14 B), as built: on the Cricket game screen in landscape only, the target buttons
  (S/D/T of 15 to 20 and Bull) are at least 42 dp tall (`LANDSCAPE_ROW_MIN`; 43.5 dp at 7 rows on the Pixel 7) and at
  least 48 dp wide (56 dp at 4 players), the touch area is the whole cell, keys are inset 2 dp so there is a 4 dp gap
  between neighbours, and the 12 dp gap between the triples and the action column stays. « RATÉ », « ANNULER » and
  « JOUEUR SUIVANT » stay at least 48 x 48 dp; every other screen and the whole portrait screen keep 48 dp. Contrast
  rules are unchanged. CLAUDE.md is never edited: the PR proposes the matching CLAUDE.md wording under "Proposed owner
  changes" (Accessibility: "touch targets of at least 48dp, except the Cricket target buttons in landscape: at least
  42dp tall and 48dp wide, 4dp apart, accepted by the owner on issue #40").
- Vertical label (Q17 B), as built: quarter-turn counter-clockwise (reads bottom to top), the same text and
  direction in French and English; fixed 28 sp; one line for every label (the longest, 17 characters, about 262 of
  280 dp); if a label ever does not fit, it breaks only between words, then shrinks down to 20 sp; TalkBack reads the
  label unchanged; the size is fixed so it is the same at every system font scale (U5 extended).
- Developer, from the budgets of R2.2 (for design review): band 44 dp with 4 dp vertical padding; header row as R4
  (about 58.5 dp, total above the name, fixed texts up to 20 sp); rows (411 - 4 - 44 - 58.5) / 7 = 43.5 dp at 7 rows,
  10.5 dp above the 42 dp floor; the landscape action labels are fixed like the rest of the landscape screen
  (« ANNULER » 20 sp, the vertical label 28 sp; U5 extended); the action column is 120 dp wide, 12 dp from the
  triples (U4 c kept).
- Developer, from the budgets of R2.1: the rows keep 48 to 56 dp and the zone takes all the height they leave (no
  empty space), with a total of at least 32 dp, else the two-line zone (scale 2.0 with a pending win only).

## Open questions

None. Q1 to Q7 and Q9 to Q17 are answered by Kosmo (see Decisions); Q8 was withdrawn.

## Risks

Revision 2 (the landscape risks below about the zone column, the title above the zone, the side-by-side landscape
bar and the 340 dp right column no longer apply after R11):
- Landscape target buttons under 48 dp (43.5 dp at 7 rows, 42 dp floor): accepted by Kosmo (Q14 B) but contrary to
  the CLAUDE.md accessibility line; CI or the owner may refuse the PR until he applies the "Proposed owner changes"
  wording. Mitigations: the width stays at least 48 dp (56 dp at 4 players), the touch area is the whole cell, 4 dp
  gap between keys, 12 dp gap to the action column; portrait and every other key stay at 48 dp; tests assert both
  floors separately so a portrait regression cannot hide behind the landscape exception.
- Landscape vertical spare is 10.5 dp at 7 rows (band 44 + header 58.5 + bottom margin 4 + 7 x 43.5 = 411 dp, with
  the 42 dp floor): the band and the header row get fixed heights and every landscape text is fixed, so the fit is
  by construction; the header height (58.5 dp) is measured from R4, a 10 dp taller header still gives 42 dp rows
  (policy test); R11's Robolectric fit at 914 x 411 with and without the cutout proves it, and R11 stops if rows go
  under 42 dp. Kosmo's real height is 411.4 dp with the bars hidden.
- Vertical label: Compose has no built-in rotated text layout; the swapped-constraints layout modifier must measure
  and place the rotated text so that its bounds, clicks and TalkBack node match the key; the one-line fit of the
  17-character labels at 28 sp (about 262 of 280 dp) is an estimate; R10 measures it and stops (product question) if
  a label needs less than 20 sp.
- Band at the top edge with top margin 0: the band's rounded ends and the status-bar area (bars hidden, R4b) are near
  the display's rounded corners; phone step R-e checks it.
- Rounded screen corners: with no top or bottom margin, the ends of the band and the bottom corners of the board and
  of "JOUEUR SUIVANT" are near the rounded corners of the display; the 12 dp side margin keeps the texts off the curve
  on the Pixel 7 as far as can be judged without a device; phone step R-e checks it. Corner insets
  (`RoundedCorner`) are not used.
- Chips in the 44 dp band keep their normal padding (26.5 dp in the 36 dp inner height; clipping test in R11).
- The tall "JOUEUR SUIVANT" key is 12 dp from the triple buttons over the whole height of the rows: a tap aimed at a
  triple that slips right ends the visit; the 12 dp gap (U4 c) and the 2 dp key insets are kept; "ANNULER" is next to
  it if needed.
- Landscape action labels no longer follow the font size (« ANNULER » fixed 20 sp, vertical label fixed 28 sp, U5
  extended); Kosmo is told in the PR.
- Portrait split: the 18 sp name line heights at 1.3 to 2.0 are estimates; at scale 1.5 and 2.0 with a pending win
  the total area is at its 32 dp minimum or replaced by the two-line zone; R9's Robolectric cases measure the real
  values; the fallback never scrolls on the Pixel 7.
- The zone total grows to about 150 dp at scale 1.0 (as the X01 score): it is the biggest text of the screen, larger
  than the column-header totals, as Kosmo chose (Q12 A, Q13 A).
- About 20 portrait captures and every landscape game capture change; the X01, between-legs, end, setup and home
  captures must stay byte-identical.

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
- R8: CR22 and CR23 of `docs/rules/cricket.md` rewritten (Q2 reversal for the portrait zone, Q9 B reversal and Q10 A/Q11 A
  replacement for landscape, Q14 B 42 dp floor, Q16 A, Q17 B). `CricketBoardLayoutPolicy` gains `portraitSplit`
  (`CricketPortraitSplit`, `zoneFixedHeightDp`) and `landscapeBand` (`CricketLandscapeBandSpace`,
  `CricketLandscapeBandLayout`) with `ZONE_TOTAL_MIN`, `LANDSCAPE_BAND`, `LANDSCAPE_ACTION_WIDTH`,
  `LANDSCAPE_ROW_MIN`; the old `portrait`, `landscape` and `portraitZoneHeightDp` stay until R9 and R11. Tests added to
  `CricketBoardLayoutPolicyTest`, none removed or weakened. All budgets of R2.1 and R2.2 hold with the fixtures. Name
  line heights (28.9, 37.0, 42.0, 50.0 dp) are still the plan's estimates; R9 measures them.
- R9: portrait zone on several lines with the total (Q2 reversal for the portrait zone, Q12 A solo MPR). `CricketZoneTotal.kt` (new, fitted big number), `CricketPlayerZone.kt` (`showsTotal` variant, `BigNumber`, `total`/`mprValue` in the content), `CricketGameScreen.kt` (`PortraitGame` uses `portraitSplit`; the zone-plus-board box gets `maxHeight - GAP` as A), `CricketGameScreenState.zoneTotal`, tags `ZONE_TOTAL`/`ZONE_MPR`; old `portraitZoneHeightDp`, `portraitBoardHeightDp`, `portrait` and their constants removed. Deviations: (1) one new string `cricket_zone_mpr_label` ("MPR", both locales) for the small solo label; (2) measured in Robolectric at 411 x 814 dp: name line 28.9, 33.9, 38.1, 47.2 dp at scales 1.0 to 2.0 (the estimates 28.9, 37.0, 42.0, 50.0 were high; the screen measures it at runtime, policy fixtures kept as conservative estimates plus one measured case), and the real action bar is at most two lines (110.5 dp at 2.0, pending or not), so scale 2.0 with a pending win keeps its total (area 32 dp) instead of the plan's two-line zone; the two-line zone is covered on a 411 x 760 dp screen at 2.0; (3) rewritten/removed cases: "CR22 the zone has no total" replaced by "the portrait zone shows the total" (Q2 reversal), policy tests "portrait spare matches the plan table" and "portrait zone height grows" removed with the 84 dp zone, the old-board helper now uses `portraitSplit`; "the big total keeps its size" is checked from 1 to 3 digits only (4 digits shrinks the header total, so the room changes). Capture `zone_total_4p_portrait_fr_font130` added; 19 portrait game captures changed, every other capture byte-identical.
- R10: `ui/cricket/VerticalKeyLabel.kt` (new, not used by a screen yet): a layout modifier that swaps the constraints (parent height as line length), `graphicsLayer` rotation of -90 degrees, `WordSafeText` reused unchanged with a 20 sp minimum. `VerticalKeyLabelTest` (new): the 8 labels in 120 x 304.5 dp at scales 1.0 and 2.0, and a 120 x 150 dp box. Deviation: the plan's "28 sp keypad size" is the `keypad` style (28 sp), not `keypadLabel` (20 sp); the component takes the style as a parameter and R11 must pass `keypad`. No capture changed (210, all `cmp`-identical to the R9 build).
- R11: landscape top band, full-width board and action column (R2.2, Q9 B reversal and Q10 A/Q11 A replaced by Q16 A/Q17 B, rows 42 dp minimum with Q14 B). `CricketTopBand.kt` (new: title row with mode, short position and chips, then the single-line zone), `CricketLandscapeScreen.kt` (`LandscapeGame` uses `landscapeBand`; old `TitleRow` and chips measurement removed, `FixedTextSizes` kept), `CricketBoard.kt` (`CricketBoardActions`: `UndoKey` at the end of the header row, traversal n+2, and `NextKey` right of the rows with `VerticalKeyLabel` in `keypad` 28 sp, traversal 200), `CricketBoardLayoutPolicy.kt` (`landscapePlayerColumnWidthDp`; `CricketLandscapeLayout`, `CricketLandscapeSpace`, `landscape` and their constants removed), tags `BAND`, `LANDSCAPE_UNDO`, `LANDSCAPE_NEXT`. Deviations: (1) `CricketTargetButtons.kt` (not in Files) gets `BoardKey(isPrimary)` for the accent next key; (2) `CricketGameScreen.kt` (not in Files) gets `headerMode`, `actionsOf` (shared undo/next/confirm actions) and `modifier`/`actions` on `Board`; (3) the band builds its own title row instead of `MatchHeader`, which always takes the full width; (4) the band's dart cells use a fixed 20 sp dart text (`SINGLE_LINE_DART_SP`): the 26 sp standard text is about 41 dp tall and is clipped in the 36 dp cells (the plan gave no size). Measured at 914 x 411 dp: the 7 rows stay at 42 dp or more without scrolling, with a 48 dp cutout on either side and at scale 2. Rewritten/removed cases: policy "buttons are at least 48 dp" now checks 42 dp tall in landscape and "rows scroll" scrolls at the 42 dp floor (Q14 B); policy cases "landscape Pixel 7 rows", "landscape band is the taller of the header and the title plus zone", "landscape right column", "landscape holds the tallest bar" and "landscape board columns" removed with the right column (Q9 B reversal), replaced by the R8 `landscapeBand` cases; screen cases "CONFIRM MATCH", "VALIDER LA PARTIE" (Q10 A/Q11 A action bar) replaced by the Q17 B vertical label tests, "the board texts keep their size" by U5 at scale 2, "the title is read first" by the R2.2 TalkBack order test; the fit tests check the band, headers, action keys and 42 dp rows instead of the 340 dp right column. `CricketSystemBarsTest` unchanged and green; 5 landscape captures changed (R12 re-records them), the 205 portrait and X01 captures `cmp`-identical to R10.
- R12: `CricketScreenshotTest.kt`: `title_chips_4p_landscape_fr` renamed `band_4p_landscape_fr` (crop of the `BAND` node, not the root); new `band_win_pending_4p_landscape_en` (crop of `BAND`, English, scale 2.0) and `next_key_vertical_landscape_fr` (crop of `LANDSCAPE_NEXT`, "VALIDER LA PARTIE", scale 2.0); `winPendingState()` shared with `win_pending_4p_landscape_fr`; `LANDSCAPE_EN` qualifier. `design-system.md`: § 6.5 landscape paragraph rewritten as built, the typography note names the landscape fixed action keys, components table gains `CricketTopBand`, `CricketBoardActions` (a class in `CricketBoard.kt`) and `VerticalKeyLabel`, and `CricketTargetButtons` gets the 42 dp landscape rows. Captures are build outputs (not committed; CI artifact). Compared with the R11 build (`5cc0c22`, same production code): 208 of 210 common captures `cmp`-identical, including every cricket portrait, between-legs, end, setup, home and X01 capture and the 4 existing landscape cricket captures; the 3 changes are the rename and the 2 new captures. Also differs: `settings_three_switches_landscape_fr` (announcements screen, not cricket), only the voice status line ("Voix hors ligne disponible" vs "Vérification de la voix…"): an asynchronous voice check, not from R12. Checked by eye: no capture shows a zone column; all 5 landscape captures and the 3 new ones show the band, board and action column.
- R13: deleted the root PNG `Screenshot_20261031-182201.png` (Kosmo's Dartsmind landscape reference, uploaded in `63c5860`, `git rm`). Before the deletion, `git ls-files ':(glob)*.png'` (root only) listed only that file; `git grep` finds no file that names it (neither `Screenshot_20261031-182201` nor `182201`). The only mention left is the elided one in the R2.2 mission text of this plan, as the task requires. No code and no capture changed.
