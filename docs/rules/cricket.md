# Cricket rules

Status: v1 (issue #40, with the owner answers Q1 to Q11, P1 to P3, O1 and O2 recorded in
`docs/plans/40-cricket-mode.md`).
This document is the single source of truth for the Cricket tests: tests cite these rules, never the issue text. Rule
ids (CR1, CR2, ...) are stable. They use the prefix CR so that they never collide with the X01 rules
(`docs/rules/x01.md`), the match rules (`docs/rules/match.md`), the persistence rules (`docs/rules/persistence.md`),
the announcement rules (`docs/rules/announcements.md`), the visit total rules (`docs/rules/visit-total.md`) or the
player rules (`docs/rules/players.md`). CR17 to CR19 are reserved for later engine rules.

Vocabulary:
- *Target*: a sector in play: 20, 19, 18, 17, 16, 15 and the Bull (the outer bull `25` and the bull `Bull` together).
- *Mark*: one hit counted on a target (a single gives 1, a double 2, a triple 3).
- *Board marks*: the sum over the targets of min(marks, 3).
- *Visit*: a player's turn of at most three darts (the French texts say « volée »).

## Engine

- CR1. Configuration: 1 to 4 players (name rules shared with X01, rules C4 and C5 of `x01.md`), variant Standard
  (default) or Cut-throat, and "25-Bull" on by default. Targets in play: 20, 19, 18, 17, 16, 15 and Bull, or only 20
  to 15 with 25-Bull off.
- CR2. Marks of a dart: a single, double or triple of 15 to 20 gives 1, 2 or 3 marks on that number; `25` gives 1 mark
  and `Bull` gives 2 marks on the Bull when it is in play. Every other dart gives 0 marks: a miss, 1 to 14, and 25 or
  Bull when 25-Bull is off.
- CR3. A target is closed for a player once he has 3 marks on it. A dart's marks beyond what closes the target, and
  every mark on a target the player has already closed, are surplus marks.
- CR4. Standard: each surplus mark scores the target's points for the thrower (15 to 20; 25 for the Bull), as long as
  at least one opponent has not closed that target. Otherwise it scores nothing.
- CR5. Cut-throat: each surplus mark adds the target's points to every opponent who has not closed that target. The
  thrower scores nothing.
- CR6. With 1 player, no points are ever scored, in either variant.
- CR7. Turns: the leg starter follows M6. A turn has at most 3 darts and ends after its third dart or on a win. There
  is no bust.
- CR8. Win condition:
  - Standard: all targets in play are closed, and the player's points are greater than or equal to each opponent's;
  - Cut-throat: all targets in play are closed, and the player's points are lower than or equal to each opponent's;
  - 1 player: all targets in play are closed.
- CR9. The condition is checked after every dart, for every player (owner Q1 A). If the thrower meets it, he wins.
  Otherwise the winner is the first player who meets it, in play order after the thrower. The leg is then finished,
  and any further dart is rejected with an explicit error.
- CR10. A player who has closed every target without meeting the score condition keeps playing.
- CR11. Undo: rules U1 to U3 of `x01.md` apply to the Cricket leg (undoing the winning dart reopens the leg). M8 is
  unchanged.
- CR12. Leg ranking: the winner first. The others are ranked by points (Standard: more is better; Cut-throat: fewer is
  better), then by board marks (more is better). Players equal on both share a rank, and the next rank is skipped
  (1, 2, 2, 4). The ranking is empty while the leg is in progress.
- CR13. Statistics: a player's marks hit are the CR2 marks of all his darts, surplus marks and marks on dead targets
  included. MPR = marks hit / darts thrown x 3, over the whole match (completed legs and the current one, owner Q4 A).
  No dart thrown means no MPR (absent, not zero). The engine returns the exact value.
- CR14. Visit marks: each player is in exactly one state:
  - *not played*;
  - *visit in progress* (marks of the darts of his current turn; 0 before his first dart);
  - *last visit* (marks of his last completed turn, the winning turn included).

  They follow VT1 to VT7 of `visit-total.md` with marks instead of points and without the bust state. Like VT6, the
  engine also gives each player's last completed visit, used while a turn waits. They restart at each leg (M9).
