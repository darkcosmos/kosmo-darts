# Plan: player Profile and statistics, heatmap and head-to-head (issues #52 and #53)

Status: draft for design review. One mission, one branch `feat/52-53-profile-head-to-head-heatmap`, one PR (owner P0).

## Goal

- #52 (H3): every player has a Profile screen with his X01 and Cricket statistics, filtered by variant and period, and
  a trend curve. All the Players screen actions move into the Profile (owner Q12 B).
- #53 (H4): the Profile shows a per-zone heatmap of the dartboard, and a Head-to-head screen compares two players.
- New owner request (mission intake): the heatmap of the last leg played is also shown at the end of a leg and at the
  end of a match, with a way to switch from one player to another.
- Two rule fixes decided at intake: merge reset date (Q1 B) and the in-game MPR on useful marks (Q2 B).

Issues: https://github.com/darkcosmos/kosmo-darts/issues/52 and https://github.com/darkcosmos/kosmo-darts/issues/53

## Scope and out of scope

In scope:
- PM4 merge reset date rule (Q1 B) and the "really reset" test (Q13).
- CR13 rewritten: the in-game MPR counts useful marks (Q2 B). CR14 visit marks stay all marks (P7).
- Statistics engine (X01, Cricket, trend), profile data loader, Profile screen with sections X01, Cricket and Cible
  (P4 A), trend curve, actions moved from the Players screen (Q12 B, P1 A, P2 A), tapping a player in a match detail.
- A real back stack for Players, History, Profile, Head-to-head and Match detail (#53 Q11, P3).
- Board zone geometry in `:game-engine`, heatmap aggregation, reusable dartboard Canvas component, heatmap colour
  scale chosen by Kosmo from SVG mockups (P9 C, gate G1), Profile "Cible" section.
- Head-to-head engine, data, state and screen.
- Heatmap of the last leg on the between-legs and end screens, X01 and Cricket.

Out of scope: heatmap by real position (camera milestone), group ranking (dropped), training statistics, a statistics
cache (added only if the performance test fails, see "Approach" 3), any schema version change, any new dependency,
screens smaller than a Pixel 7 (owner decision).

## Approach

1. **Single source of truth.** Every figure is a pure `:game-engine` function of a match configuration plus its ordered
   darts: the archived matches (AR5) for the Profile and the Head-to-head, the in-memory `MatchJournal` (DJ1) for the
   game, between-legs and end screens. Nothing is stored. DAOs only load rows.
2. **New engine package `engine/stats`.** Input type `StatsMatch` (session id, start, end, settings as
   `ArchivedSettings`, players with profile id, rank, legs and sets won, legs, darts in leg/visit/rank order) built by
   the app from rows; `MatchSelection` applies ST2 to ST5; `X01Stats`, `CricketStats`, `TrendSeries`,
   `HeatmapAggregation`, `HeadToHead` compute the figures. `CricketUsefulMarks` replays a Cricket leg with the existing
   `CricketGame` and returns, for each dart in play, its useful marks and the points it scored or inflicted (STC1). The
   same function serves the in-game MPR (CR13) and the Profile, so both always agree.
   - Alternative rejected for CR13: store useful marks in the confirmed-leg summaries of the saved game (format v3). It
     duplicates a value derivable from the journal (R26) and needs a stored format change; the journal replay needs
     none.
3. **Performance.** One read loads every match of the profile (sessions, players, legs, darts joined by indexed
   columns: `match_player.profile_id`, `leg(session_id, ordinal)`, `dart(leg_id, visit, rank)`), then the engine
   computes off the UI thread. A JVM test computes all Profile figures on 1,000 generated matches under 1 s. A cache
   is added only if that test or the loader test fails; it would then follow the #52 constraint (derived, invalidated
   on archive, reset, merge, delete).
4. **Navigation.** `Screen` gains a back stack for the history screens: `Screen.Players`, `Screen.History`,
   `Screen.Profile`, `Screen.HeadToHead` and `Screen.MatchDetail` each hold their parent screen (as `MatchDetail` holds
   `History` today). Opening a screen equal to one already in the chain (same profile, same pair, same match) returns
   to that screen and drops those above (HS9). The chain lives in `AppState` in the ViewModel, so it survives rotation.
5. **Players actions in the Profile.** The rename, merge, reset and delete panels, requests and answers of
   `ui/players` move to `ui/profile` unchanged in behavior (PM3 to PM11), opened by "Gérer le joueur" (P1 A). The
   Players screen becomes a list of rows that open the Profile.
