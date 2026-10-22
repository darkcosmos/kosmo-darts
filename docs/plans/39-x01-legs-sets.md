# Plan: X01 match in legs and sets (issue #39)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/39

Let players play an X01 match over several legs, and optionally over sets. A leg is today's X01 game: the first
player who checks out wins it. A player wins a set on reaching the number of legs per set (legs then restart at zero
for everyone), and the match on reaching the number of sets. The default format, 1 leg and 1 set, is today's single
game and must behave strictly as today.

The match logic (format, legs, sets, starting-player rotation, ranking) lives in `:game-engine` and is independent of
X01, so that a later mode (Cricket) reuses it; the mode only supplies the leg and the final tie-break criterion (X01:
remaining score in the last leg).

## Scope and out of scope

In scope:
- Engine: a generic match package (`engine/match`) and its X01 binding (`X01Match`); a leg starting player for
  `X01Game`; match-wide statistics; match ranking; win cues that say whether a leg, a set or the match was won.
- Rules: new `docs/rules/match.md` (prefix `M`); amended X01 rule T1; new persistence rules R13 to R16; new
  announcement rules AN27 and AN28.
- App: setup steppers, won set/leg indicators on the player cards, the checkout dialog in three variants, a new
  "between legs" screen, the end screen and the home summary of a match, rematch with the same format.
- Persistence: save format version 2 for both files, with the version 1 decoders kept; new ADR 0005.
- Strings in `values/` and `values-fr/`.
- Tests: engine JVM, persistence JVM, app JVM, Robolectric, Roborazzi captures.

Out of scope (issue): "continue for places", statistics and game history. Also out of scope: any change to the X01
scoring, bust, checkout or undo rules inside a leg, and to the checkout suggestion rules (they apply per leg as is).

## Rules (to be written in `docs/rules/match.md`, prefix M)

The prefix `M` collides with no other rule file (X01: C, D, T, S, U, E, A, P; persistence: R; announcements: AN;
visit total: VT; checkout suggestions: CS).

Engine:
- M1. A match has a format: legs per set L and sets S, each from 1 to 9. Default 1 and 1. A format outside these
  bounds is rejected with an explicit error. Both are shown as "First to N" (« Premier à N »), never "Best of".
- M2. A leg is one game of the mode (for X01: one leg under rules C to A of `x01.md`). The first player to finish
  wins the leg.
- M3. A player wins a set on reaching L legs won in the current set. Every player's legs of the current set then go
  back to 0.
- M4. A player wins the match on reaching S sets won. The match is then finished: no further leg can be played.
- M5. With the format 1 and 1 (single game), the match is exactly one leg and every existing X01 behavior is
  unchanged: same screens, same texts (no leg or set mention), same ranking (E1, E2), same save content apart from
  the format version (R13).
- M6. Starting player: legs are numbered from 0 over the whole match, sets ignored. Leg k starts with the player at
  position k mod n in the configuration order (n players). The setup "Shuffle" only fixes the configuration order,
  hence the starter of the first leg. Cards keep the configuration order; only the highlight moves (owner Q9).
- M7. When the winning dart of a leg is thrown, the match tells what confirming it would decide: LEG (neither a set
  nor the match), SET (a set but not the match) or MATCH. With S = 1, a leg that reaches L legs decides the match.
- M8. A confirmed leg is final: undo never goes back into a previous leg. Inside the current leg, U1 to U3 apply,
  including undoing the winning dart before confirmation.
- M9. Statistics: a player's three-dart average is computed over the whole match: points scored in every leg
  (completed legs and the current one) divided by darts thrown in every leg, times 3 (bust darts count, A1). No dart
  thrown means no average. Darts thrown on the end screen are also counted over the whole match. Per-leg figures
  restart at each leg: remaining score, darts of the current turn, the visit total box (the former "Prev." label,
  removed by PR37-R1, `visit-total.md`), which shows "not played" for everyone at the start of a leg.
- M10. Final ranking: the match winner first. The others by sets won (more is better), then by legs won over the
  whole match (owner Q1), then by the mode tie-break (X01: remaining score in the last leg, lower is better). Players
  equal on all criteria share a rank and the next rank is skipped (1, 2, 2, 4). With 1 and 1 this is E1 and E2.
- M11. The match logic knows nothing of X01: the mode provides the leg winner, the per-leg figures it needs and the
  tie-break value of each player.
- M12. A match has at most (n(L-1)+1) x (n(S-1)+1) legs (1,089 for 4 players, 9 and 9): this bound is used to
  validate saves.

App:
- M13. Setup: two settings, "Legs per set" and "Sets", each with − and + buttons, value shown as "First to N". − is
  disabled at 1, + at 9. When Sets = 1, the first label reads "Legs" (owner Q10). Legs and sets are allowed with a
  single player (owner Q8).
- M14. Indicators: with a format other than 1 and 1, every player card and row shows the sets won and the legs won
  in the current set, each group with a visible label (design below). Nothing is shown in a single game. With
  Sets = 1, no set indicator is shown, and no text anywhere mentions sets. The current player's card is always
  scrolled fully into view.
- M15. Checkout dialog: the confirm button reads "Finish leg" (« Terminer la manche ») for LEG, "Finish set"
  (« Terminer le set ») for SET, "Finish game" (« Terminer la partie ») for MATCH. Title and "Undo" are unchanged.
  A single game always shows "Finish game".
- M16. Between legs: after "Finish leg" or "Finish set", a screen shows the match score (each player's sets, when
  S > 1, and legs of the current set) and the player who starts the next leg, with one button "Next leg"
  (« Manche suivante »). System back opens "Abandon the game?" (owner Q6 i).
- M17. End screen: the match winner, then the M10 ranking with today's columns (average and darts over the match,
  owner Q2) plus each player's match score when the format is not 1 and 1. "Rematch" starts a match with the same
  format; its order follows today's rule (first player moved to the end, owner Q3). "New game" is unchanged.