- CR15. Dead target (« zone totalement fermée », owner O1 A): a target closed by every player (by the only player in a
  solo game). A target becomes dead on the dart that gives its last missing mark to the last player who had not closed
  it. A *closed-sector hit* is a dart with at least one mark on a target that was already dead **before** this dart
  (owner sound request, AN30). Consequences (owner example, 3 players: P1 closed 20 with three single 20s, P2 with T20
  then D20):
  - the dart of P3 that gives him his third mark on 20 makes 20 dead, but it is not a closed-sector hit: it is a
    normal hit, because 20 was not dead before it;
  - every later dart on 20, by P1, P2 or P3, the rest of P3's visit included, is a closed-sector hit;
  - a dart that closes a target for the thrower while an opponent still has it open is a normal hit, and so is any
    scoring (Standard) or point-giving (Cut-throat) surplus dart;
  - undoing the dart that made a target dead makes it live again (CR11), and its row and sound follow;
  - with 25-Bull off, the Bull is not a target, so 25 and Bull darts are never closed-sector hits.
- CR16. Match tie-break (M10, M20): points in the last leg, in the CR12 direction, then board marks in the last leg
  (more is better).

## App

- CR20. Home:
  - the Cricket `ModeCard` follows mockup 06: a 64 x 64 "15–20" thumbnail outlined in `accent`, then "CRICKET" and
    "Standard ou Cut-throat";
  - a saved Cricket game shows in `ResumeCard` the format line "CRICKET · CUT-THROAT", followed by "· SANS 25-BULL"
    when 25-Bull is off and by the M14 format text in a match;
  - the mini tiles show each player's points, and nothing in a solo game (no "4/7": owner, PR #60 review, Q6 A), plus
    "S n · M n" in a match;
  - the button says "REPRENDRE · À X DE LANCER" (M18).
- CR21. Setup: the title is "CRICKET", followed by:
  - "VARIANTE": a `SegmentedChoice`, Standard / Cut-throat, with one hint line under it in `onBackgroundMuted` for the
    selected variant:
    - Standard: « Vos points sont pour vous ; le plus haut score gagne. » / "Your points are yours; highest score
      wins.";
    - Cut-throat: « Vos points vont aux adversaires ; le plus bas score gagne. » / "Your points go to your opponents;
      lowest score wins.";
  - "25-BULL": a `SettingsSwitch`, on by default;
  - the M13 "MATCH" steppers;
  - "JOUEURS · ORDRE DE LANCER" and "Mélanger";
  - "COMMENCER".

  The setup is prefilled with the shared roster and the last Cricket settings (R22).