6. **Heatmap colours.** Before any heatmap code, two static SVG mockups (relative scale and absolute scale, same ramp,
   same sample data) are committed under `docs/design/mockups/heatmap/` (task T16). `main` stops for Kosmo's choice
   (gate G1); task T20 implements only the chosen scale and records it in `docs/design/design-system.md` section 2.
7. **End-of-leg heatmap.** `Screen.End` and `Screen.CricketEnd` also carry the `MatchJournal` (between-legs screens
   already do). The last leg's journal gives each player's darts in play; the shared aggregation (HM2) gives the values.
   The selected player is part of the screen state.

## Files and types added or changed

Engine (`game-engine/.../engine/`):
- `profile/ProfileRules.kt`: `merge` follows PM4 (Q1 B), `isReallyReset(dates)` (ST9).
- `stats/` (new): `StatsMatch.kt`, `MatchSelection.kt` (filters, periods), `X01Stats.kt`, `CricketUsefulMarks.kt`,
  `CricketStats.kt`, `TrendSeries.kt`, `HeatmapAggregation.kt`, `HeadToHead.kt`; tests under `stats/`.
- `board/BoardGeometry.kt` made public as `BoardZones` (zone list, radii, sector order, `zoneAt(x, y)`); BC4, BC5.
- `cricket/CricketMatch.kt`: MPR from the match journal on useful marks (CR13).

App (`app/.../`):
- `history/StatsDao.kt`, `history/ProfileStatsRepository.kt` (new): load a profile's matches, map to `StatsMatch`.
- `ui/app/AppState.kt`, `AppReducer.kt`, `HistoryReducer.kt`, `PlayersReducer.kt`, new `ProfileReducer.kt`,
  `HeadToHeadReducer.kt`: back stack and new screens.
- `ui/profile/` (new): state, actions (moved from `ui/players`), screen, sections, trend curve, Cible section.
- `ui/players/`: list only.
- `ui/board/` (new): `Dartboard.kt` (Canvas, values per zone, tap callback), `HeatmapScale.kt`, `HeatmapLegend.kt`.
- `ui/headtohead/` (new): state, screen.
- `ui/between/`, `ui/end/`, `ui/cricket/CricketBetweenLegsContent.kt`, `CricketEndContent.kt`: heatmap block.
- `ui/history/MatchDetailScreen.kt`: tappable player names (HS8).
- `res/values/strings.xml`, `res/values-fr/strings.xml`: every new text, both languages in the same task.
- Docs: `docs/rules/statistics.md` (new), `docs/rules/history.md`, `docs/rules/cricket.md`,
  `docs/design/design-system.md` (section 2 heatmap tokens after G1), `docs/design/mockups/heatmap/*.svg`.

## Data read, stored or exposed

- Read: the archive tables (profile, session, match_record, match_player, leg, dart) and the in-memory match journal.
- Stored: nothing new. No schema version change, no saved game or last setup format change. Profile updates are the
  existing PM3 to PM6 writes (PM4 date computation changed).
- Exposed: nothing leaves the device. No permission, no dependency added. Android backup unchanged.

## Rules (new and rewritten)

Task T1 copies this section into the rule documents. Ids are stable.

### `docs/rules/cricket.md`

