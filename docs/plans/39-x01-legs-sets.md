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
  in the current set (design below). Nothing is shown in a single game. With Sets = 1, no set indicator is shown, and
  no text anywhere mentions sets.
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

- Setup (`SetupScreen.kt`): a "Match" block under the finish rule: two rows `label · − · "First to N" · +`, 48 dp
  touch targets, content descriptions on − and + ("Fewer legs per set", ...), the value read by TalkBack as
  "Legs per set: first to 3".
- Game screen indicators (M14, design submitted to the UX reviewer):
  - One indicator line under the player name, in the highlighted card and in every other row, only when the format
    is not 1 and 1. The line height is fixed, even with nothing won yet, so nothing moves when a pip appears.
  - Sets won: filled discs (●), one per set won. Legs won in the current set: rings (○, outline only), one per leg.
    The two groups are separated by a 12 dp gap and a thin vertical rule. With S = 1, only rings.
  - The kind is carried by the shape (filled vs outline), never by color alone. Pips are 10 dp, 4 dp apart, in the
    row's text color (`onBackground` in rows, `onPrimaryContainer` in the card), which keeps today's dark theme
    contrast. At most 8 + 8 pips (a 9th set or leg ends the set or match): about 230 dp, which fits under the name
    on a Pixel 7 in portrait (412 dp wide) and in landscape (scoreboard half, about 440 dp), 2 to 4 players. The
    scoreboard already scrolls vertically if 4 players do not fit.
  - TalkBack: one description per line, after the name: "1 set won, 2 legs won" (« 1 set gagné, 2 manches
    gagnées »), plurals, set part omitted when S = 1.
- Checkout dialog: the confirm label follows `checkoutDialogOutcome` (M15).
- Between-legs screen (new `ui/between/BetweenLegsScreen.kt`): title "Match score" (« Score du match »), one row per
  player in configuration order with name, sets (when S > 1) and legs of the current set as numbers and with the
  same pips, the line "{name} starts the next leg" (« {nom} commence la manche suivante »), and a full-width "Next
  leg" button. Portrait and landscape, scrollable. Back opens the existing abandon dialog.
- End screen: `EndRow` gets `setsWon`, `legsWon` (total) and `showsMatchScore`; average and darts from the match
  (M9, M17).
- Home summary: `ResumeSummary.PlayerScore` gets an optional match score text (M18); for a saved match between legs,
  the highlighted player is the starter of the next leg.

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
  Checks (`OUT_OF_RANGE`): winner in range; points 0 to start score, equal to the start score exactly for the
  winner; darts 0 to `MAX_DARTS`; points at most 60 x darts. The winner sequence is replayed by `MatchScore`: a leg
  after the match is decided is `REJECTED_BY_ENGINE`.
- `darts`: the current leg only, as in version 1, replayed with the starter `legs mod players` (M6).
- `between-legs`: `true` only when the current leg has no dart, at least one leg is confirmed, the match is not
  finished and `awaiting-next-player` is `false` (`INCONSISTENT_WAITING_FLAG` otherwise).

Dart storage vs size (the structural choice):
- A. Keep a summary per confirmed leg and the darts of the current leg only (chosen). M8 makes confirmed legs final,
  and M9 and M10 only need their winner, points and darts. `MAX_DARTS` (10,000) keeps applying to the current leg,
  as in R12; a match is never cut off by its length. Largest file: 1,089 leg lines of at most 46 bytes (about 50 KB)
  plus today's 41 KB: `MAX_FILE_BYTES` goes from 65,536 to 131,072.
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
`UNSUPPORTED_VERSION`. An older app that finds version 2 rejects it with the R9 message (ADR 0001, accepted).

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
  Q4). Sound and timing as AN9 and AN10 (see Open questions about the victory sound).
- AN28. "Next leg" produces a turn start cue for the new leg's starter, with the "throws first" line (AN4, owner
  Q5). AN4's list of hooks gains this one.

### 7. Strings (`values/` and `values-fr/`)

| Key | English | Français |
|---|---|---|
| `setup_match_label` | Match | Match |
| `setup_legs_per_set` | Legs per set | Manches par set |
| `setup_legs` | Legs | Manches |
| `setup_sets` | Sets | Sets |
| `setup_first_to` | First to %1$d | Premier à %1$d |
| `setup_fewer_legs` / `setup_more_legs` | Fewer legs / More legs | Moins de manches / Plus de manches |
| `setup_fewer_sets` / `setup_more_sets` | Fewer sets / More sets | Moins de sets / Plus de sets |
| `checkout_finish_leg` | Finish leg | Terminer la manche |
| `checkout_finish_set` | Finish set | Terminer le set |
| `between_title` | Match score | Score du match |
| `between_next_starter` | %1$s starts the next leg | %1$s commence la manche suivante |
| `between_next_leg` | Next leg | Manche suivante |
| plurals `match_sets_won` | %d set won / %d sets won | %d set gagné / %d sets gagnés |
| plurals `match_legs_won` | %d leg won / %d legs won | %d manche gagnée / %d manches gagnées |
| `match_indicators_description` | %1$s, %2$s | %1$s, %2$s |
| `announce_leg_won` | Leg for %1$s | Manche pour %1$s |
| `announce_set_won` | Set for %1$s | Set pour %1$s |