- M18. Home summary of a saved match: today's content plus each player's match score (owner Q11).

## Approach

### 1. Engine: generic match (`engine/match`, pure Kotlin)

New package `io.github.darkcosmos.kosmodarts.engine.match`, with no dependency on `engine.x01`:

- `MatchFormat(legsPerSet: Int = 1, sets: Int = 1)`: validated at construction (M1), throws
  `InvalidMatchFormatException`. `isSingleGame` (1 and 1), `hasSets` (S > 1), `maxLegs(playerCount)` (M12).
  Constants `MIN = 1`, `MAX = 9`.
- `MatchScore(format, playerCount, legWinners: List<Int>)`: immutable, derived by replaying the leg winners in order
  (same idea as P1). Exposes per player `setsWon`, `legsWonInSet`, `legsWonTotal`; `winnerIndex` / `isFinished`
  (M4); `legsPlayed`; `starterOfNextLeg` = `legsPlayed % playerCount` (M6); `outcomeIfWonBy(player): LegOutcome`
  (`LEG`, `SET`, `MATCH`, M7); `withLegWonBy(player)`, which throws `MatchFinishedException` once the match is won.
  The constructor rejects winners out of range and any winner after the match is decided.
- `MatchRanking.rank(score, tieBreak: List<Int>): List<MatchRank>` (M10): `tieBreak[i]` is the mode value of player
  i, lower is better. `MatchRank(rank, playerIndex)`.

### 2. Engine: X01 binding