- CR13 (rewritten, owner Q2 B of #52). Statistics: a player's useful marks are the STC1 useful marks of his darts.
  MPR = useful marks / darts thrown x 3, over the whole match (completed legs and the current one, owner Q4 A of
  #40), computed from the match journal. No dart thrown means no MPR (absent, not zero). The engine returns the exact
  value. CR14 visit marks stay the CR2 marks of the visit (owner P7).

### `docs/rules/history.md`

- PM2 (rewritten). The Players screen lists every profile except the anonymous one, most recently used first. Each
  row shows the name, the number of matches and the end date of the last match ("No match yet" when none), and opens
  the Profile (PS1). The Players screen has no action. With no profile, the screen says so.
- PM4 (rewritten, owner Q1 B). Merge a source into a target: every participation of the source moves to the target,
  then the source is deleted. The target keeps its name; its last-used date becomes the latest of the two, its
  creation date the earliest; its reset date becomes the latest real reset (ST9) of the two, or the new creation date
  when neither was really reset. Refused, with a short message, when both profiles played a same match. One
  transaction. Irreversible, so preceded by an explicit confirmation inside the screen.
- PM11 (rewritten). No popup: confirmations, refusals and errors are shown inside the Profile screen (PS8), announced
  by TalkBack, in portrait and landscape and at 200% font size.
- HS8. In a match detail, tapping a player's name opens his Profile. "Deleted player" is not tappable.
- HS9. Back stack (owner #53 Q11, P3): Players, History, Profile, Head-to-head and Match detail open on top of the
  screen they come from; Back always returns one screen, with its filters and scroll position. Opening a screen equal
  to one already in the stack (same profile, same pair in the same order, same match) returns to it and drops the
  screens above: never two copies. The stack survives a rotation.
- BC4. The engine exposes the board zones: for each number 1 to 20 its sector angle (order of BC2) and the radii of
  the single, triple and double rings; the 25 and Bull radii (official dimensions, source cited in the code). Each
  nominal point (BC2) lies inside its own zone.
- BC5. `zoneAt(x, y)` gives the heatmap zone (HM1) under a board point, or none outside the double ring; a point on a
  border belongs to one zone only.

### `docs/rules/statistics.md` (new; prefixes ST, STX, STC, PS, HM, HH)

Common:
- ST1. Voided darts are always excluded. All legs of all sets count.
- ST2. A match counts for a profile when it started at or after the profile's reset date (owner Q3 A).
- ST3. X01 filters, independent (owner Q5 A): start score All / 501 / 301, and out rule All / Double out / Single
  out. Cricket filter: All variants / Standard / Cut-throat. No 25-Bull filter.
- ST4. Periods, three fixed views: Since always (all matches after ST2); Last 30 days (match end within the last
  30 x 24 h, rolling); Last 10 matches (the 10 most recent by end, taken after the filters and ST2) (owner Q4).
- ST5. A solo match counts in averages and performance figures, never in the results (matches won and lost, win %,
  legs and sets won) (owner Q6 A, P5).
- ST6. A figure that cannot be computed (no dart, no attempt, no leg won) shows "–", never 0 nor a division by zero.
- ST7. Figures are computed by `:game-engine` from the archive, off the UI thread; a Profile with 1,000 archived
  matches shows in less than 1 s. Nothing is stored.
- ST8. Won = final rank 1; lost = any other rank (owner Q6).
- ST9. A profile is *really reset* when its reset date is after its creation date. The Profile then shows
  "Since <date>", otherwise "Since always" (owner Q13).

X01:
- STX1. 3-dart average = points scored / darts thrown x 3. A bust visit scores 0 and its darts count. No padding to
  three darts (owner Q11).
- STX2. First-9 average: the same over the player's first 9 darts of each leg (fewer when the leg ended before).
- STX3. Checkout %: successes / attempts, per dart. Remaining = before each dart, mid-visit included. Double out:
  attempt when the remaining is even from 2 to 40, or 50. Single out: attempt when the remaining equals the value of
  one dart (1 to 20, a double, a triple, 25, 50; so 60 counts). A success is a dart that wins the leg; an attempt
  that busts is a failed attempt (owner Q10).
- STX4. Best checkout: the highest total of a visit that wins a leg (a 1- or 2-dart visit included, owner Q11).
- STX5. Score bands, exclusive: 180, 140 to 179, 100 to 139, 60 to 99; bust visits excluded; incomplete winning
  visits included (owner Q11).
- STX6. Best visit (bust visits excluded) and number of bust visits.
- STX7. Darts per leg won (average) and best leg (fewest darts), over the legs the player won, solo included.
- STX8. Results: matches won and lost, win %, legs won and sets won (stored values, AR6), solo excluded (ST5).

Cricket:
- STC1. Useful marks of a dart (owner Q9): the marks that close a target for the thrower are useful; surplus marks
  are useful only if they score (Standard, CR4) or give points (Cut-throat, CR5); marks on a dead target (CR15) are
  not; in a solo game only closing marks are useful. A triple that closes with 1 mark and makes the target dead gives
  1 useful mark. 25 and Bull give 1 and 2 marks when in play, else 0; every dart counts in the MPR darts.
- STC2. MPR = useful marks / darts x 3 (same as CR13, over the selected matches).
- STC3. Best visit in useful marks (at most 9). Useful marks per target: 20 to 15 and Bull (25 and Bull together).
- STC4. Points scored (Standard) and points given (Cut-throat): total and average per leg played. A surplus mark that
  gives 20 points to 2 opponents counts 40 (owner Q8, P6).
- STC5. Results by variant: matches won and lost, win %, legs and sets won, solo excluded. With "All variants", two
  blocks Standard and Cut-throat; the other figures follow the filter (owner Q7 A).

Profile screen:
- PS1. Access: a row of the Players screen, a player name in a match detail (HS8), the player picked in a head-to-head
  is not a link. Never for the anonymous profile.
- PS2. Header: the name, "Since always" or "Since <date>" (ST9), the buttons "Manage player" and "Compare with…".
  "Manage player" unfolds inside the screen the four actions Rename, Merge, Reset, Delete (owner P1 A); a second tap
  folds them.
- PS3. Three sections X01, Cricket, Board (owner P4 A), as tabs at the top. X01 and Cricket each have their filters
  (ST3) and the period (ST4); the selections survive a rotation and a return from a pushed screen.
- PS4. X01 section: STX1 to STX8 and the trend curve. Cricket section: STC2 to STC5 and the trend curve.
- PS5. Trend curve: one point per selected match in end order, 3-dart average (X01) or MPR (Cricket), drawn with
  Compose Canvas, no chart library. With fewer than 2 points, a short text replaces the curve. TalkBack reads the
  number of matches, the first, last, lowest and highest values.
- PS6. States: no match at all, an explicit empty state; no match for the selected filters or period, a message with
  the filters still shown; loading and read failure as HS7.
- PS7. Actions (PM3 to PM10, PM11) behave as before, inside the Profile. After a merge where the shown profile is the
  source, the target's Profile replaces it; after a delete, Back to the previous screen, reloaded, and when that
  screen is the detail of a match the deletion erased, to the History list. The actions are available from every
  entry point (owner P2 A).
- PS8. Design system of `docs/design/`, portrait and landscape, 200% font size, no popup, visible Back with a
  description, 48 dp targets (42 dp tall allowed in landscape).

Heatmap:
- HM1. Zones: single (inner and outer single rings as one zone, owner Q5 A), double and triple of each number 1 to 20,
  25 and Bull: 62 zones. Misses are shown apart, outside the board.
- HM2. A zone's value = its darts / all darts (misses included, voided excluded) of the selection (owner Q6 A).
- HM3. Profile Board section: mode filter All modes / X01 / Cricket, then the filters of the chosen mode (none for
  All modes), then the period (ST4); ST2 applies.
- HM4. Miss label: "Miss" when only X01 darts are counted; "Miss or outside Cricket numbers" as soon as Cricket darts
  are counted (Cricket, All modes, a Cricket leg) (owner #53 Q4 C).
- HM5. Colour scale: decided at gate G1 (relative to the most hit zone, or absolute 0 to 100 %); a zone with 0 dart
  has a distinct neutral colour; a legend shows the scale. Tokens in `docs/design/design-system.md` section 2.
- HM6. Tapping a zone shows, in a fixed band under the board, the zone name, its darts and its percentage; a new tap
  replaces it. A text list of the 5 most hit zones serves TalkBack (owner #53 Q8 A). No popup.
- HM7. No dart in the selection: explicit empty state.
- HM8. The dartboard component draws values per zone with the BC4 geometry and has no rule logic; taps resolve with
  BC5.
- HM9. End of a leg (between-legs screens M16, CR26) and end of a match (M17, CR27): the heatmap of the last leg
  played, for one player at a time, with a player selector (one entry per player, name and colour; none in a solo
  game). Same zones, values, scale, misses line and HM4 label. Derived from the match journal.

Head-to-head:
- HH1. "Compare with…" opens a picker inside the screen: every profile except the shown one and the anonymous one,
  with its number of common matches, sorted by that number (most first), then by name; a profile with 0 is listed and
  opens the empty state (owner #53 Q9 A).
- HH2. Common matches: matches both profiles played (2, 3 or 4 players), started at or after both reset dates (the
  latest, owner #53 Q3).
- HH3. Duel winner: the better final rank of the two; equal ranks count as a draw shown apart; common matches = wins
  A + wins B + draws (owner #53 Q1 A).
- HH4. Legs won by each in the common matches (stored values, owner #53 Q2 A).
- HH5. Side by side, over the common matches only: STX1, STX2, STX3, STX4 for X01, STC2 for Cricket.
- HH6. Filters: mode All / X01 / Cricket, the ST3 filters of the mode, the ST4 periods applied to common matches ("Last
  10 matches" = the last 10 common matches). "All" shows the counters over every mode, then the X01 figures and the
  Cricket MPR apart (owner #53 Q10).
- HH7. The list of common matches opens the match detail; Back returns to the head-to-head with its filters (HS9).
- HH8. Date line: "Since <latest real reset date>" when at least one of the two is really reset, else "Since always"
  (owner P8).
- HH9. No common match: explicit empty state. PS8 applies.

## Screens and states

- **Players**: list rows (name, matches, last match date), tap opens the Profile; empty, loading, failure.
- **Profile**: header (PS2) with the optional inline actions panel (the existing panels: rename with error, merge
  pick, merge confirm, reset confirm, delete confirm with count, messages); tabs X01 / Cricket / Board; filter chips;
  period chips; content, empty-for-filters, empty, loading, failure. Portrait: one column, scrolling, header and tabs
  scroll away. Landscape: tabs and filters at the left, figures in two columns at the right.
- **Head-to-head**: picker (list, empty if no other profile); loaded (names, date line, counters, side-by-side
  figures, list of common matches); empty (no common match); loading; failure.
- **Between legs and end screens**: a "BOARD · LEG n" block (title per HM9) with the player selector (chips with the
  colour strip and the name, horizontal scroll, 48 dp), the board, the misses line, the legend and the tap band.
  Portrait: the block under the scoreboard in the scrolling content, the main button ("MANCHE SUIVANTE", "REVANCHE" /
  "NOUVELLE PARTIE") stays pinned at the bottom. Landscape: a third column at the right of the scoreboard column, board
  sized to the available height. Pending owner answers N1 to N3 ("Open questions").

## Test plan

- Engine (JVM, cite rule ids): PM4 dates for the four reset combinations, ST9; ST2 boundary (start equal to the reset
  date); ST3 every filter combination; ST4 boundaries (exactly 30 days, 9/10/11 matches, filters before "last 10");
  ST5 solo; STX1 to STX8 on hand-built journals: busts, voided darts, double and single out, a leg won on the first
  dart of a visit, bands at 59, 60, 99, 100, 139, 140, 179, 180, remaining 60 in single out, 1- and 2-dart winning
  visits, a leg under 9 darts; STC1 useful and useless marks in both variants, solo, dead target, triple that closes
  and kills, 25-Bull off; STC4 40 points case; CR13 in-game MPR on useful marks with undo at every step (voided
  darts); trend series; 1,000-match performance test; BC4 nominal points inside their zone, BC5 borders and outside;
  HM2 with misses and voided darts; HM9 last leg per player, 1 to 4 players, after undo; HH2 common matches at 2, 3
  and 4 players, both reset dates; HH3 draws.
- Data (Robolectric): loader returns the profile's matches with all players' darts; anonymous never loaded as a
  profile; merge dates through the repository; a read failure gives `DbResult` failure.
- UI (Compose JVM tests): navigation stack (no copies, Back one level, rotation), Profile states, filters, actions
  panels and P2 outcomes, Players row opens the Profile, detail name tap, heatmap tap band and top-5 list, picker,
  head-to-head states, end-screen selector.
- Captures (Roborazzi, Pixel 7 only, portrait, landscape, 200 % font): Profile empty and filled (X01, Cricket),
  actions panel, Players list; trend curve; Board section empty, spread, concentrated; head-to-head picker, filled,
  empty; between-legs and end screens with the heatmap, X01 and Cricket, 1 and 4 players.
- Every edge case of the project review focus that this mission touches: bust, double-out and single-out, undo at
  every step (CR13, HM9), 1 to 4 players, 501 and 301, rematch (end-screen heatmap uses the finished match), process
  death on the between-legs screen (journal restored, heatmap shown, selection back to the default).

## How Kosmo verifies

On the Pixel 7, after uninstalling the previous version and installing the CI debug APK of the latest commit:
1. Play an X01 501 double-out match of 2 legs with 2 players, with a bust and an undo. At the end of the first leg,
   the between-legs screen shows the leg heatmap; switch players; tap a zone (count and %). Same at the end screen.
2. Play a Cricket match with 3 players; check the in-game MPR moves only with closing or scoring marks; the end screen
   heatmap labels misses "Raté ou hors numéros Cricket".
3. Home, Joueurs: rows open the Profile. X01 and Cricket figures, filters, periods, trend curve; "–" where nothing is
   computable; "Depuis toujours".
4. "Gérer le joueur": rename, reset ("Depuis le <date>"), merge (lands on the target's Profile), delete (back to the
   list).
5. Section Cible: filters, tap a zone, misses line, empty state on a filter without darts.
6. "Comparer avec…": picker with common match counts, figures side by side, a draw case at 3 players, open a common
   match and come back with Back; tap a name in that detail, Back returns step by step.
7. Rotate on every new screen; repeat with font size at maximum.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Rule documents | `52-53-profile-head-to-head-heatmap/T1-rules-docs.md` | - | haiku | Text copied from this plan, no choice |
| T2 | Merge reset date and "really reset" | `52-53-profile-head-to-head-heatmap/T2-profile-reset-rules.md` | T1 | sonnet | Small specified engine rule with tests |
| T3 | Stats input and match selection | `52-53-profile-head-to-head-heatmap/T3-stats-selection.md` | T1 | sonnet | Specified filters and periods, pure functions |
| T4 | X01 statistics | `52-53-profile-head-to-head-heatmap/T4-x01-stats.md` | T3 | sonnet | Formulas specified (STX), tests listed |
| T5 | Cricket useful marks per dart | `52-53-profile-head-to-head-heatmap/T5-cricket-useful-marks.md` | T1 | sonnet | Rule STC1 fully specified after Q9 |
| T6 | In-game MPR on useful marks | `52-53-profile-head-to-head-heatmap/T6-ingame-mpr.md` | T5 | sonnet | Wiring of an engine function into existing state holders |
| T7 | Cricket statistics | `52-53-profile-head-to-head-heatmap/T7-cricket-stats.md` | T3, T5 | sonnet | Formulas specified (STC), tests listed |
| T8 | Trend series and performance test | `52-53-profile-head-to-head-heatmap/T8-trend-performance.md` | T4, T7 | sonnet | Small pure function plus a benchmark test |
| T9 | Profile data loader | `52-53-profile-head-to-head-heatmap/T9-stats-loader.md` | T3 | sonnet | DAO and mapper over the existing schema |
| T10 | Back stack for history screens | `52-53-profile-head-to-head-heatmap/T10-back-stack.md` | T1 | sonnet | Reducer change specified by HS9, JVM tests |
| T11 | Profile state holder | `52-53-profile-head-to-head-heatmap/T11-profile-state.md` | T8, T9, T10, T2 | sonnet | State holder with JVM tests |
| T12 | Actions moved into the Profile (state) | `52-53-profile-head-to-head-heatmap/T12-profile-actions-state.md` | T11 | sonnet | Move of tested code plus PS7 outcomes |
| T13 | Profile screen | `52-53-profile-head-to-head-heatmap/T13-profile-screen.md` | T12 | sonnet | Screen from this plan, Compose tests, captures |
| T14 | Trend curve component | `52-53-profile-head-to-head-heatmap/T14-trend-curve.md` | T13 | sonnet | Canvas component from PS5 |
| T15 | Actions panel UI, Players list, detail links | `52-53-profile-head-to-head-heatmap/T15-actions-ui-links.md` | T13 | sonnet | Move of existing panels, tests and captures |
| T16 | Heatmap scale mockups (SVG) | `52-53-profile-head-to-head-heatmap/T16-heatmap-mockups.md` | T1 | sonnet | Ramp choice from the design system, hand geometry |
| G1 | Gate: Kosmo picks the scale | (main stops) | T16 | - | Owner decision P9 |
| T17 | Board zone geometry | `52-53-profile-head-to-head-heatmap/T17-board-zones.md` | T1 | sonnet | Geometry from official dimensions, tests |
| T18 | Heatmap aggregation | `52-53-profile-head-to-head-heatmap/T18-heatmap-aggregation.md` | T3, T17 | sonnet | Specified counting rules HM1, HM2, HM4, HM9 |
| T19 | Dartboard Canvas component | `52-53-profile-head-to-head-heatmap/T19-dartboard-component.md` | T17 | sonnet | Logic-free drawing with tests and captures |
| T20 | Heatmap colour scale and legend | `52-53-profile-head-to-head-heatmap/T20-heatmap-scale.md` | T19, G1 | sonnet | Mapping and legend from the chosen mockup |
| T21 | Profile Board section (state) | `52-53-profile-head-to-head-heatmap/T21-board-section-state.md` | T18, T11 | sonnet | State holder with JVM tests |
| T22 | Profile Board section (screen) | `52-53-profile-head-to-head-heatmap/T22-board-section-screen.md` | T20, T21, T13 | sonnet | Screen from this plan, tests, captures |
| T23 | Head-to-head engine | `52-53-profile-head-to-head-heatmap/T23-head-to-head-engine.md` | T4, T7 | sonnet | Specified HH rules, tests listed |
| T24 | Head-to-head data and state | `52-53-profile-head-to-head-heatmap/T24-head-to-head-state.md` | T23, T9, T10 | sonnet | Loader and state holder with JVM tests |
| T25 | Head-to-head screen | `52-53-profile-head-to-head-heatmap/T25-head-to-head-screen.md` | T24, T13 | sonnet | Screen from this plan, tests, captures |
| T26 | End-screen heatmap state | `52-53-profile-head-to-head-heatmap/T26-end-heatmap-state.md` | T18 | sonnet | Wiring of the journal and a selection event |
| T27 | End-screen heatmap layout | `52-53-profile-head-to-head-heatmap/T27-end-heatmap-layout.md` | T26, T20, N1 to N3 | sonnet | Layout from this plan, captures |

No `opus` task: no stored format change (the CR13 change derives from the journal), no cross-module refactoring
(the back stack stays in `:app`), and no rule left ambiguous after the intake answers. T16 can run right after T1 so
that gate G1 does not block the #52 tasks.

## Decisions

Owner answers, #52 (2026-11):
- Q1 B: only real resets count in a merge (PM4, ST9). Q2 B: the in-game MPR also uses useful marks (CR13). Q3 A:
  a match counts if it started at or after the reset (ST2). Q4 yes: "last 10" after filters and reset, "30 days"
  rolling on the end date (ST4). Q5 A: two independent X01 filters (ST3). Q6 A: solo in averages, not in results
  (ST5). Q7 A: two result blocks with "All variants" (STC5). Q8: 40 (STC4). Q9 ok (STC1). Q10 yes (STX3). Q11 yes
  (STX1, STX4, STX5). Q12 B, and all actions move into the Profile (PM2, PS2, PS7). Q13 yes (ST9).
Owner answers, #53:
- Q1 A draws apart (HH3). Q2 A stored legs (HH4). Q3 same convention as H3, latest date (HH2, HH8). Q4 C combined miss
  label (HM4). Q5 A one single zone (HM1). Q6 A all darts as base (HM2). Q7: simulate both scales (P9 C, T16, G1). Q8
  A band plus top 5 list (HM6). Q9 A all profiles with counts (HH1). Q10 ok (HH6). Q11 ok (HS9).
Owner answers to the intake follow-up:
- P0: one single PR, #52 tasks first then #53, small independently checkable tasks. P1 A: "Gérer le joueur" unfolds
  the actions in the screen. P2 A for the three: merge source lands on the target, delete goes back (History list if
  the detail's match was erased), actions from every entry point (PS7). P3: real back stack without copies (HS9). P4
  A: Board section (PS3, HM3). P5: solo excluded from all results, counted in darts per leg won and best leg (ST5,
  STX7). P6: total plus average per leg (STC4). P7: CR14 stays all marks. P8: date line rule (HH8). P9 C: static SVG
  mockups of both scales before any heatmap code, gate G1 before T20.
- New request: heatmap of the last leg on the end-of-leg and end-of-match screens, with a player switch (HM9).
Design decisions (developer, to be confirmed in design review):
- D1: CR13 derives useful marks from the journal replay, no save format change (Approach 2).
- D2: Profile figures computed without cache; a cache only if the performance tests fail (Approach 3).
- D3: the Profile opens on the X01 tab, period "Since always", filters "All".

## Open questions

For Kosmo (needed before T27 only; the other tasks do not depend on them):
- N1. End-of-leg and end-of-match heatmap placement. A = always visible under the scoreboard (portrait, scrolling;
  the main button stays pinned at the bottom; landscape third column); B = a folded "Voir la cible" row that unfolds
  the heatmap in the screen. Recommendation: A.
- N2. Player shown first on those screens. A = the leg winner (between legs) / the match winner (end); B = the first
  player of the leg. Recommendation: A.
- N3. Tapping a zone on those screens. A = same band as the Profile (zone, darts, %); B = display only.
  Recommendation: A.
Gate:
- G1. Kosmo picks the relative or the absolute scale from `docs/design/mockups/heatmap/` (after T16).
