# Plan: Cricket mode (issue #40)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/40

Add Cricket as a second game mode next to X01, with the Standard and Cut-throat variants, the "25-Bull" setting, 1 to
4 players, and legs and sets reused from issue #39. Cricket follows the same rules of play as X01: "Next player",
unlimited undo inside the leg, the winning dart confirmed in the action bar, an end screen with "Rematch" and "New
game". The screens use the design system (`docs/design/design-system.md`), and existing components come first. The
game rules live in `:game-engine`, and the UI only renders them. One game is saved at a time, whatever the mode.
Settings are kept per mode, and player names are shared between modes.

## Scope and out of scope

In scope:
- Engine (`engine/cricket`, new): configuration, marks, closing, points (Standard, Cut-throat), win, leg ranking,
  undo, statistics (MPR, visit marks), the Cricket match on top of `engine/match`, and the Cricket audio cues.
- Engine (`engine/match`): a match ranking whose mode tie-break can have several criteria (M20).
- Engine (`engine/player`): player list validation shared by X01 and Cricket.
- Rules:
  - new `docs/rules/cricket.md` (prefix `CR`);
  - `match.md` amended (M10, M11, new M20);
  - `persistence.md`, new R20 to R25;
  - `announcements.md`, new AN29 to AN32 and an amended AN24.
- Persistence: save format version 4 (mode line, Cricket saved game, per-mode last setup), and new ADR 0008. Versions 1
  to 3 are still read.
- App:
  - Cricket card on the home screen and a mode-aware resume summary;
  - Cricket setup;
  - Cricket game screen: marks board, Cricket keypad, current player zone;
  - between-legs and end screens for Cricket;
  - one saved game across modes;
  - the new sound `sfx_closed_cricket_sector.ogg`.
- Strings in `values/` and `values-fr/`. The design system doc gets the new components.
- Tests: engine JVM, persistence JVM, app JVM, Robolectric, and the Roborazzi captures listed by the issue.

Out of scope (issue): the "No score" variant, other modes, statistics and game history. Also out of scope:
- any change to the X01 rules or screens, beyond the refactors listed below, which leave X01 behavior unchanged;
- moving `Dart` out of `engine/x01` (see Approach, alternative A3);
- deleting the remote branch `feat/sfx_cricket` (owner follow-up, see Open questions).

## Approach

### 1. Engine: Cricket leg (`engine/cricket`, pure Kotlin)

- `CricketVariant { STANDARD, CUT_THROAT }`.
- `CricketTarget { TWENTY, NINETEEN, EIGHTEEN, SEVENTEEN, SIXTEEN, FIFTEEN, BULL }`, each with its `points` (20 to
  15, Bull 25), in board order (20 to 15, then Bull).
- `CricketConfig(players, variant = STANDARD, isBullInPlay = true)`:
  - `targets` is the 7 targets, or the 6 numbers when the Bull is not in play (CR1);
  - names are validated by the shared `PlayerListRules` (section 3).
- `CricketMarks.of(dart, config)`: the target and number of marks of a `Dart` (CR2). The existing `x01.Dart` is reused
  as is: `segment` + `Multiplier`, the 25 and Bull special segments, and `Dart.MISS`.
- `CricketGame(config, darts, startingPlayerIndex)`:
  - immutable, replayed from its darts like `X01Game` (pattern of P1 in `x01.md`);
  - `throwDart`, `undo` and `canUndo` (CR11), and `state: CricketState`.
- `CricketState`:
  - per player (`CricketPlayerState`): name, marks per target capped at 3, points, darts thrown, marks hit (CR13),
    darts of the current turn;
  - `currentPlayerIndex`, `winnerIndex`, `ranking` (CR12), `completedTurns` (`CricketTurn`: player, darts, marks);
  - `deadTargets`: the targets closed by every player (CR15).
- `CricketReplay` (internal) applies CR3 to CR10 dart by dart. Win check (CR9): after every dart, the thrower is
  checked first, then the other players in play order after him.

### 2. Engine: Cricket match (`engine/cricket` + `engine/match`)

- `CricketMatchConfig(config, format, colorSlots)` and `CricketLegSummary`, which holds per player the winner, marks
  hit, darts thrown, points and board marks (`boardMarks` is used only by the tie-break of the last leg, which is never
  summarized).
- `CricketMatch(matchConfig, completedLegs, currentLeg)`:
  - same contract as `X01Match`: `score: MatchScore`, `pendingOutcome`, `isFinished`, `finalScore`, `confirmLeg`,
    `undo` within the leg (M8), and starter rotation (M6);
  - `playerStats` (MPR over the match, darts over the match, CR13);
  - `ranking` (M10, with the Cricket tie-break CR16).
- `MatchRanking.rank(score, tieBreak: Comparator<Int>)` is new, and the existing `rank(score, List<Int>)` delegates to
  it. X01 is unchanged (M20).
- `CricketVisits.of(state)`: the visit marks of each player (CR14). It mirrors `VisitTotals`, without the bust state.

### 3. Engine: shared player list rules (`engine/player`)

- `PlayerListRules.violations(names): List<PlayerListViolation>`: no players, too many, blank, too long, duplicate. The
  checks are moved from `X01Config`, unchanged.
- `X01Config` maps them to its existing `X01ConfigError` values. Its public API and messages are unchanged, and X01
  tests stay green unchanged.
- `CricketConfig` throws `InvalidCricketConfigException(violations: List<PlayerListViolation>)`.

### 4. Engine: Cricket cues (`engine/cricket`)

- `CricketCue`, sealed:
  - `DartHit(dart)`: at least one mark, on a target that was not dead before the dart;
  - `ClosedSectorHit(dart)`: at least one mark, on a target dead before the dart (CR15, AN30);
  - `DartMiss`: 0 marks;
  - `DartUndone`;
  - `TurnStart(playerIndex, name, isGameStart)`;
  - `Win(playerIndex, name, outcome)`.
- `CricketCues.afterDart(before, after)` and `CricketCues.turnStart(game)` mirror `X01Cues`.
- A separate type keeps `X01Cue` (bust, 180, turn score, checkout remaining) untouched.

### 5. Persistence (format version 4, ADR 0008)

- Both files get a `mode` line (`x01` or `cricket`) right after the version line. Every write uses version 4. Versions 1
  to 3 are read as X01, exactly as today (R16, R18, and R25).
- Saved game:
  - `sealed interface StoredGame`, with the existing `SavedGame` (X01, unchanged name and fields) and the new
    `SavedCricketGame(match: CricketMatch, isAwaitingNextPlayer, isBetweenLegs)`;
  - `GameSaveCodec` dispatches on the mode. The Cricket body has: names, slots, variant, bull flag, format, the
    confirmed legs (winner, marks hit, darts, points per player) and the darts of the current leg, in the existing
    dart tokens.
  - Validation follows ADR 0001 and 0005: canonical values, CRC, replay by the engine, and consistency of the flags.
- Last setup:
  - `LastSetups(roster, x01: X01Settings?, cricket: CricketSettings?)`, where each mode keeps its own format and options
    (X01: start score and checkout rule; Cricket: variant and 25-Bull) and the roster is shared (Q9 A);
  - a version 1 to 3 file gives `roster` + `x01`, and `cricket = null`, which means the Cricket defaults;
  - `MAX_FILE_BYTES` is unchanged.
- A single saved game across modes (R20): starting any mode replaces the saved game, after the R3 confirmation.

### 6. App

Screens (`AppState`):
- new `Screen.CricketGame(CricketGameScreenState, isNewGame, noVoiceNotice)`, `Screen.CricketBetweenLegs` and
  `Screen.CricketEnd`;
- `AppReducer` dispatches to a new `CricketReducer.kt`, so the X01 branches stay as they are.

Events:
- `NewGame` becomes `NewGame(mode: GameMode)`, with `GameMode { X01, CRICKET }`. The confirmation dialog remembers the
  mode;
- new `CricketVariantSelected` and `BullInPlayToggled`;
- the keypad events are reused: `NumberTapped`, `MultiplierTapped`, `OuterBullTapped`, `BullTapped`, `MissTapped`,
  `UndoTapped`, `NextPlayerTapped`, `FinishGameTapped`.

`CricketGameScreenState` (pure JVM) mirrors `GameScreenState`: multiplier, waiting flag, abandon dialog, highlighted
player (the winner while a win is pending, CR25), keypad and bull key enabling, zone mode (normal or victory), header
position, and the board rows (`CricketBoardRow`: target, marks per player, `isDead`).

Setup:
- `SetupState` gets `mode` and `cricket: CricketSettings` (variant, bull). The X01 fields stay, and the roster and
  format are shared within the screen;
- `SetupState.start()` returns an X01 or a Cricket match config.

Components:
- reused: `MatchHeader`, `CurrentPlayerZone` (its stat line becomes generic, `ZoneStat`: "MOY 62,40" or
  "MPR 2,4"), `PlayerTile` (not on the Cricket game screen, see CR22), `ActionBar`, `Scoreboard`, `WinnerZone`,
  `ModeCard`, `ResumeCard`, `VisitBox`, `DartSlots`, `Stepper`, `SegmentedChoice` and `SettingsSwitch`;
- new: `CricketBoard` (marks board) and `CricketKeypad`. They are built from the internal `Key` and `ModifierKey` of
  `Keypad.kt`, moved to a shared internal file.

Screens:
- `BetweenLegsScreen` and `EndScreen` are refactored to take mode-neutral UI models (`BetweenLegsContent`,
  `EndContent`: columns, rows and winner detail), built by an X01 mapper (unchanged output, proven by the existing
  tests and captures) and a Cricket mapper.

Cues and sound:
- `gameCues` gets a Cricket branch that uses `CricketCues`, and `CueScript.of(CricketCue)` renders it (AN29 to AN32);
- `SoundEffect.CLOSED_SECTOR` plays `R.raw.sfx_closed_cricket_sector`.

Alternatives considered:
- **A1. A generic `Match<Leg>` / `GameMode` abstraction in the engine and a single generic game screen.** Rejected for
  this mission: it rewrites `X01Match`, `GameScreenState` and every X01 screen and test, so the risk falls on the
  shipped mode. `engine/match` already carries what both modes share (M11), and both bindings stay small.