- `X01Game(config, darts, startingPlayerIndex: Int = 0)`: the replay starts with this player (T1 amended: "the leg's
  starting player starts; it is the first player unless a match says otherwise, M6"). Default 0, so every existing
  test and call stays as is. `equals`, `hashCode`, `toString`, `throwDart` and `undo` keep it. Invalid index:
  `IllegalArgumentException`.
- `X01MatchConfig(config: X01Config, format: MatchFormat = MatchFormat())`: what the setup produces and the last
  setup stores.
- `X01LegSummary(winnerIndex, pointsScored: List<Int>, dartsThrown: List<Int>)`: what is kept of a confirmed leg
  (M8, M9). `X01LegSummary.of(game)` for a finished leg; the constructor validates sizes and ranges (used by the
  decoder, see 5).
- `X01Match(matchConfig, completedLegs: List<X01LegSummary> = emptyList(), currentLeg: X01Game)` (with a factory
  `X01Match.start(matchConfig)`):
  - `score: MatchScore` from the completed legs' winners; the current leg must start with `score.starterOfNextLeg`
    and use the same `X01Config`, otherwise `IllegalArgumentException`.
  - `pendingOutcome: LegOutcome?`: when the current leg is finished, what confirming it decides (M7).
  - `isFinished`: the current leg is finished and its outcome is MATCH.
  - `confirmLeg(): X01Match`: only for a finished leg whose outcome is LEG or SET; appends its summary and starts the
    next leg with the next starter (M6, M8). A MATCH leg is not confirmed into the list: the end screen reads the
    finished match as is (it needs the last leg's remaining scores for M10).
  - `playerStats`: per player `threeDartAverage` and `dartsThrown` over the match (M9).
  - `ranking`: M10 with the tie-break "remaining in the current (last) leg", empty while not finished.
  - `throwDart`, `undo` delegate to the current leg (undo never crosses a leg, M8).
- Cues (`X01Cue`, `X01Cues`): the win cue gets a `outcome: LegOutcome` field (`GameShot` and `BigFish` keep their
  sound behavior). `X01Cues.afterDart(before: X01Match, after: X01Match)` reads `pendingOutcome`. `turnStart` is
  unchanged and already reports `isGameStart` when the leg has no dart, which gives the "throws first" line at every
  leg (owner Q5).

Alternatives considered for the match structure:
- A. Generic `MatchScore` on leg winners + per-mode binding (chosen): the generic part is small, fully testable alone,
  and Cricket only adds its own binding and tie-break.
- B. A generic `Match<L>` that owns the mode's leg objects through an interface (`Leg { winner; isFinished }`):
  more generic plumbing (type parameters through the app state and the codec) for no present gain.
- C. Fields added to `X01Game` (legs and sets inside the X01 replay): mixes match rules into X01, against the
  issue's constraint.

### 3. App state (pure JVM)

- `GameScreenState(match: X01Match, ...)` replaces `game: X01Game`; `val game: X01Game get() = match.currentLeg`
  keeps the existing per-leg code (visit boxes, checkout suggestion, BUST, dialog) as is. New:
  `checkoutDialogOutcome: LegOutcome?`, `averageOf(index)` (match-wide, M9), `indicators: List<MatchIndicator>?`
  (`null` for a single game, M14).
- `Screen.BetweenLegs(match: X01Match, isAbandonDialogShown: Boolean = false)` (M16), `Screen.End(match: X01Match)`.
- `AppEvent`: `LegsPerSetChanged(delta)`, `SetsChanged(delta)` (M13), `NextLegTapped` (M16). `FinishGameTapped`
  keeps its name and covers the three dialog variants.
- Reducer:
  - "Finish …" in the dialog: MATCH → `Screen.End(match)`; LEG or SET → `Screen.BetweenLegs(match.confirmLeg())`.
  - `BetweenLegs`: `NextLegTapped` → `Screen.Game(GameScreenState(match))`; `Back` opens the abandon dialog;
    `AbandonConfirmed` → home (deletes, R6); `AbandonDismissed` closes it.
  - Rematch: `X01Match.start(X01MatchConfig(rotated config, same format))`, last setup updated (R7).
- `SetupState` gets `legsPerSet` and `sets` (prefilled from the last setup, defaults 1 and 1), `start()` returns an
  `X01MatchConfig`. `AppState.lastSetup: X01MatchConfig?`.
- `gameCues`: new hook "BetweenLegs → Game with `NextLegTapped`" gives `turnStart` of the new leg (AN28). Leaving
  `BetweenLegs` any other way gives nothing. A resume on `BetweenLegs` gives nothing (AN7).
- `savedGameOf`: `BetweenLegs` carries the saved match (with `isBetweenLegs = true`); `End` carries none (R6:
  deletion at the match win only, R14).
- R1: `wasOnGameScreen` becomes true for `Game` and `BetweenLegs`; `initialState` restores the screen the save says
  (`SavedGame.toScreen()`), so a kill on the between-legs screen comes back to it (owner Q6 ii).

### 4. Screens

- Setup (`SetupScreen.kt`): a "Match" heading under the finish rule, then two stepper rows. Each row is the label
  above the controls `− · "First to N" · +`, so a long French label at 200 % never squeezes the buttons (U4). The
  touch targets are 48 dp. The bounds come only from `MatchFormat.MIN` and `MatchFormat.MAX`: `SetupState` clamps
  with them and exposes `canDecreaseLegs`, `canIncreaseLegs`, `canDecreaseSets` and `canIncreaseSets`, which drive
  the disabled − and + states. `SetupScreen` holds no literal (A1).
  - TalkBack (U5, one set of keys): the value reads "Legs per set: first to 3" (`setup_legs_per_set_value`), or
    "Legs: first to 3" (`setup_legs_value`) when Sets = 1, and "Sets: first to 2" (`setup_sets_value`). The buttons
    read "Fewer legs" / "More legs" / "Fewer sets" / "More sets". A disabled button is announced as disabled by the
    standard `Modifier.clickable(enabled = false)` semantics, so no extra text is needed.
- Game screen indicators (M14):
  - Model: `MatchIndicator(setsWon: Int?, legsWon: Int)`, built in `GameScreenState.indicators` (`null` for 1 and 1;
    `setsWon` is `null` when S = 1). The composable `MatchIndicators` only draws it (architecture note).
  - One indicator line under the player name, in the highlighted card and in every other row, only when the format
    is not 1 and 1. The line height does not change with the counts, so nothing moves when a pip appears.
  - Each group has a visible text label, then its marks (U2): "Sets ●●" and "Legs ○○○" (« Sets ●● »,
    « Manches ○○○ »). Sets are filled discs and legs are rings. The label names the group, so the shapes need no
    legend, and color never carries the meaning. With S = 1, only the "Legs" group is shown.
  - Up to 4 marks are drawn one per item. From 5, a group shows one mark followed by "×N" (for example "Legs ○ ×5").
    So a group is never wider than its label plus 4 marks (U3). Marks are 12 dp, 4 dp apart. The ring stroke is 2 dp,
    in the row's text color (`onBackground` in rows, `onPrimaryContainer` in the card), which keeps today's dark theme
    contrast.
  - Width: label plus at most 4 marks is about 110 dp per group at 100 %. When both groups do not fit on one line
    (4 players in landscape, or 200 % font scale), the line wraps into two lines, one group per line (`FlowRow`). It
    never truncates.
  - TalkBack: one description per line, after the name: "1 set won, 2 legs won" (« 1 set gagné, 2 manches
    gagnées »), plurals, set part omitted when S = 1. The visual labels are hidden from TalkBack, so they are not
    read twice.
- The current player's card always stays visible (U1):
  - Today the scoreboard is a `verticalScroll` column and nothing scrolls to the highlighted card. With 4 players in
    portrait, the indicator line can push the current card out of view.
  - The scoreboard scrolls the highlighted card fully into view each time `GameScreenState.highlightedPlayerIndex`
    changes, and on first display, including after a resume (`BringIntoViewRequester` in a keyed effect). The choice
    of card is already state (`highlightedPlayerIndex`); the composable only carries out the scroll (architecture
    note).
  - The highlighted card keeps its current content order. Its height grows by one indicator line (two at 200 % when
    they wrap). On a Pixel 7 in portrait at 200 %, the card is the tallest item and stays shorter than the scoreboard
    viewport. A Robolectric test proves this (see the test plan).
  - Alternative rejected: moving the indicators onto the name line when space is short. The name line already gives
    way to the average under VT11 (`OneOrTwoLines`), so a third element there would truncate long names first.
- Checkout dialog: the confirm label follows `checkoutDialogOutcome` (M15).
- Between-legs screen (new `ui/between/BetweenLegsScreen.kt`, model `BetweenLegsState` built from the match in pure
  JVM code):
  - First line: who won the leg or set that just ended, "Leg won by {name}" or "Set won by {name}" (« Manche gagnée
    par {nom} », « Set gagné par {nom} ») (U7).
  - Title "Match score" (« Score du match »).
  - A table with column headers "Sets" (only when S > 1) and "Legs" (« Manches »), one row per player in
    configuration order: name, then the numbers. Names take the remaining width and wrap on two lines at most, then
    ellipsize (U2).
  - The line "{name} starts the next leg" (« {nom} commence la manche suivante »), then a full-width "Next leg"
    button.
  - Scrollable column with `safeDrawingPadding` (insets) in portrait and landscape; the button stays at the end of
    the content (U7). Back opens the existing abandon dialog.
- End screen (M17, U8):
  - `EndRow` gets `setsWon: Int?` and `legsWon: Int?` (total over the match). Both are `null` in a single game, and
    `setsWon` is `null` when S = 1; the composable shows a column only when its value is not `null` (architecture
    note). Average and darts come from the match (M9).
  - Each row is the rank and the name on the first line, then the figures on a second line, each with its label:
    "Sets 2 · Legs 5 · 3-dart avg. 45.3 · 39 darts" (« Sets 2 · Manches 5 · Moy. 3 fléchettes 45,3 · 39 fléchettes »).
    The figures line wraps at 200 %, and the column scrolls. A long name wraps on two lines at most, so nothing is cut.
- Home summary (M18): `ResumeSummary.PlayerScore` gets `setsWon: Int?` and `legsWon: Int?` (null as above). The
  player line adds "Sets 1 · Legs 2" (« Sets 1 · Manches 2 ») before the remaining score, with the same labels as the
  cards. For a saved match between legs, the highlighted player is the starter of the next leg.

### 5. Persistence (format version 2, new ADR 0005)

ADR 0001 requires a new version for any new key, with the old decoders kept. New `docs/adr/0005-save-format-v2-match.md`
(status "Proposed", then accepted with the PR) extends ADR 0001; ADR 0001 gets one line under "Status" pointing to
it. ADR 0002 is unchanged except for one sentence: the between-legs screen counts as the game screen for R1.

Game file, version 2 (keys in this order):

```
kosmodarts=saved-game
version=2
game=x01
start-score=501
checkout=double-out
legs-per-set=3
sets=2
players=3
player=QWxpY2U=
player=Qm9i
player=Q2zDqW1lbnQ=
legs=2
leg=0 501/15 380/15 260/14
leg=1 300/12 501/13 401/12
darts=T20 20 D5
awaiting-next-player=false
between-legs=false
crc32=0a1b2c3d
end
```

- `legs`: number of confirmed legs, 0 to `maxLegs` (M12), followed by exactly that many `leg` lines in play order.
  A `leg` line is the winner index, then for each player in configuration order `points/darts` (canonical integers).
  Checks (`OUT_OF_RANGE`): winner in range; the winner's points equal the start score; every other player's points
  are from 0 to start score − 1, strictly below the start score (S1); darts 0 to `MAX_DARTS`, at least 1 for the
  winner; points at most 60 x darts. These range rules live in the engine, in the `X01LegSummary` constructor
  (`IllegalArgumentException`). The codec maps that exception to `OUT_OF_RANGE` and holds no copy of the rules. The
  winner sequence is replayed by `MatchScore`: a leg after the match is decided is `REJECTED_BY_ENGINE`.
- Strict keys (S2): for each version, the ordered list of keys is defined once in `GameSaveCodec`
  (`V1_LAYOUT`, `V2_LAYOUT`) and in `LastSetupCodec`, and read through the existing strict `BodyReader`. Any missing,
  extra, duplicated or out-of-order key is `MALFORMED`, and so is a version 1 file that carries version 2 keys.
- `darts`: the current leg only, as in version 1, replayed with the starter `legs mod players` (M6).
- `between-legs`: `true` only when the current leg has no dart, at least one leg is confirmed, the match is not
  finished and `awaiting-next-player` is `false` (`INCONSISTENT_WAITING_FLAG` otherwise).

Dart storage vs size (the structural choice):
- A. Keep a summary per confirmed leg and the darts of the current leg only (chosen). M8 makes confirmed legs final,
  and M9 and M10 only need their winner, points and darts. `MAX_DARTS` (10,000) keeps applying to the current leg,
  as in R12; a match is never cut off by its length. Largest file: 1,089 leg lines of at most 46 bytes (about 50 KB)
  plus today's 41 KB: `MAX_FILE_BYTES` goes from 65,536 to 131,072.
  - Arithmetic, written in ADR 0005 (A2). A `leg` line is at most `leg=` (4) + winner (1) + 4 × ` 501/10000` (10)
    + LF (1) = 46 bytes, so 1,089 lines take at most 50,094 bytes. The new header lines (`legs-per-set=9`, `sets=9`,
    `legs=1089`, `between-legs=false`) add under 64 bytes, and version 1 content is under 41 KB. The total stays
    under 92 KB, below 131,072.
  - `MAX_FILE_BYTES` stays one named constant in `GameSaveCodec`. The tests use it (a maximum-size valid file is
    accepted, `MAX_FILE_BYTES + 1` is rejected) and never repeat the number.
- B. Keep every dart of every leg (one `darts` line per leg): needed only for history, which is out of scope; a long
  match (up to 1,089 legs) would pass 10,000 darts and stop being saved (R12), and the file limit would have to grow
  far more.
- C. Keep only match totals per player: points over a long match pass the 5-digit canonical integer limit
  (1,089 x 501), and the save could not be checked leg by leg.

Encoding refuses (returns `null`, previous file kept, as R12) when the current leg has more than `MAX_DARTS` darts or
a confirmed leg has a darts value above `MAX_DARTS`.

Last setup file, version 2: version 1 lines plus `legs-per-set` and `sets` after `checkout`.

Version 1 files (both kinds) are still decoded: a v1 game is a single game (format 1 and 1, no confirmed leg,
`between-legs=false`), a v1 setup has the format 1 and 1, without any message (R16). Every write uses version 2.
`SaveFormat.decode` takes the set of versions the kind accepts and returns the version read; anything else stays
`UNSUPPORTED_VERSION`. An older app that finds version 2 rejects it with the R9 message (ADR 0001, accepted). ADR 0005
states the effect (S3): under ADR 0002 decision 5, today's app deletes every rejected file. A version 2 game file is
deleted, with the R9 message shown once. A version 2 last setup is deleted silently (R10). Nothing is kept for a
later upgrade. This is accepted for the same reason as in ADR 0001: there is only one user, who installs builds from
PRs.

`SavedGame(match: X01Match, isAwaitingNextPlayer: Boolean, isBetweenLegs: Boolean)`; `isConsistent` covers the new
flag. `SaveLoader`, `Persistence` and `PersistenceEffect` keep their structure.

New persistence rules (`docs/rules/persistence.md`):
- R13. The match format is saved with the game and with the last setup (with names, start score and finish rule).
- R14. Resuming a match restores the sets and legs won, the current leg with its darts and its starter, a turn waiting
  for "Next player", the checkout dialog with its M15 label, and the between-legs screen if it was shown. The saved
  match is deleted only when the match win is confirmed ("Finish game") or on abandon (owner Q7, R6).
- R15. Writes (R12) also happen on "Finish leg", "Finish set" and "Next leg"; resuming on the between-legs screen
  writes nothing.
- R16. A saved game or last setup written by the previous version (format 1) is read as a single game (1 and 1),
  without any message.

### 6. Announcements

- AN27. The win voice depends on M7: "Leg for {name}" (« Manche pour {nom} ») for LEG, "Set for {name}" (« Set pour
  {nom} ») for SET, today's "Game shot! {name} wins" (« Victoire de {nom} ») for MATCH and for a single game (owner
  Q4).
  - `sfx_victory` plays only for MATCH and for a single game (owner O1). A LEG or SET win plays the winning dart's
    sound, its dart call when that setting is on, then the voice line alone, with no victory sound. With the voice off,
    a LEG or SET win makes no sound after the dart.
  - For MATCH, AN9 and AN10 are unchanged: the victory sound and the voice start together, and the next step waits
    for both. For LEG and SET, the next step waits for the voice only.
  - The big fish rules (AN5) do not change: a leg won from 170 still carries the big-fish cue, and that cue follows
    the same LEG / SET / MATCH rules for the sound.
- AN28. "Next leg" produces a turn start cue for the new leg's starter, with the "throws first" line (AN4, owner
  Q5). AN4's list of hooks gains this one. Its voice waits for the end of any announcement in progress (AN10).

