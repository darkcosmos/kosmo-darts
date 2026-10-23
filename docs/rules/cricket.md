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
  - the mini tiles show each player's points, or his closed targets "4/7" ("4/6" without the Bull) in a solo game
    (Q8 A), plus "S n · M n" in a match;
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
- CR22. Game screen, from top to bottom in portrait:
  - the `MatchHeader` ("CRICKET · STANDARD" and the M14 position);
  - a compact `CurrentPlayerZone`: name, "MPR 2,4", the S/M chips, points (or "4/7" solo), `DartSlots` and the filled
    `VisitBox` with the marks of the visit;
  - the `CricketBoard`;
  - the `CricketKeypad`;
  - the `ActionBar` ("ANNULER", "JOUEUR SUIVANT").

  No `PlayerTile`s: the board carries every player.

  Layout contract, computed by a pure `CricketLayoutPolicy` and asserted by the fit tests:
  - Fixed parts, which never scroll and are always fully visible: the `CricketKeypad` and the `ActionBar` (with
    "ANNULER") are pinned to the bottom. Keys: 3 rows, 54 dp shrinking to 48 dp, never below. The action bar is 58 dp
    shrinking to 48 dp. Gaps go from 6 to 4 dp, as in the X01 policy.
  - Compact zone, 96 dp tall, minimum 76 dp:
    - line 1: name (1 line, ellipsis), "MPR 2,4", S/M chips;
    - line 2: points in `tileScore` instead of `scoreHuge`, then `DartSlots` and the filled `VisitBox`.

    At font scale 2.0 the zone may grow up to 1.5 times its height, and the name keeps 1 line with an ellipsis.
  - Board:
    - it takes the remaining height;
    - its cells are display-only, so they are not touch targets and may be under 48 dp;
    - row height = max(24 dp, text line + 2 dp), 32 dp by default; the text line of the 14 sp board texts is taken as
      22.5 dp times the font scale of a 14 sp text (the display font has tall metrics, review U1);
    - column header = 3 dp strip + name line (22.5 dp times the font scale) + 2 dp gap + a compact outlined `VisitBox`
      (max(26, name line + 4) dp tall, `label` text, one digit
      since a visit gives at most 9 marks);
    - the points footer row has the same height as the other rows.

    When the rows at their minimum do not fit, the board region alone scrolls vertically, with its column header kept
    on top (sticky). The current player's column is always on screen, since all columns are always visible
    horizontally.
  - Columns: a target label column of 40 dp at scale 1.0, wide enough for "BULL" at every scale (2.5 em of the 14 sp
    label size + 5 dp, never truncated), then the players share the rest equally: about
    76 dp each at 360 dp wide with 4 players. A name in a column header takes 1 line, cut with an ellipsis; the
    TalkBack text always has the full name. Points take one line and are never cut.
  - Portrait, at 360 x 640 (about 568 dp usable), 4 players, scale 1.0: header 24 + zone 76 + keypad 152 + action bar
    48 + margins 16 = 316 dp, which leaves about 252 dp for the board. That is the column header 54 + 7 rows of 24.75 +
    footer 24.75 = 252 dp, so it fits without scrolling at the minimums. At scale 2.0, the board scrolls.
  - Landscape (640 x 360, about 336 dp usable):
    - left column: the header and the board, which scrolls vertically when needed;
    - right column: the compact zone, the keypad and the action bar, with the last two pinned to the bottom. The zone
      gives way first, down to its minimum, and then scrolls by itself, never the keypad.
  - Nothing essential is cut: keys, action bar, marks and points are never truncated. Only player names get an
    ellipsis, and only the board region and, in landscape, the zone may scroll.

  The contract is also written in `docs/design/design-system.md`.
- CR23. `CricketBoard`:
  - rows: 20 to 15, then Bull (no Bull row with 25-Bull off); one column per player, in configuration order;
  - a column header with the player's color strip and name, and his outlined `VisitBox` with the marks of his last
    visit (owner precision P2, replacing "Préc.");
  - cells: empty, `/`, `X` or `Ⓧ`;
  - a footer row with the points, absent in a solo game;
  - the current player's column has an `accent` border and a "▶" before his name;
  - a dead row (CR15, owner complement to O1) is struck through and in a less visible color, so that only the rows
    still in play read clearly:
    - one horizontal line of 2 dp in `onBackgroundMuted` crosses the whole row: the target label and every player's
      cell;
    - the label and the marks switch from `onBackground` to `onBackgroundMuted`, an existing token that keeps the
      design system's 4.5:1 rule; no opacity is applied to the text. The live rows stay in `onBackground`;
    - the row keeps its height and its marks (`Ⓧ` for everyone), so the board does not move when a row dies;
    - the strike-through line carries the information, and the color only adds to it.

  No information is carried by color alone. TalkBack:
  - a live cell is read as « 20, Anna : 2 marques » / "20, Anna: 2 marks", and « 20, Anna : fermée » /
    "20, Anna: closed" at 3 marks;
  - a dead row is one merged node, read once as « 20 : fermée par tous, ne compte plus » / "20: closed by everyone,
    no longer scores", without reading its cells one by one;
  - when a row dies, nothing extra is announced: the sound (AN30) and the visual change are the feedback;
  - reading order:
    1. the column headers, starting with the current player's (« Anna, au lancer, dernière volée : 3 marques »), then
       the others in configuration order;
    2. the rows from 20 down to Bull, each row read cell by cell in column order (a dead row as its single merged
       node);
    3. the points row last (« Points : Anna 60, Bob 40 »).
  - mark counts use plurals in both languages.
- CR24. `CricketKeypad`:
  - 20 to 15, `25`, `Bull`, `Raté`, plus the one-shot modifiers Double and Triple (the X01 keypad pattern: single by
    default, so there is no "Simple" key);
  - `25` and `Bull` are absent with 25-Bull off, and disabled while a modifier is active, exactly as in X01 (Q11 A,
    O2 A);
  - every key is locked while a turn waits, as in X01;
  - keys are at least 48 dp.
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
