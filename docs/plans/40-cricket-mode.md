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
- CR15. Dead target: a target closed by every player (by the only player in a solo game). A dart with at least one mark
  on a target that was dead before the dart is a *closed-sector hit* (owner sound request, AN30).
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
  - "VARIANTE": a `SegmentedChoice`, Standard / Cut-throat;
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

  No `PlayerTile`s: the board carries every player. In landscape, the zone and the board are in the left column, and
  the keypad and the action bar in the right column, following `GameLayoutPolicy`. Final layout by the UX review: it
  must be readable at 4 players in portrait and in landscape, from 360 x 640 up, at font scales 1.0 and 2.0, with
  nothing truncated or cut.
- CR23. `CricketBoard`:
  - rows: 20 to 15, then Bull (no Bull row with 25-Bull off); one column per player, in configuration order;
  - a column header with the player's color strip and name, and his outlined `VisitBox` with the marks of his last
    visit (the visit system of issue #49, replacing "Préc.", owner precision);
  - cells: empty, `/`, `X` or `Ⓧ`;
  - a footer row with the points, absent in a solo game;
  - the current player's column has an `accent` border and a "▶" before his name;
  - a row closed by every player is greyed and its target label struck through.

  No information is carried by color alone. TalkBack reads each cell, for example "20, Anna : 2 marques" or
  "20 : fermée par tous".
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
  - R23: format version 4 adds the mode line, and every write uses it;
  - R24: Cricket saves are validated like X01 saves (R9 to R11);
  - R25: a version 1 to 3 saved game or last setup is read as X01 (R16, R18). Cricket then uses its defaults, with the
    shared roster.
- `announcements.md`:
  - AN29: the Cricket cues of section 4 and their hook points, the same as `gameCues` for X01 (AN7);
  - AN30: `sfx_closed_cricket_sector` plays instead of the hit sound for a closed-sector hit (CR15). Any other dart with
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
- `match/MatchRanking.kt` (comparator overload).

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
    `CricketTags`, `SavedCricketGameScreen`;
  - `CricketBetweenLegsContent` and `CricketEndContent` (mappers).
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
  the VICTOIRE banner).

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
| TalkBack, cell | "%1$s, %2$s: %3$d marks" | « %1$s, %2$s : %3$d marques » |
| TalkBack, dead row | "%1$s: closed by everyone" | « %1$s : fermée par tous » |
| TalkBack, current column | "%1$s, throwing" | « %1$s, au lancer » |
| TalkBack, visit marks | "Current turn: %1$d marks" / "Last turn: %1$d marks" | « Volée en cours : … », « Dernière volée : … » |

The French texts use a non-breaking space before the colon. The final wording is checked in the UX review.

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
  - solo: never any points.
- `CricketCutThroatScoringTest` (CR5): points go to every opponent who has not closed, none to the thrower, none to
  opponents who have closed.
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
  - visit marks in every state, reset at a new leg, last completed visit while waiting.
- `CricketDeadTargetTest` (CR15): dead only when every player has closed; solo.
- `CricketMatchTest` (M1 to M12, M19, CR16):
  - leg, set and match outcomes;
  - starter rotation;
  - confirmed legs final for undo;
  - match ranking with the Cricket tie-break (points, then board marks), in both variants;
  - the max-legs bound;
  - solo match.
- `MatchRankingTest`: the comparator overload (M20); the existing X01 ranking tests stay unchanged.
- `CricketCuesTest` (AN29 to AN32):
  - hit, closed-sector hit and miss;
  - undo;
  - turn start (game start, after "Next player", next leg);
  - leg, set and match win, including a win by a non-thrower;
  - no bust, 180 or score cue.

### `:app` pure JVM

- Persistence:
  - `CricketSaveCodecTest` (R21, R23, R24): round trip of single game, match, between legs, waiting, pending win,
    solo, 25-Bull off;
  - every invalid case of `GameSaveDecodeInvalidTest` applied to Cricket bodies;
  - `SaveFuzzTest` extended to Cricket.
- Old files:
  - `SaveV3CompatibilityTest` (R25): frozen version 3 game and last setup fixtures, committed under
    `src/test/resources/persistence/v3/`, read as X01;
  - the v1 and v2 fixtures stay green unchanged.
- `LastSetupCodecTest` (R22, R25): per-mode settings, shared roster, a v1 to v3 file gives the Cricket defaults.
- `PersistenceEffectsTest` and `DeletionOrderTest` (R12, R20):
  - a Cricket start replaces a saved X01 game after the confirmation, and the reverse;
  - Cricket writes on dart, undo, "Next player", "Finish leg" and "Next leg".
- `CricketGameScreenStateTest`:
  - keypad lock, modifier toggling, 25 and Bull disabled with a modifier, highlighted winner on a non-thrower win;
  - zone mode, action bar labels for LEG, SET and MATCH;
  - board rows (dead rows, marks), visit boxes.
