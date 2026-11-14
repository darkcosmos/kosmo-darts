# Statistics rules (profile statistics, heatmap, head-to-head)

Status: draft for design review (issues #52 and #53, owner answers recorded in
`docs/plans/52-53-profile-head-to-head-heatmap.md`, section "Decisions").
This document is the single source of truth for the statistics, heatmap and head-to-head tests: tests cite these
rules, never the issue text. Rule ids are stable. Prefixes: ST (common statistics), STX (X01 statistics), STC (Cricket
statistics), PS (Profile screen), HM (heatmap), HH (head-to-head). They never collide with R, M, PC, CR, CP, AN, VT, CS,
PF, DJ, BC, AR, HS, PM, AV or the X01 rules (`docs/rules/x01.md`).

## Common

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
  "Since <date>" (date in the device locale format), otherwise "Since always" (owner Q13).
- ST10. An archived match that breaks the rules (settings that do not decode, a position, rank, legs or sets won out
  of range, a leg without darts, an unknown dart value, a dart the engine rejects such as a dart after the win, or a
  replayed leg winner different from the stored one) is ignored by every statistic, heatmap and head-to-head figure,
  and is never replayed past the faulty dart. The other matches are unaffected. When at least one match is ignored,
  the screen shows "n matchs ignorés (données illisibles)" (plural resource). The History list and detail are
  unchanged. The ignored match stays in the database as it is: never deleted, never rewritten (owner D4).

## X01

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

## Cricket

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

## Profile screen

- PS1. Access: a row of the Players screen, a player name in a match detail (HS8), the player picked in a head-to-head
  is not a link. Never for the anonymous profile.
- PS2. Header: the name, "Since always" or "Since <date>" (ST9), the buttons "Manage player" and "Compare with…".
  "Manage player" unfolds inside the screen the four actions Rename, Merge, Reset, Delete (owner P1 A); a second tap
  folds them.
- PS3. Three sections X01, Cricket, Heatmap (owner P4 A; "Heatmap" in English and in French), as tabs at the top; in
  portrait the tabs stay pinned at the top while the header and the content scroll. X01 and Cricket each have their
  filters (ST3) and the period (ST4); the selections survive a rotation and a return from a pushed screen.
- PS4. X01 section: STX1 to STX8 and the trend curve. Cricket section: STC2 to STC5 and the trend curve.
- PS5. Trend curve: one point per selected match in end order, 3-dart average (X01) or MPR (Cricket), drawn with
  Compose Canvas, no chart library. With fewer than 2 points, a short text replaces the curve. The curve shows as text
  its lowest and highest values and the dates of the first and last matches; TalkBack reads the number of matches,
  the first, last, lowest and highest values and the two dates. These values come from the engine (`TrendSummary`),
  never from a composable.
- PS6. States: no match at all, an explicit empty state; no match for the selected filters or period, a message with
  the filters still shown; loading and read failure as HS7.
- PS7. Actions (PM3 to PM10, PM11) behave as before, inside the Profile. After a merge where the shown profile is the
  source, the target's Profile replaces it; after a delete, Back to the previous screen, reloaded, and when that
  screen is the detail of a match the deletion erased, to the History list. The actions are available from every
  entry point (owner P2 A).
- PS8. Design system of `docs/design/`, portrait and landscape, 200% font size, no popup, visible Back with a
  description, 48 dp targets (42 dp tall allowed in landscape).

## Heatmap

- HM1. Zones: single (inner and outer single rings as one zone, owner Q5 A), double and triple of each number 1 to 20,
  25 and Bull: 62 zones. Misses are shown apart, outside the board.
- HM2. A zone's value = its darts / all darts (misses included, voided excluded) of the selection (owner Q6 A).
- HM3. Profile Heatmap section: mode filter All modes / X01 / Cricket, then the filters of the chosen mode (none for
  All modes), then the period (ST4); ST2 applies.
- HM4. Miss label: "Miss" / « Raté » when only X01 darts are counted; "Miss or outside Cricket numbers" / « Raté ou
  hors numéros Cricket » as soon as Cricket darts are counted (Cricket, All modes, a Cricket leg) (owner #53 Q4 C).
  One resource key per label and language; the misses line fits at 200 % font (it wraps, never cut).
- HM5. Colour scale: decided at gate G1 (relative to the most hit zone, or absolute 0 to 100 %); a zone with 0 dart
  has a distinct neutral colour; a legend shows the scale with its numeric end values. The ramp has a monotonic
  luminance (readable in greyscale) and the meaning never relies on colour alone (the band and the top 5 list give
  the numbers). Tokens in `docs/design/design-system.md` section 2.
- HM6. Tapping a zone shows, in a fixed band under the board, the zone name, its darts and its percentage; a new tap
  replaces it; with no zone selected the band says « Touchez une zone ». Zone taps are a supplementary access (the
  smallest zones are far below 48 dp): the text list of the 5 most hit zones (darts and %, ties ordered by count then
  zone order) is the reliable access and serves TalkBack, whose zone text includes the count and the percentage
  (owner #53 Q8 A). Names, counts, percentages and the top 5 come from the state holder, never from a composable. No
  popup.
- HM7. No dart in the selection: explicit empty state.
- HM8. The dartboard component draws values per zone with the BC4 geometry and has no rule logic; taps resolve with
  BC5.
- HM9. End of a leg (between-legs screens M16, CR26) and end of a match (M17, CR27): two tabs at the top of the
  screen (owner N1), "Result" / « Résultat » (the screen as before, unchanged) and "Leg heatmap" / « Manche Heatmap »
  (the term "heatmap" is kept in French). The screen opens on "Result". The "Leg heatmap" tab shows the heatmap of the
  last leg played, for one player at a time, with a player selector (one chip per player, name and colour; none in a
  solo game); the player shown first is the leg winner between legs and the match winner at the end (owner N2 A).
  Same zones, values, scale, misses line, legend and HM4 label; tapping a zone shows the HM6 band (owner N3 A); the
  top 5 list of HM6 is shown too. Derived from the match journal: between legs, the last completed leg (the current
  leg journal is the empty next leg); at the end of a match, the current (finished) leg.
- HM10. On those screens the main actions ("Next leg"; "Rematch" and "New game") are reachable in both tabs (always in
  the right column in landscape between legs, see "Screens and states"). The selected tab, player and zone survive a
  rotation; after a process death the screen reopens on "Result" with the default player (nothing new is saved). A
  new between-legs screen, a rematch or a new game opens on "Result".

## Head-to-head

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
- HH9. No common match: explicit empty state. PS8 applies. The picker, the selected pair, the filters and the period
  are kept in the screen state in `AppState`: they survive a rotation and a return from a common match detail. ST10
  applies (notice of ignored matches).

## Related rules

- Cricket: CR13 (MPR), CR15 (dead targets) (`docs/rules/cricket.md`).
- History: PM2 to PM11 (Players and Profile actions), HS5, HS8, HS9 (history screens), BC2, BC4, BC5 (board
  coordinates and zones), AR5, AR6 (archived match and stored results) (`docs/rules/history.md`).
