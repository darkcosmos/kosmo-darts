# Plan: profiles, match archive and History screens (issue #50)

## Goal

Issue #50 (H1 of "Historique et statistiques"): every confirmed X01 or Cricket match is archived locally in full;
players become profiles; Kosmo browses the list of matches and the detail of each, dart by dart. Nothing archived is
ever deleted. Rules: `docs/rules/history.md` (PF, DJ, BC, AR, HS) and `docs/rules/persistence.md` R26 to R29.

## Scope and out of scope

In scope: profiles (automatic creation, suggestions in the setup, launch migration of the last-setup and saved-game
names); the dart journal with entry times and voided darts; nominal board coordinates; save format v5; the Room
database v1 with its exported schema; the archive at the confirmed win, idempotent, with its failure path; History and
match detail screens in portrait and landscape; `docs/backlog.md`.

Out of scope: H2 (free color per match, Players screen: rename, merge, reset), H3 (statistics, profile screen), H4
(heatmap, head to head), training mode (`TRAINING` is only a reserved value), camera data (`CAMERA` reserved), Android
Auto Backup (backlog only), Paging.

## Approach

1. **Dependencies first (T1).** Room 2.8.5 + KSP 2.3.13 + Room Gradle plugin in `:app` (AGP 9 built-in Kotlin,
   Kotlin 2.4.21), schema export, one placeholder entity. Fallback rule D3: if Room 2.8 fails to compile or its KSP
   step fails, switch to Room3 3.0.3, no workaround, no long diagnosis; record it in ADR 0012 and in the notes.
2. **Engine (T2 to T4), pure JVM, clock injected.**
   - `engine/board`: `BoardGeometry` (official dimensions, source cited) and `nominalPoint(dart): BoardPoint?` (BC).
   - `engine/journal`: events (`DartEntered`, `Undone`), `LegJournal`, `MatchJournal(matchId, startedAt, legs)`,
     `Clock` (`fun interface`, `nowMillis()`), and the replay to `JournalDart` (player, visit, rank, bust, voided,
     time) with an X01 and a Cricket leg replayer (DJ1 to DJ5). Alternatives: ADR 0011, "Options".
   - `engine/archive`: `ArchivedMatch` (session times, rules version, mode, variant, settings values, players with
     rank, legs and sets won, legs with set/leg number, starter, winner, darts with nominal points) built from an
     `X01Match` or `CricketMatch`, its `MatchJournal` and the end time; `ENGINE_RULES_VERSION = 1`. Results come from
     `MatchScore` and `MatchRanking` (AR6). The engine knows nothing of colors and app version: the app adds them.
3. **Save format v5 (T5)**, ADR 0011: match id, start time, journals; versions 1 to 4 migrated on read; limits R28.
4. **App journal (T6).** The reducers carry a `MatchJournal` next to the match: "Start" and "Rematch" create the
   match id (`UUID.randomUUID`) and start time; a dart records `DartEntered` with `clock.nowMillis()`; an effective
   undo records `Undone`; a confirmed leg opens a new leg journal. The ViewModel injects the clock.
5. **Database (T7)**, ADR 0012: entities, DAOs, `HistoryDatabase`, `ArchiveRepository.archive(...)` in one
   transaction, `HistoryRepository` reads; mapping from `ArchivedMatch` plus colors (slot to ARGB from
   `PlayerPalette`) and app version (`PackageManager` `versionName`).
6. **Profiles (T8).** `PlayerListRules.nameKey` made public and used for profiles; profiles written at Start/Rematch
   on the persistence queue (PF2); launch migration PF4 on the queue before the first load result is delivered.
7. **Archive flow (T9).** `FinishGameTapped` with a pending win: state `archiving` (button disabled), effect
   `ArchiveMatch` on the persistence queue, then `DeleteGame` (AR2); events `ArchiveSucceeded` (End screen, as
   today) or `ArchiveFailed` (stay, message, AR3). On launch, a save whose match id is archived is deleted (AR4).
8. **Screens (T10 to T12).** Pure state holders tested on the JVM; composables only render. History reads go through
   a read executor (ADR 0012). List: one summary query plus one player query, grouped in memory, `LazyColumn` with
   stable keys. No new mockup: screens composed from `docs/design/design-system.md` components (Q7).

## Files and types added or changed

- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/schemas/**` (T1, T7).
- `game-engine/.../engine/board/*`, `engine/journal/*`, `engine/archive/*`; `engine/player/PlayerListRules.kt`
  (`nameKey` public, T8).
- `app/.../persistence/*`: `GameSaveCodec`, `SaveFormat` (`VERSION_5`), `SavedGame`, `SavedCricketGame`,
  `SaveLoader`, `InvalidSaveReason.INCONSISTENT_JOURNAL`, `Persistence` (archive and migration requests).
- `app/.../history/` (new): entities, DAOs, `HistoryDatabase`, `ArchiveRepository`, `ProfileRepository`,
  `HistoryRepository`, `SettingsText`.
- `app/.../ui/app/*`: `AppState`, `AppReducer`, `CricketReducer`, `SetupTransitions`, `PersistenceEffect`,
  `KosmoDartsViewModel`; `ui/history/*` (new: list, detail, state holders, tags); `ui/home/HomeScreen.kt`; setup name
  rows (`ui/components/PlayerNameRow.kt`).
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`; `docs/backlog.md` (new).

## Data read, stored or exposed

- Stored, private app storage only: the saved game v5 (adds match id, times, journal) and `kosmo-history.db`
  (profiles: names only; matches, darts, times, app version). No personal data beyond the names typed by the players.
- Nothing exposed: no new permission, no provider, no export; backup stays disabled (`allowBackup="false"`).
- Read: the saved game and last setup (PF4 migration).

## Screens and states

- **Setup**: suggestions under the focused name field (PF3): up to 5 rows of 48dp, profile names; none when no
  profile matches. Works with 200 % font and in landscape (list scrolls with the screen).
- **X01 CHECKOUT zone / Cricket win zone**: new states `archiving` (confirm disabled) and `archive failed` (short
  message under the zone, confirm enabled, AR3).
- **Home**: a "History" / « Historique » button under the existing ones (HS1).
- **History**: filter row (mode: segmented All / X01 / Cricket; player: a chip row "All" + profiles, scrollable),
  then the list (HS2); states: empty archive, no match for the filters, loading (nothing shown until the first
  result, no spinner flash under 300 ms), filled.
- **Match detail**: summary card, then one section per leg ("Set 2 · Leg 3", starter, winner), one row per visit:
  player in his color, darts (PC9: name always shown), visit total, a BUST label; legacy legs show the "not
  recorded" line.
- Strings (`values/` and `values-fr/`): History, filters, empty states, row texts, duration, "—", "Bust", leg
  headers, "Darts not recorded for this leg", archive failure message. Exact texts are chosen in T10 to T12 and listed
  in their notes; French first-class.

## Test plan

- **Engine (JUnit, cites rules)**: BC2 nominal point of every zone (20 sectors × S/D/T, 25, Bull, miss), axis
  orientation (20 on +y, 6 on +x, 3 on -y, 11 on -x); DJ2/DJ3 journals with undos across turns, the a,b,u,u,c case,
  bust visits, Cricket no bust; DJ4; DJ5 legacy; archive builder for X01 and Cricket, single leg and 2 sets of 3 legs,
  1 to 4 players, results equal to `MatchScore`/`MatchRanking`, voided darts kept with their positions.
- **App JVM**: v5 codec round trips (both modes, undos, legacy legs), v1 to v4 fixtures read as v5 (R27), each R29
  rejection, size limit both sides (R28); reducers record darts with the fake clock and undos only when effective;
  settings text; history state holders (filters, grouping, duration, "—").
- **Robolectric, in-memory Room**: every DAO; archive idempotent (AR4); archive failure keeps the saved game (AR3,
  failing repository fake and a closed database); profile migration PF4; legacy match archive AR7; 1,000 matches
  listed by the list query under a time budget logged in the notes (no flaky assert below 1 s).
- **Compose UI and Roborazzi** (French, Pixel 7): setup suggestions; History empty, no-match, filled; detail X01 2
  sets, Cricket, legacy leg; CHECKOUT archive failure; each in portrait and landscape, and with `fontScale = 2f`.

## How Kosmo verifies on the phone

1. Update over the previous version with a game in progress: resume it, finish it, confirm: it appears in History
   without start time (duration "—").
2. Play an X01 match in 2 sets of 3 legs with some Undo, confirm: History shows it first, with colors, winner and
   score; the detail shows each leg, visit by visit, the busts marked, the undone darts absent.
3. Play a Cricket match; filter by Cricket, then by a player.
4. On a new setup, focus a name: earlier players are suggested, most recent first; typing "kev" filters; "kevin" for
   an existing "Kevin" shows "Kevin" in History.
5. Abandon a match: it never appears. Rotate every screen; set the font to the largest size.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Room and KSP setup spike, Room3 fallback | `50-history-archive/T1-room-spike.md` | - | sonnet | Build wiring with a written fallback rule, no design choice left |
| T2 | Board geometry and nominal points | `50-history-archive/T2-board-coordinates.md` | - | sonnet | Fully specified rule (BC) with listed tests |
| T3 | Dart journal and replay | `50-history-archive/T3-dart-journal.md` | - | sonnet | Specified engine rules DJ1 to DJ5 with tests |
| T4 | Archived match builder | `50-history-archive/T4-archived-match.md` | T2, T3 | sonnet | Engine mapping over existing match types, rules AR5, AR6 |
| T5 | Save format v5 with migration of v1 to v4 | `50-history-archive/T5-save-v5.md` | T3 | opus | Case: new stored format with its migration (ADR 0011) |
| T6 | Journal, match id and clock in the reducers | `50-history-archive/T6-app-journal.md` | T5 | sonnet | State holder wiring with JVM tests |
| T7 | Room schema v1, DAOs and archive repository | `50-history-archive/T7-room-schema.md` | T1, T4 | opus | Case: new stored format (schema v1 that can never be dropped) |
| T8 | Profiles: creation and launch migration | `50-history-archive/T8-profiles.md` | T7 | sonnet | Specified rules PF1, PF2, PF4 over T7's DAOs |
| T9 | Archive at the confirmed win, failure and AR4 | `50-history-archive/T9-archive-flow.md` | T6, T7, T8 | sonnet | Effect and reducer wiring with specified rules |
| T10 | Profile suggestions in the setup | `50-history-archive/T10-profile-suggestions.md` | T8 | sonnet | Screen change from rules PF3, state holder tests |
| T11 | History screen and home entry | `50-history-archive/T11-history-list.md` | T9 | sonnet | Screen from the design system, state holder tests |
| T12 | Match detail screen | `50-history-archive/T12-match-detail.md` | T11 | sonnet | Screen from the design system, state holder tests |
| T13 | Backlog entries | `50-history-archive/T13-backlog.md` | - | haiku | Two documentation entries, text given |

Two `opus` tasks (T5, T7).

## Decisions

- D1 (Kosmo Q1): keep the last-setup prefill; add the profile list; migrate profiles from the last setup and the saved
  game (at most 4 names each). PF3, PF4.
- D2 (Kosmo Q2, Q3, Q5, Q6, Q7, Q8): suggestions under the focused field, no popup; a profile keeps its first
  spelling; abandon, "New game" then "Delete" and unreadable saves are never archived; filters mode All/X01/Cricket and
  player All/one, no variant filter; screens composed without mockups; duration from Start/Rematch to the
  confirmation, "—" for pre-update matches.
- D3 (issue, fallback rule): T1 tries Room 2.8.5 with KSP 2.3.13. On a compile or KSP failure, it switches to Room3
  3.0.3 at once and reports it; no workaround, no long diagnosis. Versions re-checked at the start of T1.
- D4 (Kosmo Q4): archive failure keeps the CHECKOUT (or Cricket win) zone with a short message; confirm retries. AR3.
- D5 (R12 question): the 10,000-dart limit keeps counting darts in play of the current leg; voided darts do not
  count, so undo still brings saving back as R12 says. The file size limit becomes 1 MiB for every version (R28,
  ADR 0011). The archive is built from memory, never from the file.
- D6: the journal is stored as events (dart with time, undo) and replayed by the engine; voided flags, visits, ranks
  and busts are derived (ADR 0011, "Options").
- D7: entry times are absolute epoch milliseconds; a pre-update match has `started-at=-` and unknown dart times.
- D8: profiles carry `last_used_at` (not asked by the issue) for the "most recently used first" order; set at
  Start/Rematch (PF2), so a profile of an abandoned match also counts as used.
- D9: on launch, a saved game whose match id is already archived is deleted silently (AR4).
- D10: no coroutines code of our own and no Paging: blocking DAOs on the persistence queue (writes) and a read
  executor (History), ADR 0012.
- D11: the X01 variant is the start score (`501`/`301`); the Cricket variant is `STANDARD`/`CUT_THROAT`.
- D12: the profile name key is `PlayerListRules`' duplicate key, made public, so the setup and the profiles never
  disagree on "same name".

## Open questions

None. Design review may add some.