### 7. Strings (`values/` and `values-fr/`)

| Key | English | Français |
|---|---|---|
Setup:

| Key | English | Français |
|---|---|---|
| `setup_match_label` (heading) | Match | Match |
| `setup_legs_per_set` | Legs per set | Manches par set |
| `setup_legs` | Legs | Manches |
| `setup_sets` | Sets | Sets |
| `setup_first_to` | First to %1$d | Premier à %1$d |
| `setup_legs_per_set_value` (TalkBack) | Legs per set: first to %1$d | Manches par set : premier à %1$d |
| `setup_legs_value` (TalkBack) | Legs: first to %1$d | Manches : premier à %1$d |
| `setup_sets_value` (TalkBack) | Sets: first to %1$d | Sets : premier à %1$d |
| `setup_fewer_legs` / `setup_more_legs` | Fewer legs / More legs | Moins de manches / Plus de manches |
| `setup_fewer_sets` / `setup_more_sets` | Fewer sets / More sets | Moins de sets / Plus de sets |

Shared match labels (cards, between-legs headers, end screen, home summary):

| Key | English | Français |
|---|---|---|
| `match_sets_label` | Sets | Sets |
| `match_legs_label` | Legs | Manches |
| `match_count_overflow` | ×%1$d | ×%1$d |
| `match_score_sets` | Sets %1$d | Sets %1$d |
| `match_score_legs` | Legs %1$d | Manches %1$d |
| `match_score_separator` (not translatable) | ` · ` | ` · ` |
| plurals `match_sets_won` (TalkBack) | one: %d set won; other: %d sets won | one: %d set gagné; other: %d sets gagnés |
| plurals `match_legs_won` (TalkBack) | one: %d leg won; other: %d legs won | one: %d manche gagnée; other: %d manches gagnées |
| `match_indicators_description` (TalkBack) | %1$s, %2$s | %1$s, %2$s |