- **A2. One `SavedGame` sealed type renamed everywhere.** Rejected: `StoredGame` above `SavedGame` keeps every X01
  persistence test unchanged.
- **A3. Moving `Dart`/`Multiplier` to a neutral `engine/dart` package.** This is a mechanical change over about 60
  files, out of this mission's size rule. `engine/cricket` imports `engine/x01.Dart`, which is documented in ADR 0008,
  and a later `chore` PR can move it.
- **A4. Separate last-setup files per mode.** Rejected: names are shared (Q9 A), so one file holds the shared roster
  and the per-mode settings atomically.
- **A5. A Cricket version of the X01 screens (copies of `EndScreen` / `BetweenLegsScreen`).** Rejected: the UI models
  avoid duplicating layout code that the owner already validated.

## Rules (new `docs/rules/cricket.md`, prefix CR; T1 writes CR1 to CR28, later tasks only refine them)

Engine:
- CR1. Configuration: 1 to 4 players (name rules shared with X01, C rules of `x01.md`), variant Standard (default) or
  Cut-throat, and "25-Bull" on by default. Targets in play: 20, 19, 18, 17, 16, 15 and Bull, or only 20 to 15 with
  25-Bull off.
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
  better), then by board marks (the sum over the targets of min(marks, 3); more is better). Players equal on both
  share a rank, and the next rank is skipped (1, 2, 2, 4). The ranking is empty while the leg is in progress.
- CR13. Statistics: a player's marks hit are the CR2 marks of all his darts, surplus marks and marks on dead targets
  included. MPR = marks hit / darts thrown x 3, over the whole match (completed legs and the current one, owner Q4 A).
  No dart thrown means no MPR (absent, not zero). The engine returns the exact value.
- CR14. Visit marks: each player is in exactly one state:
  - *not played*;
  - *visit in progress* (marks of the darts of his current turn; 0 before his first dart);
  - *last visit* (marks of his last completed turn, the winning turn included).

  They follow VT1 to VT7 of `visit-total.md` with marks instead of points and without the bust state. Like VT6, the
  engine also gives each player's last completed visit, used while a turn waits. They restart at each leg (M9).