- `CricketReducerTest`:
  - `NewGame(CRICKET)` with and without a saved game, the confirmation keeping the mode;
  - start, play, finish leg, next leg, end, rematch (CR28), abandon, back.
- `SetupStateCricketTest`: variant, bull, shared roster, per-mode format, validation errors.
- `GameCuesTest` (Cricket cases) and `CueScriptTest`: closed sector sound instead of the hit, dart call kept, no
  victory sound on a leg.
- `ResumeSummaryCricketTest` (CR20), and `BetweenLegsContentTest` / `EndContentTest` for both mappers (the X01 output
  is unchanged, CR26 and CR27).
- `FrenchStringsTest`: the new keys exist in both languages.

### Robolectric (Compose UI)

- Cricket game screen:
  - the keypad sends the right events;
  - the keys and the action bar follow the state;
  - TalkBack descriptions of the board cells, the dead rows and the current column;
  - touch targets of at least 48 dp;
  - fit at 360 x 640 and 640 x 360 with 4 players at font scales 1.0 and 2.0 (nothing truncated);
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
  - solo;
  - Cut-throat;
  - a modifier active;
  - a turn waiting;
  - between legs and end screens in Cricket (legs only, sets, single game);
  - setup with 25-Bull off;
  - font scale 2.0 for game 4 players and setup;
  - small screens 360 x 640 and 640 x 360;
  - English for the game portrait and the setup.

The existing X01 captures must stay visually unchanged (the refactors in T8 and T9 are checked against them).

### Local checks (every task)

`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`, then `kosmo-detekt` (exit 0).

## How Kosmo verifies on the phone (Pixel)

The PR description turns this into numbered steps, starting from a fresh install. Each line cites its rule:

1. Home: the Cricket card "15–20 · Standard ou Cut-throat" sits under X01 (CR20).
2. Cricket setup: the defaults are Standard and 25-Bull on, and the player names are the ones of the last X01 game
   (CR21, R22).
3. Standard, 2 players:
   - three singles on 20 close it (`/`, `X`, `Ⓧ`), and a triple 20 afterwards scores 60 (CR3, CR4);
   - once the opponent closes 20, a 20 scores nothing, the row turns grey and struck through, and the new sound plays
     (CR4, CR15, AN30);
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
  tie-break, the `MatchRanking` comparator overload (M20) and the `match.md` amendments.
- Files:
  - `engine/cricket/CricketMatchConfig.kt`, `CricketLegSummary.kt`, `CricketMatch.kt`;
  - `engine/match/MatchRanking.kt`, `docs/rules/match.md`;
  - tests `CricketMatchTest`, `CricketStatisticsTest`, `MatchRankingTest` (new cases), plus fixtures.
- Depends on: T2.
- Done when: `./gradlew :game-engine:test` and `./gradlew build` are green, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: a change to the shared match ranking that X01 uses, and match-wide rules.

### T4 - Cricket cues and the closed-sector sound

- Goal: `CricketCue` and `CricketCues` (AN29 to AN32). Recover the sound with
  `git checkout origin/feat/sfx_cricket -- app/src/main/res/raw/sfx_closed_cricket_sector.ogg`, committed unchanged.
  Add `SoundEffect.CLOSED_SECTOR` and its `SoundPoolPlayer` mapping, `CueScript.of(CricketCue)`, the
  `announcements.md` rules and the credits.
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
  `GameSaveCodec`, the `SaveLoader` and `Persistence` types, ADR 0008 and R20, R21, R23 to R25.
- Files:
  - `persistence/SaveFormat.kt`, `SaveBody.kt`, `GameSaveCodec.kt`, `StoredGame.kt` (new), `SavedCricketGame.kt`
    (new), `CricketSaveBody.kt` (new), `SaveLoader.kt`, `Persistence.kt`;
  - `docs/adr/0008-save-format-v4-modes.md`, `docs/rules/persistence.md`;
  - tests `CricketSaveCodecTest`, `SaveV3CompatibilityTest` with the frozen v3 fixtures (generated from the current
    `main` code before any change), plus the Cricket cases of the invalid and fuzz tests.
- Depends on: T3.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, the v1 and v2 fixtures pass
  unchanged, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: a stored format and its migration.

### T6 - Last setup per mode with a shared roster

- Goal: `LastSetups`, `LastSetupCodec` version 4 (R22, R25), `AppState.lastSetup: LastSetups` and the X01 use sites
  (`SetupTransitions`, `InitialState`, `PersistenceEffect`), with the X01 behavior unchanged.
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
  (`CricketBoardText`, pure).
- Files: `ui/app/AppState.kt`, `AppReducer.kt`, `CricketReducer.kt` (new), `GameMode.kt` (new), `GameCues.kt`,
  `PersistenceEffect.kt`, `ui/cricket/CricketGameScreenState.kt`, `SavedCricketGameScreen.kt`, `CricketBoardText.kt`,
  tests `CricketGameScreenStateTest`, `CricketReducerTest`, `GameCuesTest` (Cricket cases).