Checkout dialog, between-legs screen and voice:

| Key | English | Français |
|---|---|---|
| `checkout_finish_leg` | Finish leg | Terminer la manche |
| `checkout_finish_set` | Finish set | Terminer le set |
| `between_leg_won_by` | Leg won by %1$s | Manche gagnée par %1$s |
| `between_set_won_by` | Set won by %1$s | Set gagné par %1$s |
| `between_title` | Match score | Score du match |
| `between_next_starter` | %1$s starts the next leg | %1$s commence la manche suivante |
| `between_next_leg` | Next leg | Manche suivante |
| `announce_leg_won` | Leg for %1$s | Manche pour %1$s |
| `announce_set_won` | Set for %1$s | Set pour %1$s |

These are reused as they are: `checkout_finish` ("Finish game" / « Terminer la partie ») and `announce_win` for
MATCH, plus `end_average` and the plural `end_darts_thrown` on the end screen's figures line. Plurals are `<plurals>`
resources. `FrenchStringsTest` covers every new key.

## Files and types added or changed

`:game-engine`
- Added `engine/match/MatchFormat.kt`, `MatchScore.kt`, `LegOutcome.kt`, `MatchRanking.kt`.
- Added `engine/x01/X01Match.kt`, `X01MatchConfig.kt`, `X01LegSummary.kt`.
- Changed `X01Game.kt`, `X01Replay.kt` (starting player), `X01Cue.kt` (win cue outcome, `afterDart` on matches).
- Tests added: `match/MatchFormatTest`, `MatchScoreTest`, `MatchRankingTest`, `x01/X01MatchTest`,
  `X01MatchStatisticsTest`, `X01MatchRankingTest`, `X01StartingPlayerTest`; `X01CuesTest` extended. Existing tests
  untouched.

`:app`
- Changed `ui/app/AppState.kt`, `AppReducer.kt`, `GameCues.kt`, `InitialState.kt`, `KosmoDartsViewModel.kt`
  (`wasOnGameScreen`), `PersistenceEffect.kt`; `ui/game/GameScreenState.kt`, `GameScreen.kt`,
  `SavedGameScreen.kt`; `ui/setup/SetupState.kt`, `SetupScreen.kt`, `SetupTags.kt`; `ui/end/EndRow.kt`,
  `EndScreen.kt`; `ui/home/ResumeSummary.kt`, `HomeScreen.kt`; `ui/KosmoDartsApp.kt`; `audio/CueScript.kt`,
  `Phrases.kt`, `ResourcePhrases.kt`.
- Added `ui/game/MatchIndicators.kt` (composable + `MatchIndicator` model), `ui/between/BetweenLegsState.kt`
  (pure model), `BetweenLegsScreen.kt`, `BetweenLegsTags.kt`.
- Persistence: `SaveFormat.kt` (accepted versions), `GameSaveCodec.kt` (v1 and v2 decoders, v2 encoder, new limit),
  `LastSetupCodec.kt`, `SavedGame.kt`, `Persistence.kt`, `SaveLoader.kt` (types only).