- CR15. Dead target ("zone totalement fermée", owner O1 A): a target closed by every player (by the only player in a
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

App (implemented in T8 to T10):
- CR20. Home:
  - the Cricket `ModeCard` follows mockup 06: a 64 x 64 "15–20" thumbnail outlined in `accent`, then "CRICKET" and
    "Standard ou Cut-throat";
  - a saved Cricket game shows in `ResumeCard` the format line "CRICKET · CUT-THROAT", followed by "· SANS 25-BULL" when
    25-Bull is off and by the M14 format text in a match;
  - the mini tiles show each player's points, or his closed targets "4/7" ("4/6" without the Bull) in a solo game
    (Q8 A), plus "S n · M n" in a match;
  - the button says "REPRENDRE · À X DE LANCER" (M18).
- CR21. Setup: the title is "CRICKET", followed by:
  - "VARIANTE": a `SegmentedChoice`, Standard / Cut-throat, with one hint line under it in `onBackgroundMuted` for the
    selected variant (review U5):
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

  **Layout contract** (review U1). It is computed by a pure `CricketLayoutPolicy`, next to `GameLayoutPolicy`, tested
  on the JVM, and asserted by the Robolectric fit tests:
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
    - row height = max(26 dp, text line height + 6 dp), 32 dp by default;
    - column header = 4 dp strip + name line + a compact outlined `VisitBox` (24 dp tall, `label` text, one digit
      since a visit gives at most 9 marks);
    - the points footer row has the same height as the other rows.

    When the rows at their minimum do not fit, the board region alone scrolls vertically, with its column header kept
    on top (sticky). The current player's column is always on screen, since all columns are always visible
    horizontally.
  - Columns: a target label column of 40 dp (48 dp at scale 2.0), then the players share the rest equally: about
    76 dp each at 360 dp wide with 4 players. A name in a column header takes 1 line, cut with an ellipsis; the
    TalkBack text always has the full name. Points take up to 5 digits without truncation at scale 2.0 in 76 dp.
  - Portrait, at 360 x 640 (about 568 dp usable), 4 players, scale 1.0: header 24 + zone 76 + keypad 152 + action bar
    48 + margins 16 = 316 dp, which leaves about 252 dp for the board. That is the column header 44 + 7 rows of 26 +
    footer 26 = 252 dp, so it fits without scrolling at the minimums. At scale 2.0, the board scrolls.
  - Landscape (640 x 360, about 336 dp usable):
    - left column: the header and the board, which scrolls vertically when needed;
    - right column: the compact zone, the keypad and the action bar, with the last two pinned to the bottom. The zone
      gives way first, down to its minimum, and then scrolls by itself, never the keypad.
  - Nothing essential is cut: keys, action bar, marks and points are never truncated. Only player names get an
    ellipsis, and only the board region and, in landscape, the zone may scroll.

  The contract is also written in `docs/design/design-system.md` (new components, T8). That file is not protected: it
  was last changed by PR #57.
- CR23. `CricketBoard`:
  - rows: 20 to 15, then Bull (no Bull row with 25-Bull off); one column per player, in configuration order;
  - a column header with the player's color strip and name, and his outlined `VisitBox` with the marks of his last
    visit (the visit system of issue #49, replacing "Préc.", owner precision);
  - cells: empty, `/`, `X` or `Ⓧ`;
  - a footer row with the points, absent in a solo game;
  - the current player's column has an `accent` border and a "▶" before his name;
  - a dead row (CR15, owner complement to O1) is **struck through and in a less visible color**, so that only the
    rows still in play read clearly:
    - one horizontal line of 2 dp in `onBackgroundMuted` crosses the whole row: the target label and every player's
      cell;
    - the label and the marks switch from `onBackground` to `onBackgroundMuted`. This is an existing token (8.7:1 on
      `surface`), so the row stays above the design system's 4.5:1 rule (section 8) and no opacity is applied to its
      text. The live rows stay in `onBackground`;
    - the row keeps its height and its marks (`Ⓧ` for everyone), so the board does not move when a row dies;
    - the strike-through line carries the information, and the color only adds to it.

  No information is carried by color alone.

  Proof (review T3): the composable only draws, and the style is decided outside it.
  - The pure `CricketBoardText` maps each `CricketBoardRow` to `CricketRowText(target label, cells, style:
    RowStyle.LIVE | RowStyle.DEAD, textColor: BoardTextColor.PRIMARY | BoardTextColor.MUTED, isStruck)`. JVM tests
    assert `DEAD`, `MUTED` and `isStruck` on dead rows only, and `LIVE`, `PRIMARY` and not struck on live rows.
  - `CricketBoard` draws the strike line as a node tagged `CricketTags.rowStrike(target)`. Each row carries the custom
    semantics property `CricketSemantics.RowDead`. Robolectric asserts, per row, that the strike node exists exactly on
    the dead rows and that the property matches.
  - The grayscale readability claim is proven by the strike node: a non-color cue present on every dead row and absent
    on every live row. The grayscale capture is only a review aid (Roborazzi records and never compares).

  TalkBack:
  - a live cell is read as "20, Anna : 2 marques" (« 20, Anna : 2 marques » / "20, Anna: 2 marks"), and "fermée"
    (« 20, Anna : fermée » / "20, Anna: closed") at 3 marks;
  - a dead row is one merged node, read once as « 20 : fermée par tous, ne compte plus » ("20: closed by everyone, no
    longer scores"), without reading its cells one by one;
  - when a row dies, nothing extra is announced: the sound (AN30) and the visual change are the feedback, and a live
    region would talk over the voice calls;
  - reading order (review U7):
    1. the column headers, starting with the current player's (« Anna, au lancer, dernière volée : 3 marques »), then
       the others in configuration order;
    2. the rows from 20 down to Bull, each row read cell by cell in column order (a dead row as its single merged
       node);
    3. the points row last (« Points : Anna 60, Bob 40 »).

    It is set with `traversalIndex` and `isTraversalGroup` and asserted by a Robolectric test on the semantics tree.
  - mark counts use `<plurals>` (review U2): see the strings table.
- CR24. `CricketKeypad`:
  - 20 to 15, `25`, `Bull`, `Raté`, plus the one-shot modifiers Double and Triple (the X01 keypad pattern: single by
    default, so there is no "Simple" key);
  - `25` and `Bull` are absent with 25-Bull off, and disabled while a modifier is active, exactly as in X01 (Q11 A);
  - every key is locked while a turn waits, as in X01;
  - keys are at least 48 dp.
- CR25. Win: no dialog. The zone shows the winner, also when the winning dart was thrown by another player (CR9), with
  a white "VICTOIRE" banner in the route area (in place of the X01 CHECKOUT banner). The action bar reads "ANNULER" /
  "VALIDER LA MANCHE", "VALIDER LE SET" or "VALIDER LA PARTIE", with the M15 labels (Q6 A). "ANNULER" undoes the winning
  dart.
- CR26. Between legs (M16), with these differences: the winner detail is "245 PTS", and nothing in a solo game (Q7 A);
  the scoreboard shows MPR MATCH instead of MOY MATCH.
- CR27. End screen (M17), with these differences: the ranking follows M10 with CR16; the columns are SETS / MANCHES as
  in M17, PTS (points of the last leg, absent in a solo game), MPR MATCH and FLÉCH. (Q7 A).
- CR28. "REVANCHE" keeps the variant, 25-Bull and the format, and moves the first player to the end (M17). Each player
  keeps his color (PC7).

Changes to other rules:
- `match.md`:
  - M10 and M11: the tie-break is "the mode tie-break" (X01: remaining score; Cricket: CR16);
  - new M20: the mode tie-break may be a sequence of criteria, each with its own direction;
  - M2: "for Cricket, CR7 to CR9".
- `persistence.md`:
  - R20: one saved game, whatever the mode. Starting any mode while a game is saved asks for confirmation (R3), and the
    summary says the mode (CR20);
  - R21: resuming a Cricket game restores everything R4, R14, R15 and R19 restore for X01, with the CR25 zone for a
    pending win;
  - R22: the last setup keeps the shared roster (names, order, colors) and, per mode, its format and options. "Start"
    and "Rematch" write the roster and the settings of their mode;
  - R23: format version 4 adds the mode line, and every write uses it. A file is rejected (the R9 message, file
    deleted; for the last setup, R10, ignored silently) when (review S1):
    - its v4 mode line is missing or has an unknown value;
    - a v1 to v3 file contains a mode line;
    - the body does not match its mode (`cricket` with an X01 body, or the reverse);
    - the CRC does not cover the mode line correctly.
  - R24: Cricket saves are validated like X01 saves (R9 to R11), with these bounds checked by the decoder (review S2,
    S3):
    - player count 1 to 4, with names valid under `PlayerListRules`. An `InvalidCricketConfigException` thrown while
      decoding is caught and gives "rejected", never a crash;
    - variant and 25-Bull tokens from a closed set (`standard`/`cut-throat`, `1`/`0`), in the saved game and in the
      last setup;
    - each confirmed-leg summary has a winner index below the player count, darts at most the 10,000-dart limit (R12),
      marks hit at most 3 x darts (CR2), and points from 0 to 99,999. That is the existing 5-digit canonical integer
      of `SaveFormat`, which is unchanged. A leg above it is not written and the previous file is kept, exactly like
      the 10,000-dart limit of R12; reaching it takes more than 1,600 darts. So the MPR and ranking sums cannot
      overflow an `Int`;
    - the leg count respects M12, and no confirmed leg decides the match (the match is never decided before its last
      leg, as for X01);
    - the darts of the current leg are replayed by the engine (rejected by engine on any error).

    The confirmed-leg summaries are the match's accepted record, as in ADR 0001 and 0005 (review A5). They cannot be
    replayed (their darts are not stored), so the decoder checks only the bounds above and their consistency with the
    match score.
  - R25: a version 1 to 3 saved game or last setup is read as X01 (R16, R18). Cricket then uses its defaults, with the
    shared roster.
- `announcements.md`:
  - AN29: the Cricket cues of section 4 and their hook points, the same as `gameCues` for X01 (AN7);
  - AN30: `sfx_closed_cricket_sector` plays instead of the hit sound for a closed-sector hit (CR15: a dart on a target
    already closed by every player before this dart; the dart that completes the closure plays the hit sound). Any other dart with
    marks plays the hit sound, and a dart with 0 marks plays the miss sound. With "Call each dart" on, a closed-sector
    hit is still called after its sound;
  - AN31: turn start (without the checkout suffix), the leg, set and victory voice (AN27) and the victory sound only on
    a match win;
  - AN32: in Cricket there is no turn score, no 180, no bust and no remaining-score announcement (Q10 A);
  - AN24: seven sounds, the new file credited.

## Files and types added or changed

`:game-engine` (`io.github.darkcosmos.kosmodarts.engine`):
- new in `cricket/`: `CricketVariant`, `CricketTarget`, `CricketConfig` (+ `InvalidCricketConfigException`),
  `CricketMarks`, `CricketGame`, `CricketState` (`CricketPlayerState`, `CricketTurn`, `CricketRank`), `CricketReplay`,
  `CricketMatchConfig`, `CricketLegSummary`, `CricketMatch` (`CricketMatchPlayerStats`), `CricketVisits`
  (`CricketVisit`), `CricketCue`, `CricketCues`;
- `player/PlayerListRules.kt` (new) and `x01/X01Config.kt` (delegation only);
- `match/MatchRanking.kt` (comparator overload), `match/LegOutcome.kt` (`isConfirmable`), and `x01/X01Match.kt` (uses
  `isConfirmable`).

`:app` (`io.github.darkcosmos.kosmodarts`):
- `persistence/`:
  - changed: `SaveFormat`, `SaveBody`, `GameSaveCodec`, `LastSetupCodec`, `SavedGame` (implements `StoredGame`),
    `SaveLoader`, `Persistence`;
  - new: `StoredGame`, `SavedCricketGame`, `CricketSaveBody`, `LastSetups`.
- `ui/app/`:
  - changed: `AppState` (screens, events, `lastSetup: LastSetups`), `AppReducer`, `SetupTransitions`, `InitialState`,
    `PersistenceEffect`, `GameCues`;
  - new: `CricketReducer`, `GameMode`.
- `ui/cricket/` (new):
  - `CricketGameScreenState`, `CricketGameScreen`, `CricketBoard`, `CricketBoardText` (mapping, pure), `CricketKeypad`,
    `CricketTags`, `CricketSemantics` (`RowDead`), `CricketLayoutPolicy` (pure), `SavedCricketGameScreen`;
  - `CricketBetweenLegsContent` and `CricketEndContent` (mappers).
- `ui/format/`: `MprFormat` (new, `formatMpr`).
- `ui/game/`: `KeypadKeys.kt` (new, `Key` and `ModifierKey` moved out of `Keypad.kt`), `Keypad.kt`.
- `ui/components/`: `CurrentPlayerZone` (`ZoneStat`, VICTOIRE banner), `ComponentModels`.
- `ui/between/` and `ui/end/`: `BetweenLegsScreen`, `BetweenLegsState`, `EndScreen` and `EndRow`, switched to UI
  models.
- `ui/setup/`: `SetupState`, `SetupScreen`, plus the new `CricketSettings` section composable.
- `ui/home/`: `HomeScreen` (Cricket card, `NewGame(mode)`) and `ResumeSummary` (mode-aware).
- `audio/`: `SoundEffect`, `SoundPoolPlayer` and `CueScript`.
- `res/raw/sfx_closed_cricket_sector.ogg` (recovered from `origin/feat/sfx_cricket`), and `values/strings.xml`,
  `values-fr/strings.xml`.

Docs:
- new: `docs/rules/cricket.md`, `docs/adr/0008-save-format-v4-modes.md`;
- changed: `docs/rules/match.md`, `docs/rules/persistence.md`, `docs/rules/announcements.md`,
  `docs/sounds/CREDITS.md`, `docs/design/design-system.md` (new components `CricketBoard`, `CricketKeypad`, `ZoneStat`,
  the VICTOIRE banner, and the CR22 layout contract).

## Data read, stored or exposed

- Stored, in the existing app-private files and nothing else:
  - the saved game: mode, names, color slots, variant, 25-Bull, format, confirmed legs and darts;
  - the last setup: roster, plus per mode its format and options.
- No new permission, no network, no export, no new component in the manifest. The audio settings are unchanged.
- Untrusted input: the two files only. They get the same bounds, canonical checks, CRC and engine replay as today
  (R9 to R11). A Cricket body is limited by M12 (legs) and by the existing 10,000-dart limit (R12).

## Screens and states

- Home:
  - without a saved game;
  - with a saved X01 game (unchanged except the mode line);
  - with a saved Cricket game (standard, solo, match);
  - the confirmation dialog opened from either card.
- Cricket setup: defaults; prefilled; 4 players; with a name error; 25-Bull off.
- Cricket game:
  - turn in progress;
  - turn waiting for "Next player" (keypad locked);
  - modifier active (25 and Bull disabled);
  - pending win, also when won by a non-thrower (CR25);
  - abandon dialog;
  - no-voice notice;
  - solo (no points row, "n/7");
  - 25-Bull off (no Bull row and no 25/Bull keys);
  - Cut-throat;
  - with 2, 3 and 4 players, in portrait and landscape.
- Between legs: Cricket legs only, and in sets.
- End: single game, legs only, sets, solo, and ex aequo ranks.

### Strings (new, `values/` and `values-fr/`)

| Strings | English | French |
|---|---|---|
| Mode description | "Standard or Cut-throat" | « Standard ou Cut-throat » |
| Setup section labels | "VARIANT" | « VARIANTE » |
| Variant names | "STANDARD", "CUT-THROAT" | identical |
| 25-Bull setting | "25-Bull" | identical |
| Header without 25-Bull | "WITHOUT 25-BULL" | « SANS 25-BULL » |
| Zone stat | "MPR %1$s" | identical |
| Winner detail | "%1$d PTS" | identical |
| Closed targets (solo) | "%1$d/%2$d" | identical |
| End screen columns | "PTS", "MPR MATCH" | identical |
| Win banner | "VICTORY" | « VICTOIRE » |
| Keypad miss key | the existing miss string | identical |
| Variant hint, Standard | "Your points are yours; highest score wins." | « Vos points sont pour vous ; le plus haut score gagne. » |
| Variant hint, Cut-throat | "Your points go to your opponents; lowest score wins." | « Vos points vont aux adversaires ; le plus bas score gagne. » |
| TalkBack, cell (plurals `cricket_cell_marks`) | one: "%1$s, %2$s: %3$d mark"; other: "%1$s, %2$s: %3$d marks" | one: « %1$s, %2$s : %3$d marque »; other: « %1$s, %2$s : %3$d marques » |
| TalkBack, closed cell | "%1$s, %2$s: closed" | « %1$s, %2$s : fermée » |
| TalkBack, dead row | "%1$s: closed by everyone, no longer scores" | « %1$s : fermée par tous, ne compte plus » |
| TalkBack, current column | "%1$s, throwing" | « %1$s, au lancer » |
| TalkBack, current visit marks (plurals `cricket_current_visit_marks`) | one: "Current turn: %1$d mark"; other: "Current turn: %1$d marks" | one: « Volée en cours : %1$d marque »; other: « Volée en cours : %1$d marques » |
| TalkBack, last visit marks (plurals `cricket_last_visit_marks`) | one: "Last turn: %1$d mark"; other: "Last turn: %1$d marks" | one: « Dernière volée : %1$d marque »; other: « Dernière volée : %1$d marques » |
| TalkBack, points row | "Points: %1$s" | « Points : %1$s » |

The French texts use a non-breaking space before the colon and the semicolon. Mark counts are `<plurals>` (review U2).
In English, `one` is used for 1, and `other` for 0 and 2 and more. In French, the CLDR `one` category covers 0 and 1,
so "0 marque" and "1 marque", and `other` covers 2 and more. Each file defines both quantities `one` and `other`. In
code, they are read with `pluralStringResource`, never with a `%d marks` string.

## Test plan

### `:game-engine` (JUnit, JVM; each test cites its rule)

- `CricketConfigTest` (CR1): defaults; the targets with and without the Bull; the player list rules (shared tests of
  `PlayerListRules`, and the X01 config tests unchanged).
- `CricketMarksTest` (CR2): single, double and triple of 15 to 20; 25 = 1 and Bull = 2; 1 to 14 and miss = 0; 25 and
  Bull = 0 with 25-Bull off.
- `CricketClosingTest` (CR3): closing with 3 singles, a single then a triple (1 closes, 2 surplus), a triple at once.
- `CricketStandardScoringTest` (CR4, CR6):
  - surplus scores for the thrower, the Bull worth 25;
  - nothing once every opponent has closed (the issue's case);
  - a dart that both closes and scores;
  - solo: never any points;
  - 3 players, with P2 closed and P3 still open on 20: P1's surplus scores for P1 (review T1);
  - 4 players, with 2 opponents closed and 1 open: the surplus still scores; then, once the last one closes, nothing.
- `CricketCutThroatScoringTest` (CR5): points go to every opponent who has not closed, none to the thrower, none to
  opponents who have closed. Cases (review T1):
  - 3 players, P2 closed and P3 open: a T20 surplus of 40 goes to P3 only, and P2 is unchanged;
  - 4 players, P2 closed, P3 and P4 open: each of P3 and P4 receives the full surplus, and P2 and P1 are unchanged;
  - 4 players, a Bull surplus (25 per mark) with one opponent open.
- `CricketWinTest` (CR8 to CR10):
  - Standard win with more points and with equal points; no win with fewer points;
  - Cut-throat win with fewer points and with equal points; no win with more points;
  - a player fully closed but not the winner keeps playing, then wins later;
  - a Cut-throat win triggered by another player's dart (CR9);
  - two players meeting the condition at once, with the thrower first, then play order;
  - darts rejected after the win;
  - solo win;
  - 25-Bull off: a win without the Bull.
- `CricketTurnTest` (CR7): 3 darts per turn, rotation, starter.
- `CricketUndoTest` (CR11): undo inside a turn and across turns, of a scoring dart (points restored) and of the
  winning dart (leg reopened); undo on an empty leg.
- `CricketRankingTest` (CR12): 3 and 4 players in Standard and Cut-throat, the board-marks tie-break, ex aequo with
  rank skipping, an empty ranking in progress.
- `CricketStatisticsTest` (CR13, CR14):
  - MPR over completed legs and the current leg, surplus and dead marks included;
  - no MPR before the first dart;
  - visit marks in every state, reset at a new leg, last completed visit while waiting;
  - `CricketPlayerState.closedTargetCount` with and without 25-Bull, against `CricketConfig.targets.size` (6 or 7;
    review A2). The app reads these two values and never counts marks itself or hardcodes 7.
- `CricketDeadTargetTest` (CR15):
  - dead only when every player has closed;
  - the owner's 3-player example: P3's closing dart is not a closed-sector hit, and his next dart on 20 and later
    darts by P1 or P2 on 20 are;
  - a dart that closes for the thrower while an opponent is open is not one;
  - undo of the dart that made the target dead makes it live again;
  - solo;
  - 25-Bull off.
- `CricketMatchTest` (M1 to M12, M19, CR16):
  - leg, set and match outcomes;
  - starter rotation;
  - confirmed legs final for undo;
  - match ranking with the Cricket tie-break (points, then board marks), in both variants;
  - a match where the tie-break of the last leg puts the players in a different order than their sets and legs: the
    sets and legs win (M17/M20; review T8);
  - a full tie (same sets, legs, points and marks) giving two players the same rank, with the next rank skipped;
  - the confirm guard is the shared `LegOutcome.isConfirmable` (true for LEG and SET), also used by `X01Match`. The
    next starter is `MatchScore.starterOfNextLeg`. Neither is copied into `CricketMatch` (review A1), and
    `LegOutcomeTest` covers `isConfirmable` for the 3 values;
  - the max-legs bound;
  - solo match.
- `MatchRankingTest`: the comparator overload (M20); the existing X01 ranking tests stay unchanged.
- `CricketCuesTest` (AN29 to AN32):
  - hit, closed-sector hit and miss;
  - the closing dart is a hit, and the next dart on the same target in the same visit is a closed-sector hit (CR15);
  - undo;
  - undo of the dart that made the target dead, then the same dart again: a normal hit, not a closed-sector hit
    (review T7);
  - turn start (game start, after "Next player", next leg);
  - leg, set and match win, including a win by a non-thrower;
  - no bust, 180 or score cue.

### `:app` pure JVM

- Persistence:
  - `CricketSaveCodecTest` (R21, R23, R24): round trip of single game, match, between legs, waiting, pending win,
    solo, 25-Bull off;
  - a v4 X01 round trip of every existing X01 save case (single game, match, between legs, waiting, pending win);
  - every invalid case of `GameSaveDecodeInvalidTest` applied to Cricket bodies;
  - `GameSaveDecodeInvalidTest`, new cases (R23, reviews T2 and S1), each one rejected:
    - v4 without a mode line;
    - unknown mode value;
    - mode `cricket` with an X01 body, and mode `x01` with a Cricket body;
    - a mode line inside a v1, v2 or v3 file;
    - a v4 file whose CRC is wrong once the mode line is there (CRC computed without it, or mode line edited after);
  - `CricketSaveDecodeBoundsTest` (R24, reviews S2 and S3), one test per bound, each giving "rejected", never an
    exception:
    - winner index equal to the player count, and negative;
    - leg count above M12;
    - darts above the 10,000-dart limit;
    - marks hit above 3 x darts;
    - points above 99,999 (6 digits) and a signed value;
    - encode: a leg with more than 99,999 points is not written (the previous file is kept, R12);
    - a confirmed leg after the match is decided;
    - 0 and 5 players;
    - a duplicate name and a blank name (the `InvalidCricketConfigException` is caught);
    - unknown variant token and unknown 25-Bull token;
  - `SaveFuzzTest` extended to Cricket bodies and to the mode line. It keeps its fixed `FUZZ_SEED`, so a failure can
    be replayed; nothing in the fuzz is random per run.
- Old files:
  - `SaveV3CompatibilityTest` (R25): frozen version 3 game and last setup fixtures, committed under
    `src/test/resources/persistence/v3/`, read as X01;
  - the v1 and v2 fixtures stay green unchanged.
- `LastSetupCodecTest` (R22, R25): per-mode settings, shared roster, a v1 to v3 file gives the Cricket defaults.
  Rejected (ignored, R10; review T2), with the same cases as the game file:
  - a missing or unknown mode section;
  - a mode line in v1 to v3;
  - a wrong CRC;
  - an unknown variant or 25-Bull token (S3).
- `PersistenceEffectsTest` and `DeletionOrderTest` (R12, R20):
  - a Cricket start replaces a saved X01 game after the confirmation, and the reverse;
  - Cricket writes on dart, undo, "Next player", "Finish leg" and "Next leg".
- `CricketGameScreenStateTest`:
  - keypad lock, modifier toggling, 25 and Bull disabled with a modifier, highlighted winner on a non-thrower win;
  - zone mode, action bar labels for LEG, SET and MATCH;
  - board rows (dead rows, marks), visit boxes.
- `CricketBoardTextTest` (CR23, review T3): `RowStyle`, `BoardTextColor` and `isStruck` for live and dead rows, solo,
  25-Bull off.
- `CricketLayoutPolicyTest` (CR22, review U1): key, action bar and row heights at their nominal values and at their
  floors, column widths at 360 dp with 1 to 4 players, and the "board scrolls" decision at 360 x 640, at 640 x 360
  and at font scale 2.0.
- `MprFormatTest` (CR13, review T4): `formatMpr(Double?, Locale)`, next to `AverageFormat`:
  - one decimal, with "2,4" in French and "2.4" in English;
  - a dash for an absent value;
  - rounding half up, so 2.45 gives "2,5" and 2.449 gives "2,4";
  - 0.0 gives "0,0";
  - 9.0, the highest value, gives "9,0".
- `CricketReducerTest`:
  - `NewGame(CRICKET)` with and without a saved game, the confirmation keeping the mode;
  - start, play, finish leg, next leg, end, rematch (CR28), abandon, back.
- `SetupStateCricketTest`: variant, bull, shared roster, per-mode format, validation errors.
- `GameCuesTest` (Cricket cases) and `CueScriptTest`: closed sector sound instead of the hit, dart call kept, no
  victory sound on a leg.
- `ResumeSummaryCricketTest` (CR20), and `BetweenLegsContentTest` / `EndContentTest` for both mappers (the X01 output
  is unchanged, CR26 and CR27).
- `FrenchStringsTest`: the new keys exist in both languages; the three mark `<plurals>` define `one` and `other` in
  both files, and the French quantity for 0 and for 1 resolves to the singular "marque" (review U2).

### Robolectric (Compose UI)

- Cricket game screen:
  - the keypad sends the right events;
  - the keys and the action bar follow the state;
  - TalkBack descriptions of the board cells, the current column and the dead rows (one merged node, CR23), with
    "1 marque" and "2 marques";
  - the TalkBack order of CR23 (current header first, then the other headers, then rows, then points; review U7);
  - per row, the `CricketTags.rowStrike(target)` node exists exactly on the dead rows, and the `CricketSemantics.RowDead`
    property matches (review T3);
  - the contrast of `onBackgroundMuted` on the board background is at least 4.5:1 (`PaletteContrastTest` case);
  - touch targets of at least 48 dp;
  - fit tests at 360 x 640 and 640 x 360, with 4 players, at font scales 1.0 and 2.0. They assert the CR22 contract
    (reviews U1 and T5):
    - every key and the action bar are fully inside the root bounds, at least 48 dp tall, and not inside a scroll
      container;
    - the 4 column headers, with their names (ellipsis allowed) and visit boxes, the target label column and the
      points footer exist and are inside the root bounds once the board is scrolled to them;
    - the current player's header is on screen without scrolling;
    - at 360 x 640, font scale 1.0, 4 players, with 25-Bull on (the zero-margin case of CR22): the board needs no
      scrolling. Its scroll range is 0, and the header, all 7 rows and the points footer are inside the root bounds
      without any scroll action;
    - the compact zone shows its points and darts;
  - the same fit assertions for the Cricket between-legs and end screens with 4 players, at 360 x 640 and at font
    scale 2.0 (review U3): every row of the ranking, the PTS and MPR MATCH columns and the action buttons can be
    reached, and the buttons are at least 48 dp;
  - the `Ⓧ` glyph check: if Saira has no `Ⓧ`, `CricketBoard` draws it (circle + X), and a test proves the three marks
    render with the same width.
- Resume after a process kill (`ui/resume`): the Cricket game, between legs and pending win come back as they were.

### Roborazzi captures (record only, Pixel 7, animations disabled; French unless stated)

- Required by the issue:
  - home without a saved game, with a saved X01 game, with a saved Cricket game;
  - the Cricket setup;
  - the board with 2 and 4 players, in portrait and landscape;
  - the keypad with and without 25-Bull;
  - the win state (CR25: banner and action bar).
- Also:
  - a win by a non-thrower;
  - a board with 2 dead rows and the others live, with 2 and 4 players, plus a grayscale copy of the 4-player capture.
    That copy is a review aid only: the non-color cue is proven by the strike-node assertion (CR23, review T3);
  - solo;
  - Cut-throat;
  - a modifier active;
  - a turn waiting;
  - between legs and end screens in Cricket (legs only, sets, single game);
  - setup with 25-Bull off;
  - font scale 2.0 for game 4 players and setup;
  - small screens 360 x 640 and 640 x 360;
  - between legs and end with 4 players at 360 x 640 and at font scale 2.0 (review U3);
  - English for the game portrait and the setup.

The existing X01 captures must stay unchanged (the refactors in T8 and T9 are checked against them). Proof, in T8 and
T9 (review T6):
1. Record a baseline from `e7372f8` in a separate worktree:
   `git worktree add "$(git rev-parse --git-dir)/squad/baseline" e7372f8`, then `./gradlew :app:testDebugUnitTest` in
   it.
2. Run `./gradlew :app:testDebugUnitTest` on the task head.
3. Compare every X01 PNG of `app/build/outputs/roborazzi` byte for byte with `cmp`.

The worktree is removed afterwards with `git worktree remove`. If the same commit does not give identical PNGs twice
(non-deterministic rendering), the task reports it to main with the file names. It never skips the comparison or
weakens it. A difference that is intended is listed with the reason in the implementation notes.

### Local checks (every task)

- `./gradlew :game-engine:test`;
- `./gradlew :app:testDebugUnitTest`, which also records the Roborazzi captures (record mode only);
- `./gradlew build`;
- then `kosmo-detekt` (exit 0).

## How Kosmo verifies on the phone (Pixel)

The PR description turns this into numbered steps, starting from a fresh install. Each line cites its rule:

1. Home: the Cricket card "15–20 · Standard ou Cut-throat" sits under X01 (CR20).
2. Cricket setup: the defaults are Standard and 25-Bull on, and the player names are the ones of the last X01 game
   (CR21, R22).
3. Standard, 2 players:
   - three singles on 20 close it (`/`, `X`, `Ⓧ`), and a triple 20 afterwards scores 60 (CR3, CR4);
   - when the opponent puts his third mark on 20, his dart plays the normal hit sound, and the 20 row becomes struck
     through and dimmer, while the other rows stay bright (CR15, CR23);
   - from then on, any dart in 20, by either player and also later in the same visit, scores nothing and plays the new
     "closed sector" sound (CR4, CR15, AN30);
   - an undo of the closing dart brings the row back to normal (CR15);
   - with 3 players (the owner's example: P1 three single 20s, P2 T20 then D20, P3 his third mark), the sound plays
     only once all three have closed 20. A dart that closes 20 for one player while another still has it open plays the
     normal sound;
   - an undo restores the marks and the points (CR11).
4. The MPR on the zone, and the visit box with the marks of the visit, both in the zone and in the board header
   (CR13, CR14).
5. Win:
   - closing everything with at least as many points shows VICTOIRE and "VALIDER LA PARTIE" (CR8, CR25);
   - closing everything with fewer points: the game continues (CR10);
   - "ANNULER" on the winning dart reopens the game.
6. Cut-throat, 3 players:
   - surplus points go to the players who have not closed (CR5);
   - the lowest score wins (CR8);
   - a win can happen on another player's dart (CR9).
7. 25-Bull off: no Bull row and no 25/Bull keys; the game is won without the Bull (CR1, CR24).
8. Solo: no points row, "n/7", and a win once everything is closed (CR6).
9. A match "first to 2 legs":
   - between legs, the screen shows "PTS" and MPR MATCH (CR26);
   - the starter rotates (M6);
   - the end screen shows the ranking, PTS, MPR and darts (CR27);
   - REVANCHE keeps the settings (CR28).
10. With 3 or 4 players: the end ranking by points, then marks, and ex aequo ranks (CR12, CR16).
11. Persistence:
    - kill the app during a Cricket match and relaunch: the home card shows "CRICKET · …", and resuming restores
      everything (R20, R21);
    - starting X01 asks for confirmation and replaces it (R20);
    - the X01 and Cricket settings are kept separately (R22).
12. An X01 game saved by the previous version (install the previous APK, start a game, update without uninstalling)
    resumes normally (R25). The PR states that this step needs the previous APK.
13. Landscape with 4 players: the board is readable and nothing is cut (CR22).
14. The "Raté" key: throw darts in 1 to 14 with "Raté". Check that it is clear that these darts count as misses in
    Cricket, and that no key is missing (CR24, review U4).
15. Turn the phone during a match with a dead row and then with a pending win: the board, the struck row, the banner
    and the action bar stay the same (CR22, CR23, CR25; review U6).
16. Set the system font size to the maximum and play with 4 players: the keys and "ANNULER" stay fully visible; the
    board scrolls if needed, with the names on top; names may end with "…" (CR22).
17. TalkBack on: swipe through the board. You hear the current player first (« au lancer »), then the other players,
    then each row, and a dead row read as one line « 20 : fermée par tous, ne compte plus », then the points (CR23).

## Tasks

### T1 - Cricket rules document, configuration and marks

- Goal: write `docs/rules/cricket.md` (CR1 to CR28, as in this plan), `PlayerListRules`, `CricketVariant`,
  `CricketTarget`, `CricketConfig` and `CricketMarks`, with their tests.
- Files:
  - `docs/rules/cricket.md`;
  - `engine/player/PlayerListRules.kt`, `engine/x01/X01Config.kt` (delegation);
  - `engine/cricket/CricketVariant.kt`, `CricketTarget.kt`, `CricketConfig.kt`, `CricketMarks.kt`;
  - tests `PlayerListRulesTest`, `CricketConfigTest`, `CricketMarksTest`.
- Depends on: none.
- Done when: `./gradlew :game-engine:test` and `./gradlew build` are green, the X01 tests pass unchanged, and
  `kosmo-detekt` exits 0.
- Model: opus
- Why this model: game rules in `:game-engine` and a refactor of shared validation that X01 relies on.

### T2 - Cricket leg: replay, points, win, ranking, undo

- Goal: `CricketGame`, `CricketState` and `CricketReplay` implementing CR3 to CR12, CR14 (per leg) and CR15.
- Files:
  - `engine/cricket/CricketGame.kt`, `CricketState.kt`, `CricketReplay.kt`, `CricketVisits.kt`;
  - tests `CricketClosingTest`, `CricketStandardScoringTest`, `CricketCutThroatScoringTest`, `CricketWinTest`,
    `CricketTurnTest`, `CricketUndoTest`, `CricketRankingTest`, `CricketDeadTargetTest`, plus a fixtures file.
- Depends on: T1.
- Done when: `./gradlew :game-engine:test` and `./gradlew build` are green, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: subtle game rules (simultaneous win, non-thrower win, partial closing).

### T3 - Cricket match, statistics and match ranking

- Goal: `CricketMatchConfig`, `CricketLegSummary` and `CricketMatch` (M1 to M12, M19), plus MPR (CR13), the CR16
  tie-break, the `MatchRanking` comparator overload (M20) and the `match.md` amendments. Also:
  - `LegOutcome.isConfirmable`, used by `X01Match.confirmLeg` and `CricketMatch.confirmLeg`, and
    `MatchScore.starterOfNextLeg` reused as is (review A1);
  - `CricketPlayerState.closedTargetCount`, compared with `CricketConfig.targets.size` (review A2).
- Files:
  - `engine/cricket/CricketMatchConfig.kt`, `CricketLegSummary.kt`, `CricketMatch.kt`, `CricketState.kt`
    (`closedTargetCount`);
  - `engine/match/MatchRanking.kt`, `LegOutcome.kt`, `engine/x01/X01Match.kt` (guard only), `docs/rules/match.md`;
  - tests `CricketMatchTest` (with the last-leg tie-break and full-tie cases of review T8), `CricketStatisticsTest`,
    `MatchRankingTest` (new cases) and `LegOutcomeTest`, plus fixtures.
- Depends on: T2.
- Done when: `./gradlew :game-engine:test` and `./gradlew build` are green, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: a change to the shared match ranking that X01 uses, and match-wide rules.

### T4 - Cricket cues and the closed-sector sound

- Goal: `CricketCue` and `CricketCues` (AN29 to AN32). Recover the sound with
  `git checkout origin/feat/sfx_cricket -- app/src/main/res/raw/sfx_closed_cricket_sector.ogg`, committed unchanged.
  Add `SoundEffect.CLOSED_SECTOR` and its `SoundPoolPlayer` mapping, `CueScript.of(CricketCue)`, the
  `announcements.md` rules and the credits. Checks on the recovered file (review S4):
  - it is 17,734 bytes (`wc -c`);
  - it starts with the Ogg magic "OggS" (`head -c 4`), and its first page has the Vorbis identification header
    "\x01vorbis" (`od -c`), so it is Ogg Vorbis;
  - nothing else is taken from `feat/sfx_cricket`: `git diff --stat` of the commit shows only this file plus the task's
    own files;
  - the `CREDITS.md` line says "ElevenLabs, free plan, generated by the owner", which is the owner's statement P3,
    and nothing more.

  The implementation note records these values, and the PR copies them.
- Files:
  - `engine/cricket/CricketCue.kt`, `CricketCues.kt`, `CricketCuesTest.kt`;
  - `res/raw/sfx_closed_cricket_sector.ogg`;
  - `audio/SoundEffect.kt`, `audio/SoundPoolPlayer.kt`, `audio/CueScript.kt`, `CueScriptTest.kt` (Cricket cases);
  - `docs/rules/announcements.md`, `docs/sounds/CREDITS.md`.
- Depends on: T3.
- Done when: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, and
  `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: it follows the `X01Cues` and `CueScript` patterns closely, from a fully specified rule list.

### T5 - Save format v4: mode line and Cricket saved game

- Goal: `SaveFormat` version 4 with the mode line, `StoredGame`, `SavedCricketGame`, the Cricket body in
  `GameSaveCodec`, the `SaveLoader` and `Persistence` types, ADR 0008 and R20, R21, R23 to R25. Also:
  - the rejection rules of R23 and the decoder bounds of R24 (reviews S1 to S3), with `InvalidCricketConfigException`
    caught;
  - ADR 0008 states:
    - the follow-up issue to move `Dart` and `Multiplier` to a shared package, "to be created by the owner";
    - that `Dart` and `Multiplier` gain no Cricket-specific member (review A3);
    - that confirmed-leg summaries are the accepted record, as in ADR 0001 and 0005, with the list of the bounds the
      decoder checks (review A5).
- Files:
  - `persistence/SaveFormat.kt`, `SaveBody.kt`, `GameSaveCodec.kt`, `StoredGame.kt` (new), `SavedCricketGame.kt`
    (new), `CricketSaveBody.kt` (new), `SaveLoader.kt`, `Persistence.kt`;
  - `docs/adr/0008-save-format-v4-modes.md`, `docs/rules/persistence.md`;
  - tests `CricketSaveCodecTest`, `SaveV3CompatibilityTest` with the frozen v3 fixtures (generated from the current
    `main` code before any change), `CricketSaveDecodeBoundsTest`, the v4 X01 round trips, plus the mode-line and
    Cricket cases of `GameSaveDecodeInvalidTest` and `SaveFuzzTest` (fixed `FUZZ_SEED`).
- Depends on: T3.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, the v1 and v2 fixtures pass
  unchanged, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: a stored format and its migration.

### T6 - Last setup per mode with a shared roster

- Goal: `LastSetups`, `LastSetupCodec` version 4 (R22, R25), `AppState.lastSetup: LastSetups` and the X01 use sites
  (`SetupTransitions`, `InitialState`, `PersistenceEffect`), with the X01 behavior unchanged. `LastSetupCodecTest`
  covers the rejected cases of the test plan (missing or unknown mode, mode line in v1 to v3, wrong CRC, unknown
  variant or 25-Bull token; reviews T2 and S3).
- Files: `persistence/LastSetups.kt` (new), `LastSetupCodec.kt`, `Persistence.kt`, `ui/app/AppState.kt`,
  `SetupTransitions.kt`, `InitialState.kt`, `PersistenceEffect.kt`, tests `LastSetupCodecTest`,
  `PersistenceEffectsTest`, `InitialStateTest`.
- Depends on: T5.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: a stored format change that crosses persistence and app state.

### T7 - Cricket game state holder and reducer

- Goal: `CricketGameScreenState`, `Screen.CricketGame`, `Screen.CricketBetweenLegs` and `Screen.CricketEnd`,
  `GameMode`, `NewGame(mode)`, `CricketReducer` (play, finish, next leg, end, rematch, abandon), the Cricket branch of
  `gameCues`, Cricket saving in `PersistenceEffect` (R12, R15, R20), `SavedCricketGameScreen` and the board mapping
  (`CricketBoardText`, pure, with `CricketBoardRow`, `CricketRowText`, `RowStyle` and `BoardTextColor` of CR23;
  review T3). The MPR text comes from `formatMpr` (review T4), and the solo "n/7" from `closedTargetCount` and
  `targets.size` (review A2).
- Files: `ui/app/AppState.kt`, `AppReducer.kt`, `CricketReducer.kt` (new), `GameMode.kt` (new), `GameCues.kt`,
  `PersistenceEffect.kt`, `ui/cricket/CricketGameScreenState.kt`, `SavedCricketGameScreen.kt`, `CricketBoardText.kt`,
  `ui/format/MprFormat.kt` (new), tests `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketBoardTextTest`,
  `MprFormatTest`, `GameCuesTest` (Cricket cases).
- Depends on: T4, T6.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: subtle state (waiting flag, a win by a non-thrower, cues and persistence hooks); the higher model,
  since sonnet is borderline here.

### T8 - Cricket game screen: board, keypad, zone

- Goal: `CricketGameScreen`, `CricketBoard`, `CricketKeypad`, `KeypadKeys.kt` (moved keys), `ZoneStat` and the VICTOIRE
  banner in `CurrentPlayerZone`, and CR22 to CR25:
  - the layout contract of CR22 through `CricketLayoutPolicy` (review U1);
  - the dead row style, its strike node and `RowDead` property, and its merged TalkBack node (CR23, review T3);
  - the TalkBack order (review U7);
  - the strings, with the mark `<plurals>` (review U2);
  - the design system doc, with the new components and the layout contract;
  - the captures listed for the game screen, the keypad and the win state.
- Files:
  - `ui/cricket/CricketGameScreen.kt`, `CricketBoard.kt`, `CricketKeypad.kt`, `CricketTags.kt`,
    `CricketSemantics.kt`, `CricketLayoutPolicy.kt`;
  - `ui/game/KeypadKeys.kt`, `Keypad.kt`, `ui/components/CurrentPlayerZone.kt`,
    `ui/KosmoDartsApp.kt` (game screen route);
  - `res/values/strings.xml`, `res/values-fr/strings.xml`, `docs/design/design-system.md`;
  - tests:
    - `CricketLayoutPolicyTest`;
    - `CricketScreenTest` (Robolectric, with the strike-node, TalkBack order and fit-contract cases);
    - `CricketScreenshotTest` (with the dead rows and their grayscale review copy);
    - `FrenchStringsTest` (plurals);
    - a new `PaletteContrastTest` case for a dead row.
- Depends on: T7.
- Done when:
  - `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green;
  - the X01 captures are byte-identical to the `e7372f8` baseline (`cmp`, see the Roborazzi section);
  - `kosmo-detekt` exits 0.
- This is the largest task. If it goes beyond about 60 tool calls, the developer stops with a green build and
  reports what is left, rather than starting T9.
- Model: sonnet
- Why this model: screens built from existing components and patterns, against specified rules.

### T9 - Between-legs and end screens for Cricket

- Goal: switch `BetweenLegsScreen` and `EndScreen` to UI models with an X01 mapper (output unchanged) and Cricket
  mappers (CR26, CR27), the Cricket between-legs and end routes in `KosmoDartsApp`, and the captures of these screens.
- Files: `ui/between/BetweenLegsScreen.kt`, `BetweenLegsState.kt`, `ui/end/EndScreen.kt`, `EndRow.kt`,
  `ui/cricket/CricketBetweenLegsContent.kt`, `CricketEndContent.kt`, `ui/KosmoDartsApp.kt`, the strings, tests
  `BetweenLegsContentTest`, `EndContentTest` (with a full Cricket tie giving an ex aequo row, review T8),
  `CricketEndScreenTest` (Robolectric fit at 360 x 640 and font scale 2.0 with 4 players, review U3) and
  `CricketScreenshotTest` (cases added).
- Depends on: T8.
- Done when:
  - `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green;
  - the X01 between and end tests pass unchanged, and their captures are byte-identical to the `e7372f8` baseline;
  - `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a refactor that follows existing patterns, with the X01 output protected by the existing tests.

### T10 - Cricket setup, home card and resume summary

- Goal: `SetupState` mode and Cricket settings, the Cricket section of `SetupScreen`, the Cricket `ModeCard`,
  `NewGame(mode)` wired from both cards, the mode-aware `ResumeSummary` (CR20, CR21), the variant hint line (CR21,
  review U5), and the home and setup captures. Starting a Cricket game from the app then works end to end. Until this
  task, Cricket cannot be reached from the app.
- Files: `ui/setup/SetupState.kt`, `SetupScreen.kt`, `CricketSettingsSection.kt` (new), `ui/home/HomeScreen.kt`,
  `ResumeSummary.kt`, `ui/app/SetupTransitions.kt`, the strings, tests
  `SetupStateCricketTest`, `ResumeSummaryCricketTest`, `HomeScreenTest`, `CricketScreenshotTest` and
  `HomeScreenScreenshotTest` (cases added), and `ui/resume` Cricket resume test.
- Depends on: T9.
- Done when: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, and
  `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: setup and home screens that follow existing patterns.

## Decisions

Kosmo's answers to the intake questions ("R pour Tout.", issue #40 comment), each the recommended option:

| Id | Decision | Rules |
|---|---|---|
| Q1 | A win is declared on the dart that makes it true, whoever threw it. If several players meet the condition, the thrower wins, else the first in play order after the thrower. | CR9, CR25 |
| Q2 | "Total marks" in the ranking = board marks, at most 3 per target. | CR12 |
| Q3 | Match tie-break: points of the last leg, then board marks of the last leg; full tie = ex aequo. | CR16, M20 |
| Q4 | MPR = marks hit / darts x 3 over the match, every mark of the targets in play counted; 25 and Bull are 0 with 25-Bull off; one decimal, a dash without darts. | CR13 |
| Q5 | Previous visit = marks of the previous visit, reset at each leg; **replaced by the owner's precision below**. | CR14, CR23 |
| Q6 | No dialog: the X01 banner pattern with a Cricket label (VICTOIRE) and the M15 action bar labels. | CR25 |
| Q7 | Between legs: "245 PTS" winner detail (none solo), MPR MATCH. End: PTS column (none solo), MPR MATCH, darts kept. | CR26, CR27 |
| Q8 | Home: "CRICKET · VARIANT" (+ "SANS 25-BULL"), mini tiles with points, "n/7" solo. | CR20 |
| Q9 | Roster (names, count, order, colors) shared; format and options per mode. | R22 |
| Q10 | Sounds: hit, miss, undo, victory on match win. Voice: turn start, dart calls, leg/set/victory. No 180, bust or score. | AN29 to AN32 |
| Q11 | `25` = 1 mark and `Bull` = 2, without Double/Triple, as on the X01 keypad. | CR2, CR24 |

Owner precisions (same comment):
- P1. "Même si aucune maquette n'a été fournie, on respecte bien la nouvelle charte graphique." Every Cricket screen
  uses the design system's tokens and components, and new components are added to `design-system.md` (T8).
- P2. "Préc." is replaced by the framed visit box of issue #49 (`VisitBox`, design system: filled for the visit in
  progress, outlined for the other players' last visit). In Cricket the box shows the marks of the visit: filled in the
  current player's zone, and outlined in each player's board column header (CR14, CR23).
- P3. When a dart hits a fully closed sector, play `sfx_closed_cricket_sector.ogg`, generated with ElevenLabs on the
  free plan. The file is recovered from `origin/feat/sfx_cricket` (T4), credited in `docs/sounds/CREDITS.md`, and
  played instead of the hit sound (AN30).

Owner answers to the plan's open questions (issue #40 comment, after the plan):
- O1 A: "totalement fermée" means that every player has closed that sector. Owner example with 3 players: P1 put three
  single 20s and P2 put T20 then D20. When P3 puts his third mark on 20, 20 is totally closed. From then on, if P1, P2
  or P3 puts a dart in 20, the sound plays. Reading recorded in CR15, to be checked in the design review:
  - the dart that completes the closure is a normal hit, since the sector was not totally closed before it;
  - the sound plays only for darts thrown after it, the rest of the same visit included;
  - a dart that closes a sector for the thrower while an opponent still has it open is a normal hit;
  - in a solo game, "every player" is the player.
- O1 complement: a totally closed row is struck through and in a less visible color, so that only the rows still in
  play read clearly. Applied in CR23: a 2 dp line plus `onBackgroundMuted`, an existing token that keeps 4.5:1. The
  line carries the information without the color, and TalkBack reads one merged description.
- O2 A: `25` and `Bull` are greyed (disabled) while Double or Triple is active, as on the X01 keypad (CR24, D4).

Design decisions taken in this plan (open to the design review):
- D1. Separate Cricket bindings (`CricketMatch`, `CricketGameScreenState`, `CricketReducer`) on top of the shared
  `engine/match`, rather than a generic mode abstraction (alternative A1).
- D2. `Dart` stays in `engine/x01` and is imported by `engine/cricket` (alternative A3, ADR 0008).
- D3. `StoredGame` above the unchanged `SavedGame` (alternative A2); format version 4 with a mode line; one
  last-setup file (alternative A4).
- D4. The issue's "Simple / Double / Triple" follows the X01 keypad: single by default, one-shot Double and Triple
  keys, and no "Simple" key. With a modifier active, 25 and Bull are disabled (greyed), as on the X01 keypad (owner
  O2 A).
- D5. No `PlayerTile`s on the Cricket game screen: the board shows every player (CR22). To be confirmed by the UX
  review.
- D6. In the zone, a solo game shows the closed targets "n/7" in place of the points, consistent with Q8 (CR22). To be
  confirmed by the UX review.

Design review, round 1 (reviewed head `8a6d37e`; security and architecture APPROVE, test and UX CHANGES_REQUESTED).
Every finding was applied, none was rejected:

| Finding | Blocking | How it is handled |
|---|---|---|
| T1 | yes | 3- and 4-player cases with one opponent closed and another open, in `CricketStandardScoringTest` and `CricketCutThroatScoringTest` (test plan). |
| T2 | yes | v4 X01 round trips; missing or unknown mode; a body that does not match the mode; a mode line in v1 to v3; a wrong CRC; the same cases for the last setup; `SaveFuzzTest` keeps its fixed `FUZZ_SEED` (R23, test plan, T5, T6). |
| T3 | yes | A pure row model in `CricketBoardText` (`RowStyle`, `BoardTextColor`, `isStruck`) tested on the JVM. Robolectric asserts a strike node per dead row and the `RowDead` property. The grayscale capture becomes a review aid only (CR23, T7, T8). |
| U1 | yes | The layout contract, with numbers, in CR22: pinned keypad and action bar, compact zone, row and header sizes, column widths, long names, the header `VisitBox`, board-only scrolling, landscape. Computed by `CricketLayoutPolicy` (JVM tests), asserted by the fit tests, copied into `design-system.md` (T8). |
| U2 | yes | `<plurals>` for cell marks and current and last visit marks, in both languages; the French `one` category covers 0 and 1; `FrenchStringsTest` checks it (strings table, T8). |
| T4 | no | `formatMpr` next to `AverageFormat` (same `String.format` rounding, half up), with `MprFormatTest` in fr and en, including 2.45 and 2.449 (T7). |
| T5 | no | The fit tests assert bounds inside the root, the presence of names, 4 columns, footer and action bar, and that the keys and action bar are fully visible and outside any scroll container. |
| T6 | no | Local checks name `./gradlew :app:testDebugUnitTest` as the Roborazzi recording step. X01 captures are compared byte for byte with `cmp` against a baseline recorded from `e7372f8` in a git worktree (T8, T9). Non-deterministic output is reported, never skipped. |
| T7 | no | `CricketCuesTest`: undo the closing dart, then the same dart again gives a normal hit. |
| T8 | no | `CricketMatchTest`: a last-leg tie-break that differs from the sets and legs order. Full tie: `CricketMatchTest` and an ex aequo row in `EndContentTest` (T3, T9). |
| A1 | no | `LegOutcome.isConfirmable`, shared by `X01Match` and `CricketMatch`, and `MatchScore.starterOfNextLeg` reused (T3). |
| A2 | no | `CricketPlayerState.closedTargetCount` and `CricketConfig.targets.size`, tested in `CricketStatisticsTest`. The app never counts marks or hardcodes 7 (T3, T7). |
| A3 | no | ADR 0008 records the follow-up issue to move `Dart` ("to be created by the owner"), and that `Dart` and `Multiplier` gain no Cricket member (T5). |
| A4 | no | A suggested PR slicing is noted below. Main asks Kosmo, and the plan does not decide it. |
| A5 | no | ADR 0008: confirmed-leg summaries are the accepted record (as in ADR 0001 and 0005), with the list of the bounds checked at decode (R24, T5). |
| S1 | no | R23 lists the rejected mode-line cases, tested in `GameSaveDecodeInvalidTest`, `SaveFuzzTest` and `LastSetupCodecTest`. |
| S2 | no | R24 decoder bounds: winner index, leg count (M12), darts (10,000), marks per dart (3), points 0 to 99,999 (the existing 5-digit integer, not written above it, like R12), no leg after the match is decided. One test per bound in `CricketSaveDecodeBoundsTest`. |
| S3 | no | `InvalidCricketConfigException` caught at decode; duplicate or blank names and a bad player count tested; variant and 25-Bull tokens checked against a closed set in both files. |
| S4 | no | T4 checks and records the size (17,734 bytes) and the Ogg Vorbis type (the "OggS" and "\x01vorbis" headers via `head -c` and `od`). Only this file is taken from `feat/sfx_cricket`, and the CREDITS line matches the owner's statement. |
| U3 | no | Fit assertions and captures for the Cricket between-legs and end screens with 4 players, at 360 x 640 and at font scale 2.0 (T9). |
| U4 | no | "Raté" is kept; verify step 14 checks that it is understood for 1 to 14. |
| U5 | no | A one-line variant hint under the variant choice, in both languages (CR21, strings table, T10). |
| U6 | no | Verify steps 15 to 17: rotation with a dead row and a pending win, the largest system font with 4 players, and a TalkBack pass. |
| U7 | no | TalkBack order in CR23: the current player's header first ("au lancer"), then the other headers, then the rows, then the points row. Tested with Robolectric (T8). |

Suggested PR slicing (review A4, for main to put to Kosmo; not a decision):
- PR 1: the engine, T1 to T4;
- PR 2: persistence, T5 and T6;
- PR 3: the UI, T7 to T10.

Cricket stays unreachable from the app until T10 adds the home card, so each PR leaves the app shippable. The other
option is a single PR for the whole mission.

Design review, round 2 (reviewed head `54a3842`): all four reviewers APPROVE. The ux-reviewer's two non-blocking
notes are applied:
- the Robolectric fit test at 360 x 640, font scale 1.0, 4 players, asserts that the board needs no scrolling;
- the "Raté" check (U4) is phone check 14.

## Open questions

- O1 and O2: answered by Kosmo (see Decisions).
- O3 (owner follow-up, not a squad action). Kosmo asked to delete the branch `feat/sfx_cricket` once its content is
  recovered. Deleting a remote branch is left to Kosmo (or to an explicit instruction from him to `main`) after T4 is
  merged. No squad member deletes it.

## Implementation notes

(One line per task: task id, deviations, where the main pieces live.)

- T1: `docs/rules/cricket.md` (CR1 to CR16, CR20 to CR28; CR17 to CR19 reserved); `engine/player/PlayerListRules.kt`
  (`PlayerListError`, `PlayerListViolation`, `normalize`, `violations`, `MAX_PLAYERS`, `MAX_NAME_LENGTH`), to which
  `X01Config` delegates (its constants alias the shared ones, its errors map one to one, X01 tests unchanged);
  `engine/cricket/` `CricketVariant`, `CricketTarget` (`points` derived from board order, `NUMBERS`, `ofNumber`),
  `CricketConfig` (+ `InvalidCricketConfigException`, `targets`), `CricketMarks` (`target`, `count`, `NONE`,
  `of(dart, config)`). No deviation.
- T2: `engine/cricket/` `CricketGame` (+ `CricketLegFinishedException`, its own type so `x01` is untouched;
  `isClosedSectorHit(dart)`, true when the dart has marks on a target already dead in this game, for T4 to call on the
  game before the dart), `CricketState` (`deadTargets` in board order; `CricketPlayerState` with `marks` per target in
  play capped at `CLOSING_MARKS`, `points`, `dartsThrown`, `marksHit` (CR13 raw count, MPR left to T3),
  `currentTurnDarts`, `currentTurnMarks`, `marksOn`, `hasClosed`, `boardMarks`; `CricketRank`; `CricketTurn` with
  `marks` = CR2 marks of its darts), `CricketReplay` (internal), `CricketVisits` (`CricketVisit` NotPlayed /
  InProgress / LastVisit, `of`, `lastCompleted`). Tests use `CricketTestFixtures.kt` (`turns("T20 D20 MISS", ...)`).
  Deviations: an extra `CricketVisitsTest` (CR14 per leg ships with `CricketVisits`; T3 keeps the reset at a new
  leg); `CricketClosingTest` covers both "single then triple" (2 close, 1 surplus) and "two singles then triple"
  (1 closes, 2 surplus), since the test plan's "(1 closes, 2 surplus)" matches the latter.
- T3: `engine/cricket/` `CricketMatchConfig` (mirrors `X01MatchConfig`), `CricketLegSummary` (`of(leg)`; per player
  `marksHit`, `dartsThrown`, `points`, `boardMarks`, checked: marks at most 3 x darts, points >= 0, board marks at most
  3 x targets and at most marks hit, the winner's board full), `CricketMatch` (`CricketMatchPlayerStats(marksPerRound,
  dartsThrown)`, exact MPR or `null`; `ranking` through `MatchRanking.rank(score, Comparator<Int>)` with the CR16
  comparator built from the current leg; `confirmLeg` guarded by `LegOutcome.isConfirmable`, next starter from
  `score.withLegWonBy(winner).starterOfNextLeg`), `CricketPlayerState.closedTargetCount`; `match/LegOutcome.kt`
  (`isConfirmable`), `match/MatchRanking.kt` (comparator overload, the list one delegates), `x01/X01Match.kt` (guard
  only; its starter computation is untouched); `match.md` M2, M10, M11 amended, M20 added, a Cricket line in "Related
  rules". Tests: `CricketMatchTest`, `CricketStatisticsTest`, `LegOutcomeTest`, new `MatchRankingTest` cases, fixtures
  in `CricketMatchFixtures.kt` (`cricketMatch`, `turns`, `playLegWonBy`, `confirmLegsWonBy`). No deviation.
- T4: `engine/cricket/CricketCue.kt` (`CricketCue` DartHit, ClosedSectorHit, DartMiss, DartUndone, TurnStart, Win;
  `CricketCues.afterDart(game|match)`, `turnStart`, `dartUndone`; the closed-sector decision is
  `before.isClosedSectorHit(dart)` on the game before the dart; the match overload only sets the win outcome from
  `pendingOutcome`); `audio/SoundEffect.CLOSED_SECTOR`, `SoundPoolPlayer.resourceOf`, `CueScript.of(CricketCue)`
  (shares the `win` helper with X01); `announcements.md` AN29 to AN32 in a new "Cricket" section and AN24 amended
  (seven sounds); `CREDITS.md` line. Tests: `CricketCuesTest`, Cricket cases in `CueScriptTest`, `AudioFakes` duration.
  Sound file checks: `app/src/main/res/raw/sfx_closed_cricket_sector.ogg`, 17,734 bytes, starts with "OggS", first page
  has "\x01vorbis" (Ogg Vorbis, mono, 48 kHz, about 1 s); only this file taken from `origin/feat/sfx_cricket`
  (remote branch left in place). Deviation: `CricketCues`/`CricketCue` live in one file `CricketCue.kt` like
  `X01Cue.kt`; the plan's separate `CricketCues.kt` is not created.
- T5: `persistence/` `SaveFormat` (`VERSION_4`, `VERSION` = 4, mode line first in the v4 body, rejected in v1 to v3),
  `SaveBody.kt` (`SaveMode` x01/cricket), `StoredGame` (sealed: `SavedGame`, `SavedCricketGame`), `CricketSaveBody.kt`
  (write/read, `CRICKET_VARIANT_TOKENS`, `CRICKET_BULL_TOKENS`, `isWritable` with the 99,999-point cap, the R24
  bounds, `InvalidCricketConfigException` caught), `GameSaveCodec` (mode dispatch, `CricketLegFinishedException`
  caught on replay), `GameLoad.Valid(StoredGame)`, `Persistence.saveGame(StoredGame)`; ADR 0008; `persistence.md`
  R20, R21, R23 to R25. Frozen v3 fixtures in `src/test/resources/persistence/v3/` (from `e7372f8`, own commit).
  Tests: `CricketSaveCodecTest`, `CricketSaveDecodeBoundsTest`, `CricketSaveFixtures`, `SaveV3CompatibilityTest`,
  v4 golden files and R23 cases in `GameSaveCodecTest`, `GameSaveMatchCodecTest`, `GameSaveDecodeInvalidTest`,
  Cricket and mode-line seeds in `SaveFuzzTest`. Deviations: `LastSetupCodec` still writes version 3 (an explicit
  `version = VERSION_3`) until T6 gives it its v4 per-mode layout; `InitialState` sends a valid saved Cricket game to
  a plain Home (kept, not resumable) until T7 and T10 add its screens. Started by `developer-build-T5`, finished by
  `developer-build-T5-2`.
- T6: `persistence/LastSetups.kt` (`SharedRoster` (validated names and slots; named so to avoid `ui/setup/Roster`),
  `X01Settings`, `CricketSettings` (variant, bull, format), `LastSetups` with `x01Setup` and `cricketSetup` (roster +
  settings or the mode defaults), `withX01`/`withCricket`, `afterX01`/`afterCricket` for T10); `LastSetupCodec`
  writes version 4 (sections `mode=x01` then `mode=cricket`, at least one, then `players`/`player`/`colors`; layout
  in ADR 0008 "Last setup file"), reads v1 to v3 as X01 with `cricket = null`; `BodyReader.isNext` (optional
  sections); `SaveBody`/`CricketSaveBody` helpers made `internal` for reuse; `LoadResult.lastSetup`,
  `Persistence.saveLastSetup`, `PersistenceEffect.SaveLastSetup(setups)`; R22 in `persistence.md`. Tests: new cases
  in `LastSetupCodecTest` (v4 frozen lines, Cricket round trips, R23 mode-line rejections, CRC, R24 tokens and
  names), `PersistenceEffectsTest` (X01 Start keeps the Cricket settings), `InitialStateTest` (no X01 section gives
  the X01 defaults with the roster); `toLastSetups()` test helper in `SaveFixtures.kt`. Deviations:
  `AppState.lastSetup` is `LastSetups?` (`null` before any start, as before); `AppReducer` and `SaveLoader` changed
  too (`newMatchState(setup, previous)`, `prefilled(lastSetup?.x01Setup)`, loader type); test call sites of 10 other
  test files adapted mechanically. The roster names are checked through `IllegalArgumentException`, which covers
  both config exceptions.
- T7: `ui/cricket/CricketGameScreenState.kt` (state holder; `CricketZoneMode`, `CricketZoneScore` Points or
  ClosedTargets(n, `targets.size`), `CricketBoardRow`; winner highlight with empty turn darts on a non-thrower win;
  VT6 visits; `colorSlots` is a property, to stay under detekt's function count), `ui/cricket/CricketBoardText.kt`
  (`CricketRowText`, `RowStyle`, `BoardTextColor`; labels reuse `game_visit_value` and `dart_bull`),
  `ui/cricket/SavedCricketGameScreen.kt`, `ui/format/MprFormat.kt` (`formatMpr(mpr, locale, none)`: the dash is a
  resource, passed in), `ui/app/GameMode.kt`, `ui/app/CricketReducer.kt` (`onCricketGame`, `onCricketBetweenLegs`,
  `onCricketEnd`, `newCricketMatchState`, `cricketRematchSetup`), new screens and `NewGame(mode)` in `AppState.kt`
  (`Home.newGameDialogMode` replaces the flag; `isGameOrBetweenLegs`, `isNewGameShown`), Cricket branches in
  `PersistenceEffect.kt` (`SaveGame(StoredGame)`) and `InitialState.kt` (restores a Cricket screen after a process
  kill). Deviations: the Cricket cues are a separate `cricketGameCues` with a default `CueSink.onCricketCues`, so the
  X01 cue types and tests are unchanged, and their tests are in `CricketGameCuesTest` rather than `GameCuesTest`;
  `SetupState.mode` added now (`toSetup(mode)` still prefills X01 and `start()` still starts X01 until T10);
  `Home.resumableGame` stays X01-only (Cricket summary and Resume in T10); `KosmoDartsApp` has an empty branch for
  the Cricket screens (T8, T9); wiring outside the file list: `AudioBinder`, `GameAudioSession`, the ViewModel,
  `NoVoiceNotice`, `HomeScreen`/`EndScreen` events, `MatchIndicator.of`/`MatchPosition.current(score)` shared in
  `GameScreenState.kt`; extra `CricketPersistenceEffectsTest`; about 20 test files adapted to `NewGame(GameMode.X01)`
  and long lines wrapped. Process slip: `CueSink.kt` (8 lines) was rewritten with Write instead of Edit.
- T8: `ui/cricket/` `CricketGameScreen` (portrait and landscape from `CricketLayoutPolicy`, abandon and no-voice
  dialogs, keeps the screen on), `CricketBoard` (sticky headers, scrolling rows, drawn marks, strike node and
  `RowDead` on a node whose strike sibling stays outside the cleared cells, header traversal order, points row),
  `CricketKeypad`, `CricketPlayerZone`, `CricketMarksBox`, `CricketTags`, `CricketSemantics`; `ZoneStat`
  (`ComponentModels.kt`), `DartSlots(slotHeight)`, `Chips` and `confirmLabel` made `internal` for reuse;
  `KosmoDartsApp` draws `CricketGameScreen` (between legs and end stay empty for T9). Tests: `CricketScreenTest`
  (Robolectric, state given directly through a `ComposeView`), `CricketScreenshotTest` (20 captures, with the
  grayscale copy of the 4-player dead rows), a `PaletteContrastTest` case; `FrenchStringsTest` plurals were already
  in. `docs/design/design-system.md` got `CricketBoard`, `CricketKeypad` and section 6.5 (layout contract; the file
  is not protected). X01 captures: the 160 PNGs of the `e7372f8` baseline are byte-identical to the head's (`cmp`).
  Deviations: no separate `Keypad.kt` change beyond the earlier `KeypadKeys.kt` move; unused string
  `cricket_board_column_current_visit` removed. Started by `developer-build-T8`, finished by `developer-build-T8-2`.