Existing `checkout_finish` ("Finish game" / « Terminer la partie ») and `announce_win` are reused for MATCH.
`FrenchStringsTest` covers the new keys.

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
- Added `ui/game/MatchIndicators.kt` (composable + `MatchIndicator` model), `ui/between/BetweenLegsScreen.kt`,
  `BetweenLegsTags.kt`.
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
- Rotation (M6): 3 players A, B, C over a set change (2 sets of 2 legs): starters A, B, C, A, B; a single player
  always starts; "Shuffle" only sets the first starter (setup order C, A, B → starters C, A, B, C).
- `X01StartingPlayerTest` (T1): a leg started at index 2 gives the first turn to player 2 and wraps; undo back to empty
  keeps the starter.
- `X01MatchTest`: `confirmLeg` appends the summary and starts the next leg with the next starter; a MATCH leg cannot
  be confirmed; undo never crosses a leg (M8); undoing the winning dart reopens the leg and clears `pendingOutcome`.
- `X01MatchStatisticsTest` (M9): average over two legs equals total points / total darts x 3 (bust darts counted);
  visit box state (VT rules) is "not played" for everyone at the start of leg 2.
- `X01MatchRankingTest` (M10): sets first, then total legs (not legs of the last set: a case where they disagree),
  then remaining; full tie shares the rank and skips the next (1, 2, 2, 4); 2 players.
- `X01CuesTest` (AN27): win cue outcome LEG, SET, MATCH; big fish keeps its kind; turn start of a new leg has
  `isGameStart = true` (AN28).

### `:app` pure JVM
- `GameSaveCodecTest`: golden bytes v2 (single game and match); round trip; v1 golden files decode as 1/1 (R16) and
  re-encode as v2; resume in the middle of a leg and in the middle of a set (R14); `between-legs` consistency;
  every new `OUT_OF_RANGE` and `REJECTED_BY_ENGINE` case; `legs` above `maxLegs`; a file of the maximum size is
  accepted, one byte over the limit rejected.
- `LastSetupCodecTest`: v2 round trip, v1 → 1/1, out-of-range format.
- `SaveFuzzTest`: seeded mutations of a v2 match file never throw (R11).
- `PersistenceEffectsTest`: written on "Finish leg", "Finish set", "Next leg" (R15); not deleted on the between-legs
  screen; deleted only on "Finish game" of the match and on abandon (R14, R6).
- `AppReducerTest`: the three dialog outcomes; between-legs back/abandon; rematch keeps the format and rotates the
  order (M17); setup steppers clamp at 1 and 9 (M13).
- `InitialStateTest`: resume on the between-legs screen after a kill (owner Q6 ii).
- `GameCuesTest`: "Next leg" gives the new starter's turn start; resume gives nothing (AN7, AN28).
- `GameScreenStateTest`: `checkoutDialogOutcome`, match average, `indicators` null for 1/1.
- `EndRowsTest`, `ResumeSummaryTest`, `SetupStateTest`: match columns, summary match score, prefill of the format.
- `CueScriptTest` / `ResourcePhrasesTest`: leg, set and match phrases (AN27).

### Robolectric (Compose UI)
- Setup: steppers, labels "Legs" vs "Legs per set" (M13), disabled bounds.
- Game: no indicator line in a single game; pips counts and TalkBack description in a match (M14).
- Dialog labels in the 3 variants, English and French (M15).
- Between-legs screen content and back → abandon dialog (M16).
- End screen match columns (M17); home summary (M18).

### Roborazzi captures (record only, Pixel 7, as the existing tests)
New `MatchScreenshotTest`: setup (format 1/1 and 2 sets of 3, en and fr); cards with indicators for 2 and 4 players,
portrait and landscape; checkout dialog LEG, SET, MATCH; between-legs screen (portrait and landscape); end screen of
a 4-player match. Existing captures unchanged.

### Local checks
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`, then `kosmo-detekt`.

## How Kosmo verifies on the phone (Pixel)

The PR description will hold the step-by-step scenario. Outline:
1. Fresh install, "New game": "Legs" and "Sets" at "First to 1"; start a 2-player 301 game: screen, dialog ("Finish
   game"), end screen exactly as before.
2. Setup with Sets 1, Legs "First to 2": label "Legs", no "set" anywhere during the game; win a leg: "Finish leg",
   voice "Leg for …", between-legs screen with the score and the next starter; "Next leg": the other player starts,
   "throws first" voice, empty visit boxes, average still over the match.
3. 3 players, 2 sets of 2 legs: starters rotate A, B, C, A across the set change; pips: discs for sets, rings for
   legs, legs back to zero after a set; "Finish set" and voice "Set for …"; final "Finish game" and "Victory"
   voice; end screen ranking with sets, legs, averages.
4. Kill the app mid-leg, mid-set and on the between-legs screen: each comes back exactly (R14); back on the
   between-legs screen opens "Abandon the game?".
5. Rematch: same format, the second player starts the first leg.
6. Landscape with 4 players: pips readable.
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

Design decisions (proposed in this plan, to be confirmed by the design review):
- D1. Generic `MatchScore` on leg winners plus an X01 binding (Approach 2, option A).
- D2. Save format version 2 with a summary per confirmed leg and the current leg's darts; `MAX_FILE_BYTES` 131,072;
  version 1 decoders kept (Approach 5, option A); new ADR 0005.
- D3. Indicators: filled discs for sets, rings for legs, on a fixed-height line under the name (Approach 4).

## Open questions

- O1 (product, for Kosmo through main): does the victory sound (`sfx_victory`) play on every leg and set win, or only
  on the match win? Proposed default: on every win, with the AN27 voice line, as today's single game.