- Resources: `values/strings.xml`, `values-fr/strings.xml`.

Docs: `docs/rules/match.md` (new), `x01.md` (T1, planned extensions), `persistence.md` (R13 to R16), `announcements.md`
(AN27, AN28), `docs/adr/0005-save-format-v2-match.md` (new), one line in ADR 0001 and ADR 0002.

No new dependency, no build change.

## Data read, stored or exposed

- Stored, app private storage only (unchanged location): the match format and, per confirmed leg, the winner and each
  player's points and darts. No new personal data; names are stored as today.
- No new permission, no network, nothing exported. Untrusted input is still only the two files: every new field is
  bounded and validated before use (R9, R11), and the fuzz test covers version 2.

## Screens and states

| Screen | State | What shows |
|---|---|---|
| Setup | format 1/1 | "Legs" and "Sets" at "First to 1", − disabled |
| Setup | sets > 1 | label "Legs per set" |
| Game | single game | exactly today |
| Game | match | indicator line on every card and row |
| Checkout dialog | LEG / SET / MATCH | "Finish leg" / "Finish set" / "Finish game" |
| Between legs | after LEG or SET | match score, next starter, "Next leg"; back → abandon dialog |
| End | single game | exactly today |
| End | match | winner, M10 ranking, match averages and darts, match score |
| Home | saved match | summary plus match score; between legs: next starter |

## Test plan

### `:game-engine` (JUnit, JVM; each test cites its rule)
- Single game unchanged (M5): all existing X01 tests pass untouched; `X01Match.start` with 1/1: the first win has
  outcome MATCH and the ranking equals `X01Game.state.ranking` on the same darts (E1, E2).
- `MatchFormatTest` (M1): bounds 1 and 9 accepted, 0 and 10 rejected for both fields; `maxLegs` (M12).
- `MatchScoreTest`: first to 3 legs (M2, M4); 2 sets of 2 legs with reset of legs (M3); `outcomeIfWonBy` LEG, SET,
  MATCH including S = 1 (M7); `withLegWonBy` after the match throws; winners out of range rejected.
- `MatchFormatTest` / `MatchScoreTest` (M12, T7): `maxLegs` is a true upper bound. For 2 players, first to 2 legs
  and 2 sets, the longest winner sequence (each set at its longest, `A B A` then `B A B`, …) is accepted up to the
  decisive leg, and one more winner is rejected. A sequence longer than `maxLegs` cannot be built.
- Rotation (M6): 3 players A, B, C over a set change (2 sets of 2 legs): starters A, B, C, A, B. 2 players: A, B, A,
  B. 4 players over 5 legs, wrapping past n: A, B, C, D, A (T2). A single player always starts. "Shuffle" only sets
  the first starter (setup order C, A, B gives starters C, A, B, C).
- `X01StartingPlayerTest` (T1): a leg started at index 2 gives the first turn to player 2 and wraps; undo back to empty
  keeps the starter.
- `X01MatchTest`: `confirmLeg` appends the summary and starts the next leg with the next starter; a MATCH leg cannot
  be confirmed; undo never crosses a leg (M8); undoing the winning dart reopens the leg and clears `pendingOutcome`.
  - Constructor invariants (A3): a current leg with the wrong starter, a current leg with a different `X01Config`, and
    completed legs that continue after the match is decided each throw `IllegalArgumentException`.
  - `X01LegSummary` (S1): a non-winner with points equal to the start score, a winner below it, or points above
    60 x darts throw `IllegalArgumentException`.
  - Edge cases (T1): in single out, a win reaching LEG, SET and MATCH in turn; in double out, a bust on the last dart
    of a turn (remaining 2, single 2) gives no `pendingOutcome`. At least one match is played in 501 and one in 301
    (T2).
- `X01MatchStatisticsTest` (M9): average over two legs equals total points / total darts x 3, with bust darts in both
  legs counted in the darts total (T1); visit box state (VT rules) is "not played" for everyone at the start of leg 2.
- `X01MatchRankingTest` (M10): sets first, then total legs (not legs of the last set: a case where they disagree),
  then remaining; full tie shares the rank and skips the next (1, 2, 2, 4); 2 players.
- `X01CuesTest` (AN27): win cue outcome LEG, SET, MATCH; big fish keeps its kind; turn start of a new leg has
  `isGameStart = true` (AN28).

### `:app` pure JVM
- `GameSaveCodecTest`: golden bytes v2 (single game and match); round trip; v1 golden files decode as 1/1 (R16) and
  re-encode as v2; resume in the middle of a leg and in the middle of a set (R14); `between-legs` consistency;
  every new `OUT_OF_RANGE` and `REJECTED_BY_ENGINE` case, including a non-winner at the start score (S1); `legs`
  above `maxLegs`; a valid file of the maximum size is accepted, and `MAX_FILE_BYTES + 1` is rejected (A2).
  - v1 fixtures are real encoder output (T4): they are generated with the version 1 encoder of `main` (db65670) and
    committed as test fixtures before the codec changes. The existing v1 golden test becomes a decode test on those
    exact bytes.
  - A 1/1 game encodes to exactly its v1 content, with `version=2` and the new keys inserted at their places
    (`legs-per-set=1`, `sets=1`, `legs=0`, `between-legs=false`), and nothing else changed (T4).
  - Strict keys (S2): v1 with any v2 key, and v2 with a missing, duplicated or out-of-order key, are `MALFORMED`.
- `LastSetupCodecTest`: v2 round trip, v1 → 1/1, out-of-range format, strict keys as above.
- `SaveFuzzTest`: seeded mutations of a v2 match file never throw (R11). The seeds include the S2 key cases.
- `PersistenceEffectsTest`: written on "Finish leg", "Finish set", "Next leg" (R15); not deleted on the between-legs
  screen; deleted only on "Finish game" of the match and on abandon (R14, R6). No effect is produced when a game is
  resumed on the between-legs screen (R15, T3).