- Depends on: T4, T6.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, and `kosmo-detekt` exits 0.
- Model: opus
- Why this model: subtle state (waiting flag, a win by a non-thrower, cues and persistence hooks); the higher model,
  since sonnet is borderline here.

### T8 - Cricket game screen: board, keypad, zone

- Goal: `CricketGameScreen`, `CricketBoard`, `CricketKeypad`, `KeypadKeys.kt` (moved keys), `ZoneStat` and the VICTOIRE
  banner in `CurrentPlayerZone`, CR22 to CR25, the strings, the design system doc, and the captures listed for the game
  screen, the keypad and the win state.
- Files:
  - `ui/cricket/CricketGameScreen.kt`, `CricketBoard.kt`, `CricketKeypad.kt`, `CricketTags.kt`;
  - `ui/game/KeypadKeys.kt`, `Keypad.kt`, `ui/components/CurrentPlayerZone.kt`,
    `ui/KosmoDartsApp.kt` (game screen route);
  - `res/values/strings.xml`, `res/values-fr/strings.xml`, `docs/design/design-system.md`;
  - tests `CricketScreenTest` (Robolectric), `CricketScreenshotTest`.
- Depends on: T7.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, the X01 captures are unchanged, and
  `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: screens built from existing components and patterns, against specified rules.

### T9 - Between-legs and end screens for Cricket

- Goal: switch `BetweenLegsScreen` and `EndScreen` to UI models with an X01 mapper (output unchanged) and Cricket
  mappers (CR26, CR27), the Cricket between-legs and end routes in `KosmoDartsApp`, and the captures of these screens.
- Files: `ui/between/BetweenLegsScreen.kt`, `BetweenLegsState.kt`, `ui/end/EndScreen.kt`, `EndRow.kt`,
  `ui/cricket/CricketBetweenLegsContent.kt`, `CricketEndContent.kt`, `ui/KosmoDartsApp.kt`, the strings, tests
  `BetweenLegsContentTest`, `EndContentTest` and `CricketScreenshotTest` (cases added).
- Depends on: T8.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` are green, the X01 between and end tests and
  captures are unchanged, and `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a refactor that follows existing patterns, with the X01 output protected by the existing tests.

### T10 - Cricket setup, home card and resume summary

- Goal: `SetupState` mode and Cricket settings, the Cricket section of `SetupScreen`, the Cricket `ModeCard`,
  `NewGame(mode)` wired from both cards, the mode-aware `ResumeSummary` (CR20, CR21), and the home and setup captures. Starting a Cricket game from the app then works end to
  end.
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
  played instead of the hit sound (AN30). "Fully closed" is read as "closed by every player" (CR15), pending
  confirmation (Open question O1).

Design decisions taken in this plan (open to the design review):
- D1. Separate Cricket bindings (`CricketMatch`, `CricketGameScreenState`, `CricketReducer`) on top of the shared
  `engine/match`, rather than a generic mode abstraction (alternative A1).
- D2. `Dart` stays in `engine/x01` and is imported by `engine/cricket` (alternative A3, ADR 0008).
- D3. `StoredGame` above the unchanged `SavedGame` (alternative A2); format version 4 with a mode line; one
  last-setup file (alternative A4).
- D4. The issue's "Simple / Double / Triple" follows the X01 keypad: single by default, one-shot Double and Triple
  keys, and no "Simple" key. With a modifier active, 25 and Bull are disabled, as on the X01 keypad (Q11 "comme sur le
  pavé X01"). Confirmation requested as O2.
- D5. No `PlayerTile`s on the Cricket game screen: the board shows every player (CR22). To be confirmed by the UX
  review.
- D6. In the zone, a solo game shows the closed targets "n/7" in place of the points, consistent with Q8 (CR22). To be
  confirmed by the UX review.

## Open questions

- O1 (product, for Kosmo). "Lorsqu'une zone est totalement fermée": does the closed sound play when the sector is
  closed by every player (the greyed row), or as soon as the thrower himself has closed it? Options:
  - A (recommended): closed by every player (the row is greyed and scores nothing any more; in a solo game, closed by
    the player);
  - B: closed by the thrower, even when the dart still scores points (Standard) or gives points to opponents
    (Cut-throat).

  In both cases the sound replaces the hit sound (never both), and the dart is still called by the voice when "Call each
  dart" is on. The plan is written for A.
- O2 (product, for Kosmo, minor). Q11 A said that an active Double/Triple is "ignored" on `25`/`Bull`, "comme sur le
  pavé X01". The X01 keypad actually disables `25` and `Bull` while Double or Triple is active. Options:
  - A (recommended): the same as X01, keys disabled;
  - B: keys enabled, and the modifier ignored.
- O3 (owner follow-up, not a squad action). Kosmo asked to delete the branch `feat/sfx_cricket` once its content is
  recovered. Deleting a remote branch is left to Kosmo (or to an explicit instruction from him to `main`) after T4 is
  merged. No squad member deletes it.

## Implementation notes

(One line per task: task id, deviations, where the main pieces live.)