- CR22. Game screen, in portrait from top to bottom:
  - the `MatchHeader` ("CRICKET · STANDARD" and the M14 position, in its short form "S2 · L3" in landscape so that the title stays on one line);
  - a compact `CurrentPlayerZone`: line 1, the name, "MPR 2,4" and the S/M chips; line 2, the `DartSlots`, or the
    white "VICTOIRE" banner of CR25. The zone shows no total and no "n/7", in a solo game either (owner, PR #60
    review, Q2, Q4 A). A dart is shown only if it gives a mark (CR2); a dart that gives none is shown as a miss. There
    is no visit box;
  - the `CricketBoard` with its target buttons (CR23, CR24);
  - the `ActionBar` ("ANNULER", "JOUEUR SUIVANT" or the CR25 confirm label), unchanged from the other game screens.

  No `PlayerTile`s: the board carries every player. There is no separate keypad.

  Layout contract, computed by a pure `CricketBoardLayoutPolicy` and asserted by the fit tests. The reference device is
  the Pixel 7 (owner: the smallest phone), 411 x 914 dp in portrait and 914 x 411 dp in landscape; smaller screens are
  not a design constraint. The pure policy does no font-scale arithmetic: the screen passes it measured dp values.
  - Board columns, from start to end: the player columns, which share the width left equally, then the button column
    (3 x 48 dp at least, up to 3 x 56 dp while the player columns keep 76 dp each), at the end of the row, on the
    right of the screen. At 411 dp wide: player columns of 61, 76, 110 and 219 dp and a button column of 144, 159, 168
    and 168 dp at 4, 3, 2 and 1 players.
  - Rows are 48 to 56 dp in both orientations (the buttons are touch targets): there is no smaller landscape row and
    no 32 dp floor. The column header is pinned: 3 dp strip, the total, the name (about 63 dp at scale 1.0 and 101 dp
    at 2.0); in a solo game it has no total line. The top cell of the button column ("RATÉ" alone) has the header's
    height (48 dp at least) and the button column's width.
  - Fallback only, below the Pixel 7 (or with a larger "display size"), unsupported: the target rows scroll alone,
    under the pinned header, in both orientations; they never go under 48 dp. On the Pixel 7, at every font scale from
    1.0 to 2.0, with 1 to 4 players, 6 or 7 rows and a pending win, nothing scrolls. The header, "RATÉ", the zone and
    the action bar never scroll.
  - Compact zone, 84 dp tall (times the growth of the 14 sp line, up to 1.5) in portrait; the name keeps 1 line with
    an ellipsis.
  - Action bar: the existing `ActionBar`, "ANNULER" then the primary button, side by side with equal width and a 12 dp
    gap, in both orientations: 187 dp per button in portrait at 411 dp wide; labels at least 12 sp at every font
    scale (a word is never cut).
  - Portrait, at 411 x 814 dp (the Pixel 7 minus its tallest bars), 4 players, 7 rows, scale 2.0, "VALIDER LA PARTIE"
    pending, no scrolling: 8 + `MatchHeader` 44.6 + 6 + zone 126 + 6 + header 101 + 7 x 48 + 6 + action bar 151.5 + 8
    = 793.1 dp, about 21 dp to spare.
  - A column total picks the largest of 24, 20, 16 and 14 sp (20, 16 and 14 sp in landscape) whose line fits its
    column (digits x 0.6 em + 4 dp, the em read at the current font scale), and the fixed 14 sp when none fits; a total
    is never cut.
  - Landscape (914 x 411 dp, Kosmo, Q1, Q10 A and Q11 A): the same board, not turned, everything visible without
    scrolling (the fonts may be slightly reduced: board texts, zone and title at fixed 14 sp, totals at 20 sp at most,
    sizes that do not follow the system font size). On the left, the board; on the right, the `MatchHeader` (the S/M
    chips at its end) above the zone, the two together as tall as the board's column header (one band, about 57 dp),
    the zone on one line (name, MPR, the visit's darts), and at the bottom the action bar. The band is the taller of
    the column header and of the title plus the zone line. The rows fill the height under the band: 49.5 dp at 7 rows,
    56 dp at 6 rows, with a 4 dp margin top and bottom. The right column is at least 340 dp wide with a 48 dp display
    cutout on one side (358 dp at 4 players) and is separated from the board by 12 dp; its action bar buttons are at
    least 173 dp wide at 4 players, and 340 dp of height under the band hold the tallest bar (151.5 dp).
  - System bars and display cutout in landscape (owner Q5 B): the Cricket game screen hides the system bars while it
    is shown in landscape (a swipe from the edge brings them back for a moment, over the content, without moving
    it) and shows them again on every exit (portrait, between legs, end, abandon, home). The content is then padded
    with the display cutout insets only, so nothing goes under the camera hole; in portrait and everywhere else the
    padding is the safe drawing insets, as before. The decision is a pure `SystemBarsPolicy` (hide always comes with
    cutout-only padding). Hiding the bars is what gives the 49.5 dp rows (owner Q5 B); with the bars shown the rows
    would be 41 to 45 dp.
  - Nothing essential is cut: buttons, action bar, marks and totals are never truncated; only player names get an
    ellipsis.

  The contract is also written in `docs/design/design-system.md`.
- CR23. `CricketBoard`:
  - rows: 20 to 15, then Bull (no Bull row with 25-Bull off); one column per player, in configuration order; there
    is no label column and no points row (owner, PR #60 review);
  - a column header, pinned, with from top to bottom the player's color strip, his total and his name. The total is
    the largest text of the board (display font, `onBackground`, 24 sp down to 14 sp as the column and the font scale
    require, 20 sp at most in landscape, never cut) and is absent in a solo game. The last visit of a player is not
    shown (TalkBack still reads it);
  - at the top of the button column, at the height of the headers, the "RATÉ" key alone, over the whole width of the
    column; it records a miss (CR24);
  - cells: empty, `/`, `X` or `Ⓧ`;
  - the current player's column has an `accent` border and a "▶" before his name;
  - a dead row (CR15, owner complement to O1) is struck through and in a less visible color, so that only the rows
    still in play read clearly:
    - one horizontal line of 2 dp in `onBackgroundMuted` crosses the player cells of the row, not its buttons;
    - the marks switch from `onBackground` to `onBackgroundMuted`, an existing token that keeps the design system's
      4.5:1 rule; no opacity is applied to the text. The live rows stay in `onBackground`;
    - the row keeps its height and its marks (`Ⓧ` for everyone), so the board does not move when a row dies;
    - its buttons stay enabled and keep a muted style (the label in `onBackgroundMuted` on `surfaceHigh`), which
      does not look like a locked key (CR24);
    - the strike-through line carries the information, and the color only adds to it.

  No information is carried by color alone. TalkBack:
  - a live cell is read as « 20, Anna : 2 marques » / "20, Anna: 2 marks", and « 20, Anna : fermée » /
    "20, Anna: closed" at 3 marks;
  - a dead row's cells are one merged node, read once as « 20 : fermée par tous, ne compte plus » / "20: closed by
    everyone, no longer scores", without reading its cells one by one; its buttons are read after it;
  - when a row dies, nothing extra is announced: the sound (AN30) and the visual change are the feedback;
  - reading order, the same in portrait and in landscape:
    1. the column headers, starting with the current player's (« Anna, au lancer, 60 points, dernière volée : 3
       marques »; no points in a solo game), then the others in configuration order;
    2. « Raté »;
    3. the rows from 20 down to Bull; in each row first its cells in column order (a dead row as its merged node),
       then its buttons from start to end (« 20 », « Double 20 », « Triple 20 »);
    4. the action bar: « Annuler », then the primary button.
  - mark counts and the header points use plurals in both languages.
- CR24. Target buttons (`CricketTargetButtons`), in the button column of the board, at the end of each row:
  - one tap records one dart, there is no modifier state. Rows 20 to 15 have three buttons, single, double, triple,
    from start to end, each showing the number, with two dots under it on the double and three on the triple;
    TalkBack « 20 », « Double 20 », « Triple 20 » / "20", "Double 20", "Triple 20";
  - the Bull row (only with 25-Bull on, CR1) has two buttons sharing the width: "BULL" (the 25, one mark, TalkBack
    « 25 ») and "BULL" with two dots (the bull, two marks, TalkBack « Bull »); there is no triple bull (rule D2);
  - "RATÉ" (CR23) records a miss; "ANNULER" of the action bar undoes the last dart (CR11);
  - a dart is refused (ignored: no state change, no sound) while a turn waits for "Next player" (CR7), while a win is
    pending until "Confirm" or "Undo" (CR25), and for the 25 and the Bull with 25-Bull off (CR1);
  - every button is locked (35 % opacity) while a turn waits or a win is pending; a dead row's buttons stay enabled
    (a dart there is a real dart, AN30);
  - each button's touch area is the whole cell, at least 48 x 48 dp in both orientations (48 dp wide at least and
    the row height, 48 to 56 dp); the drawn key is inset by 2 dp on each side, so neighbours show a 4 dp gap.
- CR25. Win: no dialog. The zone shows the winner, also when the winning dart was thrown by another player (CR9), with
  a white "VICTOIRE" banner in the route area (in place of the X01 CHECKOUT banner). The action bar reads "ANNULER" /
  "VALIDER LA MANCHE", "VALIDER LE SET" or "VALIDER LA PARTIE", with the M15 labels (Q6 A). "ANNULER" undoes the
  winning dart.
- CR26. Between legs (M16), with these differences: the winner detail is "245 PTS", and nothing in a solo game (Q7 A);
  the scoreboard shows MPR MATCH instead of MOY MATCH.
- CR27. End screen (M17), with these differences: the ranking follows M10 with CR16; the columns are SETS / MANCHES as
  in M17, PTS (points of the last leg, absent in a solo game), MPR MATCH and FLÉCH. (Q7 A).
- CR28. "REVANCHE" keeps the variant, 25-Bull and the format, and moves the first player to the end (M17). Each player
  keeps his color (PC7).