- `AppReducerTest`: the three dialog outcomes; between-legs back/abandon; rematch keeps the format and rotates the
  order (M17); setup steppers clamp at `MatchFormat.MIN` and `MatchFormat.MAX` (M13, A1).
- `InitialStateTest`: resume on the between-legs screen after a kill (owner Q6 ii). A resume on a finished, unconfirmed
  leg restores the checkout dialog with the right label, for LEG, SET and MATCH (R14, T3).
- `GameCuesTest`: "Next leg" gives the new starter's turn start; resume gives nothing (AN7, AN28).
- `GameScreenStateTest`: `checkoutDialogOutcome`, match average, `indicators` null for 1/1.
- `EndRowsTest`, `ResumeSummaryTest`, `SetupStateTest`: match columns, summary match score, prefill of the format.
- `CueScriptTest` / `ResourcePhrasesTest`: leg, set and match phrases (AN27).
- Victory sound only for the match (AN27, owner O1), in `CueScriptTest` and in `AnnouncementPlayerTest` with the
  fake sound player:
  - A LEG win and a SET win, big fish included, play no `sfx_victory`; their voice line is spoken, and the next step
    waits for the voice only.
  - A MATCH win and a single game play `sfx_victory` together with the victory voice, as today.
  - With the voice off and sounds on, a LEG win plays nothing after the dart sound, and a MATCH win plays
    `sfx_victory`.
  - The existing AN9 and AN10 tests for a single game are unchanged.

### Robolectric (Compose UI)
- Setup: steppers, labels "Legs" vs "Legs per set" (M13), disabled bounds and their TalkBack values (U5).
- Game: no indicator line in a single game. In a match: the counts of marks, the "×N" form from 5, the visible group
  labels "Sets" / "Legs" in English and « Sets » / « Manches » in French (U2), and the TalkBack description (M14).
- Current card always visible (U1): 4-player match in portrait at font scale 2.0, with long names (20 characters).
  After each "Next player" through a full round, the last player included, `CURRENT_PLAYER` is displayed in full
  (`assertIsDisplayed` plus its bounds inside the scoreboard viewport). The same check runs after a resume.
- Dialog labels in the 3 variants, English and French (M15).
- Between-legs screen: the winner line, the column headers in English and French (U2, U7), the next starter, and back
  opening the abandon dialog (M16).
- End screen match columns (M17) with labels in English and French; home summary match score (M18).

### Roborazzi captures (record only, Pixel 7, as the existing tests)
New `MatchScreenshotTest`:
- Setup: format 1/1 and 2 sets of 3, in English and in French; the French one also at font scale 2.0 (U4).
- Cards with indicators: 2 and 4 players, portrait and landscape. Also 4 players in portrait at font scale 2.0 with
  long names, the last player highlighted (U1). Also a card with a "×5" group (U3).
- Checkout dialog: LEG, SET and MATCH.
- Between-legs screen: portrait and landscape with 3 sets or more, plus French at font scale 2.0 (T5).
- End screen: 4-player match in portrait, with long names, at font scale 1.0 and 2.0 (U8).
- Home summary of a saved match, in English and in French at font scale 2.0 (T5).

Existing captures are unchanged.

### Local checks
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`, then `kosmo-detekt`.

## How Kosmo verifies on the phone (Pixel)

The PR description will hold the step-by-step scenario. Outline:
1. Fresh install, "New game": "Legs" and "Sets" at "First to 1"; start a 2-player 301 game: screen, dialog ("Finish
   game"), end screen exactly as before.
2. Setup with Sets 1, Legs "First to 2": label "Legs", no "set" anywhere during the game; win a leg: "Finish leg",
   voice "Leg for …", between-legs screen with the score and the next starter; "Next leg": the other player starts,
   "throws first" voice, empty visit boxes, average still over the match.
3. 3 players, 2 sets of 2 legs: starters rotate A, B, C, A across the set change. The indicators read "Sets ●" and
   "Legs ○○", and legs go back to zero after a set. Then "Finish set" with the voice "Set for …", the between-legs
   screen "Set won by …". Winning a leg or a set plays no victory sound, only its voice line ("Leg for …",
   "Set for …"). The final match dart plays the victory sound together with the victory voice, then "Finish game".
   The end screen ranks by sets, legs and averages.
4. Kill the app mid-leg, mid-set and on the between-legs screen: each comes back exactly (R14); back on the
   between-legs screen opens "Abandon the game?".
5. Rematch: same format, the second player starts the first leg.
6. 4 players with long names, in portrait then in landscape, then with the phone's largest font size: the current
   player's card is always fully visible after every "Next player", and the indicators are readable with their
   labels.
7. Update over the previous version with a game in progress: it resumes as a single game, no message (R16).

## Decisions

Owner answers (relayed by main, 2026-10-21: "tout A"):

| Id | Decision | Rules |
|---|---|---|
| Q1 | Second ranking criterion: legs won over the whole match. | M10 |
| Q2 | End screen keeps its columns, average and darts over the whole match, plus the match score. | M9, M17 |
| Q3 | Rematch keeps today's rule: the first player moves to the end, so the next one starts the first leg. | M17 |
| Q4 | Distinct voice lines: "Leg for {name}", "Set for {name}", today's victory line only for the match. | AN27 |
| Q5 | The "throws first" announcement at every new leg, after "Next leg". | AN28 |
| Q6 | Back on the between-legs screen opens "Abandon the game?"; a resume shows the between-legs screen again. | M16, R14 |
| Q7 | Abandon erases the whole match, with today's text. | R14, R6 |
| Q8 | Legs and sets are allowed with a single player. | M13 |
| Q9 | Cards keep the configuration order; only the highlight moves. | M6 |
| Q10 | "Legs per set" reads "Legs" when Sets = 1. | M13 |
| Q11 | The home summary adds the match score for a match. | M18 |
| O1 | "Uniquement sur la victoire": the victory sound (`sfx_victory`) plays only on the match win (and in a single game), never on a leg or set win. The distinct voice lines of Q4 stay. | AN27 |

Design decisions (proposed in this plan, to be confirmed by the design review):
- D1. Generic `MatchScore` on leg winners plus an X01 binding (Approach 2, option A).
- D2. Save format version 2 with a summary per confirmed leg and the current leg's darts; `MAX_FILE_BYTES` 131,072;
  version 1 decoders kept (Approach 5, option A); new ADR 0005.
- D3. Indicators: labelled groups "Sets ●●" / "Legs ○○○" (filled discs, 2 dp rings, "×N" from 5) on a line under the
  name that wraps per group when space is short. The scoreboard scrolls the highlighted card into view (Approach 4).

Design review round 1 (2026-10-21; architecture, security, test APPROVE; UX CHANGES_REQUESTED):

| Finding | Where it is addressed |
|---|---|
| U1 current card hidden at 4 players portrait | Approach 4 "The current player's card always stays visible"; Robolectric check and 200 % capture; verification step 6 |
| U2 shapes without labels | Approach 4: text label per group, column headers on the between-legs screen, labelled figures on the end screen and the home summary; en/fr assertions |
| U3 ring stroke, large counts | 12 dp marks, 2 dp stroke, "×N" from 5 |
| U4 setup label at 200 % French | label above the controls; French 200 % capture |
| U5 content descriptions | one set of keys (Approach 4, strings table) |
| U6 strings table | complete table in section 7 |
| U7 between-legs winner line, insets | Approach 4, between-legs screen |
| U8 end screen at 200 % | two-line rows; captures at 1.0 and 2.0 |
| A1 stepper bounds | `MatchFormat.MIN/MAX`, `canDecrease…`/`canIncrease…` in `SetupState` |
| A2 file size arithmetic | section 5 and ADR 0005; one named constant used by tests |
| A3 constructor invariants | `X01MatchTest` |
| S1 non-winner points | `X01LegSummary` constructor; codec test `OUT_OF_RANGE` |
| S2 strict keys per version | section 5; codec and fuzz tests |
| S3 v2 file in an older app | section 5 and ADR 0005: deleted (R9 message for the game, silent for the setup) |
| T1, T2, T3, T4, T5, T7 | test plan |
| T6 O1 pending | answered (O1 below); AN27, cue and sound tests, verification step 3 |
| Architecture notes | indicator, between-legs and end-screen columns decided in state (`MatchIndicator`, `BetweenLegsState`, `EndRow`); scroll target is `highlightedPlayerIndex`; Robolectric visibility check |

## Open questions

None. O1 was answered by Kosmo (see Decisions).

## Implementation notes

Where the main pieces live:
- Engine: `engine/match/` (`MatchFormat`, `MatchScore`, `LegOutcome`, `MatchRanking`); `engine/x01/X01Match.kt`
  (with `X01MatchPlayerStats`), `X01MatchConfig.kt`, `X01LegSummary.kt`; `X01Game.startingPlayerIndex`;
  `X01Cue.Win` (common interface of `GameShot` and `BigFish`, with `outcome`) and `X01Cues.afterDart(X01Match, X01Match)`.
- Persistence: `persistence/SaveBody.kt` holds the `SaveKey` list and the shared write and read of a body, following
  the per-version layouts declared in `GameSaveCodec` and `LastSetupCodec`. v1 fixtures:
  `app/src/test/resources/persistence/v1/` (committed in 7ac2391, before any codec change).
- App: `ui/app/SetupTransitions.kt` (setup edits, match start, rematch setup), `ui/between/`,
  `ui/game/MatchIndicators.kt`, `ui/setup/MatchSettings.kt` (steppers), `ui/end/EndTags.kt`.

Deviations from the plan (none changes a product behavior):
- `audio/AudioBinder.kt` (not listed): the between-legs screen keeps the game audio session open, like the end
  screen, so that the leg or set voice is not cut by "Finish leg" and "Next leg" can speak (AN27, AN28).
- `MatchIndicator` is declared in `GameScreenState.kt` rather than `MatchIndicators.kt` (detekt
  `MatchingDeclarationName`); the reducer's setup and start helpers moved to `SetupTransitions.kt` and the steppers to
  `MatchSettings.kt` (detekt `TooManyFunctions`).
- Single-game convenience constructors (M5): `SavedGame(X01Game, Boolean)`, `GameScreenState(X01Game, …)`,
  `Screen.End(X01Game)` and `endRows(X01Game)` wrap the leg in a 1 and 1 match. They keep the existing tests and
  previews unchanged.
- `X01LegSummary` keeps its `X01Config` (its ranges need the start score and the player count). The codec adds the
  R12 storage limit (at most 10,000 darts in a confirmed leg) on top of the engine ranges.
- `X01Match.finalScore`: the score with the deciding leg counted, used by the ranking and the end screen.
- French TalkBack values use a non-breaking space before the colon (« Manches par set : premier à 3 »), as the other
  French texts of the app.
- Home summary: the match score is a second line under the player's name, still before the remaining score, so that
  it wraps at 200 % without pushing the score out.
- TalkBack order: in the highlighted card the indicator line is read right after the name; in the other rows it is
  read after the row line (name, average, remaining, visit box).
- New tests went to new classes (`*MatchTest`, `MatchSideEffectsTest`, `AppReducerMatchTest`,
  `GameSaveMatchCodecTest`, `MatchScreensTest`, `MatchResumeScreensTest`, `MatchScreenshotTest`) rather than into the
  existing ones. Existing tests were only adapted to the new types and layout: the last setup is an
  `X01MatchConfig`; the v1 golden test now decodes the frozen fixture bytes; version 2 is no longer an unsupported
  version; the injected game factory takes the starter; the setup tests scroll to "Add a player" (the match section
  pushes it down); `TestPhrases` implements the two new phrases.
